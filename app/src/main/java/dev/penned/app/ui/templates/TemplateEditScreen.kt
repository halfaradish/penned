package dev.penned.app.ui.templates

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.data.db.entity.TxTemplateEntity
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.Money
import dev.penned.app.domain.Templates
import dev.penned.app.ui.assets.selectableAccounts as visibleAccounts
import dev.penned.app.domain.TxType
import dev.penned.app.ui.add.AccountPickerSheet
import dev.penned.app.ui.add.TagPickerSheet
import dev.penned.app.ui.components.ConfirmDialog
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedSegmentedTabs
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.TagCreateDialog
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
import java.util.UUID

/** 新建/编辑模板页状态（M3-5）。 */
data class TemplateEditUiState(
    val templateId: String? = null,
    val name: String = "",
    val type: TxType = TxType.EXPENSE,
    val amountText: String = "",
    val categoryId: String? = null,
    val accountId: String? = null,
    val targetAccountId: String? = null,
    val note: String = "",
    val feeText: String = "",
    val tagIds: Set<String> = emptySet(),
    /** 数据到位标记：编辑/从流水预填是异步读库，读完才允许保存（免得空表单被存下去）。 */
    val loaded: Boolean = false,
) {
    val isEdit: Boolean get() = templateId != null

    val amountCents: Long get() = Money.parseToCents(amountText) ?: 0L

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
 * 模板编辑（M3-5）。三种进入方式：新建 / 编辑已有 / **从流水存为模板**（`fromTxId`，
 * 首页保存提示条上的「存为模板」——金额取落库净额）。
 */
class TemplateEditViewModel(
    private val app: PennedApplication,
    private val editId: String?,
    private val fromTxId: String?,
) : ViewModel() {

    private val db = app.database

    private val _state = MutableStateFlow(TemplateEditUiState(templateId = editId, loaded = editId == null && fromTxId == null))
    val state: StateFlow<TemplateEditUiState> = _state

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val tags: StateFlow<List<TagEntity>> = currentBookId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else db.tagDao().observeByBook(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 分类选择器：一级 + 它的二级（用 `父 · 子` 展示），隐藏的分类不进选择器（M3-1 同口径）。 */
    val selectableCategories: StateFlow<List<Pair<CategoryEntity, String>>> = combine(categories, state) { all, s ->
        val hiddenIds = all.filter { it.hidden }.map { it.id }.toSet()
        val keep = s.categoryId
        all.filter { c -> c.id == keep || (!c.hidden && (c.parentId == null || c.parentId !in hiddenIds)) }
            .map { c -> c to (c.parentId?.let { pid -> all.firstOrNull { it.id == pid }?.name } ?: "") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        when {
            editId != null -> viewModelScope.launch {
                val t = db.templateDao().get(editId) ?: return@launch
                _state.value = TemplateEditUiState(
                    templateId = t.id,
                    name = t.name,
                    type = t.type,
                    amountText = if (t.amountCents > 0L) Money.format(t.amountCents, grouped = false) else "",
                    categoryId = t.categoryId,
                    accountId = t.accountId,
                    targetAccountId = t.targetAccountId,
                    note = t.note.orEmpty(),
                    feeText = if (t.feeCents > 0L) Money.format(t.feeCents, grouped = false) else "",
                    tagIds = db.templateDao().tagsForTemplate(t.id).map { it.id }.toSet(),
                    loaded = true,
                )
            }
            fromTxId != null -> viewModelScope.launch {
                val tx = db.transactionDao().get(fromTxId) ?: return@launch
                val prefill = Templates.of(tx, db.tagDao().tagsForTx(fromTxId).map { it.id })
                _state.value = TemplateEditUiState(
                    name = "",
                    type = prefill.type,
                    amountText = prefill.amountText,
                    categoryId = prefill.categoryId,
                    accountId = prefill.accountId,
                    targetAccountId = prefill.targetAccountId,
                    note = prefill.note.orEmpty(),
                    feeText = if (prefill.feeCents > 0L) Money.format(prefill.feeCents, grouped = false) else "",
                    tagIds = prefill.tagIds.toSet(),
                    loaded = true,
                )
            }
        }
    }

    fun setName(name: String) = update { it.copy(name = name) }

    fun pickType(type: TxType) = update {
        // 转账没有分类、也没有"分类之外的退款/报销"；切回非转账时清掉转入端（与记一笔同口径）
        when (type) {
            TxType.TRANSFER -> it.copy(type = type, categoryId = null)
            else -> it.copy(type = type, targetAccountId = null, feeText = "")
        }
    }

    fun setAmount(text: String) = update { it.copy(amountText = text.filter { ch -> ch.isDigit() || ch == '.' }) }
    fun pickCategory(id: String) = update { it.copy(categoryId = id) }
    fun pickAccount(id: String?) = update { it.copy(accountId = id) }
    fun pickTargetAccount(id: String?) = update { it.copy(targetAccountId = id) }
    fun setNote(text: String) = update { it.copy(note = text) }
    fun setFee(text: String) = update { it.copy(feeText = text.filter { ch -> ch.isDigit() || ch == '.' }) }

    fun toggleTag(id: String) = update {
        val next = if (id in it.tagIds) it.tagIds - id else it.tagIds + id
        it.copy(tagIds = next)
    }

    /** 模板页里新建标签并直接选中（与记一笔同一口径：两级，parentId 为空 = 一级）。 */
    fun addTagAndSelect(name: String, parentId: String?) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@launch
            val bookId = app.settings.currentBookId.firstOrNull() ?: return@launch
            val siblings = db.tagDao().allInBook(bookId).filter { it.parentId == parentId }
            val now = System.currentTimeMillis()
            val tag = TagEntity(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                parentId = parentId,
                name = trimmed,
                sortOrder = siblings.size,
                createdAt = now,
                updatedAt = now,
                deviceId = app.deviceId,
            )
            db.tagDao().upsert(tag)
            update { it.copy(tagIds = it.tagIds + tag.id) }
        }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            if (!s.canSave) return@launch
            val now = System.currentTimeMillis()
            val bookId = s.templateId?.let { db.templateDao().get(it)?.bookId }
                ?: app.settings.currentBookId.firstOrNull() ?: return@launch
            val existing = s.templateId?.let { db.templateDao().get(it) }
            val template = existing?.copy(
                name = s.name.trim(),
                type = s.type,
                amountCents = s.amountCents,
                categoryId = if (s.type == TxType.TRANSFER) null else s.categoryId,
                accountId = s.accountId,
                targetAccountId = if (s.type == TxType.TRANSFER) s.targetAccountId else null,
                note = s.note.trim().ifEmpty { null },
                feeCents = if (s.type == TxType.TRANSFER) (Money.parseToCents(s.feeText) ?: 0L) else 0L,
                updatedAt = now,
            ) ?: TxTemplateEntity(
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
                // 新模板排到最后（列表按 sortOrder）
                sortOrder = db.templateDao().countInBook(bookId),
                createdAt = now,
                updatedAt = now,
                deviceId = app.deviceId,
            )
            db.templateDao().upsert(template)
            db.templateDao().setTagsForTemplate(template.id, s.tagIds.toList())
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val id = _state.value.templateId ?: return@launch
            db.templateDao().deleteTemplate(id, System.currentTimeMillis())
            onDone()
        }
    }

    private fun update(block: (TemplateEditUiState) -> TemplateEditUiState) {
        _state.value = block(_state.value)
    }
}

@Composable
fun TemplateEditScreen(
    editId: String?,
    fromTxId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: TemplateEditViewModel = viewModel(
        key = "template-edit-${editId.orEmpty()}-${fromTxId.orEmpty()}",
        initializer = { TemplateEditViewModel(app, editId, fromTxId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.selectableCategories.collectAsStateWithLifecycle()
    // 停用账户不进选择器（M3-9）
    val accounts by viewModel.selectableAccounts.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    TemplateEditContent(
        state = state,
        categories = categories,
        accounts = accounts,
        tags = tags,
        onBack = onBack,
        onNameChange = viewModel::setName,
        onPickType = viewModel::pickType,
        onAmountChange = viewModel::setAmount,
        onPickCategory = viewModel::pickCategory,
        onPickAccount = viewModel::pickAccount,
        onPickTargetAccount = viewModel::pickTargetAccount,
        onNoteChange = viewModel::setNote,
        onFeeChange = viewModel::setFee,
        onToggleTag = viewModel::toggleTag,
        onCreateTag = { name, parentId -> viewModel.addTagAndSelect(name, parentId) },
        onSave = { viewModel.save(onDone = onBack) },
        onDelete = if (state.isEdit) ({ confirmDelete = true }) else null,
        modifier = modifier,
    )

    if (confirmDelete) {
        ConfirmDialog(
            onDismiss = { confirmDelete = false },
            title = "删除模板？",
            text = "「${state.name}」将从模板列表移除；**已用它记下的流水不受影响**（模板只是记账的草稿）。",
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onDone = onBack)
            },
        )
    }
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@Composable
fun TemplateEditContent(
    state: TemplateEditUiState,
    categories: List<Pair<CategoryEntity, String>>,
    accounts: List<AccountEntity>,
    tags: List<TagEntity>,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onPickType: (TxType) -> Unit,
    onAmountChange: (String) -> Unit,
    onPickCategory: (String) -> Unit,
    onPickAccount: (String?) -> Unit,
    onPickTargetAccount: (String?) -> Unit,
    onNoteChange: (String) -> Unit,
    onFeeChange: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onCreateTag: (String, String?) -> Unit = { _, _ -> },
    onDelete: (() -> Unit)? = null,
) {
    var showCategory by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showTarget by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var showTagCreate by remember { mutableStateOf(false) }

    val categoryName = categories.firstOrNull { it.first.id == state.categoryId }
        ?.let { (c, parent) -> listOfNotNull(parent.ifEmpty { null }, c.name).joinToString(" · ") }
    val accountName = accounts.firstOrNull { it.id == state.accountId }?.name
    val targetName = accounts.firstOrNull { it.id == state.targetAccountId }?.name

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PennedTopBar(title = if (state.isEdit) "编辑模板" else "新建模板", onBack = onBack)
        },
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
                    FormFieldRow(
                        label = "名称",
                        value = state.name,
                        placeholder = "比如「午饭」",
                        onValueChange = onNameChange,
                    )
                    PennedDivider()
                    FormFieldRow(
                        label = "金额",
                        value = state.amountText,
                        placeholder = "0.00",
                        money = true,
                        onValueChange = onAmountChange,
                    )
                }

                // 卡片二：分类（非转账）/ 两端账户（转账）/ 账户
                PennedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    if (state.type == TxType.TRANSFER) {
                        PickerFormRow(label = "转出账户", value = accountName ?: "选择账户", onClick = { showAccount = true })
                        PennedDivider()
                        PickerFormRow(label = "转入账户", value = targetName ?: "选择账户", onClick = { showTarget = true })
                        PennedDivider()
                        FormFieldRow(
                            label = "手续费",
                            value = state.feeText,
                            placeholder = "选填",
                            money = true,
                            onValueChange = onFeeChange,
                        )
                    } else {
                        PickerFormRow(
                            label = "分类",
                            value = categoryName ?: "选择分类",
                            onClick = { showCategory = true },
                        )
                        PennedDivider()
                        PickerFormRow(label = "账户", value = accountName ?: "无账户", onClick = { showAccount = true })
                    }
                }

                // 卡片三：备注 / 标签
                PennedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    FormFieldRow(
                        label = "备注",
                        value = state.note,
                        placeholder = "选填",
                        onValueChange = onNoteChange,
                    )
                    PennedDivider()
                    PickerFormRow(
                        label = "标签",
                        value = if (state.tagIds.isEmpty()) "不选" else "已选 ${state.tagIds.size} 个",
                        onClick = { showTags = true },
                    )
                }
                Spacer(Modifier.height(PennedSpacing.xxl))
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md)) {
                PennedButton(text = "保存", onClick = onSave, enabled = state.canSave)
                if (onDelete != null) {
                    Spacer(Modifier.height(PennedSpacing.xs))
                    PennedButton(
                        text = "删除模板",
                        onClick = onDelete,
                        variant = PennedButtonVariant.Danger,
                    )
                }
            }
        }
    }

    if (showCategory) {
        PennedSheet(onDismiss = { showCategory = false }, title = "选择分类") {
            categories.forEach { (c, parent) ->
                val label = listOfNotNull(parent.ifEmpty { null }, c.name).joinToString(" · ")
                PennedListRow(
                    title = label,
                    trailing = if (c.id == state.categoryId) "已选" else null,
                    showChevron = false,
                    onClick = {
                        onPickCategory(c.id)
                        showCategory = false
                    },
                )
            }
            if (categories.isEmpty()) {
                Text(
                    "还没有分类——去记一笔页右上角「管理分类」建一个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PennedSpacing.xl, vertical = PennedSpacing.sm),
                )
            }
        }
    }
    if (showAccount) {
        AccountPickerSheet(
            accounts = accounts,
            selectedId = state.accountId,
            includeNone = true,
            onPick = {
                onPickAccount(it)
                showAccount = false
            },
            onDismiss = { showAccount = false },
        )
    }
    if (showTarget) {
        AccountPickerSheet(
            accounts = accounts,
            selectedId = state.targetAccountId,
            includeNone = false,
            onPick = {
                onPickTargetAccount(it)
                showTarget = false
            },
            onDismiss = { showTarget = false },
        )
    }
    if (showTags) {
        TagPickerSheet(
            tags = tags,
            selectedIds = state.tagIds,
            onToggle = onToggleTag,
            // 新建走独立弹窗（与记一笔同一做法：先收抽屉，建完即选中）
            onCreateRequest = {
                showTags = false
                showTagCreate = true
            },
            onDismiss = { showTags = false },
        )
    }
    if (showTagCreate) {
        TagCreateDialog(
            parents = tags.filter { it.parentId == null }.sortedBy { it.sortOrder },
            onConfirm = { name, parentId ->
                onCreateTag(name, parentId)
                showTagCreate = false
            },
            onDismiss = { showTagCreate = false },
        )
    }
}

private val TX_TYPES = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)

/** 表单行：左标签 + 输入（与账户/债务编辑页同一套排版）。 */
@Composable
private fun FormFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    money: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .padding(horizontal = PennedSpacing.lg),
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
            keyboardOptions = KeyboardOptions(keyboardType = if (money) KeyboardType.Decimal else KeyboardType.Text),
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
    }
}

/** 选择行：左标签 + 右侧值 + 箭头。 */
@Composable
private fun PickerFormRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.lg),
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

@Preview(showBackground = true, name = "新建模板", widthDp = 400, heightDp = 800)
@Composable
private fun TemplateEditPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        TemplateEditContent(
            state = TemplateEditUiState(
                name = "午饭",
                amountText = "16.50",
                categoryId = "c1",
                accountId = "a1",
                note = "公司楼下",
                loaded = true,
            ),
            categories = listOf(
                CategoryEntity(
                    id = "c1", bookId = "b1", name = "食品餐饮", type = CategoryType.EXPENSE,
                    createdAt = 0, updatedAt = 0, deviceId = "d",
                ) to "",
            ),
            accounts = listOf(
                AccountEntity(
                    id = "a1", bookId = "b1", name = "微信", type = dev.penned.app.domain.AccountType.DEBIT,
                    createdAt = 0, updatedAt = 0, deviceId = "d",
                ),
            ),
            tags = emptyList(),
            onBack = {},
            onNameChange = {}, onPickType = {}, onAmountChange = {}, onPickCategory = {},
            onPickAccount = {}, onPickTargetAccount = {}, onNoteChange = {}, onFeeChange = {},
            onToggleTag = {}, onSave = {},
        )
    }
}
