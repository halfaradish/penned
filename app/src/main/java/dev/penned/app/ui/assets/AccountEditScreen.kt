package dev.penned.app.ui.assets

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.AccountBookRefEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.Money
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.ConfirmDialog
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.iconMutedColor
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 添加/编辑账户页状态。
 *
 * [presetLabel] 为 null 表示走的是「自定义」格（名称不再有预设兜底）；
 * [type] 是真正落库的账户类型，由所属大类的默认类型或所选预设决定。
 */
data class AccountEditUiState(
    val loading: Boolean = false,
    val accountId: String? = null,
    val group: AccountGroup = AccountGroup.FUNDS,
    val type: AccountType = AccountGroup.FUNDS.defaultType,
    val presetLabel: String? = null,
    val name: String = "",
    val initialText: String = "",
    /** 备注（选填，只作个人备忘）。 */
    val remark: String = "",
    /** 生效账本：true = 所有账本可见（含以后新建的），false = 所属账本 + [extraBookIds]（D028）。 */
    val scopeAllBooks: Boolean = false,
    /** 可选账本（用于勾选）与所属账本；[extraBookIds] 是额外勾选的账本。 */
    val books: List<BookEntity> = emptyList(),
    val ownerBookId: String? = null,
    val extraBookIds: Set<String> = emptySet(),
    /** 编辑已有账户时：其名下流水数（>0 不给删，删除键置灰并说明原因）。 */
    val pendingTxCount: Int = 0,
    /** 删除时才发现有流水（竞态）的兜底说明。 */
    val blockedReason: String? = null,
) {
    val isEdit: Boolean get() = accountId != null

    /** 名称输入为空时显示的预设名；自定义格给一句提示。 */
    val nameHint: String get() = presetLabel ?: "账户名称"

    val canDelete: Boolean get() = isEdit && pendingTxCount == 0

    /** 允许负初始余额的账户类型（M3-4，D068）：信贷/负债的存量欠款直接从负数填起。 */
    val allowsNegativeBalance: Boolean
        get() = type == AccountType.CREDIT || type == AccountType.DEBT

    val balanceCents: Long
        get() {
            val negative = initialText.startsWith("-")
            val cents = Money.parseToCents(initialText.removePrefix("-")) ?: 0L
            return if (negative) -cents else cents
        }

    /** 生效账本行的摘要：全部账本，或者列出生效的账本名（所属账本恒定在列）。 */
    val bookSummary: String
        get() = when {
            scopeAllBooks -> "全部账本"
            else -> books.filter { it.id == ownerBookId || it.id in extraBookIds }
                .joinToString("、") { it.name }
                .ifEmpty { "仅本账本" }
        }

    /** 落库的名称：用户没填就用预设名，自定义格退到类型名。 */
    fun resolvedName(): String = name.trim().ifBlank { presetLabel ?: accountTypeLabel(type) }
}

class AccountEditViewModel(
    private val app: PennedApplication,
    private val editId: String?,
    presetLabel: String?,
    group: AccountGroup?,
) : ViewModel() {

    private val db = app.database
    private val startGroup = group ?: AccountGroup.FUNDS

    private val _state = MutableStateFlow(
        AccountEditUiState(
            accountId = editId,
            group = startGroup,
            type = accountTypeFor(startGroup, presetLabel),
            presetLabel = presetLabel,
        )
    )
    val state: StateFlow<AccountEditUiState> = _state

    init {
        viewModelScope.launch {
            val books = db.bookDao().observeAll().first()
            val account = editId?.let { db.accountDao().get(it) }
            val ownerBookId = account?.bookId ?: app.settings.currentBookId.firstOrNull()
            if (account == null) {
                update { it.copy(books = books, ownerBookId = ownerBookId) }
            } else {
                load(account, books, db.accountDao().bookIdsFor(account.id).toSet())
            }
        }
    }

    private suspend fun load(account: AccountEntity, books: List<BookEntity>, extraBookIds: Set<String>) {
        val group = AccountGroup.of(account.type)
        // 名字与预设完全一致才当作预设（导入的账户也叫现金，能对上）
        val preset = ACCOUNT_PRESETS[group].orEmpty().firstOrNull { it.label == account.name }
        _state.value = AccountEditUiState(
            accountId = account.id,
            group = group,
            type = account.type,
            presetLabel = preset?.label,
            name = account.name,
            initialText = if (account.initialBalanceCents == 0L) {
                ""
            } else {
                Money.format(account.initialBalanceCents, grouped = false)
            },
            remark = account.remark.orEmpty(),
            scopeAllBooks = account.scopeAllBooks,
            books = books,
            ownerBookId = account.bookId,
            extraBookIds = extraBookIds,
            pendingTxCount = db.transactionDao().countByAccount(account.id),
        )
    }

    /** 换大类：类型跟着大类的默认类型走，预设名不再适用。 */
    fun selectGroup(group: AccountGroup) {
        update { it.copy(group = group, type = group.defaultType, presetLabel = null) }
    }

    fun setName(name: String) {
        update { it.copy(name = name) }
    }

    fun setInitial(input: String) {
        update { s ->
            // 负号只对信贷/负债放行（存量欠款），且只能出现在最前面、只留一个
            val filtered = input.filter { ch -> ch.isDigit() || ch == '.' || (ch == '-' && s.allowsNegativeBalance) }
            val normalized = if (filtered.startsWith("-")) "-" + filtered.drop(1).replace("-", "") else filtered.replace("-", "")
            s.copy(initialText = normalized)
        }
    }

    fun setRemark(remark: String) {
        update { it.copy(remark = remark) }
    }

    fun setScopeAllBooks(allBooks: Boolean) {
        update { it.copy(scopeAllBooks = allBooks) }
    }

    /** 勾选/取消某个账本（所属账本不可取消，UI 已置灰）。 */
    fun toggleBook(bookId: String) {
        update {
            val extras = if (bookId in it.extraBookIds) it.extraBookIds - bookId else it.extraBookIds + bookId
            it.copy(extraBookIds = extras)
        }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            val now = System.currentTimeMillis()
            val name = s.resolvedName()
            val remark = s.remark.trim().ifEmpty { null }
            val existingId = s.accountId
            val accountId = if (existingId == null) {
                val bookId = app.settings.currentBookId.firstOrNull() ?: return@launch
                val id = UUID.randomUUID().toString()
                db.accountDao().upsert(
                    AccountEntity(
                        id = id,
                        bookId = bookId,
                        name = name,
                        type = s.type,
                        initialBalanceCents = s.balanceCents,
                        remark = remark,
                        scopeAllBooks = s.scopeAllBooks,
                        createdAt = now,
                        updatedAt = now,
                        deviceId = app.deviceId,
                    )
                )
                id
            } else {
                // 读回落库实体再改这几个字段：groupName/currencyCode 等本页不碰的字段原样保留
                val original = db.accountDao().get(existingId) ?: return@launch
                db.accountDao().upsert(
                    original.copy(
                        name = name,
                        type = s.type,
                        initialBalanceCents = s.balanceCents,
                        remark = remark,
                        scopeAllBooks = s.scopeAllBooks,
                        updatedAt = now,
                    )
                )
                existingId
            }
            saveBookRefs(accountId, s, now)
            onDone()
        }
    }

    /** 生效账本落库：先整批软删再 upsert 勾选的（取消勾选即收窄；全部账本时不写引用）。 */
    private suspend fun saveBookRefs(accountId: String, s: AccountEditUiState, now: Long) {
        db.accountDao().clearRefs(accountId, now)
        if (s.scopeAllBooks) return
        val refs = s.extraBookIds
            .filter { it != s.ownerBookId }
            .map {
                AccountBookRefEntity(
                    accountId = accountId,
                    bookId = it,
                    createdAt = now,
                    updatedAt = now,
                    deviceId = app.deviceId,
                )
            }
        if (refs.isNotEmpty()) db.accountDao().upsertRefs(refs)
    }

    /** 页面内删除：走同一条 [deleteAccountIfEmpty]；被拦下就把原因写回状态。 */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            val id = s.accountId ?: return@launch
            val blocked = deleteAccountIfEmpty(db, id, s.resolvedName())
            if (blocked != null) {
                update { it.copy(blockedReason = blocked) }
                return@launch
            }
            onDone()
        }
    }

    private fun update(block: (AccountEditUiState) -> AccountEditUiState) {
        _state.value = block(_state.value)
    }
}

@Composable
fun AccountEditScreen(
    app: PennedApplication,
    editId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    presetLabel: String? = null,
    group: AccountGroup? = null,
) {
    val viewModel: AccountEditViewModel = viewModel(
        key = "account-edit-${editId.orEmpty()}-${group?.name.orEmpty()}-${presetLabel.orEmpty()}",
        initializer = { AccountEditViewModel(app, editId, presetLabel, group) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    AccountEditContent(
        state = state,
        onBack = onBack,
        onSelectGroup = viewModel::selectGroup,
        onNameChange = viewModel::setName,
        onInitialChange = viewModel::setInitial,
        onRemarkChange = viewModel::setRemark,
        onScopeChange = viewModel::setScopeAllBooks,
        onToggleBook = viewModel::toggleBook,
        onSave = { viewModel.save(onDone = onBack) },
        onDelete = if (state.isEdit) ({ confirmDelete = true }) else null,
        modifier = modifier,
    )

    if (confirmDelete) {
        ConfirmDialog(
            onDismiss = { confirmDelete = false },
            title = "删除账户？",
            text = "「${state.resolvedName()}」将从资产列表移除。",
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onDone = onBack)
            },
        )
    }
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@Composable
fun AccountEditContent(
    state: AccountEditUiState,
    onBack: () -> Unit,
    onSelectGroup: (AccountGroup) -> Unit,
    onNameChange: (String) -> Unit,
    onInitialChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onRemarkChange: (String) -> Unit = {},
    onScopeChange: (Boolean) -> Unit = {},
    onToggleBook: (String) -> Unit = {},
    onDelete: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var showGroupPicker by remember { mutableStateOf(false) }
    var showScopePicker by remember { mutableStateOf(false) }

    // 从「自定义」格进来时名称没有预设兜底，直接把光标送进名称输入
    LaunchedEffect(Unit) {
        if (!state.isEdit && state.presetLabel == null) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    if (showGroupPicker) {
        GroupPickerSheet(
            selected = state.group,
            onPick = {
                onSelectGroup(it)
                showGroupPicker = false
            },
            onDismiss = { showGroupPicker = false },
        )
    }

    if (showScopePicker) {
        ScopePickerSheet(
            allBooks = state.scopeAllBooks,
            books = state.books,
            ownerBookId = state.ownerBookId,
            extraBookIds = state.extraBookIds,
            onToggleAll = { onScopeChange(!state.scopeAllBooks) },
            onToggleBook = onToggleBook,
            onDismiss = { showScopePicker = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PennedTopBar(title = if (state.isEdit) "编辑账户" else "添加账户", onBack = onBack)
        },
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // 卡片一：名称 / 余额 / 备注
                PennedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    EditFieldRow(
                        label = "名称",
                        value = state.name,
                        placeholder = state.nameHint,
                        onValueChange = onNameChange,
                        focusRequester = focusRequester,
                    )
                    EditFieldRow(
                        label = "余额",
                        value = state.initialText,
                        placeholder = "0.00",
                        money = true,
                        onValueChange = onInitialChange,
                        trailing = if (state.allowsNegativeBalance) {
                            // 数字键盘不保证有负号：给一个「±」把正负翻过来（信贷/负债的存量欠款）
                            {
                                TextButton(onClick = {
                                    val v = state.initialText
                                    onInitialChange(if (v.startsWith("-")) v.drop(1) else "-$v")
                                }) { Text("±") }
                            }
                        } else {
                            null
                        },
                    )
                    if (state.allowsNegativeBalance) {
                        Text(
                            "信贷/负债可以填负数：存量欠款直接写进来（如 -1200），或点「±」翻号",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = PennedSpacing.lg,
                                end = PennedSpacing.lg,
                                bottom = PennedSpacing.sm,
                            ),
                        )
                    }
                    EditFieldRow(
                        label = "备注",
                        value = state.remark,
                        placeholder = "选填",
                        onValueChange = onRemarkChange,
                    )
                }

                // 卡片二：归属
                PennedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    PickerRow(
                        label = "所属分组",
                        value = state.group.title,
                        onClick = { showGroupPicker = true },
                        leading = {
                            CategoryAvatar(name = state.group.title, size = PennedDimens.avatar)
                        },
                    )
                    PennedDivider()
                    PickerRow(
                        label = "生效账本",
                        value = state.bookSummary,
                        onClick = { showScopePicker = true },
                    )
                    if (state.scopeAllBooks) {
                        Text(
                            "所有账本都能看到这个账户，含以后新建的账本",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = PennedSpacing.lg,
                                end = PennedSpacing.lg,
                                bottom = PennedSpacing.md,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(PennedSpacing.xxl))
            }

            // 底部常驻动作区：不随内容滚动，键盘弹起时也跟着抬起
            Column(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md)) {
                PennedButton(text = "保存", onClick = onSave, enabled = !state.loading)
                if (onDelete != null) {
                    Spacer(Modifier.height(PennedSpacing.xs))
                    PennedButton(
                        text = "删除账户",
                        onClick = onDelete,
                        enabled = state.canDelete,
                        variant = PennedButtonVariant.Danger,
                    )
                    val hint = state.blockedReason
                        ?: state.pendingTxCount.takeIf { it > 0 }?.let { "该账户下有 $it 笔流水，先处理流水再删除" }
                    if (hint != null) {
                        Text(
                            hint,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = PennedSpacing.xs),
                        )
                    }
                }
            }
        }
    }
}

/** 选择行：左标签 + 右侧值（可带头部预览）+ 右箭头。 */
@Composable
private fun PickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
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
        if (leading != null) {
            leading()
            Spacer(Modifier.width(PennedSpacing.sm))
        }
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

/** 生效账本多选（D028）：全部账本开关 + 逐账本勾选；所属账本恒定可见、置灰。 */
@Composable
private fun ScopePickerSheet(
    allBooks: Boolean,
    books: List<BookEntity>,
    ownerBookId: String?,
    extraBookIds: Set<String>,
    onToggleAll: () -> Unit,
    onToggleBook: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "生效账本") {
        BookCheckRow(
            name = "全部账本",
            checked = allBooks,
            onToggle = onToggleAll,
        )
        if (!allBooks) {
            PennedDivider()
            books.forEach { book ->
                val isOwner = book.id == ownerBookId
                BookCheckRow(
                    name = book.name,
                    checked = isOwner || book.id in extraBookIds,
                    enabled = !isOwner,
                    tag = if (isOwner) "所属" else null,
                    onToggle = { onToggleBook(book.id) },
                )
            }
            Text(
                "所属账本恒定可见；勾选其它账本，这个账户在那边也能用",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PennedSpacing.xl, vertical = PennedSpacing.sm),
            )
        }
    }
}

/** 一行带勾选框的账本。 */
@Composable
private fun BookCheckRow(
    name: String,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    tag: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = PennedSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(PennedSpacing.md))
        Text(
            name,
            style = PennedTextStyles.rowTitle,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (tag != null) {
            Text(
                tag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 分组选择：四个大类，头像色即该组账户会用的颜色。 */
@Composable
private fun GroupPickerSheet(
    selected: AccountGroup,
    onPick: (AccountGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "所属分组") {
        AccountGroup.entries.forEach { group ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = PennedDimens.rowMinHeight)
                    .clickable { onPick(group) }
                    .padding(horizontal = PennedSpacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryAvatar(name = group.title, size = PennedDimens.avatar)
                Spacer(Modifier.width(PennedSpacing.md))
                Text(group.title, style = PennedTextStyles.rowTitle, modifier = Modifier.weight(1f))
                if (group == selected) {
                    Text(
                        "已选",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 表单行：左标签 + 紧跟其后的输入，行高与列表行一致（金额也左对齐，不带单位）。 */
@Composable
private fun EditFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    money: Boolean = false,
    focusRequester: FocusRequester? = null,
    /** 行尾的附加动作（余额行给「±」翻号用）。 */
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
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
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = PennedTextStyles.rowTitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        if (trailing != null) {
            Spacer(Modifier.width(PennedSpacing.sm))
            trailing()
        }
    }
}

@Preview(showBackground = true, name = "添加账户", widthDp = 400, heightDp = 800)
@Composable
private fun AddAccountPreview() {
    PennedTheme {
        AccountEditContent(
            state = AccountEditUiState(presetLabel = "现金"),
            onBack = {},
            onSelectGroup = {}, onNameChange = {}, onInitialChange = {}, onSave = {},
        )
    }
}

@Preview(showBackground = true, name = "编辑账户", widthDp = 400, heightDp = 800)
@Composable
private fun EditAccountPreview() {
    PennedTheme {
        AccountEditContent(
            state = AccountEditUiState(
                accountId = "a1",
                group = AccountGroup.CREDIT,
                type = AccountType.CREDIT,
                presetLabel = "信用卡",
                name = "招行信用卡",
                initialText = "0.00",
                remark = "尾号 6688",
                scopeAllBooks = true,
                pendingTxCount = 2,
            ),
            onBack = {},
            onSelectGroup = {}, onNameChange = {}, onInitialChange = {}, onSave = {},
            onDelete = {},
        )
    }
}
