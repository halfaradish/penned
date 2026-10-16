package dev.penned.app.ui.calendar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import dev.penned.app.domain.CalendarPages
import dev.penned.app.domain.Money
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.components.DayHeaderRow
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.YearMonthPickerDialog
import dev.penned.app.ui.home.HomeGroups
import dev.penned.app.ui.home.TransactionRow
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedMotion
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.heatFillColor
import dev.penned.app.ui.theme.heatInkColor
import dev.penned.app.ui.theme.iconMutedColor
import dev.penned.app.ui.theme.incomeColor
import dev.penned.app.ui.theme.primaryInkColor
import dev.penned.app.ui.theme.rememberAnimationsEnabled
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 横滑切月的测试标记：往它上面划 = 切月（手势与统计页同款）。 */
internal const val CALENDAR_PAGER_TAG = "calendar-pager"

/** 收起态的周条（测试用它判断"收起了没有"）。 */
internal const val CALENDAR_WEEK_STRIP_TAG = "calendar-week-strip"

/** 当日流水列表（收起后才轮到它滚动，测试往它上面划）。 */
internal const val CALENDAR_DAY_LIST_TAG = "calendar-day-list"

/** 日历页（M3-17 + 优化轮）：热力月网格 + 左右滑切月 + 点标题选年月 + 上滑收起成周视图。 */
class CalendarViewModel(private val app: PennedApplication) : ViewModel() {

    /** 当前看的月份（默认本月）。`internal` 只给单测读——UI 一律走 [state]（WhileSubscribed 的 stateIn 不订就只有初值）。 */
    internal val month = MutableStateFlow(YearMonth.now())

    /** 选中的日子（默认今天）。同 [month]，`internal` 只给单测读。 */
    internal val selected = MutableStateFlow(LocalDate.now().toEpochDay())

    data class UiState(
        val month: YearMonth = YearMonth.now(),
        val selectedDay: Long = LocalDate.now().toEpochDay(),
        /** 网格里每天的小计与笔数（键 = epochDay）。 */
        val dayTotals: Map<Long, HomeGroups.DayGroup> = emptyMap(),
        /** 选中那天的流水（降序，与首页一致）。 */
        val selectedItems: List<TxWithCategory> = emptyList(),
        /** 当月的收/支合计（**只算当月**：网格带出的上/下月日子不算）。 */
        val monthTotals: HomeGroups.MonthTotals = HomeGroups.MonthTotals(0L, 0L),
        /** 可翻到的月份（**新→旧**，第 0 页恒是本月；跨度与统计页的时间条同口径）。 */
        val months: List<YearMonth> = listOf(YearMonth.now()),
    ) {
        /** 当前月在第几页（横滑 pager 的初值与回写都用它）。 */
        val monthPage: Int get() = CalendarPages.pageOf(month, months)
    }

    /** 本账本最早有流水的月份（决定能往回翻多远；与统计页同一份查询与换算）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val earliestMonth = app.settings.currentBookId.flatMapLatest { bookId ->
        if (bookId == null) {
            flowOf(null)
        } else {
            app.database.transactionDao().observeEarliestMinute(bookId).map { minute ->
                minute?.let {
                    YearMonth.from(Instant.ofEpochSecond(it * 60).atZone(ZoneId.systemDefault()).toLocalDate())
                }
            }
        }
    }

    /** 可翻到的月份（新→旧）。`internal` 只给单测读初值（没账时就是兜底的 12 个月）。 */
    internal val months: StateFlow<List<YearMonth>> = earliestMonth
        .map { CalendarPages.months(it, YearMonth.now()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CalendarPages.months(earliest = null, today = YearMonth.now()),
        )

    /**
     * 网格窗口的流水：**每日合计复用 `HomeGroups.groupByDay`**（与首页日头条同一份口径，
     * 不是各算各的——M3-17 的验收里明写"回归断言"）。
     *
     * 优化轮起窗口按"上一月 ~ 下一月"取：横滑时邻页要有热力与支/收小字，滑过去不能是一屏白格。
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
                val (from, _) = CalendarMath.gridWindow(m.minusMonths(1))
                val (_, to) = CalendarMath.gridWindow(m.plusMonths(1))
                app.database.transactionDao().observeRangeTxsWithCategory(bookId, from, to)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<UiState> = combine(rows, month, selected, months) { list, m, day, allMonths ->
        val groups = HomeGroups.groupByDay(list)
        UiState(
            month = m,
            selectedDay = day,
            dayTotals = groups.associateBy { it.epochDay },
            selectedItems = groups.firstOrNull { it.epochDay == day }?.items.orEmpty(),
            monthTotals = HomeGroups.monthTotalsOf(groups, m),
            months = allMonths,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /**
     * 切月（横滑停稳 / ‹ › / 跳转都走这一条）：**选中日跟着到新月的同一天号**，
     * 该月没有这天就夹到月末（10/31 → 2/28，闰年 2/29，见 [CalendarMath.followDay]）。
     * 目标月在条外（比最早流水还老、或未来月）时夹到端点——**不给未来月**（那里一定是空网格）。
     */
    fun selectMonth(target: YearMonth) {
        val all = months.value
        if (all.isEmpty()) return
        // 条是新→旧：头（first）是本月 = 上界，尾（last）是最早的月 = 下界
        val clamped = when {
            target > all.first() -> all.first()
            target < all.last() -> all.last()
            else -> target
        }
        if (clamped == month.value) return
        month.value = clamped
        selected.value = CalendarMath.followDay(selected.value, clamped)
    }

    /** 「今」：回到本月 + 选中今天（**不动收起状态**——那是用户自己的选择，与 D032 同口径）。 */
    fun goToday() {
        val now = LocalDate.now()
        selected.value = now.toEpochDay()
        month.value = YearMonth.from(now)
    }

    fun selectDay(epochDay: Long) {
        selected.value = epochDay
        // 点到上/下月的日子：跟着翻月（日历的常规行为；选中日就是用户点的那天，不再按"同号"改）
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
        onShiftMonth = { delta -> viewModel.selectMonth(CalendarMath.shiftMonth(state.month, delta)) },
        onToday = viewModel::goToday,
        onSelectDay = viewModel::selectDay,
        onSelectMonth = viewModel::selectMonth,
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
    onSelectMonth: (YearMonth) -> Unit = {},
    onOpenDetail: (String) -> Unit = {},
    today: LocalDate = LocalDate.now(),
) {
    val month = state.month
    val todayEpochDay = today.toEpochDay()
    val todayMonth = YearMonth.from(today)
    val heatDays = remember(state.dayTotals) { state.dayTotals.toHeatDays() }

    // —— 纯页面内状态（不落库、不加设置项，与首页月卡的 toggledMonths 同档）——
    var collapsed by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val animations = rememberAnimationsEnabled()
    val density = LocalDensity.current
    val collapseAfterPx = with(density) { CalendarCollapse.collapseAfter.toPx() }
    val gate = remember(collapseAfterPx) { CalendarCollapseGate(collapseAfterPx) }

    // 横滑切月：页序 = 新→旧（第 0 页恒是本月），layout 反过来 → "往右拉 = 看更早的月"，
    // 到头就是本月、往未来滑不动（账号里没有未来月的数据）。
    val pagerState = rememberPagerState(initialPage = state.monthPage) { state.months.size.coerceAtLeast(1) }
    // 外部改了月份（‹ › /「今」/跳转/点邻月的日子）→ 翻到那一页；滑动中不动，免得跟手指抢
    LaunchedEffect(month, state.months) {
        val target = CalendarPages.pageOf(month, state.months)
        if (pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }
    // 滑完**停稳**才回写月份（滑动过程中不改月、也不改选中）；月份没变时 ViewModel 直接返回，不会回环
    LaunchedEffect(pagerState, state.months) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            CalendarPages.monthOf(page, state.months)?.let(onSelectMonth)
        }
    }

    // 当日流水列表：展开态下把位移先给"收起日历"（先收起、再滚动），收起态放行给列表滚动
    val scrollConnection = remember(listState, gate) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y == 0f) return Offset.Zero
                gate.onScroll(available.y, atTop = !listState.canScrollBackward)?.let { collapsed = !it }
                return if (collapsed) Offset.Zero else Offset(x = 0f, y = available.y)
            }
        }
    }
    // 网格/周条上没有可滚动的东西 → 自己接竖滑（横向留给 pager）：上滑收起、列表在顶时下滑展开
    val gridDrag = Modifier.pointerInput(gate, listState) {
        detectVerticalDragGestures { change, dragAmount ->
            change.consume()
            gate.onScroll(dragAmount, atTop = !listState.canScrollBackward)?.let { collapsed = !it }
        }
    }

    val canGoNewer = state.months.firstOrNull() != month
    val canGoOlder = state.months.lastOrNull() != month

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 月份行：‹ [2026年10月 ⌄] › …… [收起/展开] 今（标题可点 = 精准跳年月）
        Row(
            Modifier.fillMaxWidth().padding(start = PennedSpacing.md, end = PennedSpacing.sm, top = PennedSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onShiftMonth(-1) },
                enabled = canGoOlder,
                modifier = Modifier.size(PennedDimens.iconButton),
            ) {
                Icon(
                    PennedIcons.Back,
                    contentDescription = "上个月",
                    tint = if (canGoOlder) MaterialTheme.colorScheme.onSurface else iconMutedColor(),
                    modifier = Modifier.size(PennedDimens.iconInline),
                )
            }
            Row(
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = "选择年月", onClick = { picking = true })
                    .heightIn(min = PennedDimens.touchTarget)
                    .padding(horizontal = PennedSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(CalendarMath.monthTitle(month), style = MaterialTheme.typography.titleLarge)
                // 可点暗示：尾部一个小箭头（点它同"点标题"，都开选年月弹窗）
                Icon(
                    PennedIcons.ChevronDown,
                    contentDescription = null,
                    tint = iconMutedColor(),
                    modifier = Modifier.padding(start = PennedSpacing.xs).size(PennedDimens.iconSmall),
                )
            }
            IconButton(
                onClick = { onShiftMonth(1) },
                enabled = canGoNewer,
                modifier = Modifier.size(PennedDimens.iconButton),
            ) {
                Icon(
                    PennedIcons.ChevronRight,
                    contentDescription = "下个月",
                    tint = if (canGoNewer) MaterialTheme.colorScheme.onSurface else iconMutedColor(),
                    modifier = Modifier.size(PennedDimens.iconInline),
                )
            }
            Spacer(Modifier.weight(1f))
            // 手势之外的可点入口（无障碍：收起/展开不能只靠上滑）
            IconButton(onClick = { collapsed = !collapsed }, modifier = Modifier.size(PennedDimens.iconButton)) {
                Icon(
                    PennedIcons.ChevronDown,
                    contentDescription = if (collapsed) "展开日历" else "收起日历",
                    tint = iconMutedColor(),
                    modifier = Modifier
                        .size(PennedDimens.iconInline)
                        .rotate(if (collapsed) 0f else 180f),
                )
            }
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

        // 星期表头（周一开头；两态都在——一行数字看不出周几）
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

        // 网格 / 收起态的周条：高度在"该月的行数"与"一行"之间过渡（收起后腾出的高度自动给列表）
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.sm)) {
            val rowHeight = CalendarStyle.rowHeight(maxWidth / 7)
            val rows = if (collapsed) 1 else CalendarMath.rowCount(month)
            val gridHeight by animateDpAsState(
                targetValue = rowHeight * rows,
                animationSpec = tween(if (animations) PennedMotion.base else 0, easing = PennedMotion.ease),
                label = "calendar-grid-height",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(gridHeight)
                    .clipToBounds()
                    .then(gridDrag),
            ) {
                if (collapsed) {
                    // 收起态：选中日所在的那一周（周一开头），日子沿用同一枚格子
                    val weekHeat = remember(heatDays, month) { heatFor(heatDays, month) }
                    Row(
                        Modifier.fillMaxWidth().testTag(CALENDAR_WEEK_STRIP_TAG),
                        verticalAlignment = Alignment.Top,
                    ) {
                        CalendarMath.weekDays(state.selectedDay).forEach { epochDay ->
                            if (!isReachable(epochDay, state.months)) {
                                Spacer(Modifier.weight(1f).cellFrame())
                            } else {
                                DayCell(
                                    epochDay = epochDay,
                                    inMonth = CalendarMath.inMonth(epochDay, month),
                                    isToday = epochDay == todayEpochDay,
                                    isSelected = epochDay == state.selectedDay,
                                    heat = weekHeat[epochDay],
                                    totals = state.dayTotals[epochDay],
                                    onClick = { onSelectDay(epochDay) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize().testTag(CALENDAR_PAGER_TAG),
                    ) { page ->
                        MonthGrid(
                            month = CalendarPages.monthOf(page, state.months) ?: month,
                            months = state.months,
                            selectedDay = state.selectedDay,
                            todayEpochDay = todayEpochDay,
                            heatDays = heatDays,
                            dayTotals = state.dayTotals,
                            onSelectDay = onSelectDay,
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
                    title = "${HomeGroups.dayLabel(state.selectedDay, ZoneId.systemDefault())} 没有记录",
                    hint = "点上面别的日子看看",
                    icon = PennedIcons.Calendar,
                    modifier = Modifier.padding(top = PennedSpacing.lg),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(CALENDAR_DAY_LIST_TAG)
                    .nestedScroll(scrollConnection),
                contentPadding = PaddingValues(bottom = PennedDimens.listBottomClearance),
            ) {
                item(key = "day-header") {
                    // 日头条复用首页那枚组件（支/收小字同口径）：小计取同一天的 DayGroup
                    val day = state.dayTotals[state.selectedDay]
                    DayHeaderRow(
                        label = HomeGroups.dayLabel(state.selectedDay, ZoneId.systemDefault()),
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

    // 选年月精准跳转：与统计页同一个滚轮（`ui/components/`），年月列同口径、再按"能翻到"收窄
    if (picking) {
        YearMonthPickerDialog(
            years = CalendarPages.years(state.months),
            today = todayMonth,
            initial = month,
            monthsForYear = { year -> CalendarPages.pickerMonths(year, todayMonth, state.months) },
            onDismiss = { picking = false },
            onConfirm = { picked ->
                onSelectMonth(picked)
                picking = false
            },
        )
    }
}

/**
 * 一个月的网格（pager 的每一页各画各的月；非本月日子按同一因子变淡）。
 *
 * **条外的日子画空格**：本月那页会带出下月头几天、最老那页会带出上月尾几天，那些月不在"能翻到"的范围里——
 * 点它会翻到条外（格子与标题就错位了），所以干脆不给数字、不给热力、不给点击。
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    months: List<YearMonth>,
    selectedDay: Long,
    todayEpochDay: Long,
    heatDays: List<CalendarHeat.Day>,
    dayTotals: Map<Long, HomeGroups.DayGroup>,
    onSelectDay: (Long) -> Unit,
) {
    val heat = remember(heatDays, month) { heatFor(heatDays, month) }
    Column(Modifier.fillMaxWidth()) {
        CalendarMath.gridDays(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { epochDay ->
                    if (!isReachable(epochDay, months)) {
                        Spacer(Modifier.weight(1f).cellFrame())
                    } else {
                        DayCell(
                            epochDay = epochDay,
                            inMonth = CalendarMath.inMonth(epochDay, month),
                            isToday = epochDay == todayEpochDay,
                            isSelected = epochDay == selectedDay,
                            heat = heat[epochDay],
                            totals = dayTotals[epochDay],
                            onClick = { onSelectDay(epochDay) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 这一天所在的月在"能翻到"的范围里吗（条外画空格，见 [MonthGrid]）。 */
private fun isReachable(epochDay: Long, months: List<YearMonth>): Boolean =
    YearMonth.from(LocalDate.ofEpochDay(epochDay)) in months

/** 一格的框（细缝 + 高宽比）：网格、周条与"条外的空格"共用，行高才对得上。 */
private fun Modifier.cellFrame(): Modifier =
    padding(CalendarStyle.cellGap).aspectRatio(CalendarStyle.cellAspect)

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
            .cellFrame()
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

/**
 * 每格的热力：**一天用"它自己所在月"的尺**（跨月周里邻月的日子也有热力、也有对比度）。
 * 网格会带出上/下月各几天、周条会跨月——所以三根尺都算一份再合起来（一天只属于一个月，不会互相覆盖）。
 */
private fun heatFor(days: List<CalendarHeat.Day>, month: YearMonth): Map<Long, CalendarHeat.Cell> =
    listOf(month.minusMonths(1), month, month.plusMonths(1)).fold(emptyMap()) { acc, m ->
        acc + CalendarHeat.monthHeat(days, m)
    }

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
                months = CalendarPages.months(YearMonth.of(2024, 3), month),
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
