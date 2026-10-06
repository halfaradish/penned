package dev.penned.app.ui.add

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.domain.Money
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedSegmentedTabs
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.icons.PennedIcons
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
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onManageCategories: () -> Unit = {},
) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = editId ?: "new",
        initializer = { AddViewModel(app, editId) },
    )
    val type by viewModel.type.collectAsStateWithLifecycle()
    val amount by viewModel.amountText.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val expandedId by viewModel.expandedParentId.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val accountId by viewModel.accountId.collectAsStateWithLifecycle()
    val fromId by viewModel.transferFromId.collectAsStateWithLifecycle()
    val toId by viewModel.transferToId.collectAsStateWithLifecycle()
    val feeText by viewModel.feeText.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()
    val showEquals by viewModel.showEquals.collectAsStateWithLifecycle()
    val dateEpochDay by viewModel.dateEpochDay.collectAsStateWithLifecycle()

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
        onSelectType = viewModel::selectType,
        onKey = viewModel::pressKey,
        onSelectParent = { viewModel.selectParent(it.id, it.hasSubs) },
        onSelectSub = { viewModel.selectSub(it.id) },
        onNoteChange = viewModel::setNote,
        onPickAccount = viewModel::pickAccount,
        onPickFrom = viewModel::pickTransferFrom,
        onPickTo = viewModel::pickTransferTo,
        onSwap = viewModel::swapTransfer,
        onFeeChange = viewModel::setFee,
        onPickDate = viewModel::setDate,
        onAddCategory = { name, parentId -> viewModel.addCategoryAndSelect(name, parentId) },
        onSave = { viewModel.save(onDone) },
        onSaveAgain = viewModel::saveAgain,
        onEvaluate = viewModel::evaluate,
        onBack = onDone,
        onEdit = onManageCategories,
        modifier = modifier,
    )
}

data class AccountUi(val id: String, val name: String, val typeName: String)

@Composable
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
    onSelectType: (TxType) -> Unit,
    onKey: (String) -> Unit,
    onSelectParent: (CategoryUi) -> Unit,
    onSelectSub: (CategoryUi) -> Unit,
    onNoteChange: (String) -> Unit,
    onPickAccount: (String?) -> Unit,
    onPickFrom: (String?) -> Unit,
    onPickTo: (String?) -> Unit,
    onSwap: () -> Unit,
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
                options = TYPE_TABS,
                selected = type,
                onSelect = onSelectType,
                label = { typeLabel(it) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(PennedDimens.iconButton)) {
                Icon(PennedIcons.Edit, contentDescription = "管理分类", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (type == TxType.TRANSFER) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val today = java.time.LocalDate.now()
                    PennedChip(
                        label = dev.penned.app.domain.AddDate.chipLabel(dateEpochDay, today),
                        icon = PennedIcons.Calendar,
                        selected = dateEpochDay != null && dateEpochDay != today.toEpochDay(),
                        onClick = { showDatePicker = true },
                    )
                    if (type == TxType.TRANSFER) {
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
                    if (type != TxType.TRANSFER) {
                        // 报销状态是 M3 的编辑能力，这里保持只读语义（新记为"不报销"）
                        PennedChip(label = "不报销", icon = PennedIcons.Info, enabled = false)
                    }
                }
            }
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
            androidx.compose.material3.DatePicker(state = pickerState, title = { Text("选择记账日期", modifier = Modifier.padding(16.dp)) })
        }
    }
}


@Composable
private fun AccountFieldRow(label: String, account: AccountEntity?, onClick: () -> Unit) {
    Surface(
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
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private val TYPE_TABS = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)

private fun typeLabel(type: TxType): String = when (type) {
    TxType.EXPENSE -> "支出"
    TxType.INCOME -> "收入"
    TxType.TRANSFER -> "转账"
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
private fun TextNameDialog(
    title: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    // 用 BasicTextField 而非 OutlinedTextField：后者在 Robolectric 弹窗内会导致无法 idle
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TextAmountDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == '.' } },
                placeholder = { Text("金额（元）") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("清除") } },
    )
}

@Preview(showBackground = true, name = "记一笔-支出", widthDp = 400, heightDp = 800)
@Composable
private fun AddScreenPreview() {
    PennedTheme {
        AddTransactionContent(
            type = TxType.EXPENSE,
            amountText = "16.5",
            note = "",
            selectedId = "p0",
            expandedId = "p0",
            canSave = true,
            showEquals = false,
            accountId = null,
            fromId = null,
            toId = null,
            feeText = "",
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
    }
}
