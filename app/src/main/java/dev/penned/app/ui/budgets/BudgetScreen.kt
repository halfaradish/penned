package dev.penned.app.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.domain.BudgetMath
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.Money
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.theme.expenseColor
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.SectionLabel
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedDialog
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedProgressBar
import dev.penned.app.ui.components.PennedSectionHeader
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedTextAction
import dev.penned.app.ui.components.SectionLabel
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.primaryInkColor

/**
 * 预算管理页：总预算 + 分类预算，月度周期；支出为当月流水重算。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    viewModel: BudgetsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var editRow by remember { mutableStateOf<BudgetsViewModel.BudgetRow?>(null) }
    var showAddTotal by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { PennedTopBar(title = "预算管理", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = PennedSpacing.xxl),
        ) {
            SectionLabel("总预算（每月）")
            val total = state.total
            if (total == null) {
                PennedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                    onClick = { showAddTotal = true },
                ) {
                    Text(
                        "设置本月总预算",
                        style = MaterialTheme.typography.labelLarge,
                        color = primaryInkColor(),
                    )
                }
            } else {
                BudgetCard(row = total, label = "总预算", onClick = { editRow = total })
            }

            PennedSectionHeader(
                title = "分类预算（每月）",
                action = { PennedTextAction("+ 新增", onClick = { showAddCategory = true }) },
            )
            if (state.categoryRows.isEmpty()) {
                PennedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg)) {
                    Text(
                        "还没有分类预算",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.categoryRows.forEach { row ->
                BudgetCard(
                    row = row,
                    label = row.categoryName ?: "分类",
                    onClick = { editRow = row },
                )
            }
        }
    }

    if (showAddTotal) {
        BudgetAmountDialog(
            title = "设置总预算",
            initialAmount = "",
            categoryLabel = null,
            onConfirm = { cents ->
                viewModel.setBudget(null, cents)
                showAddTotal = false
            },
            onDismiss = { showAddTotal = false },
        )
    }
    if (showAddCategory) {
        BudgetCategoryDialog(
            categories = state.expenseCategories,
            onConfirm = { categoryId, cents ->
                viewModel.setBudget(categoryId, cents)
                showAddCategory = false
            },
            onDismiss = { showAddCategory = false },
        )
    }
    editRow?.let { row ->
        BudgetAmountDialog(
            title = if (row.budget.categoryId == null) "编辑总预算" else "编辑「${row.categoryName}」预算",
            initialAmount = Money.format(row.budget.amountCents, grouped = false),
            categoryLabel = row.categoryName,
            onConfirm = { cents ->
                viewModel.setBudget(row.budget.categoryId, cents)
                editRow = null
            },
            onDismiss = { editRow = null },
            onDelete = {
                viewModel.deleteBudget(row.budget.id)
                editRow = null
            },
        )
    }
}

@Composable
private fun BudgetCard(row: BudgetsViewModel.BudgetRow, label: String, onClick: () -> Unit) {
    PennedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = PennedTextStyles.rowTitle,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "剩 " + Money.format(row.state.remainingCents),
                style = PennedTextStyles.cardTitle,
                color = if (row.state.overspent) expenseColor() else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(PennedSpacing.sm))
        PennedProgressBar(
            progress = row.state.usedFraction,
            color = if (row.state.overspent) expenseColor() else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(PennedSpacing.sm))
        Text(
            "预算 ${Money.format(row.state.amountCents)} · 已用 ${Money.format(row.state.spentCents)}" +
                if (row.state.overspent) " · 已超支" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BudgetAmountDialog(
    title: String,
    initialAmount: String,
    categoryLabel: String?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var amountText by remember { mutableStateOf(initialAmount) }
    val cents = Money.parseToCents(amountText)
    PennedDialog(
        onDismiss = onDismiss,
        title = title,
        confirmEnabled = cents != null && cents > 0,
        onConfirm = { onConfirm(cents ?: 0) },
    ) {
        categoryLabel?.let {
            Text("分类：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(PennedSpacing.sm))
        }
        BudgetAmountField(amountText = amountText, onAmountChange = { amountText = it })
        if (onDelete != null) {
            Spacer(Modifier.height(PennedSpacing.md))
            PennedButton(
                text = "删除这条预算",
                onClick = onDelete,
                variant = PennedButtonVariant.Danger,
            )
        }
    }
}

/** 金额输入：填充式（白卡上再描一圈边是上一代的写法）。 */
@Composable
private fun BudgetAmountField(amountText: String, onAmountChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(PennedSpacing.md),
    ) {
        if (amountText.isEmpty()) {
            Text("每月预算（元）", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = amountText,
            onValueChange = { input -> onAmountChange(input.filter { it.isDigit() || it == '.' }) },
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 新增分类预算：选支出分类 + 金额。 */
@Composable
private fun BudgetCategoryDialog(
    categories: List<dev.penned.app.data.db.entity.CategoryEntity>,
    onConfirm: (String?, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<dev.penned.app.data.db.entity.CategoryEntity?>(null) }
    var amountText by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    val cents = Money.parseToCents(amountText)
    val label = selected?.let { s ->
        val parent = categories.firstOrNull { it.id == s.parentId }
        if (parent == null) s.name else "${parent.name} · ${s.name}"
    } ?: "选择分类"

    PennedDialog(
        onDismiss = onDismiss,
        title = "新增分类预算",
        confirmEnabled = selected != null && cents != null && cents > 0,
        onConfirm = { selected?.let { onConfirm(it.id, cents ?: 0) } },
    ) {
        // 分类选择：行式入口 + 底部抽屉（名单选择一律走抽屉，D024 交互范式一）
        PennedListRow(
            title = label,
            leadingIcon = PennedIcons.Tag,
            trailing = null,
            onClick = { pickerOpen = true },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(PennedSpacing.sm))
        BudgetAmountField(amountText = amountText, onAmountChange = { amountText = it })
    }
    if (pickerOpen) {
        BudgetCategorySheet(
            categories = categories,
            onPick = { selected = it; pickerOpen = false },
            onDismiss = { pickerOpen = false },
        )
    }
}

/** 分类选择抽屉：一级分类作分区头、二级作可点行（只有一级的就直接是一行）。 */
@Composable
private fun BudgetCategorySheet(
    categories: List<dev.penned.app.data.db.entity.CategoryEntity>,
    onPick: (dev.penned.app.data.db.entity.CategoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "选择分类") {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            categories.filter { it.parentId == null }.forEach { parent ->
                val children = categories.filter { it.parentId == parent.id }
                if (children.isEmpty()) {
                    PennedListRow(title = parent.name, onClick = { onPick(parent) })
                } else {
                    SectionLabel(parent.name)
                    children.forEach { child ->
                        PennedListRow(title = child.name, onClick = { onPick(child) })
                        PennedDivider(startIndent = PennedSpacing.lg)
                    }
                }
            }
        }
    }
}
