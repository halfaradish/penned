package dev.penned.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.Money
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.DayHeaderRow
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedCardSection
import dev.penned.app.ui.components.PennedCardSectionPosition
import dev.penned.app.ui.components.PennedDialog
import dev.penned.app.ui.components.SwipeToDeleteRow
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTheme
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.incomeColor
import dev.penned.app.ui.theme.primaryInkColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 列表项 key：FAB 显隐判断靠它（"添加一条新记账"滚出视野后 FAB 接管）。 */
private const val ADD_BUTTON_KEY = "add-button"
private const val SUMMARY_CARD_KEY = "month-card"
private const val BUDGET_CARD_KEY = "budget-card"
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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var undoJob by remember { mutableStateOf<Job?>(null) }

    // 删除后给一次「撤销」机会（D024 交互范式二）；连续删除时后一条顶掉前一条
    fun showUndo(message: String) {
        undoJob?.cancel()
        undoJob = scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "撤销",
                // 带动作的 Snackbar 按 M3 给足 10s：撤销窗口太短等于没有
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearUndo()
        }
    }

    HomeContent(
        bookName = currentBook?.name ?: "账本",
        rows = rows.orEmpty(),
        loading = rows == null,
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
        onDeleteRow = { id ->
            viewModel.deleteRow(id)
            showUndo("已删除 1 条")
        },
        onDeleteSelected = {
            val count = selectedIds.size
            viewModel.deleteSelected()
            showUndo("已删除 $count 条")
        },
        onExitSelection = viewModel::exitSelection,
        snackbarHostState = snackbarHostState,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeContent(
    bookName: String,
    rows: List<TxWithCategory>,
    onAdd: () -> Unit,
    loading: Boolean = false,
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
    onDeleteRow: (String) -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onExitSelection: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HomeHeader(
                bookName = bookName,
                selectionMode = selectionMode,
                selectedCount = selectedIds.size,
                onBookClick = onBookClick,
                bookMenu = bookMenu,
                onSearch = onSearch,
                onExitSelection = onExitSelection,
                onDeleteSelected = onDeleteSelected,
                canDelete = selectedIds.isNotEmpty(),
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val listState = rememberLazyListState()
                // 月汇总/预算/添加按钮都是列表项：随内容滚走，滚走后由右下角 FAB 接管"记一笔"
                val addButtonVisible by remember {
                    derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == ADD_BUTTON_KEY } }
                }
                val showFab = !selectionMode &&
                    listState.layoutInfo.totalItemsCount > 0 && // 首帧尚未布局时不闪现
                    !addButtonVisible

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag(HOME_LIST_TAG),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = PennedSpacing.sm,
                        bottom = PennedSpacing.xxl,
                    ),
                ) {
                    if (!selectionMode) {
                        item(key = SUMMARY_CARD_KEY) {
                            val today = LocalDate.now(zone)
                            HomeSummaryCard(
                                totals = HomeGroups.monthTotals(rows, today.year, today.monthValue, zone),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
                            )
                        }
                        item(key = BUDGET_CARD_KEY) {
                            HomeBudgetCard(
                                budgetSummary = budgetSummary,
                                onOpenBudgets = onOpenBudgets,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
                            )
                        }
                        item(key = ADD_BUTTON_KEY) {
                            PennedButton(
                                text = "添加一条新记账",
                                onClick = onAdd,
                                leadingIcon = PennedIcons.Plus,
                                modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                            )
                        }
                    }

                    // loading：账本/流水还没读到——不渲染空态，避免冷启动闪"还没有记账"
                    if (rows.isEmpty() && !loading) {
                        item(key = "empty") {
                            EmptyState(
                                title = "还没有记账",
                                hint = "点上方按钮记下第一笔",
                                icon = PennedIcons.Edit,
                            )
                        }
                    } else {
                        // 一个月 = 一张卡。卡由「月标题（顶段，扛圆角）+ 各日（吸顶日头条 + 行）+ 最后一行（底段，扛圆角）」拼成；
                        // 圆角必须落在够高的段上，见 PennedCardSection 的注释。
                        val months = HomeGroups.groupByMonth(rows, zone)
                        months.forEach { month ->
                            item(key = "month-title-${month.yearMonth}") {
                                PennedCardSection(
                                    position = PennedCardSectionPosition.Top,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = PennedSpacing.lg),
                                ) {
                                    MonthTitleRow(month.yearMonth)
                                }
                            }
                            month.days.forEachIndexed { dayIndex, day ->
                                val isLastDay = dayIndex == month.days.lastIndex
                                stickyHeader(key = "day-${day.epochDay}") {
                                    PennedCardSection(
                                        position = PennedCardSectionPosition.Middle,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = PennedSpacing.lg),
                                    ) {
                                        DayHeaderRow(
                                            label = HomeGroups.dayLabel(day.epochDay, zone),
                                            incomeCents = day.incomeCents,
                                            expenseCents = day.expenseCents,
                                        )
                                    }
                                }
                                itemsIndexed(day.items, key = { _, row -> row.id }) { rowIndex, row ->
                                    val isLastRowOfDay = rowIndex == day.items.lastIndex
                                    val isLastRowOfCard = isLastDay && isLastRowOfDay
                                    PennedCardSection(
                                        position = if (isLastRowOfCard) {
                                            PennedCardSectionPosition.Bottom
                                        } else {
                                            PennedCardSectionPosition.Middle
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem()
                                            .padding(horizontal = PennedSpacing.lg)
                                            // 卡间距 12：留在卡片外，圆角贴着卡边走
                                            .then(
                                                if (isLastRowOfCard) {
                                                    Modifier.padding(bottom = PennedSpacing.md)
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                    ) {
                                        if (selectionMode) {
                                            TransactionRow(
                                                row = row,
                                                selectionMode = true,
                                                selected = row.id in selectedIds,
                                                onClick = { onToggleSelect(row.id) },
                                                onLongClick = { onLongPressRow(row.id) },
                                            )
                                        } else {
                                            SwipeToDeleteRow(
                                                onDelete = { onDeleteRow(row.id) },
                                                rowBackground = MaterialTheme.colorScheme.surface,
                                            ) {
                                                TransactionRow(
                                                    row = row,
                                                    selectionMode = false,
                                                    selected = false,
                                                    onClick = { onOpenRow(row.id) },
                                                    onLongClick = { onLongPressRow(row.id) },
                                                )
                                            }
                                            // 日与日之间靠日头条分开，最后一行不再画线
                                            if (!isLastRowOfDay) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(start = PennedDimens.rowTextIndent),
                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val snackbarVisible = snackbarHostState?.currentSnackbarData != null
                HomeAddFab(
                    visible = showFab && !snackbarVisible,
                    onClick = onAdd,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        if (snackbarHostState != null) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(PennedSpacing.lg),
            )
        }
    }
}

/** 页头：账本名（可点出账本菜单）+ 搜索入口；多选模式换成批量操作条。 */
@Composable
private fun HomeHeader(
    bookName: String,
    selectionMode: Boolean,
    selectedCount: Int,
    onBookClick: () -> Unit,
    bookMenu: @Composable () -> Unit,
    onSearch: () -> Unit,
    onExitSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    canDelete: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PennedSpacing.md, end = PennedSpacing.sm, top = PennedSpacing.sm, bottom = PennedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            TextButton(onClick = onExitSelection) { Text("取消") }
            Text(
                "已选 $selectedCount 条",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDeleteSelected, enabled = canDelete) {
                Text("删除", color = expenseColor())
            }
        } else {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = onBookClick)
                        .padding(horizontal = PennedSpacing.sm, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(bookName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(PennedSpacing.xs))
                    Icon(
                        PennedIcons.ChevronDown,
                        contentDescription = "切换账本",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                bookMenu()
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearch) {
                Icon(
                    PennedIcons.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
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
                .padding(PennedSpacing.lg)
                .testTag(HOME_FAB_TAG),
        ) {
            Icon(PennedIcons.Plus, contentDescription = "记一笔")
        }
    }
}

/** 月标题（卡片最上面一行）：只显示 `yyyy/m`，不带收支（用户裁决）。 */
@Composable
private fun MonthTitleRow(yearMonth: YearMonth, modifier: Modifier = Modifier) {
    Text(
        text = HomeGroups.monthLabel(yearMonth.year, yearMonth.monthValue),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        // 上宽下窄：标题压住下面的日头条，不留一段空
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = PennedSpacing.lg,
                end = PennedSpacing.lg,
                top = PennedSpacing.md,
                bottom = PennedSpacing.sm,
            ),
    )
}

/** 月汇总卡：支出/收入/结余三列（标签一行 + 数值一行）。 */
@Composable
private fun HomeSummaryCard(
    totals: HomeGroups.MonthTotals,
    modifier: Modifier = Modifier,
) {
    PennedCard(modifier = modifier) {
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
    }
}

/**
 * 预算卡：与月汇总卡同款三列布局（预算/已用/剩余），只多一条进度条；整卡可点进预算管理。
 * 未设预算时是"设置本月预算"入口。
 */
@Composable
private fun HomeBudgetCard(
    budgetSummary: HomeViewModel.BudgetSummary?,
    onOpenBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (budgetSummary == null) {
        PennedCard(modifier = modifier, onClick = onOpenBudgets) {
            Text(
                "设置本月预算，控制花销 →",
                style = MaterialTheme.typography.labelMedium,
                color = primaryInkColor(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
        }
        return
    }
    val state = dev.penned.app.domain.BudgetMath.state(budgetSummary.totalCents, budgetSummary.spentCents)
    val remainColor = if (state.overspent) expenseColor() else MaterialTheme.colorScheme.onSurface
    PennedCard(modifier = modifier, onClick = onOpenBudgets) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryColumn("预算", Money.format(budgetSummary.totalCents), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            SummaryColumn("已用", Money.format(budgetSummary.spentCents), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            // 超支时 remainingCents 为负，红字直接读出超了多少
            SummaryColumn("剩余", Money.format(state.remainingCents), remainColor, Modifier.weight(1f))
        }
        Spacer(Modifier.height(PennedSpacing.sm))
        LinearProgressIndicator(
            progress = { state.usedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(percent = 50)),
            color = if (state.overspent) expenseColor() else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/** 卡片里的三列：标签一行、数值一行（等宽三列，居中）。 */
@Composable
private fun SummaryColumn(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}

@Composable
fun AddBookDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    PennedDialog(
        onDismiss = onDismiss,
        title = "新增账本",
        onConfirm = { onConfirm(name) },
        confirmEnabled = name.isNotBlank(),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("账本名称") },
            singleLine = true,
        )
    }
}

@Preview(showBackground = true, name = "首页-有数据", widthDp = 400, heightDp = 780)
@Composable
private fun HomeDataPreview() {
    val rows = listOf(
        tx("1", minuteOf(2026, 10, 4, 19, 30), TxType.EXPENSE, 1600, "晚餐", "食品餐饮", "北苑餐厅"),
        tx("2", minuteOf(2026, 10, 4, 8, 10), TxType.INCOME, 150000, "工资", null, null),
        tx("3", minuteOf(2026, 10, 3, 12, 0), TxType.EXPENSE, 700, "午餐", "食品餐饮", "西门"),
        // 上个月 = 另一张卡；跨年的日头带 yyyy年
        tx("4", minuteOf(2026, 9, 30, 21, 5), TxType.EXPENSE, 3200, "打车", "出行交通", null),
        tx("5", minuteOf(2025, 12, 31, 23, 50), TxType.EXPENSE, 1200, "夜宵", "食品餐饮", "夜市"),
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
