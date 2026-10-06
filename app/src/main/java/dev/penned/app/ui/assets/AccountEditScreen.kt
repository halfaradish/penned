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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.Money
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.ConfirmDialog
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    /** 编辑已有账户时：其名下流水数（>0 不给删，删除键置灰并说明原因）。 */
    val pendingTxCount: Int = 0,
    /** 删除时才发现有流水（竞态）的兜底说明。 */
    val blockedReason: String? = null,
) {
    val isEdit: Boolean get() = accountId != null

    /** 名称输入为空时显示的预设名；自定义格给一句提示。 */
    val nameHint: String get() = presetLabel ?: "账户名称"

    val canDelete: Boolean get() = isEdit && pendingTxCount == 0

    val balanceCents: Long get() = Money.parseToCents(initialText) ?: 0L

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
        if (editId != null) viewModelScope.launch { load(editId) }
    }

    private suspend fun load(id: String) {
        val account = db.accountDao().get(id) ?: return
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
            pendingTxCount = db.transactionDao().countByAccount(id),
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
        update { it.copy(initialText = input.filter { ch -> ch.isDigit() || ch == '.' }) }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            val now = System.currentTimeMillis()
            val name = s.resolvedName()
            val existingId = s.accountId
            if (existingId == null) {
                val bookId = app.settings.currentBookId.firstOrNull() ?: return@launch
                db.accountDao().upsert(
                    AccountEntity(
                        id = UUID.randomUUID().toString(),
                        bookId = bookId,
                        name = name,
                        type = s.type,
                        initialBalanceCents = s.balanceCents,
                        createdAt = now,
                        updatedAt = now,
                        deviceId = app.deviceId,
                    )
                )
            } else {
                // 读回落库实体再改这几个字段：groupName/currencyCode 等本页不碰的字段原样保留
                val original = db.accountDao().get(existingId) ?: return@launch
                db.accountDao().upsert(
                    original.copy(
                        name = name,
                        type = s.type,
                        initialBalanceCents = s.balanceCents,
                        updatedAt = now,
                    )
                )
            }
            onDone()
        }
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
    onDelete: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var showGroupPicker by remember { mutableStateOf(false) }

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
                // 卡片一：名称 / 余额
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
                        suffix = "元",
                        money = true,
                        onValueChange = onInitialChange,
                    )
                }

                // 卡片二：归属
                PennedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    GroupPickerRow(
                        group = state.group,
                        type = state.type,
                        onClick = { showGroupPicker = true },
                    )
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

/** 所属分组行：右边是账户头像预览（颜色跟着类型走）+ 分组名 + 右箭头。 */
@Composable
private fun GroupPickerRow(group: AccountGroup, type: AccountType, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "所属分组",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(72.dp),
        )
        Spacer(Modifier.weight(1f))
        CategoryAvatar(group.title, size = PennedDimens.avatar, slot = accountPigmentSlot(type))
        Spacer(Modifier.width(PennedSpacing.sm))
        Text(group.title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(PennedSpacing.sm))
        Icon(
            PennedIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
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
                CategoryAvatar(
                    name = group.title,
                    size = PennedDimens.avatar,
                    slot = accountPigmentSlot(group.defaultType),
                )
                Spacer(Modifier.width(PennedSpacing.md))
                Text(group.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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

/** 表单行：左标签 + 右侧输入（金额右对齐成列），行高与列表行一致。 */
@Composable
private fun EditFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    suffix: String? = null,
    money: Boolean = false,
    focusRequester: FocusRequester? = null,
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(72.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = if (money) TextAlign.End else TextAlign.Start,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = if (money) KeyboardType.Decimal else KeyboardType.Text),
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            decorationBox = { inner ->
                Box(contentAlignment = if (money) Alignment.CenterEnd else Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        if (suffix != null) {
            Spacer(Modifier.width(PennedSpacing.sm))
            Text(
                suffix,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                pendingTxCount = 2,
            ),
            onBack = {},
            onSelectGroup = {}, onNameChange = {}, onInitialChange = {}, onSave = {},
            onDelete = {},
        )
    }
}
