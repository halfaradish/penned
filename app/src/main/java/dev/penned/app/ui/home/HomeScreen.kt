package dev.penned.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.Money
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.ExpenseRed
import dev.penned.app.ui.theme.IncomeTeal
import dev.penned.app.ui.theme.PennedTheme
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAdd: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onManageBooks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val currentBook by viewModel.currentBook.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    var showBookMenu by remember { mutableStateOf(false) }
    var showAddBook by remember { mutableStateOf(false) }

    HomeContent(
        bookName = currentBook?.name ?: "账本",
        rows = rows,
        onAdd = onAdd,
        onBookClick = { showBookMenu = true },
        bookMenu = {
            DropdownMenu(expanded = showBookMenu, onDismissRequest = { showBookMenu = false }) {
                books.forEach { book ->
                    DropdownMenuItem(
                        text = {
                            val mark = if (book.id == currentBook?.id) "✓ " else ""
                            Text(mark + book.name)
                        },
                        onClick = {
                            showBookMenu = false
                            viewModel.switchBook(book.id)
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("新增账本") },
                    onClick = {
                        showBookMenu = false
                        showAddBook = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("管理账本") },
                    onClick = {
                        showBookMenu = false
                        onManageBooks()
                    },
                )
            }
        },
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        onToggleSelect = viewModel::toggleSelect,
        onLongPressRow = viewModel::enterSelection,
        onOpenRow = onOpenDetail,
        onDeleteSelected = viewModel::deleteSelected,
        onExitSelection = viewModel::exitSelection,
        modifier = modifier,
    )

    if (showAddBook) {
        AddBookDialog(
            onConfirm = { name ->
                viewModel.addBook(name)
                showAddBook = false
            },
            onDismiss = { showAddBook = false },
        )
    }
}

@Composable
fun HomeContent(
    bookName: String,
    rows: List<TxWithCategory>,
    onAdd: () -> Unit,
    onBookClick: () -> Unit = {},
    bookMenu: @Composable () -> Unit = {},
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelect: (String) -> Unit = {},
    onLongPressRow: (String) -> Unit = {},
    onOpenRow: (String) -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onExitSelection: () -> Unit = {},
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                TextButton(onClick = onExitSelection) { Text("取消") }
                Text(
                    "已选 ${selectedIds.size} 条",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onDeleteSelected, enabled = selectedIds.isNotEmpty()) {
                    Text("删除", color = ExpenseRed)
                }
            } else {
                Box {
                    Text(
                        text = bookName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clickable(onClick = onBookClick),
                    )
                    bookMenu()
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (!selectionMode) {
            val today = LocalDate.now(zone)
            val totals = HomeGroups.monthTotals(rows, today.year, today.monthValue, zone)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SummaryCell("本月支出", Money.format(totals.expenseCents), ExpenseRed)
                    SummaryCell("本月收入", Money.format(totals.incomeCents), IncomeTeal)
                    SummaryCell("本月结余", Money.format(totals.incomeCents - totals.expenseCents), MaterialTheme.colorScheme.onSurface)
                }
            }

            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("添加一条新记账")
            }
        }

        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("还没有记账", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("点上方按钮记下第一笔", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                HomeGroups.groupByDay(rows, zone).forEach { group ->
                    item(key = "day-${group.epochDay}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = HomeGroups.dayLabel(group.epochDay, zone),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "收 ${Money.format(group.incomeCents)}  支 ${Money.format(group.expenseCents)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(group.items, key = { it.id }) { row ->
                        TransactionRow(
                            row = row,
                            selectionMode = selectionMode,
                            selected = row.id in selectedIds,
                            onClick = {
                                if (selectionMode) onToggleSelect(row.id) else onOpenRow(row.id)
                            },
                            onLongClick = { onLongPressRow(row.id) },
                        )
                        if (!selectionMode) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

// （批量选择状态由 HomeContent.selectedIds 直接传入）

@Composable
private fun SummaryCell(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    row: TxWithCategory,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val title = listOfNotNull(row.categoryParentName, row.categoryName).joinToString(" · ").ifEmpty { "未分类" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text("✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(categoryColor(row.categoryName ?: "?"), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (row.categoryName ?: "?").take(1),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!row.note.isNullOrBlank()) {
                Text(
                    row.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val color = when (row.type) {
            TxType.EXPENSE -> ExpenseRed
            TxType.INCOME -> IncomeTeal
            TxType.TRANSFER -> MaterialTheme.colorScheme.onSurface
        }
        val prefix = when (row.type) {
            TxType.EXPENSE -> "-"
            TxType.INCOME -> "+"
            TxType.TRANSFER -> ""
        }
        Text(
            text = prefix + Money.format(row.amountCents),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            textAlign = TextAlign.End,
        )
    }
}

/** 由分类名哈希生成稳定配色：黄金角散列避免色相聚集（M0 方案，图标系统随 M6 落地）。 */
fun categoryColor(name: String): Color {
    val golden = 0.61803398875
    val h = name.hashCode().toLong() and 0xFFFFFFFFL
    val hueFraction = ((h * golden) % 1.0).toFloat()
    return Color.hsl(hueFraction * 360f, 0.42f, 0.55f) // Color.hsl 的 hue 单位是度
}

@Composable
fun AddBookDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增账本") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("账本名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Preview(showBackground = true, name = "首页-有数据", widthDp = 400, heightDp = 780)
@Composable
private fun HomeDataPreview() {
    val rows = listOf(
        tx("1", minuteOf(2026, 10, 4, 19, 30), TxType.EXPENSE, 1600, "晚餐", "食品餐饮", "北苑餐厅"),
        tx("2", minuteOf(2026, 10, 4, 8, 10), TxType.INCOME, 150000, "工资", null, null),
        tx("3", minuteOf(2026, 10, 3, 12, 0), TxType.EXPENSE, 700, "午餐", "食品餐饮", "西门"),
    )
    PennedTheme { HomeContent("日常账本", rows, onAdd = {}) }
}

private fun tx(
    id: String,
    minute: Long,
    type: TxType,
    cents: Long,
    name: String?,
    parent: String?,
    note: String?,
) = TxWithCategory(
    id = id, type = type, amountCents = cents, accountId = null, categoryId = null,
    dateEpochMinute = minute, note = note, categoryName = name, categoryParentName = parent, categoryIconKey = null,
)

private fun minuteOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    LocalDate.of(year, month, day).atTime(hour, minute).atZone(ZoneId.systemDefault()).toEpochSecond() / 60
