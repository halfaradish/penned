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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.Money
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import java.time.LocalDate
import java.time.ZoneId
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.incomeColor

/** 列表项 key：FAB 显隐判断靠它（"添加一条新记账"滚出视野后 FAB 接管）。 */
private const val ADD_BUTTON_KEY = "add-button"
private const val SUMMARY_CARD_KEY = "month-card"
private const val HOME_LIST_TAG = "home-list"
private const val HOME_FAB_TAG = "home-fab"

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAdd: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onManageBooks: () -> Unit,
    onManageBudgets: () -> Unit = {},
    onSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val currentBook by viewModel.currentBook.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val budgetSummary by viewModel.budgetSummary.collectAsStateWithLifecycle()

    var showBookMenu by remember { mutableStateOf(false) }
    var showAddBook by remember { mutableStateOf(false) }

    HomeContent(
        bookName = currentBook?.name ?: "账本",
        rows = rows,
        onAdd = onAdd,
        budgetSummary = budgetSummary,
        onOpenBudgets = onManageBudgets,
        onSearch = onSearch,
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
    budgetSummary: HomeViewModel.BudgetSummary? = null,
    onOpenBudgets: () -> Unit = {},
    onSearch: () -> Unit = {},
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
                    Text("删除", color = expenseColor())
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
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSearch)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val listState = rememberLazyListState()
            // 月汇总/预算/添加按钮都是列表项：随内容滚走，滚走后由右下角 FAB 接管"记一笔"
            val addButtonVisible by remember {
                derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == ADD_BUTTON_KEY } }
            }
            val showFab = !selectionMode &&
                listState.layoutInfo.totalItemsCount > 0 &&  // 首帧尚未布局时不闪现
                !addButtonVisible

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag(HOME_LIST_TAG),
            ) {
                if (!selectionMode) {
                    item(key = SUMMARY_CARD_KEY) {
                        val today = LocalDate.now(zone)
                        HomeSummaryCard(
                            totals = HomeGroups.monthTotals(rows, today.year, today.monthValue, zone),
                            budgetSummary = budgetSummary,
                            onOpenBudgets = onOpenBudgets,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    item(key = ADD_BUTTON_KEY) {
                        Button(
                            onClick = onAdd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text("添加一条新记账")
                        }
                    }
                }

                if (rows.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("还没有记账", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("点上方按钮记下第一笔", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
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

            HomeAddFab(
                visible = showFab,
                onClick = onAdd,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

/** 右下角"记一笔"FAB：独立 composable，避免 AnimatedVisibility 命中 ColumnScope 的重载。 */
@Composable
private fun HomeAddFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .padding(16.dp)
                .testTag(HOME_FAB_TAG),
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/**
 * 月汇总 + 预算合并成一张卡（省屏占比）：
 * 第一行 支出/收入/结余，第二行 剩余预算 + 预算明细（可点进预算管理），第三行 4dp 进度条。
 * 没设预算时第二行换成"设置本月预算"入口。
 */
@Composable
private fun HomeSummaryCard(
    totals: HomeGroups.MonthTotals,
    budgetSummary: HomeViewModel.BudgetSummary?,
    onOpenBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryColumn("支出", Money.format(totals.expenseCents), expenseColor(), Modifier.weight(1f))
                SummaryColumn("收入", Money.format(totals.incomeCents), incomeColor(), Modifier.weight(1f))
                SummaryColumn(
                    "结余",
                    Money.format(totals.incomeCents - totals.expenseCents),
                    MaterialTheme.colorScheme.onSurface,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (budgetSummary == null) {
                Text(
                    "设置本月预算，控制花销 →",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenBudgets)
                        .padding(vertical = 2.dp),
                )
            } else {
                val state = dev.penned.app.domain.BudgetMath.state(budgetSummary.totalCents, budgetSummary.spentCents)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenBudgets)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("剩余预算", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (state.overspent) "已超支 ${Money.format(-state.remainingCents)}" else Money.format(state.remainingCents),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (state.overspent) expenseColor() else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "预算 ${Money.format(budgetSummary.totalCents)} · 已用 ${Money.format(budgetSummary.spentCents)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.usedFraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = if (state.overspent) expenseColor() else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 合并卡第一行的三列：标签一行、数值一行（等宽三列，居中）。 */
@Composable
private fun SummaryColumn(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor)
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
    PennedTheme {
        HomeContent(
            "日常账本",
            rows,
            onAdd = {},
            budgetSummary = HomeViewModel.BudgetSummary(totalCents = 300000, spentCents = 2200),
        )
    }
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
    dateEpochMinute = minute, note = note, categoryName = name, categoryParentName = parent,
    categoryIconKey = null, targetAccountName = null, excludeFromStats = false,
)

private fun minuteOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    LocalDate.of(year, month, day).atTime(hour, minute).atZone(ZoneId.systemDefault()).toEpochSecond() / 60
