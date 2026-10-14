package dev.penned.app.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.RecurringRuleEntity
import dev.penned.app.domain.Money
import dev.penned.app.domain.RecurringMath
import dev.penned.app.domain.RecurringPeriod
import dev.penned.app.domain.Templates
import dev.penned.app.ui.assets.selectableAccounts as visibleAccounts
import dev.penned.app.domain.TxType
import dev.penned.app.ui.add.AccountPickerSheet
import dev.penned.app.ui.components.ConfirmDialog
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedSegmentedTabs
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.iconMutedColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** 新建/编辑周期规则页状态（M3-6）。 */
data class RecurringEditUiState(
    val ruleId: String? = null,
    val name: String = "",
    val type: TxType = TxType.EXPENSE,
    val amountText: String = "",
    val categoryId: String? = null,
    val accountId: String? = null,
    val targetAccountId: String? = null,
    val note: String = "",
    val feeText: String = "",
    val period: RecurringPeriod = RecurringPeriod.MONTHLY,
    val intervalText: String = "1",
    val startEpochDay: Long = LocalDate.now().toEpochDay(),
    val endEpochDay: Long? = null,
    val enabled: Boolean = true,
    val loaded: Boolean = false,
) {
    val isEdit: Boolean get() = ruleId != null

    val amountCents: Long get() = Money.parseToCents(amountText) ?: 0L

    val interval: Int get() = intervalText.toIntOrNull()?.coerceIn(1, 99) ?: 1

    val canSave: Boolean
        get() {
            if (!loaded || name.isBlank() || amountCents <= 0L) return false
            return if (type == TxType.TRANSFER) {
                accountId != null && targetAccountId != null && accountId != targetAccountId
            } else {
                categoryId != null
            }
        }
}

/**
 * 周期规则编辑（M3-6）。`nextEpochDay` 只在**新建**时按起始日初始化；
 * 编辑改起始日**不动已生成的指针**（否则改一次日期就会把补记窗口重新拉开一遍——
 * 幂等会挡住重复期次，但语义上"已补到哪儿"是运行状态，不该被编辑覆盖）。
 */
class RecurringEditViewModel(
    private val app: PennedApplication,
    private val editId: String?,
) : ViewModel() {

    private val db = app.database

    private val _state = MutableStateFlow(RecurringEditUiState(ruleId = editId, loaded = editId == null))
    val state: StateFlow<RecurringEditUiState> = _state

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentBookId = app.settings.currentBookId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<CategoryEntity>> = currentBookId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else db.categoryDao().observeByBook(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val accounts: StateFlow<List<AccountEntity>> = currentBookId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else db.accountDao().observeByBook(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 选择器可见账户（M3-9：停用的不列；编辑时保留本页已选中的那个）。 */
    val selectableAccounts: StateFlow<List<AccountEntity>> = combine(accounts, state) { all, s ->
        visibleAccounts(all, listOf(s.accountId, s.targetAccountId))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectableCategories: StateFlow<List<Pair<CategoryEntity, String>>> = combine(categories, state) { all, s ->
        val hiddenIds = all.filter { it.hidden }.map { it.id }.toSet()
        all.filter { c -> c.id == s.categoryId || (!c.hidden && (c.parentId == null || c.parentId !in hiddenIds)) }
            .map { c -> c to (c.parentId?.let { pid -> all.firstOrNull { it.id == pid }?.name } ?: "") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (editId != null) {
            viewModelScope.launch {
                val r = db.recurringDao().get(editId) ?: return@launch
                _state.value = RecurringEditUiState(
                    ruleId = r.id,
                    name = r.name,
                    type = r.type,
                    amountText = if (r.amountCents > 0L) Money.format(r.amountCents, grouped = false) else "",
                    categoryId = r.categoryId,
                    accountId = r.accountId,
                    targetAccountId = r.targetAccountId,
                    note = r.note.orEmpty(),
                    feeText = if (r.feeCents > 0L) Money.format(r.feeCents, grouped = false) else "",
                    period = r.periodType,
                    intervalText = r.interval.toString(),
                    startEpochDay = r.startEpochDay,
                    endEpochDay = r.endEpochDay,
                    enabled = r.enabled,
                    loaded = true,
                )
            }
        }
    }

    fun setName(name: String) = update { it.copy(name = name) }

    fun pickType(type: TxType) = update {
        if (type == TxType.TRANSFER) it.copy(type = type, categoryId = null)
        else it.copy(type = type, targetAccountId = null, feeText = "")
    }

    fun setAmount(text: String) = update { it.copy(amountText = text.filter { ch -> ch.isDigit() || ch == '.' }) }
    fun pickCategory(id: String) = update { it.copy(categoryId = id) }
    fun pickAccount(id: String?) = update { it.copy(accountId = id) }
    fun pickTargetAccount(id: String?) = update { it.copy(targetAccountId = id) }
    fun setNote(text: String) = update { it.copy(note = text) }
    fun setFee(text: String) = update { it.copy(feeText = text.filter { ch -> ch.isDigit() || ch == '.' }) }
    fun pickPeriod(period: RecurringPeriod) = update { it.copy(period = period) }
    fun setInterval(text: String) = update { it.copy(intervalText = text.filter { ch -> ch.isDigit() }.take(2)) }
    fun setStartDay(day: Long) = update { it.copy(startEpochDay = day) }
    fun setEndDay(day: Long?) = update { it.copy(endEpochDay = day) }
    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            if (!s.canSave) return@launch
            val now = System.currentTimeMillis()
            val existing = s.ruleId?.let { db.recurringDao().get(it) }
            val rule = existing?.copy(
                name = s.name.trim(),
                type = s.type,
                amountCents = s.amountCents,
                categoryId = if (s.type == TxType.TRANSFER) null else s.categoryId,
                accountId = s.accountId,
                targetAccountId = if (s.type == TxType.TRANSFER) s.targetAccountId else null,
                note = s.note.trim().ifEmpty { null },
                feeCents = if (s.type == TxType.TRANSFER) (Money.parseToCents(s.feeText) ?: 0L) else 0L,
                periodType = s.period,
                interval = s.interval,
                startEpochDay = s.startEpochDay,
                endEpochDay = s.endEpochDay,
                enabled = s.enabled,
                updatedAt = now,
            ) ?: run {
                val bookId = app.settings.currentBookId.firstOrNull() ?: return@launch
                RecurringRuleEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    name = s.name.trim(),
                    type = s.type,
                    amountCents = s.amountCents,
                    categoryId = if (s.type == TxType.TRANSFER) null else s.categoryId,
                    accountId = s.accountId,
                    targetAccountId = if (s.type == TxType.TRANSFER) s.targetAccountId else null,
                    note = s.note.trim().ifEmpty { null },
                    feeCents = if (s.type == TxType.TRANSFER) (Money.parseToCents(s.feeText) ?: 0L) else 0L,
                    periodType = s.period,
                    interval = s.interval,
                    startEpochDay = s.startEpochDay,
                    // 新建：下一期 = 起始日（打开即补会把 ≤ 今天的期数一次补齐）
                    nextEpochDay = s.startEpochDay,
                    endEpochDay = s.endEpochDay,
                    enabled = s.enabled,
                    createdAt = now,
                    updatedAt = now,
                    deviceId = app.deviceId,
                )
            }
            db.recurringDao().upsert(rule)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val id = _state.value.ruleId ?: return@launch
            db.recurringDao().softDelete(id, System.currentTimeMillis())
            onDone()
        }
    }

    private fun update(block: (RecurringEditUiState) -> RecurringEditUiState) {
        _state.value = block(_state.value)
    }
}

@Composable
fun RecurringEditScreen(
    editId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: RecurringEditViewModel = viewModel(
        key = "recurring-edit-${editId.orEmpty()}",
        initializer = { RecurringEditViewModel(app, editId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.selectableCategories.collectAsStateWithLifecycle()
    // 停用账户不进选择器（M3-9）
    val accounts by viewModel.selectableAccounts.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    RecurringEditContent(
        state = state,
        categories = categories,
        accounts = accounts,
        onBack = onBack,
        onNameChange = viewModel::setName,
        onPickType = viewModel::pickType,
        onAmountChange = viewModel::setAmount,
        onPickCategory = viewModel::pickCategory,
        onPickAccount = viewModel::pickAccount,
        onPickTargetAccount = viewModel::pickTargetAccount,
        onNoteChange = viewModel::setNote,
        onFeeChange = viewModel::setFee,
        onPickPeriod = viewModel::pickPeriod,
        onIntervalChange = viewModel::setInterval,
        onPickStartDay = viewModel::setStartDay,
        onPickEndDay = viewModel::setEndDay,
        onToggleEnabled = viewModel::setEnabled,
        onSave = { viewModel.save(onDone = onBack) },
        onDelete = if (state.isEdit) ({ confirmDelete = true }) else null,
        modifier = modifier,
    )

    if (confirmDelete) {
        ConfirmDialog(
            onDismiss = { confirmDelete = false },
            title = "删除规则？",
            text = "「${state.name}」不再自动补记；**已生成的流水保留**（钱确实记过），不随规则一起删。",
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onDone = onBack)
            },
        )
    }
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@Composable
fun RecurringEditContent(
    state: RecurringEditUiState,
    categories: List<Pair<CategoryEntity, String>>,
    accounts: List<AccountEntity>,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onPickType: (TxType) -> Unit,
    onAmountChange: (String) -> Unit,
    onPickCategory: (String) -> Unit,
    onPickAccount: (String?) -> Unit,
    onPickTargetAccount: (String?) -> Unit,
    onNoteChange: (String) -> Unit,
    onFeeChange: (String) -> Unit,
    onPickPeriod: (RecurringPeriod) -> Unit,
    onIntervalChange: (String) -> Unit,
    onPickStartDay: (Long) -> Unit,
    onPickEndDay: (Long?) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    var showCategory by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showTarget by remember { mutableStateOf(false) }
    var datePickerFor by remember { mutableStateOf<RuleDateField?>(null) }

    val categoryName = categories.firstOrNull { it.first.id == state.categoryId }
        ?.let { (c, parent) -> listOfNotNull(parent.ifEmpty { null }, c.name).joinToString(" · ") }
    val accountName = accounts.firstOrNull { it.id == state.accountId }?.name
    val targetName = accounts.firstOrNull { it.id == state.targetAccountId }?.name

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = if (state.isEdit) "编辑规则" else "新建规则", onBack = onBack) },
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                // 卡片一：名称 / 类型 / 金额
                PennedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column(Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md)) {
                        PennedSegmentedTabs(
                            options = TX_TYPES,
                            selected = state.type,
                            onSelect = onPickType,
                            label = { Templates.typeLabel(it) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    PennedDivider()
                    RuleFieldRow(label = "名称", value = state.name, placeholder = "比如「房租」", onValueChange = onNameChange)
                    PennedDivider()
                    RuleFieldRow(label = "金额", value = state.amountText, placeholder = "0.00", money = true, onValueChange = onAmountChange)
                }

                // 卡片二：分类/账户（转账两端）
                PennedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    if (state.type == TxType.TRANSFER) {
                        RulePickerRow(label = "转出账户", value = accountName ?: "选择账户", onClick = { showAccount = true })
                        PennedDivider()
                        RulePickerRow(label = "转入账户", value = targetName ?: "选择账户", onClick = { showTarget = true })
                        PennedDivider()
                        RuleFieldRow(label = "手续费", value = state.feeText, placeholder = "选填", money = true, onValueChange = onFeeChange)
                    } else {
                        RulePickerRow(label = "分类", value = categoryName ?: "选择分类", onClick = { showCategory = true })
                        PennedDivider()
                        RulePickerRow(label = "账户", value = accountName ?: "无账户", onClick = { showAccount = true })
                    }
                }

                // 卡片三：周期 / 起止
                PennedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column(Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md)) {
                        PennedSegmentedTabs(
                            options = PERIODS,
                            selected = state.period,
                            onSelect = onPickPeriod,
                            label = { periodShortLabel(it) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    PennedDivider()
                    RuleFieldRow(
                        label = "每隔",
                        value = state.intervalText,
                        placeholder = "1",
                        number = true,
                        suffix = periodUnit(state.period),
                        onValueChange = onIntervalChange,
                    )
                    PennedDivider()
                    RulePickerRow(label = "起始日", value = dev.penned.app.domain.DebtText.fullDate(state.startEpochDay), onClick = { datePickerFor = RuleDateField.START })
                    PennedDivider()
                    RulePickerRow(
                        label = "结束日",
                        value = state.endEpochDay?.let { dev.penned.app.domain.DebtText.fullDate(it) } ?: "不设",
                        onClick = { datePickerFor = RuleDateField.END },
                    )
                    PennedDivider()
                    RuleFieldRow(label = "备注", value = state.note, placeholder = "选填", onValueChange = onNoteChange)
                }

                // 编辑时：启停 + 一句口径说明
                if (state.isEdit) {
                    PennedCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        PennedListRow(
                            title = if (state.enabled) "已启用" else "已停用",
                            subtitle = "停用后不再自动补记；已生成的流水不受影响",
                            onClick = { onToggleEnabled(!state.enabled) },
                            showChevron = false,
                        )
                    }
                }
                Spacer(Modifier.height(PennedSpacing.xxl))
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md)) {
                PennedButton(text = "保存", onClick = onSave, enabled = state.canSave)
                if (onDelete != null) {
                    Spacer(Modifier.height(PennedSpacing.xs))
                    PennedButton(text = "删除规则", onClick = onDelete, variant = PennedButtonVariant.Danger)
                }
            }
        }
    }

    if (showCategory) {
        PennedSheet(onDismiss = { showCategory = false }, title = "选择分类") {
            categories.forEach { (c, parent) ->
                PennedListRow(
                    title = listOfNotNull(parent.ifEmpty { null }, c.name).joinToString(" · "),
                    trailing = if (c.id == state.categoryId) "已选" else null,
                    showChevron = false,
                    onClick = {
                        onPickCategory(c.id)
                        showCategory = false
                    },
                )
            }
        }
    }
    if (showAccount) {
        AccountPickerSheet(
            accounts = accounts,
            selectedId = state.accountId,
            includeNone = true,
            onPick = { onPickAccount(it); showAccount = false },
            onDismiss = { showAccount = false },
        )
    }
    if (showTarget) {
        AccountPickerSheet(
            accounts = accounts,
            selectedId = state.targetAccountId,
            includeNone = false,
            onPick = { onPickTargetAccount(it); showTarget = false },
            onDismiss = { showTarget = false },
        )
    }

    val picking = datePickerFor
    if (picking != null) {
        val initial = when (picking) {
            RuleDateField.START -> state.startEpochDay
            RuleDateField.END -> state.endEpochDay ?: LocalDate.now().toEpochDay()
        }
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = dev.penned.app.domain.AddDate.dayToPickerMillis(initial),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { datePickerFor = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val day = dev.penned.app.domain.AddDate.pickerMillisToDay(millis)
                        if (picking == RuleDateField.START) onPickStartDay(day) else onPickEndDay(day)
                    }
                    datePickerFor = null
                }) { Text("确定") }
            },
            dismissButton = {
                if (picking == RuleDateField.END && state.endEpochDay != null) {
                    TextButton(onClick = { onPickEndDay(null); datePickerFor = null }) { Text("清除") }
                } else {
                    TextButton(onClick = { datePickerFor = null }) { Text("取消") }
                }
            },
        ) {
            androidx.compose.material3.DatePicker(
                state = pickerState,
                title = { Text(if (picking == RuleDateField.START) "选择起始日" else "选择结束日", modifier = Modifier.padding(PennedSpacing.lg)) },
            )
        }
    }
}

private enum class RuleDateField { START, END }

private val TX_TYPES = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)
private val PERIODS = listOf(RecurringPeriod.DAILY, RecurringPeriod.WEEKLY, RecurringPeriod.MONTHLY, RecurringPeriod.YEARLY)

/** 分段里用最短的词（每天/每周/每月/每年 只留「天/周/月/年」）。 */
private fun periodShortLabel(period: RecurringPeriod): String = when (period) {
    RecurringPeriod.DAILY -> "天"
    RecurringPeriod.WEEKLY -> "周"
    RecurringPeriod.MONTHLY -> "月"
    RecurringPeriod.YEARLY -> "年"
}

private fun periodUnit(period: RecurringPeriod): String = when (period) {
    RecurringPeriod.DAILY -> "天一次"
    RecurringPeriod.WEEKLY -> "周一次"
    RecurringPeriod.MONTHLY -> "个月一次"
    RecurringPeriod.YEARLY -> "年一次"
}

/** 表单行：左标签 + 输入（+ 可选后缀）。 */
@Composable
private fun RuleFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    money: Boolean = false,
    number: Boolean = false,
    suffix: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = PennedDimens.rowMinHeight).padding(horizontal = PennedSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = PennedTextStyles.rowTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(PennedDimens.formLabel),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = PennedTextStyles.rowTitle.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    number -> KeyboardType.Number
                    money -> KeyboardType.Decimal
                    else -> KeyboardType.Text
                },
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = PennedTextStyles.rowTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
        if (suffix != null) {
            Spacer(Modifier.width(PennedSpacing.sm))
            Text(suffix, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 选择行：左标签 + 右侧值 + 箭头。 */
@Composable
private fun RulePickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = PennedDimens.rowMinHeight).clickable(onClick = onClick).padding(horizontal = PennedSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = PennedTextStyles.rowTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(PennedDimens.formLabel),
        )
        Spacer(Modifier.weight(1f))
        Text(value, style = PennedTextStyles.rowTitle)
        Spacer(Modifier.width(PennedSpacing.sm))
        Icon(
            PennedIcons.ChevronRight,
            contentDescription = null,
            tint = iconMutedColor(),
            modifier = Modifier.size(PennedDimens.iconChevron),
        )
    }
}

@Preview(showBackground = true, name = "新建周期规则", widthDp = 400, heightDp = 800)
@Composable
private fun RecurringEditPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        RecurringEditContent(
            state = RecurringEditUiState(
                name = "房租", amountText = "2000.00", categoryId = "c1", accountId = "a1", loaded = true,
            ),
            categories = listOf(
                CategoryEntity(
                    id = "c1", bookId = "b1", name = "居家生活",
                    type = dev.penned.app.domain.CategoryType.EXPENSE,
                    createdAt = 0, updatedAt = 0, deviceId = "d",
                ) to "",
            ),
            accounts = listOf(
                AccountEntity(
                    id = "a1", bookId = "b1", name = "储蓄卡TestCard",
                    type = dev.penned.app.domain.AccountType.DEBIT,
                    createdAt = 0, updatedAt = 0, deviceId = "d",
                ),
            ),
            onBack = {},
            onNameChange = {}, onPickType = {}, onAmountChange = {}, onPickCategory = {},
            onPickAccount = {}, onPickTargetAccount = {}, onNoteChange = {}, onFeeChange = {},
            onPickPeriod = {}, onIntervalChange = {}, onPickStartDay = {}, onPickEndDay = {},
            onToggleEnabled = {}, onSave = {},
        )
    }
}
