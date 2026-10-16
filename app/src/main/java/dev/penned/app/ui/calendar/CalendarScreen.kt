package dev.penned.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.CalendarHeat
import dev.penned.app.domain.CalendarMath
import dev.penned.app.domain.Money
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.components.DayHeaderRow
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.home.HomeGroups
import dev.penned.app.ui.home.TransactionRow
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.heatFillColor
import dev.penned.app.ui.theme.heatInkColor
import dev.penned.app.ui.theme.incomeColor
import dev.penned.app.ui.theme.primaryInkColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/** 日历页（M3-17）：月网格（热力 + 每日支/收）+ 点选看当日流水 + 「今」+ 月小字总结。 */
class CalendarViewModel(private val app: PennedApplication) : ViewModel() {

    /** 当前看的月份（默认本月）。 */
    private val month = MutableStateFlow(YearMonth.now())

    /** 选中的日子（默认今天）。 */
    private val selected = MutableStateFlow(LocalDate.now().toEpochDay())

    data class UiState(
        val month: YearMonth = YearMonth.now(),
        val selectedDay: Long = LocalDate.now().toEpochDay(),
        /** 网格里每天的小计与笔数（键 = epochDay）。 */
        val dayTotals: Map<Long, HomeGroups.DayGroup> = emptyMap(),
        /** 选中那天的流水（降序，与首页一致）。 */
        val selectedItems: List<TxWithCategory> = emptyList(),
        /** 当月的收/支合计（**只算当月**：网格带出的上/下月日子不算）。 */
        val monthTotals: HomeGroups.MonthTotals = HomeGroups.MonthTotals(0L, 0L),
    )

    /**
     * 网格窗口的流水：**每日合计复用 `HomeGroups.groupByDay`**（与首页日头条同一份口径，
     * 不是各算各的——M3-17 的验收里明写"回归断言"）。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val rows: StateFlow<List<TxWithCategory>> = combine(
        app.settings.currentBookId,
        month,
    ) { bookId, m -> bookId to m }
        .flatMapLatest { (bookId, m) ->
            if (bookId == null) {
                flowOf(emptyList())
            } else {
                val (from, to) = CalendarMath.gridWindow(m)
                app.database.transactionDao().observeRangeTxsWithCategory(bookId, from, to)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<UiState> = combine(rows, month, selected) { list, m, day ->
        val groups = HomeGroups.groupByDay(list)
        UiState(
            month = m,
            selectedDay = day,
            dayTotals = groups.associateBy { it.epochDay },
            selectedItems = groups.firstOrNull { it.epochDay == day }?.items.orEmpty(),
            monthTotals = HomeGroups.monthTotalsOf(groups, m),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun shiftMonth(delta: Long) {
        month.value = CalendarMath.shiftMonth(month.value, delta)
    }

    /** 「今」：回到本月 + 选中今天。 */
    fun goToday() {
        month.value = YearMonth.now()
        selected.value = LocalDate.now().toEpochDay()
    }

    fun selectDay(epochDay: Long) {
        selected.value = epochDay
        // 点到上/下月的日子：跟着翻月（日历的常规行为）
        val date = LocalDate.ofEpochDay(epochDay)
        val current = month.value
        if (date.year != current.year || date.monthValue != current.monthValue) {
            month.value = YearMonth.from(date)
        }
    }
}

@Composable
fun CalendarScreen(onOpenDetail: (String) -> Unit) {
    val app = LocalApp.current
    val viewModel: CalendarViewModel = viewModel(initializer = { CalendarViewModel(app) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    CalendarContent(
        state = state,
        onShiftMonth = viewModel::shiftMonth,
        onToday = viewModel::goToday,
        onSelectDay = viewModel::selectDay,
        onOpenDetail = onOpenDetail,
    )
}

/** 无状态入口：截图测试/预览用。 */
@Composable
fun CalendarContent(
    state: CalendarViewModel.UiState,
    onShiftMonth: (Long) -> Unit = {},
    onToday: () -> Unit = {},
    onSelectDay: (Long) -> Unit = {},
    onOpenDetail: (String) -> Unit = {},
    today: LocalDate = LocalDate.now(),
) {
    val month = state.month
    val cells = CalendarMath.gridDays(month)
    val todayEpochDay = today.toEpochDay()
    // 热力标尺 = 当月（带出的上/下月日子不进标尺，但照样按这份标尺上色、并按"非本月"变淡）
    val heat = remember(state.dayTotals, month) { CalendarHeat.monthHeat(state.dayTotals.toHeatDays(), month) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 月份行：‹ 2026年10月 › + 「今」
        Row(
            Modifier.fillMaxWidth().padding(start = PennedSpacing.md, end = PennedSpacing.lg, top = PennedSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onShiftMonth(-1) }, modifier = Modifier.size(PennedDimens.iconButton)) {
                Icon(
                    PennedIcons.Back,
                    contentDescription = "上个月",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(PennedDimens.iconInline),
                )
            }
            Text(
                CalendarMath.monthTitle(month),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = PennedSpacing.xs),
            )
            IconButton(onClick = { onShiftMonth(1) }, modifier = Modifier.size(PennedDimens.iconButton)) {
                Icon(
                    PennedIcons.ChevronRight,
                    contentDescription = "下个月",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(PennedDimens.iconInline),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "今",
                style = PennedTextStyles.rowTitle,
                color = primaryInkColor(),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToday)
                    .padding(horizontal = PennedSpacing.md, vertical = PennedSpacing.xs),
            )
        }

        // 月小字总结：收绿 / 支红 / 余灰（空月照渲染 0.00 三项）
        MonthSummaryRow(state.monthTotals)

        // 星期表头（周一开头）
        Row(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.sm, vertical = PennedSpacing.xs)) {
            CalendarMath.WEEKDAY_LABELS.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 月网格
        Column(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.sm)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { epochDay ->
                        DayCell(
                            epochDay = epochDay,
                            inMonth = CalendarMath.inMonth(epochDay, month),
                            isToday = epochDay == todayEpochDay,
                            isSelected = epochDay == state.selectedDay,
                            heat = heat[epochDay],
                            totals = state.dayTotals[epochDay],
                            onClick = { onSelectDay(epochDay) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        PennedDivider()
        // 选中那天的流水（与首页同一行渲染）
        val items = state.selectedItems
        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                EmptyState(
                    title = "${HomeGroups.dayLabel(state.selectedDay, java.time.ZoneId.systemDefault())} 没有记录",
                    hint = "点上面别的日子看看",
                    icon = PennedIcons.Calendar,
                    modifier = Modifier.padding(top = PennedSpacing.lg),
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = PennedDimens.listBottomClearance),
            ) {
                item(key = "day-header") {
                    // 日头条复用首页那枚组件（支/收小字同口径）：小计取同一天的 DayGroup
                    val day = state.dayTotals[state.selectedDay]
                    DayHeaderRow(
                        label = HomeGroups.dayLabel(state.selectedDay, java.time.ZoneId.systemDefault()),
                        incomeCents = day?.incomeCents ?: 0L,
                        expenseCents = day?.expenseCents ?: 0L,
                    )
                }
                items(items, key = { it.id }) { row ->
                    TransactionRow(
                        row = row,
                        selectionMode = false,
                        selected = false,
                        onClick = { onOpenDetail(row.id) },
                        onLongClick = {},
                    )
                }
            }
        }
    }
}

/**
 * 月小字总结行：`收 3,000.00   支 6.00   余 2,994.00`（收绿 / 支红 / 余灰）。
 * 整行量不下时三项**一起去掉千分位**（退化规则见 [CalendarSummary]）。
 */
@Composable
private fun MonthSummaryRow(totals: HomeGroups.MonthTotals, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelMedium
    val gap = PennedSpacing.lg
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
    ) {
        val gapPx = with(density) { gap.roundToPx() }
        val availablePx = with(density) { maxWidth.toPx() }
        val labels = remember(totals, availablePx, style, measurer) {
            val widths = CalendarSummary.labels(totals, grouped = true)
                .map { measurer.measure(it, style).size.width }
            val grouped = CalendarSummary.fitsWithGrouping(
                groupedWidthPx = CalendarSummary.totalWidth(widths, gapPx),
                availablePx = availablePx.toInt(),
            )
            CalendarSummary.labels(totals, grouped = grouped)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SummaryText(labels[0], incomeColor())
            Spacer(Modifier.size(gap))
            SummaryText(labels[1], expenseColor())
            Spacer(Modifier.size(gap))
            SummaryText(labels[2], MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 总结行里的一段：自然宽度、放不下才收省略号（三段一起被 1/3 宽度封顶）。 */
@Composable
private fun RowScope.SummaryText(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
    )
}

/**
 * 一格：日期数字 + （有账时）支/收两行小字 + **热力底**（支出红 / 收入青绿，越多越深）。
 *
 * 三层的叠加规则（D091）：
 * - **热力 = 格子的底**（语义色按档位稀释的淡底）
 * - **选中 = 品牌色描边**（不与热力抢底；再叠字重加粗——除颜色外还有形状差）
 * - **今天 = 品牌实心圈**（压在最上层，日期字反白）
 *
 * 非本月（网格带出的上/下月日子、周条里邻月的日子）整格乘同一个 [CalendarStyle.outOfMonthAlpha]。
 */
@Composable
private fun DayCell(
    epochDay: Long,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    heat: CalendarHeat.Cell?,
    totals: HomeGroups.DayGroup?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = LocalDate.ofEpochDay(epochDay)
    Column(
        modifier
            .padding(CalendarStyle.cellGap)
            .aspectRatio(CalendarStyle.cellAspect)
            // 统一变淡：整格（日期字 + 支/收小字 + 热力底 + 今天圈）乘同一个因子，一处一处各写一个 alpha 会漂
            .alpha(if (inMonth) 1f else CalendarStyle.outOfMonthAlpha)
            .clip(MaterialTheme.shapes.small)
            .background(heat?.let { heatFillColor(it.expense, it.level) } ?: Color.Transparent)
            .then(
                if (isSelected) {
                    Modifier.border(CalendarStyle.selectStroke, primaryInkColor(), MaterialTheme.shapes.small)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = PennedSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(PennedDimens.avatarLarge)
                .then(
                    if (isToday) {
                        Modifier.clip(CircleShape).background(primaryInkColor())
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (totals != null) {
            // 两行小字：支出红 / 收入青绿（**不带"支/收"前缀**——格子只有 57dp 宽，带前缀会被截成"收 300…"；
            // 颜色就是全 App 的收支语言，选中那天下面还有日头条的「支 X 收 Y」把话说全）。
            // 颜色走**热力成对的字色**：底越深字越深/越亮（ContrastTest 逐对核过），不是把底色调浅
            if (totals.expenseCents > 0L) {
                Text(
                    Money.format(totals.expenseCents, grouped = false),
                    style = MaterialTheme.typography.labelSmall,
                    color = heatInkColor(expense = true),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (totals.incomeCents > 0L) {
                Text(
                    Money.format(totals.incomeCents, grouped = false),
                    style = MaterialTheme.typography.labelSmall,
                    color = heatInkColor(expense = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 网格里每天的额 → 热力分档的输入（`HomeGroups.DayGroup` 与 domain 之间的适配，一处写完）。 */
private fun Map<Long, HomeGroups.DayGroup>.toHeatDays(): List<CalendarHeat.Day> =
    map { (epochDay, day) -> CalendarHeat.Day(epochDay, day.expenseCents, day.incomeCents) }

@Preview(showBackground = true, name = "日历", widthDp = 400, heightDp = 800)
@Composable
private fun CalendarPreview() {
    val month = YearMonth.of(2026, 10)
    dev.penned.app.ui.theme.PennedTheme {
        val totals = HomeGroups.MonthTotals(incomeCents = 300_000L, expenseCents = 2200L)
        CalendarContent(
            state = CalendarViewModel.UiState(
                month = month,
                selectedDay = LocalDate.of(2026, 10, 8).toEpochDay(),
                dayTotals = mapOf(
                    LocalDate.of(2026, 10, 8).toEpochDay() to HomeGroups.DayGroup(
                        epochDay = LocalDate.of(2026, 10, 8).toEpochDay(),
                        incomeCents = 0L,
                        expenseCents = 600L,
                        items = emptyList(),
                    ),
                    LocalDate.of(2026, 10, 4).toEpochDay() to HomeGroups.DayGroup(
                        epochDay = LocalDate.of(2026, 10, 4).toEpochDay(),
                        incomeCents = 300_000L,
                        expenseCents = 1_600L,
                        items = emptyList(),
                    ),
                ),
                monthTotals = totals,
                selectedItems = listOf(
                    TxWithCategory(
                        id = "t1", type = dev.penned.app.domain.TxType.EXPENSE, amountCents = 600L,
                        accountId = null, categoryId = null, dateEpochMinute = 30_000_000L, note = "饮料",
                        categoryName = "食品餐饮", categoryParentName = null, categoryIconKey = null,
                        targetAccountName = null, excludeFromStats = false, refundCents = 0, discountCents = 0,
                    ),
                ),
            ),
            today = LocalDate.of(2026, 10, 15),
        )
    }
}
