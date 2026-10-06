package dev.penned.app.ui.budgets

import androidx.compose.foundation.clickable
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
import dev.penned.app.ui.theme.PennedSpacing
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = PennedSpacing.xl, end = PennedSpacing.sm, top = PennedSpacing.md, bottom = PennedSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "分类预算（每月）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showAddCategory = true }) { Text("+ 新增") }
            }
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
            initialAmount = Money.format(row.budget.amountCents),
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
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "剩 " + Money.format(row.state.remainingCents),
                style = MaterialTheme.typography.titleMedium,
                color = if (row.state.overspent) expenseColor() else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(PennedSpacing.sm))
        LinearProgressIndicator(
            progress = { row.state.usedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(percent = 50)),
            color = if (row.state.overspent) expenseColor() else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                categoryLabel?.let {
                    Text("分类：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("每月预算（元）") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(cents ?: 0) }, enabled = cents != null && cents > 0) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除", color = expenseColor()) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增分类预算") },
        text = {
            Column {
                Box {
                    OutlinedButton(
                        onClick = { pickerOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(220.dp),
                        )
                    }
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                        val parents = categories.filter { it.parentId == null }
                        parents.forEach { parent ->
                            val children = categories.filter { it.parentId == parent.id }
                            if (children.isEmpty()) {
                                DropdownMenuItem(text = { Text(parent.name) }, onClick = { selected = parent; pickerOpen = false })
                            } else {
                                Text(
                                    parent.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                children.forEach { child ->
                                    DropdownMenuItem(
                                        text = { Text("　${child.name}") },
                                        onClick = { selected = child; pickerOpen = false },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("每月预算（元）") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected!!.id, cents ?: 0) },
                enabled = selected != null && cents != null && cents > 0,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
