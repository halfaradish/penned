package dev.penned.app.ui.wishlist

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.WishlistItemEntity
import dev.penned.app.domain.AddDate
import dev.penned.app.domain.DebtText
import dev.penned.app.domain.Money
import dev.penned.app.domain.WishlistStatus
import dev.penned.app.ui.components.ConfirmDialog
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedSegmentedTabs
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedSwitchRow
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** 「手动存 / 跟随账户」两段切换器的定宽：比卡头那档宽一点（"跟随账户"四个字比"支出/收入"长）。 */
private val SAVED_MODE_TOGGLE = PennedDimens.segmentToggle + 56.dp

/** 存入方式（M3-15）：手动存 / 跟随某账户余额。 */
enum class SavedMode { MANUAL, FOLLOW_ACCOUNT }

/** 新建/编辑攒钱目标的状态。 */
data class WishlistEditUiState(
    val loaded: Boolean = false,
    val itemId: String? = null,
    val name: String = "",
    val targetText: String = "",
    val savedText: String = "",
    val mode: SavedMode = SavedMode.MANUAL,
    val accountId: String? = null,
    val accountName: String? = null,
    val dueEpochDay: Long? = null,
    val note: String = "",
    val status: WishlistStatus = WishlistStatus.ACTIVE,
) {
    val isEdit: Boolean get() = itemId != null
    val targetCents: Long get() = Money.parseToCents(targetText) ?: 0L
    val savedCents: Long get() = Money.parseToCents(savedText) ?: 0L

    /** 能保存：名称非空 + 目标 > 0 + （跟随模式必须选了账户）。 */
    val canSave: Boolean
        get() = loaded && name.isNotBlank() && targetCents > 0L &&
            (mode == SavedMode.MANUAL || accountId != null)
}

class WishlistEditViewModel(
    private val app: PennedApplication,
    private val editId: String?,
) : ViewModel() {

    private val db = app.database
    private val _state = MutableStateFlow(WishlistEditUiState(itemId = editId, loaded = editId == null))
    val state: StateFlow<WishlistEditUiState> = _state

    /** 可跟随的账户（同账本可见、不含停用）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val accounts: StateFlow<List<AccountEntity>> = app.settings.currentBookId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else db.accountDao().observeByBook(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (editId != null) {
            viewModelScope.launch {
                val item = db.wishlistDao().get(editId) ?: return@launch
                _state.value = WishlistEditUiState(
                    loaded = true,
                    itemId = item.id,
                    name = item.name,
                    targetText = Money.format(item.targetCents, grouped = false),
                    savedText = if (item.savedCents == 0L) "" else Money.format(item.savedCents, grouped = false),
                    mode = if (item.accountId == null) SavedMode.MANUAL else SavedMode.FOLLOW_ACCOUNT,
                    accountId = item.accountId,
                    accountName = item.accountId?.let { db.accountDao().get(it)?.name },
                    dueEpochDay = item.dueEpochDay,
                    note = item.note.orEmpty(),
                    status = item.status,
                )
            }
        }
    }

    fun setName(value: String) = update { it.copy(name = value) }
    fun setTarget(value: String) = update { it.copy(targetText = value.filter { ch -> ch.isDigit() || ch == '.' }) }
    fun setSaved(value: String) = update { it.copy(savedText = value.filter { ch -> ch.isDigit() || ch == '.' }) }
    fun setNote(value: String) = update { it.copy(note = value) }
    fun setDue(day: Long?) = update { it.copy(dueEpochDay = day) }
    fun setStatus(done: Boolean) = update { it.copy(status = if (done) WishlistStatus.DONE else WishlistStatus.ACTIVE) }

    fun setMode(mode: SavedMode) = update {
        // 切回手动时保留已选的账户 id（切回来不用重选），但保存时不落它
        it.copy(mode = mode)
    }

    fun setAccount(account: AccountEntity) = update { it.copy(accountId = account.id, accountName = account.name) }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            if (!s.canSave) return@launch
            val now = System.currentTimeMillis()
            val existing = s.itemId?.let { db.wishlistDao().get(it) }
            val bookId = existing?.bookId ?: app.settings.currentBookId.first() ?: return@launch
            val entity = (existing ?: WishlistItemEntity(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                name = s.name.trim(),
                targetCents = s.targetCents,
                sortOrder = db.wishlistDao().nextSortOrder(bookId),
                createdAt = now,
                updatedAt = now,
                deviceId = app.deviceId,
            )).copy(
                name = s.name.trim(),
                targetCents = s.targetCents,
                savedCents = s.savedCents,
                accountId = if (s.mode == SavedMode.FOLLOW_ACCOUNT) s.accountId else null,
                dueEpochDay = s.dueEpochDay,
                note = s.note.trim().ifEmpty { null },
                status = s.status,
                updatedAt = now,
            )
            db.wishlistDao().upsert(entity)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val id = _state.value.itemId ?: return@launch
            db.wishlistDao().softDelete(id, System.currentTimeMillis())
            onDone()
        }
    }

    private fun update(block: (WishlistEditUiState) -> WishlistEditUiState) {
        _state.value = block(_state.value)
    }
}

@Composable
fun WishlistEditScreen(editId: String?, onBack: () -> Unit) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: WishlistEditViewModel = viewModel(
        key = "wishlist-edit-${editId.orEmpty()}",
        initializer = { WishlistEditViewModel(app, editId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    WishlistEditContent(
        state = state,
        accounts = accounts,
        onBack = onBack,
        onNameChange = viewModel::setName,
        onTargetChange = viewModel::setTarget,
        onSavedChange = viewModel::setSaved,
        onNoteChange = viewModel::setNote,
        onModeChange = viewModel::setMode,
        onPickAccount = viewModel::setAccount,
        onPickDue = viewModel::setDue,
        onToggleDone = viewModel::setStatus,
        onSave = { viewModel.save(onDone = onBack) },
        onDelete = if (state.isEdit) ({ confirmDelete = true }) else null,
    )

    if (confirmDelete) {
        ConfirmDialog(
            onDismiss = { confirmDelete = false },
            title = "删除目标？",
            text = "「${state.name}」将从愿望清单移除（金额与流水都不动）。",
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onDone = onBack)
            },
        )
    }
}

/** 无状态入口：截图测试/预览用。 */
@Composable
fun WishlistEditContent(
    state: WishlistEditUiState,
    onBack: () -> Unit,
    accounts: List<AccountEntity> = emptyList(),
    onNameChange: (String) -> Unit = {},
    onTargetChange: (String) -> Unit = {},
    onSavedChange: (String) -> Unit = {},
    onNoteChange: (String) -> Unit = {},
    onModeChange: (SavedMode) -> Unit = {},
    onPickAccount: (AccountEntity) -> Unit = {},
    onPickDue: (Long?) -> Unit = {},
    onToggleDone: (Boolean) -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
) {
    var showAccountPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }

    if (showAccountPicker) {
        PennedSheet(onDismiss = { showAccountPicker = false }, title = "跟随哪个账户") {
            val usable = accounts.filter { !it.archived }
            if (usable.isEmpty()) {
                Text(
                    "本账本还没有可用账户",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PennedSpacing.xl, vertical = PennedSpacing.sm),
                )
            }
            usable.forEach { account ->
                PennedListRow(
                    title = account.name,
                    subtitle = if (account.id == state.accountId) "当前跟随" else null,
                    showChevron = false,
                    onClick = {
                        onPickAccount(account)
                        showAccountPicker = false
                    },
                )
            }
        }
    }
    if (showDuePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = AddDate.dayToPickerMillis(state.dueEpochDay ?: LocalDate.now().toEpochDay()),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDuePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPickDue(AddDate.pickerMillisToDay(it)) }
                    showDuePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDuePicker = false }) { Text("取消") } },
        ) {
            androidx.compose.material3.DatePicker(
                state = pickerState,
                title = { Text("截止日", modifier = Modifier.padding(PennedSpacing.lg)) },
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PennedTopBar(title = if (state.isEdit) "编辑目标" else "新建目标", onBack = onBack)
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                PennedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    WishFieldRow("名称", state.name, "比如「换电脑」", onNameChange)
                    WishFieldRow("目标", state.targetText, "0.00", onTargetChange, money = true)
                    PennedDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(PennedSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("存入方式", style = PennedTextStyles.rowTitle, modifier = Modifier.weight(1f))
                        PennedSegmentedTabs(
                            options = SavedMode.entries.toList(),
                            selected = state.mode,
                            onSelect = onModeChange,
                            label = { if (it == SavedMode.MANUAL) "手动存" else "跟随账户" },
                            modifier = Modifier.width(SAVED_MODE_TOGGLE),
                        )
                    }
                }

                PennedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    if (state.mode == SavedMode.MANUAL) {
                        WishFieldRow("已存", state.savedText, "0.00", onSavedChange, money = true)
                        Text(
                            "「存入一笔」在列表卡上，也可以在这里直接改（只动这条目标，不生成流水）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = PennedSpacing.lg,
                                end = PennedSpacing.lg,
                                bottom = PennedSpacing.md,
                            ),
                        )
                    } else {
                        PennedListRow(
                            title = "跟随账户",
                            subtitle = state.accountName ?: "选一个账户，已存跟着它的余额走",
                            leadingIcon = PennedIcons.Wallet,
                            onClick = { showAccountPicker = true },
                        )
                        Text(
                            "跟随模式下「已存」= 该账户的重算余额（初始余额 + 收支 ± 转账）；存入/取出记在账户上",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = PennedSpacing.lg,
                                end = PennedSpacing.lg,
                                bottom = PennedSpacing.md,
                            ),
                        )
                    }
                    PennedDivider()
                    PennedListRow(
                        title = "截止日",
                        trailing = state.dueEpochDay?.let { DebtText.dueLabel(it, LocalDate.now()) } ?: "未设",
                        showChevron = false,
                        onClick = { showDuePicker = true },
                        content = if (state.dueEpochDay != null) {
                            {
                                TextButton(onClick = { onPickDue(null) }) { Text("清除") }
                            }
                        } else {
                            null
                        },
                    )
                    PennedDivider()
                    WishFieldRow("备注", state.note, "选填", onNoteChange)
                }

                if (state.isEdit) {
                    PennedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        PennedSwitchRow(
                            title = "标记达成",
                            subtitle = "只是给这条目标盖个章；金额够了也会显示「已达成」",
                            checked = state.status == WishlistStatus.DONE,
                            onCheckedChange = onToggleDone,
                        )
                    }
                }
                Spacer(Modifier.height(PennedSpacing.xxl))
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md)) {
                PennedButton(text = "保存", onClick = onSave, enabled = state.canSave)
                if (onDelete != null) {
                    Spacer(Modifier.height(PennedSpacing.xs))
                    PennedButton(
                        text = "删除目标",
                        onClick = onDelete,
                        variant = PennedButtonVariant.Danger,
                    )
                }
            }
        }
    }
}

/** 表单行：左标签 + 输入（金额用数字键盘）。 */
@Composable
private fun WishFieldRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    money: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = PennedDimens.rowMinHeight).padding(horizontal = PennedSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = PennedTextStyles.rowTitle, modifier = Modifier.width(PennedDimens.formLabel))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = PennedTextStyles.rowTitle.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = if (money) KeyboardType.Decimal else KeyboardType.Text),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, style = PennedTextStyles.rowTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
    }
}

@Preview(showBackground = true, name = "新建目标", widthDp = 400, heightDp = 800)
@Composable
private fun WishlistEditPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        WishlistEditContent(
            state = WishlistEditUiState(loaded = true, name = "换电脑", targetText = "8000", savedText = "3200"),
            onBack = {},
        )
    }
}
