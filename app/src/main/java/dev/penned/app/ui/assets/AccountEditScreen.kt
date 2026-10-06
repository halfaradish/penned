package dev.penned.app.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
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
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedSegmentedTabs
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.SectionLabel
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTheme
import dev.penned.app.ui.theme.primaryInkColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 添加/编辑账户页状态。
 *
 * [presetLabel] 为 null 表示选中了「自定义」格（此时名称不再有预设兜底）；
 * [type] 是真正落库的账户类型，跟随预设或所属大类的默认类型。
 */
data class AccountEditUiState(
    val loading: Boolean = false,
    val accountId: String? = null,
    val group: AccountGroup = AccountGroup.FUNDS,
    val type: AccountType = AccountType.CASH,
    val presetLabel: String? = DEFAULT_PRESET.label,
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

private val DEFAULT_PRESET = ACCOUNT_PRESETS.getValue(AccountGroup.FUNDS).first()

class AccountEditViewModel(
    private val app: PennedApplication,
    private val editId: String?,
) : ViewModel() {

    private val db = app.database

    private val _state = MutableStateFlow(AccountEditUiState(accountId = editId))
    val state: StateFlow<AccountEditUiState> = _state

    /** 点「自定义」格的次数：>0 时把焦点送进名称输入（用户下一步必然是起名字）。 */
    private val _focusName = MutableStateFlow(0)
    val focusName: StateFlow<Int> = _focusName

    init {
        if (editId != null) viewModelScope.launch { load(editId) }
    }

    private suspend fun load(id: String) {
        val account = db.accountDao().get(id) ?: return
        val group = AccountGroup.of(account.type)
        // 名字与预设完全一致才高亮预设格，否则按「自定义」呈现（导入的账户也叫现金，能对上）
        val preset = ACCOUNT_PRESETS[group].orEmpty().firstOrNull { it.label == account.name }
        _state.value = AccountEditUiState(
            accountId = account.id,
            group = group,
            type = account.type,
            presetLabel = preset?.label,
            name = account.name,
            initialText = if (account.initialBalanceCents == 0L) "" else Money.format(account.initialBalanceCents),
            pendingTxCount = db.transactionDao().countByAccount(id),
        )
    }

    fun selectGroup(group: AccountGroup) {
        val first = ACCOUNT_PRESETS[group].orEmpty().firstOrNull()
        update { it.copy(group = group, type = first?.type ?: group.defaultType, presetLabel = first?.label) }
    }

    /** 选预设只记类型与预设名，不覆盖用户已经输入的名称（看板待办）。 */
    fun selectPreset(preset: AccountPreset) {
        update { it.copy(type = preset.type, presetLabel = preset.label) }
    }

    fun selectCustom() {
        update { it.copy(type = it.group.defaultType, presetLabel = null) }
        _focusName.value += 1
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
) {
    val viewModel: AccountEditViewModel = viewModel(
        key = "account-edit-${editId.orEmpty()}",
        initializer = { AccountEditViewModel(app, editId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusName by viewModel.focusName.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    AccountEditContent(
        state = state,
        focusNameTick = focusName,
        onBack = onBack,
        onSelectGroup = viewModel::selectGroup,
        onSelectPreset = viewModel::selectPreset,
        onSelectCustom = viewModel::selectCustom,
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
    onSelectPreset: (AccountPreset) -> Unit,
    onSelectCustom: () -> Unit,
    onNameChange: (String) -> Unit,
    onInitialChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    focusNameTick: Int = 0,
    onDelete: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusNameTick) {
        if (focusNameTick > 0) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
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
                SectionLabel("类型")
                PennedSegmentedTabs(
                    options = AccountGroup.entries,
                    selected = state.group,
                    onSelect = onSelectGroup,
                    label = { it.tab },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                )
                AccountTypeGrid(
                    group = state.group,
                    selectedLabel = state.presetLabel,
                    onSelectPreset = onSelectPreset,
                    onSelectCustom = onSelectCustom,
                )

                SectionLabel("详情")
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
                    PennedDivider()
                    EditFieldRow(
                        label = "初始余额",
                        value = state.initialText,
                        placeholder = "0.00",
                        suffix = "元",
                        money = true,
                        onValueChange = onInitialChange,
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

/**
 * 类型网格：4 列固定，预设格在前、「自定义」格收尾。
 * 末行不足 4 个补空位（weight 会把落单的那格拉宽）。
 */
@Composable
private fun AccountTypeGrid(
    group: AccountGroup,
    selectedLabel: String?,
    onSelectPreset: (AccountPreset) -> Unit,
    onSelectCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = ACCOUNT_PRESETS[group].orEmpty()
    val customSelected = selectedLabel == null
    val tiles = presets.size + 1
    Column(modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg)) {
        (0 until tiles).chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(top = PennedSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
            ) {
                row.forEach { index ->
                    val preset = presets.getOrNull(index)
                    if (preset == null) {
                        TypeTile(
                            label = "自定义",
                            selected = customSelected,
                            slot = null,
                            onClick = onSelectCustom,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        TypeTile(
                            label = preset.label,
                            selected = !customSelected && preset.label == selectedLabel,
                            slot = accountPigmentSlot(preset.type),
                            onClick = { onSelectPreset(preset) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 一个类型格：颜料头像（或自定义的加号圆）+ 名称，选中 = 实心圆 + 主色墨字。 */
@Composable
private fun TypeTile(
    label: String,
    selected: Boolean,
    slot: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = PennedSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (slot != null) {
            CategoryAvatar(name = label, size = PennedDimens.categoryBubble, selected = selected, slot = slot)
        } else {
            Box(
                Modifier
                    .size(PennedDimens.categoryBubble)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PennedIcons.Plus,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(PennedSpacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) primaryInkColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            state = AccountEditUiState(),
            onBack = {},
            onSelectGroup = {}, onSelectPreset = {}, onSelectCustom = {},
            onNameChange = {}, onInitialChange = {}, onSave = {},
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
            onSelectGroup = {}, onSelectPreset = {}, onSelectCustom = {},
            onNameChange = {}, onInitialChange = {}, onSave = {},
            onDelete = {},
        )
    }
}
