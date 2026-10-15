package dev.penned.app.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.data.db.dao.DebtWithRepaid
import dev.penned.app.data.db.dao.TemplateWithTags
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.domain.DebtAction
import dev.penned.app.domain.DebtDirection
import dev.penned.app.domain.DebtMath
import dev.penned.app.domain.Money
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.components.PennedDialog
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedSegmentedTabs
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.TagCreateDialog
import dev.penned.app.ui.components.reimburseStateLabel
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.templates.TemplatePickerSheet
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTheme
import java.time.LocalDate
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.incomeColor

@Composable
fun AddTransactionScreen(
    editId: String?,
    app: dev.penned.app.PennedApplication,
    /** 保存成功回调：参数是落库的流水 id（首页提示条的「存为模板」要用，M3-5）。 */
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onManageCategories: () -> Unit = {},
    onNewDebt: () -> Unit = {},
    onManageTemplates: () -> Unit = {},
    /** 「记一笔还款」预填（M3-4）：债务详情页跳进来时带着债务与那一格。 */
    presetDebtId: String? = null,
    presetDebtAction: DebtAction? = null,
    /** 房贷首期预填（M3-11）：分期房贷页「按首期记一笔」带的金额（分）/ 日期 / 备注。 */
    presetCents: Long? = null,
    presetDay: Long? = null,
    presetNote: String? = null,
) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = editId ?: "new-${presetDebtId.orEmpty()}-${presetDebtAction?.name.orEmpty()}-${presetCents ?: ""}",
        initializer = { AddViewModel(app, editId, presetDebtId, presetDebtAction, presetCents, presetDay, presetNote) },
    )
    val type by viewModel.type.collectAsStateWithLifecycle()
    val amount by viewModel.amountText.collectAsStateWithLifecycle()
    val refundText by viewModel.refundText.collectAsStateWithLifecycle()
    val discountText by viewModel.discountText.collectAsStateWithLifecycle()
    val amountField by viewModel.amountField.collectAsStateWithLifecycle()
    val paySummary by viewModel.paySummary.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val expandedId by viewModel.expandedParentId.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val accountId by viewModel.accountId.collectAsStateWithLifecycle()
    val fromId by viewModel.transferFromId.collectAsStateWithLifecycle()
    val toId by viewModel.transferToId.collectAsStateWithLifecycle()
    val feeText by viewModel.feeText.collectAsStateWithLifecycle()
    // 记账宫格用"可见分类"（隐藏分类不进选择器；本笔已选中的那个保留，M3-1）
    val categories by viewModel.selectableCategories.collectAsStateWithLifecycle()
    // 选择器只列在用的账户（停用的不列；本笔已选中的保留，M3-9）
    val accounts by viewModel.selectableAccounts.collectAsStateWithLifecycle()
    val tags by viewModel.selectableTags.collectAsStateWithLifecycle()
    val selectedTagIds by viewModel.selectedTagIds.collectAsStateWithLifecycle()
    val reimburseState by viewModel.reimburseState.collectAsStateWithLifecycle()
    val reimburseAccountId by viewModel.reimburseAccountId.collectAsStateWithLifecycle()
    val claimLabel by viewModel.claimLabel.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()
    val showEquals by viewModel.showEquals.collectAsStateWithLifecycle()
    val dateEpochDay by viewModel.dateEpochDay.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val debtTab by viewModel.debtTab.collectAsStateWithLifecycle()
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val debtId by viewModel.debtId.collectAsStateWithLifecycle()
    val debtAction by viewModel.debtAction.collectAsStateWithLifecycle()

    val parents = remember(categories, type) {
        val domain = if (type == TxType.INCOME) dev.penned.app.domain.CategoryType.INCOME else dev.penned.app.domain.CategoryType.EXPENSE
        categories.filter { it.parentId == null && it.type == domain }
            .sortedBy { it.sortOrder }
            .map { CategoryUi(it.id, it.name, hasSubs = categories.any { s -> s.parentId == it.id }) }
    }
    val expandedSubs = remember(categories, expandedId, type) {
        val domain = if (type == TxType.INCOME) dev.penned.app.domain.CategoryType.INCOME else dev.penned.app.domain.CategoryType.EXPENSE
        categories.filter { it.parentId == expandedId && it.type == domain }
            .sortedBy { it.sortOrder }
            .map { CategoryUi(it.id, it.name, hasSubs = false) }
    }

    AddTransactionContent(
        type = type,
        amountText = amount,
        note = note,
        selectedId = selectedId,
        expandedId = expandedId,
        canSave = canSave,
        showEquals = showEquals,
        accountId = accountId,
        fromId = fromId,
        toId = toId,
        feeText = feeText,
        dateEpochDay = dateEpochDay,
        accounts = accounts,
        parents = parents,
        expandedSubs = expandedSubs,
        tags = tags,
        selectedTagIds = selectedTagIds,
        refundText = refundText,
        discountText = discountText,
        amountField = amountField,
        paySummary = paySummary,
        reimburseState = reimburseState,
        reimburseAccountId = reimburseAccountId,
        claimLabel = claimLabel,
        debtTab = debtTab,
        templates = templates,
        onPickTemplate = viewModel::applyTemplateById,
        onManageTemplates = onManageTemplates,
        debts = debts,
        debtId = debtId,
        debtAction = debtAction,
        onSelectType = viewModel::selectType,
        onSelectDebtTab = viewModel::selectDebtTab,
        onPickDebt = viewModel::pickDebt,
        onPickDebtAction = viewModel::pickDebtAction,
        onNewDebt = onNewDebt,
        onKey = viewModel::pressKey,
        onFocusField = viewModel::focusAmountField,
        onPickReimburseState = viewModel::pickReimburseState,
        onPickReimburseAccount = viewModel::pickReimburseAccount,
        onSelectParent = { viewModel.selectParent(it.id, it.hasSubs) },
        onSelectSub = { viewModel.selectSub(it.id) },
        onNoteChange = viewModel::setNote,
        onPickAccount = viewModel::pickAccount,
        onToggleTag = viewModel::toggleTag,
        onCreateTag = { name, parentId -> viewModel.addTagAndSelect(name, parentId) },
        onPickFrom = viewModel::pickTransferFrom,
        onPickTo = viewModel::pickTransferTo,
        onSwap = viewModel::swapTransfer,
        onFeeChange = viewModel::setFee,
        onPickDate = viewModel::setDate,
        onAddCategory = { name, parentId -> viewModel.addCategoryAndSelect(name, parentId) },
        onSave = { viewModel.save(onDone) },
        onSaveAgain = viewModel::saveAgain,
        onEvaluate = viewModel::evaluate,
        onBack = onBack,
        onEdit = onManageCategories,
        modifier = modifier,
    )
}

data class AccountUi(val id: String, val name: String, val typeName: String)

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AddTransactionContent(
    type: TxType,
    amountText: String,
    note: String,
    selectedId: String?,
    expandedId: String?,
    canSave: Boolean,
    showEquals: Boolean,
    accountId: String?,
    fromId: String?,
    toId: String?,
    feeText: String,
    dateEpochDay: Long? = null,
    accounts: List<AccountEntity>,
    parents: List<CategoryUi>,
    expandedSubs: List<CategoryUi>,
    tags: List<dev.penned.app.data.db.entity.TagEntity> = emptyList(),
    selectedTagIds: Set<String> = emptySet(),
    /** 退款额 / 优惠额（M3-2）：信息条上各自的 chip 回显，落库时算净额（D069）。 */
    refundText: String = "",
    discountText: String = "",
    /** 键盘当前指到哪一格（退款/优惠复用同一个键盘）。 */
    amountField: AmountField = AmountField.AMOUNT,
    /** 「实付」行文案（[dev.penned.app.domain.RefundMath.summaryText]）；没退优惠时为 null。 */
    paySummary: String? = null,
    /** 报销状态/账户（M3-3）：「报销」chip 的回显与抽屉；金额文案见 [claimLabel]。 */
    reimburseState: ReimburseState = ReimburseState.NONE,
    reimburseAccountId: String? = null,
    /** 报销金额正在键盘上打的文本（抽屉收起后才进得了键盘）。 */
    reimburseText: String = "",
    /** 报销金额文案（`全额 100.00` / `30.00`）；金额还没输时为 null。 */
    claimLabel: String? = null,
    /** 模板抽屉（M3-5）：点一行把字段预填进本页（不直接入库）。 */
    templates: List<TemplateWithTags> = emptyList(),
    onPickTemplate: (String) -> Unit = {},
    onManageTemplates: () -> Unit = {},
    /** 第四 Tab「债务」（M3-4）：面板换成 选择债务 + 借入/归还 + 资金账户。 */
    debtTab: Boolean = false,
    debts: List<DebtWithRepaid> = emptyList(),
    debtId: String? = null,
    debtAction: DebtAction = DebtAction.OPEN,
    onSelectType: (TxType) -> Unit,
    onSelectDebtTab: () -> Unit = {},
    onPickDebt: (String?) -> Unit = {},
    onPickDebtAction: (DebtAction) -> Unit = {},
    /** 债务选择器里的「新建债务」：跳新建债务页（建完回来就在列表里可选）。 */
    onNewDebt: () -> Unit = {},
    onKey: (String) -> Unit,
    onSelectParent: (CategoryUi) -> Unit,
    onSelectSub: (CategoryUi) -> Unit,
    onNoteChange: (String) -> Unit,
    onPickAccount: (String?) -> Unit,
    onPickFrom: (String?) -> Unit,
    onPickTo: (String?) -> Unit,
    onSwap: () -> Unit,
    onToggleTag: (String) -> Unit = {},
    onCreateTag: (name: String, parentId: String?) -> Unit = { _, _ -> },
    onFocusField: (AmountField) -> Unit = {},
    onPickReimburseState: (ReimburseState) -> Unit = {},
    onPickReimburseAccount: (String?) -> Unit = {},
    onFeeChange: (String) -> Unit,
    onPickDate: (Long?) -> Unit = {},
    onAddCategory: (name: String, parentId: String?) -> Unit = { _, _ -> },
    onEvaluate: () -> Unit,
    onSave: () -> Unit,
    onSaveAgain: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageBg = MaterialTheme.colorScheme.background
    var showAccountPicker by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var showFee by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var addCategoryParent by remember { mutableStateOf<String?>(null) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showTagCreate by remember { mutableStateOf(false) }
    var showReimburse by remember { mutableStateOf(false) }
    var showReimburseAccount by remember { mutableStateOf(false) }
    var showDebtPicker by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    val amountColor = when (type) {
        TxType.EXPENSE -> expenseColor()
        TxType.INCOME -> incomeColor()
        TxType.TRANSFER -> MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = modifier.fillMaxSize().background(pageBg)) {
        // 顶栏：返回 + 类型分段 + 分类管理（原 disabled 的"编辑"死 UI 已接通）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PennedSpacing.xs, vertical = PennedSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(PennedDimens.iconButton)) {
                Icon(PennedIcons.Back, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            PennedSegmentedTabs(
                options = ADD_TABS,
                selected = currentTab(type, debtTab),
                onSelect = { tab ->
                    // 债务不是一种流水类型（落库的仍是收入/支出），单独一位路由
                    if (tab == AddTab.DEBT) onSelectDebtTab() else onSelectType(TxType.valueOf(tab.name))
                },
                label = { it.label },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(PennedDimens.iconButton)) {
                Icon(PennedIcons.Edit, contentDescription = "管理分类", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (debtTab) {
            // 债务面板（M3-4）：选择债务 → 借入/归还 → 资金账户
            DebtPanel(
                debts = debts,
                debtId = debtId,
                action = debtAction,
                account = accounts.firstOrNull { it.id == accountId },
                onPickDebt = { showDebtPicker = true },
                onPickAction = onPickDebtAction,
                onPickAccount = { showAccountPicker = true },
                onNewDebt = onNewDebt,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.md),
            )
        } else if (type == TxType.TRANSFER) {
            // 转账面板：转出 / 互换 / 转入
            PennedCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.md),
            ) {
                AccountFieldRow(
                    label = "转出账户",
                    account = accounts.firstOrNull { it.id == fromId },
                    onClick = { showFromPicker = true },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = PennedSpacing.sm),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onSwap) {
                        Icon(
                            PennedIcons.Transfer,
                            contentDescription = "互换账户",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                AccountFieldRow(
                    label = "转入账户",
                    account = accounts.firstOrNull { it.id == toId },
                    onClick = { showToPicker = true },
                )
            }
        } else {
            // 分类卡片
            PennedCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.md),
                contentPadding = PaddingValues(horizontal = PennedSpacing.sm, vertical = PennedSpacing.md),
            ) {
                CategoryPanel(
                    parents = parents,
                    selectedId = selectedId,
                    expandedId = expandedId,
                    expandedSubs = expandedSubs,
                    onSelectParent = onSelectParent,
                    onSelectSub = onSelectSub,
                    onAddParent = { addCategoryParent = null; showAddCategory = true },
                    onAddSub = { parentId -> addCategoryParent = parentId; showAddCategory = true },
                )
            }
        }

        // 信息条：备注 + 实时金额 + chips
        PennedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PennedSpacing.md, vertical = PennedSpacing.sm),
            contentPadding = PaddingValues(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = note,
                        onValueChange = onNoteChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        decorationBox = { inner ->
                            Box {
                                if (note.isEmpty()) {
                                    Text(
                                        "添加备注",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    Text(
                        text = if (amountText.isEmpty()) "0.00" else amountText,
                        style = MaterialTheme.typography.displaySmall,
                        color = amountColor,
                    )
                }
                Spacer(Modifier.height(PennedSpacing.sm))
                // FlowRow 而不是 Row：字体放大到 1.5× 时 chip 会放不下，Row 会把最后一个挤成竖排单字，
                // FlowRow 让它换行（D044 字体缩放检查）
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(PennedSpacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    val today = java.time.LocalDate.now()
                    PennedChip(
                        label = dev.penned.app.domain.AddDate.chipLabel(dateEpochDay, today),
                        icon = PennedIcons.Calendar,
                        selected = dateEpochDay != null && dateEpochDay != today.toEpochDay(),
                        onClick = { showDatePicker = true },
                    )
                    if (debtTab) {
                        // 债务 Tab：资金账户在面板上选，备注在左边，没有分类/标签/退优惠/报销
                    } else {
                        // 模板（M3-5）：点开抽屉选一个，字段预填进本页（确认后才入库）
                        PennedChip(
                            label = "模板",
                            icon = PennedIcons.Receipt,
                            selected = false,
                            onClick = { showTemplatePicker = true },
                        )
                    }
                    if (debtTab) {
                    } else if (type == TxType.TRANSFER) {
                        PennedChip(
                            label = if (feeText.isBlank()) "手续费" else "手续费 $feeText",
                            icon = PennedIcons.Tag,
                            selected = feeText.isNotBlank(),
                            onClick = { showFee = true },
                        )
                    } else {
                        PennedChip(
                            label = accounts.firstOrNull { it.id == accountId }?.name ?: "无账户",
                            icon = PennedIcons.Wallet,
                            selected = accountId != null,
                            onClick = { showAccountPicker = true },
                        )
                    }
                    if (!debtTab && type != TxType.TRANSFER) {
                        // 标签（M3-1）：多选；信息条只回显数量，明细在抽屉里
                        PennedChip(
                            label = if (selectedTagIds.isEmpty()) "标签" else "标签 · ${selectedTagIds.size}",
                            icon = PennedIcons.Tag,
                            selected = selectedTagIds.isNotEmpty(),
                            onClick = { showTagPicker = true },
                        )
                        // 退款 / 优惠（M3-2）：点开把键盘指过去，值就地回显在这两个 chip 上
                        PennedChip(
                            label = if (refundText.isBlank()) "退款" else "退款 $refundText",
                            icon = PennedIcons.Refund,
                            selected = refundText.isNotBlank() || amountField == AmountField.REFUND,
                            onClick = { onFocusField(AmountField.REFUND) },
                        )
                        PennedChip(
                            label = if (discountText.isBlank()) "优惠" else "优惠 $discountText",
                            icon = PennedIcons.Discount,
                            selected = discountText.isNotBlank() || amountField == AmountField.DISCOUNT,
                            onClick = { onFocusField(AmountField.DISCOUNT) },
                        )
                        // 报销（M3-3）：chip 只回显状态，三态 + 报销账户/金额在抽屉里选
                        PennedChip(
                            label = reimburseStateLabel(reimburseState),
                            icon = PennedIcons.Receipt,
                            selected = reimburseState != ReimburseState.NONE,
                            onClick = { showReimburse = true },
                        )
                    }
                }
                // 实付 = 原价 − 退款 − 优惠（只回显，落库在 saveInternal 一次算清）；
                // 债务 Tab 换成口径说明：这笔钱不进收支统计（借入不是收入、还款不是支出，D068）
                if (debtTab) {
                    Spacer(Modifier.height(PennedSpacing.xs))
                    Text(
                        "不计入收支与预算：借入不是收入、还款不是支出（详情页可改）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    paySummary?.let { summary ->
                        Spacer(Modifier.height(PennedSpacing.xs))
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 键盘正指在退款/优惠/报销金额格时的提示条：值由上面的 chip 与这里同时回显，
        // 右端给一条回主金额的出路
        if (amountField != AmountField.AMOUNT) {
            AmountFocusBar(
                label = when (amountField) {
                    AmountField.REFUND -> "退款"
                    AmountField.DISCOUNT -> "优惠"
                    else -> "报销金额"
                },
                value = when (amountField) {
                    AmountField.REFUND -> refundText
                    AmountField.DISCOUNT -> discountText
                    else -> reimburseText
                },
                onBackToAmount = { onFocusField(AmountField.AMOUNT) },
            )
        }

        // 键盘
        AmountKeyboard(
            canSave = canSave,
            showEquals = showEquals,
            onKey = onKey,
            onEvaluate = onEvaluate,
            onSaveAgain = onSaveAgain,
            onSave = onSave,
        )
    }

    if (showAccountPicker) {
        AccountPickerSheet(
            accounts = accounts,
            selectedId = accountId,
            includeNone = true,
            onPick = {
                onPickAccount(it)
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false },
        )
    }
    if (showFromPicker) {
        AccountPickerSheet(
            accounts = accounts,
            selectedId = fromId,
            includeNone = false,
            onPick = {
                onPickFrom(it)
                showFromPicker = false
            },
            onDismiss = { showFromPicker = false },
        )
    }
    if (showToPicker) {
        AccountPickerSheet(
            accounts = accounts,
            selectedId = toId,
            includeNone = false,
            onPick = {
                onPickTo(it)
                showToPicker = false
            },
            onDismiss = { showToPicker = false },
        )
    }
    if (showFee) {
        TextAmountDialog(
            title = "手续费",
            initial = feeText,
            onConfirm = {
                onFeeChange(it)
                showFee = false
            },
            onDismiss = { showFee = false },
        )
    }
    if (showAddCategory) {
        val parentName = parents.firstOrNull { it.id == addCategoryParent }?.name
        TextNameDialog(
            title = if (addCategoryParent == null) "新增一级分类" else "添加子分类到「$parentName」",
            placeholder = "分类名称",
            onConfirm = { name ->
                onAddCategory(name, addCategoryParent)
                showAddCategory = false
            },
            onDismiss = { showAddCategory = false },
        )
    }
    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = dev.penned.app.domain.AddDate.dayToPickerMillis(
                dateEpochDay ?: java.time.LocalDate.now().toEpochDay(),
            ),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onPickDate(dev.penned.app.domain.AddDate.pickerMillisToDay(millis))
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            androidx.compose.material3.DatePicker(state = pickerState, title = { Text("选择记账日期", modifier = Modifier.padding(PennedSpacing.lg)) })
        }
    }
    if (showTagPicker) {
        TagPickerSheet(
            tags = tags,
            selectedIds = selectedTagIds,
            onToggle = onToggleTag,
            // 新建走独立弹窗（不在抽屉里再套一层）：先收抽屉，建完即选中、回到表单
            onCreateRequest = {
                showTagPicker = false
                showTagCreate = true
            },
            onDismiss = { showTagPicker = false },
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
    if (showReimburse) {
        ReimburseSheet(
            state = reimburseState,
            accountName = accounts.firstOrNull { it.id == reimburseAccountId }?.name,
            claimLabel = claimLabel,
            onPickState = onPickReimburseState,
            onPickAccount = {
                // 两层抽屉不叠（与标签抽屉同一个做法）：先收这个，再起账户选择
                showReimburse = false
                showReimburseAccount = true
            },
            onEditAmount = {
                // 金额用同一个键盘录：收起抽屉，把键盘指到报销那一格
                showReimburse = false
                onFocusField(AmountField.REIMBURSE)
            },
            onDismiss = { showReimburse = false },
        )
    }
    if (showReimburseAccount) {
        AccountPickerSheet(
            accounts = accounts,
            selectedId = reimburseAccountId,
            includeNone = true,
            onPick = {
                onPickReimburseAccount(it)
                showReimburseAccount = false
            },
            onDismiss = { showReimburseAccount = false },
        )
    }
    if (showTemplatePicker) {
        TemplatePickerSheet(
            templates = templates,
            onPick = {
                showTemplatePicker = false
                onPickTemplate(it)
            },
            onManage = {
                // 先收抽屉再跳（两层浮层不叠）
                showTemplatePicker = false
                onManageTemplates()
            },
            onDismiss = { showTemplatePicker = false },
        )
    }
    if (showDebtPicker) {
        DebtPickerSheet(
            debts = debts,
            selectedId = debtId,
            onPick = {
                onPickDebt(it)
                showDebtPicker = false
            },
            onNewDebt = {
                // 先收抽屉再跳（两层浮层不叠，与报销/标签同一做法）；建完回到本页即可选中
                showDebtPicker = false
                onNewDebt()
            },
            onDismiss = { showDebtPicker = false },
        )
    }
}


/**
 * 键盘指到退款/优惠格时的提示条（M3-2）：说明键位现在打在哪个数上、值是多少，
 * 右端「回到金额」把键盘还给主金额（主金额本身一直在信息条右上，不被顶掉）。
 */
@Composable
private fun AmountFocusBar(label: String, value: String, onBackToAmount: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PennedSpacing.xs)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = PennedSpacing.lg, end = PennedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "正在输入：$label",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (value.isEmpty()) "0.00" else value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = onBackToAmount) { Text("回到金额") }
    }
}

@Composable
private fun AccountFieldRow(label: String, account: AccountEntity?, onClick: () -> Unit) {    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(PennedSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(account?.name ?: label, size = PennedDimens.avatar)
            Spacer(Modifier.width(PennedSpacing.md))
            Text(
                account?.name ?: label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (account == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                PennedIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(PennedDimens.iconChevron),
            )
        }
    }
}

/**
 * 记一笔的四个 Tab（M3-4）：前三个是 [TxType]（要落库的流水类型），
 * 「债务」不是类型而是入口（落库的仍是收入/支出 + `debtId`），所以单独枚举而不是往 [TxType] 里加值。
 */
private enum class AddTab(val label: String) {
    EXPENSE("支出"),
    INCOME("收入"),
    TRANSFER("转账"),
    DEBT("债务"),
}

private val ADD_TABS = AddTab.entries

private fun currentTab(type: TxType, debtTab: Boolean): AddTab =
    if (debtTab) AddTab.DEBT else AddTab.valueOf(type.name)

/**
 * 债务面板（M3-4）：选择债务 → 借入/归还 → 资金账户。
 * 一笔债务没有时给空态与出路（D044 六态）：先去建一笔债务，再回来记它的借入/还款。
 */
@Composable
private fun DebtPanel(
    debts: List<DebtWithRepaid>,
    debtId: String?,
    action: DebtAction,
    account: AccountEntity?,
    onPickDebt: () -> Unit,
    onPickAction: (DebtAction) -> Unit,
    onPickAccount: () -> Unit,
    onNewDebt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = debts.firstOrNull { it.id == debtId }
    // 还没选债务时先按"我欠对方"这一面给词；选了就跟着债务的方向走（两面用词不同）
    val direction = selected?.direction ?: DebtDirection.BORROW
    PennedCard(modifier = modifier) {
        if (debts.isEmpty()) {
            EmptyState(
                title = "还没有债务",
                hint = "债务是独立实体：先建一笔（对方 + 本金），再回来记它的借入与还款",
                icon = PennedIcons.Receipt,
                action = { PennedButton(text = "新建债务", onClick = onNewDebt) },
            )
        } else {
            DebtFieldRow(
                title = selected?.counterparty ?: "选择债务",
                subtitle = selected?.let {
                    "${DebtMath.directionLabel(it.direction)} · 剩余 ${Money.format(it.remainingCents)}"
                },
                onClick = onPickDebt,
                placeholder = selected == null,
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.md, vertical = PennedSpacing.sm)) {
                PennedSegmentedTabs(
                    options = DEBT_ACTIONS,
                    selected = action,
                    onSelect = onPickAction,
                    label = { DebtMath.actionLabel(direction, it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                debtActionHint(direction, action),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PennedSpacing.lg),
            )
            Spacer(Modifier.height(PennedSpacing.sm))
            DebtFieldRow(
                title = account?.name ?: "选择账户",
                subtitle = null,
                onClick = onPickAccount,
                placeholder = account == null,
            )
        }
    }
}

private val DEBT_ACTIONS = DebtAction.entries

/** 债务面板上那一格的说明（钱从哪儿到哪儿 + 这一笔会怎么记）。 */
private fun debtActionHint(direction: DebtDirection, action: DebtAction): String = when (action) {
    DebtAction.OPEN -> if (direction == DebtDirection.BORROW) {
        "钱进资金账户；这笔债务的欠款从这里开始记"
    } else {
        "钱从资金账户出去；对方欠你这一笔"
    }
    DebtAction.SETTLE -> if (direction == DebtDirection.BORROW) {
        "钱从资金账户出去；冲减这笔债务的欠款"
    } else {
        "钱进资金账户；冲减对方欠你的钱"
    }
}

/** 债务面板上的一行：头像 + 值（没选时是灰色占位）+ 副标题 + 箭头。 */
@Composable
private fun DebtFieldRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    placeholder: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PennedSpacing.md, vertical = PennedSpacing.xs)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(PennedSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(title, size = PennedDimens.avatar)
            Spacer(Modifier.width(PennedSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    // 没选的时候 title 就是提示语，用次级色，别把占位读成已选
                    color = if (placeholder) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                PennedIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(PennedDimens.iconChevron),
            )
        }
    }
}

/** 债务选择抽屉：本账本债务（对方 + 立场 + 剩余）+「新建债务」出口。 */
@Composable
internal fun DebtPickerSheet(
    debts: List<DebtWithRepaid>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onNewDebt: () -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "选择债务") {
        debts.forEach { debt ->
            PennedListRow(
                title = debt.counterparty,
                subtitle = "${DebtMath.directionLabel(debt.direction)} · 剩余 ${Money.format(debt.remainingCents)}",
                leadingIcon = PennedIcons.Wallet,
                trailing = if (debt.id == selectedId) "已选" else null,
                showChevron = false,
                onClick = { onPick(debt.id) },
            )
        }
        PennedListRow(
            title = "新建债务",
            subtitle = "建完回到这里就能选它",
            leadingIcon = PennedIcons.Plus,
            onClick = onNewDebt,
        )
    }
}

/** 账户选择：从底部升起的抽屉（D024 交互范式一），选中项打勾。 */
@Composable
fun AccountPickerSheet(
    accounts: List<AccountEntity>,
    selectedId: String?,
    includeNone: Boolean,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "选择账户") {
        if (includeNone) {
            PennedListRow(
                title = "无账户",
                trailing = if (selectedId == null) "已选" else null,
                showChevron = false,
                onClick = { onPick(null) },
            )
        }
        accounts.forEach { account ->
            PennedListRow(
                title = account.name,
                leadingIcon = PennedIcons.Wallet,
                trailing = if (account.id == selectedId) "已选" else null,
                showChevron = false,
                onClick = { onPick(account.id) },
            )
        }
        if (accounts.isEmpty()) {
            Text(
                "还没有账户，去「资产」页添加",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PennedSpacing.xl, vertical = PennedSpacing.sm),
            )
        }
    }
}

@Composable
internal fun TextNameDialog(
    title: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    PennedDialog(
        onDismiss = onDismiss,
        title = title,
        confirmEnabled = text.isNotBlank(),
        onConfirm = { onConfirm(text) },
    ) {
        // 用 BasicTextField 而非 OutlinedTextField：后者在 Robolectric 弹窗内会导致无法 idle
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(PennedSpacing.md),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun TextAmountDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    PennedDialog(
        onDismiss = onDismiss,
        title = title,
        dismissLabel = "清除",
        onConfirm = { onConfirm(text) },
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == '.' } },
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(PennedSpacing.md),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text("金额（元）", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun previewContent(
    amountText: String = "16.5",
    refundText: String = "",
    discountText: String = "",
    amountField: AmountField = AmountField.AMOUNT,
    paySummary: String? = null,
) = AddTransactionContent(
    type = TxType.EXPENSE,
    amountText = amountText,
    note = "",
    selectedId = "p0",
    expandedId = "p0",
    canSave = true,
    showEquals = false,
    accountId = null,
    fromId = null,
    toId = null,
    feeText = "",
    refundText = refundText,
    discountText = discountText,
    amountField = amountField,
    paySummary = paySummary,
    accounts = emptyList(),
    parents = listOf(
        CategoryUi("p0", "其他", hasSubs = true),
        CategoryUi("p1", "购物消费", hasSubs = true),
        CategoryUi("p2", "食品餐饮", hasSubs = false),
        CategoryUi("p3", "出行交通", hasSubs = false),
        CategoryUi("p4", "休闲娱乐", hasSubs = false),
    ),
    expandedSubs = listOf(
        CategoryUi("s0", "罚款赔偿", hasSubs = false),
        CategoryUi("s1", "理财支出", hasSubs = false),
    ),
    onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
    onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
    onSwap = {}, onFeeChange = {}, onEvaluate = {}, onSave = {}, onSaveAgain = {}, onBack = {}, onEdit = {},
)

@Preview(showBackground = true, name = "记一笔-支出", widthDp = 400, heightDp = 800)
@Composable
private fun AddScreenPreview() {
    PennedTheme { previewContent() }
}

/** 退款态（M3-2）：两个 chip 回显金额、键盘指在退款格、信息条下方一条实付算式。 */
@Preview(showBackground = true, name = "记一笔-退款态", widthDp = 400, heightDp = 800)
@Composable
private fun AddScreenRefundPreview() {
    PennedTheme {
        previewContent(
            amountText = "20.00",
            refundText = "3.00",
            discountText = "5.00",
            amountField = AmountField.REFUND,
            paySummary = dev.penned.app.domain.RefundMath.summaryText(2000L, 300L, 500L),
        )
    }
}
