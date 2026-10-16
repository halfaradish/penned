package dev.penned.app.ui.calendar

import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import dev.penned.app.ui.components.PennedCard
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
import kotlin.math.roundToInt

/** 横滑切月的测试标记：往它上面划 = 切月（手势与统计页同款）。 */
internal const val CALENDAR_PAGER_TAG = "calendar-pager"

/** 网格视口（高度随收起进度收缩；收满只剩一行高——测试按几何断言"收满了没有"）。 */
internal const val CALENDAR_GRID_TAG = "calendar-grid"

/** 当日流水的白框面板（贴网格视口下缘；测试量它的顶边跟着视口上移）。 */
internal const val CALENDAR_PANEL_TAG = "calendar-panel"

/** 当日流水列表（收起后才轮到它滚动，测试往它上面划）。 */
internal const val CALENDAR_DAY_LIST_TAG = "calendar-day-list"

/** 当天没记录时的空态区（那里也要能上滑收起/下滑展开）。 */
internal const val CALENDAR_EMPTY_TAG = "calendar-empty"

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
        onToday = viewModel::goToday,
        onSelectDay = viewModel::selectDay,
        onSelectMonth = viewModel::selectMonth,
        onOpenDetail = onOpenDetail,
    )
}

/** 无状态入口：截图测试/预览用；[initialProgress] 给"收起到一半"的中间帧截图用。 */
@Composable
fun CalendarContent(
    state: CalendarViewModel.UiState,
    onToday: () -> Unit = {},
    onSelectDay: (Long) -> Unit = {},
    onSelectMonth: (YearMonth) -> Unit = {},
    onOpenDetail: (String) -> Unit = {},
    today: LocalDate = LocalDate.now(),
    initialProgress: Float = 0f,
) {
    val month = state.month
    val todayEpochDay = today.toEpochDay()
    val todayMonth = YearMonth.from(today)
    val heatDays = remember(state.dayTotals) { state.dayTotals.toHeatDays() }

    // —— 纯页面内状态（不落库、不加设置项，与首页月卡的 toggledMonths 同档）——
    // progress = 收起进度（0 = 整月、1 = 收成一周）：拖动时**每帧 1:1 跟手**，松手后补间吸附。
    // 不是二值开关——面板与本周行都按它连续位移，"逐渐覆盖日期视图"就是它一个值的推论。
    var progress by remember { mutableFloatStateOf(initialProgress.coerceIn(0f, 1f)) }
    var settleTarget by remember { mutableStateOf<Float?>(null) }
    var picking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val animations = rememberAnimationsEnabled()
    val density = LocalDensity.current
    // 速度阈值按 dp/s 记（见 CalendarCollapse.flingAfter）：这里换成 px/s
    val flingPx = with(density) { CalendarCollapse.flingAfter.toPx() }

    // 松手/点箭头后的补间（吸附到哪由 snapTarget 定）：动画缩放 0 时 tween(0) 直接落位
    LaunchedEffect(settleTarget) {
        val target = settleTarget ?: return@LaunchedEffect
        animate(
            initialValue = progress,
            targetValue = target,
            animationSpec = tween(if (animations) PennedMotion.base else 0, easing = PennedMotion.ease),
        ) { value, _ -> progress = value }
        settleTarget = null
    }

    // 横滑切月：页序 = 新→旧（第 0 页恒是本月），layout 反过来 → "往右拉 = 看更早的月"，
    // 到头就是本月、往未来滑不动（账号里没有未来月的数据）。
    val pagerState = rememberPagerState(initialPage = state.monthPage) { state.months.size.coerceAtLeast(1) }
    // 外部改了月份（「今」/跳转/点邻月的日子）→ 翻到那一页；滑动中不动，免得跟手指抢
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 一格的宽度 = 屏宽去掉网格两侧边距再分 7 列；行高与"收起行程"都由它推出来（公式只写在一处）
        val rowHeight = CalendarStyle.rowHeight((maxWidth - PennedSpacing.sm * 2) / 7)
        val expandedHeight by animateDpAsState(
            targetValue = rowHeight * CalendarMath.rowCount(month),
            animationSpec = tween(if (animations) PennedMotion.base else 0, easing = PennedMotion.ease),
            label = "calendar-expanded-height",
        )
        // 收起行程 = 整月高 − 一行高：手指划多少、视口就缩多少（1:1）
        val spanPx = with(density) { (expandedHeight - rowHeight).toPx() }

        // 页级竖滑兜底：网格 / 星期表头 / 月份行 / 白卡里的空态都没有可滚动的东西，都从这里收
        // （手势落在当日流水列表上时由列表赢走，走下面那条 nestedScroll——两条路喂的是同一根 progress）
        val followDrag = Modifier.pointerInput(spanPx, listState) {
            detectVerticalDragGestures(
                onDragStart = { settleTarget = null },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    progress = if (dragAmount < 0f) {
                        // 上滑 = 收起：列表在不在顶都一样（"先收起、再滚动"）
                        CalendarCollapse.consumeUp(progress, -dragAmount, spanPx).progress
                    } else if (!listState.canScrollBackward) {
                        // 下滑 = 展开：**回顶才展开**（列表滚到中部时往回滚不弹开）
                        CalendarCollapse.consumeDown(progress, dragAmount, spanPx).progress
                    } else {
                        progress
                    }
                },
            )
        }

        // 松手收尾：不论手势落在哪条路径，都从这里统一吸附（速度方向明确时以速度为准）。
        // 只看不消费（不碰 change），所以不跟任何子级抢手势；只有"停在中间"才需要吸附——
        // 0/1 是稳定态（列表在滚 / 面板已收），别乱动。
        val releaseWatch = Modifier.pointerInput(flingPx) {
            val tracker = VelocityTracker()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    if (change.pressed) {
                        tracker.addPosition(change.uptimeMillis, change.position)
                    } else if (event.changes.none { it.pressed }) {
                        val velocity = tracker.calculateVelocity().y
                        tracker.resetTracking()
                        if (settleTarget == null && progress > 0f && progress < 1f) {
                            settleTarget = CalendarCollapse.snapTarget(progress, velocity, flingPx)
                        }
                    }
                }
            }
        }

        // 当日流水列表：展开途中位移全归"收起"（面板内容不滚），收满之后才轮到列表滚
        val scrollConnection = remember(listState, spanPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y == 0f) return Offset.Zero
                    settleTarget = null
                    return if (available.y < 0f) {
                        val drag = CalendarCollapse.consumeUp(progress, -available.y, spanPx)
                        progress = drag.progress
                        Offset(x = 0f, y = -drag.consumed)
                    } else if (listState.canScrollBackward) {
                        // 离顶还远：让列表先滚回顶，面板不动
                        Offset.Zero
                    } else {
                        val drag = CalendarCollapse.consumeDown(progress, available.y, spanPx)
                        progress = drag.progress
                        Offset(x = 0f, y = drag.consumed)
                    }
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    // 这一帧列表刚滚到顶、位移还剩着 → 接着展开（"回顶才展开"的另一半）
                    if (available.y <= 0f || listState.canScrollBackward) return Offset.Zero
                    settleTarget = null
                    val drag = CalendarCollapse.consumeDown(progress, available.y, spanPx)
                    progress = drag.progress
                    return Offset(x = 0f, y = drag.consumed)
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(followDrag)
                .then(releaseWatch),
        ) {
            // 月份行：[年月 ⌄ / 月小字总结] …… [收起/展开] 今（标题可点 = 精准跳年月；翻月靠横滑）
            Row(
                Modifier.fillMaxWidth().padding(start = PennedSpacing.md, end = PennedSpacing.sm, top = PennedSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左列：年月 + 紧贴其下的月小字总结（两者同一枚 xs 内边距 → 文字左边缘对齐，
                // 也正好落在下方白卡的左边缘上：12dp 行边距 + 4dp = 16dp = PennedSpacing.lg）
                Column(Modifier.weight(1f)) {
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
                    // 月小字总结：收绿 / 支红 / 余灰（空月照渲染 0.00 三项）；只读
                    MonthSummaryRow(state.monthTotals, Modifier.padding(horizontal = PennedSpacing.xs))
                }
                // 手势之外的可点入口（无障碍：收起/展开不能只靠上滑）；点击走同一根 progress 的补间
                val settledCollapsed = progress >= CalendarCollapse.snapHalf
                IconButton(
                    onClick = { settleTarget = if (settledCollapsed) 0f else 1f },
                    modifier = Modifier.size(PennedDimens.iconButton),
                ) {
                    Icon(
                        PennedIcons.ChevronDown,
                        contentDescription = if (settledCollapsed) "展开日历" else "收起日历",
                        tint = iconMutedColor(),
                        modifier = Modifier
                            .size(PennedDimens.iconInline)
                            .rotate(if (settledCollapsed) 0f else 180f),
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

            // 网格视口：高度随 p 从"整月"收到"一行"（内容在视口里上移 + 裁剪，见 MonthGrid）；
            // pager 常驻在树上——收起态照样横滑切月（与展开态同一套语义），不需要第二套"周条"
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.sm)
                    .height(CalendarCollapse.viewportHeight(expandedHeight, rowHeight, progress))
                    .clipToBounds()
                    .testTag(CALENDAR_GRID_TAG),
            ) {
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
                        progress = progress,
                        rowHeight = rowHeight,
                        onSelectDay = onSelectDay,
                    )
                }
            }

            // 当日流水面板：与首页卡片同款（surface 白底 / 16dp 圆角 / 左右 lg 边距 / 零阴影），
            // 包住日头条 + 流水行 + 空态；贴住网格视口下缘、占满余下高度（收起时随着视口收缩自然"顶"上来）
            val items = state.selectedItems
            PennedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg)
                    .weight(1f)
                    .testTag(CALENDAR_PANEL_TAG),
                contentPadding = PaddingValues(0.dp),
            ) {
                if (items.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().weight(1f).testTag(CALENDAR_EMPTY_TAG),
                        contentAlignment = Alignment.TopCenter,
                    ) {
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
 * 一个月的网格（pager 的每一页各画各的月）：**热力只按这一页自己那个月的尺算**
 * （非本月日子不参与热力、一律灰字，见 [DayCell]）。
 *
 * **收起动画全部由 [progress] 一个值推导**（几何公式在 `CalendarCollapse`）：
 * - 内容整体上移 `p × 本周行序号 × 行高`——本周（含"滑过去之后会选中的那天"那一周）p=1 时贴住视口顶
 * - 非本周行随 p 渐隐到 0（本周行恒 1），视口的高度收缩在调用方（`CalendarCollapse.viewportHeight`）
 * - 内容按**无界高度**量（`wrapContentHeight(unbounded = true)`）：视口收成一行时不许把整月压扁，
 *   多出来的行由视口的 `clipToBounds` 裁掉
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
    progress: Float,
    rowHeight: Dp,
    onSelectDay: (Long) -> Unit,
) {
    val heat = remember(heatDays, month) { CalendarHeat.monthHeat(heatDays, month) }
    // 本周 = 含"这一页的选中日"的那一行；邻页按 followDay 推（滑过去之后选中日会落在哪天）——
    // 这样滑动过渡里两页的本周行在同一高度，不会跳
    val weekIndex = CalendarMath.weekIndexOf(CalendarMath.followDay(selectedDay, month), month)
    val shiftPx = with(LocalDensity.current) {
        CalendarCollapse.weekShift(rowHeight, weekIndex, progress).roundToPx()
    }
    val otherRowAlpha = CalendarCollapse.otherRowAlpha(progress)
    Column(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .offset { IntOffset(x = 0, y = -shiftPx) },
    ) {
        CalendarMath.gridDays(month).chunked(7).forEachIndexed { index, week ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (index == weekIndex) 1f else otherRowAlpha),
            ) {
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
 * 月小字总结：`收 3,000.00   支 6.00   余 2,994.00`（收绿 / 支红 / 余灰），挂在年月正下方。
 * 可用宽度 = 月份行左列（右边要让出收起箭头 + 「今」）；整行量不下时三项**一起去掉千分位**
 * （退化规则见 [CalendarSummary]），再放不下交给省略号。
 */
@Composable
private fun MonthSummaryRow(totals: HomeGroups.MonthTotals, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelMedium
    val gap = PennedSpacing.lg
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(vertical = PennedSpacing.xs),
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
 * **非本月**（网格带出的上/下月日子、跨月周里邻月的日子）**不参与热力、也一律灰字**（D093）：
 * 不画热力底（无色）、日期与支/收小字都走 `onSurfaceVariant`——灰本身就是去强调，
 * 不再用整格 alpha 变淡（真数在选中那天的日头条里）。选中日恒在当月，所以"灰的选中格"不会出现。
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
    val outInk = MaterialTheme.colorScheme.onSurfaceVariant
    val showToday = inMonth && isToday
    Column(
        modifier
            .cellFrame()
            .clip(MaterialTheme.shapes.small)
            .background(if (inMonth) heat?.let { heatFillColor(it.expense, it.level) } ?: Color.Transparent else Color.Transparent)
            .then(
                if (inMonth && isSelected) {
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
                    if (showToday) {
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
                    fontWeight = if (showToday || (inMonth && isSelected)) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = when {
                    showToday -> MaterialTheme.colorScheme.onPrimary
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> outInk
                },
            )
        }
        if (totals != null) {
            // 两行小字：支出红 / 收入青绿（**不带"支/收"前缀**——格子只有 57dp 宽，带前缀会被截成"收 300…"；
            // 颜色就是全 App 的收支语言，选中那天下面还有日头条的「支 X 收 Y」把话说全）。
            // 颜色走**热力成对的字色**：底越深字越深/越亮（ContrastTest 逐对核过），不是把底色调浅；
            // 非本月没有热力底，两行一律灰字。
            if (totals.expenseCents > 0L) {
                Text(
                    Money.format(totals.expenseCents, grouped = false),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (inMonth) heatInkColor(expense = true) else outInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (totals.incomeCents > 0L) {
                Text(
                    Money.format(totals.incomeCents, grouped = false),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (inMonth) heatInkColor(expense = false) else outInk,
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
