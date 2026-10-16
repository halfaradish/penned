package dev.penned.app.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.domain.AddDate
import dev.penned.app.domain.Money
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.StatsPeriod
import dev.penned.app.domain.AssetTrendMath
import dev.penned.app.domain.StatsLayout
import dev.penned.app.domain.StatsModule
import dev.penned.app.domain.StatsPeriods
import dev.penned.app.domain.StatsTrend
import dev.penned.app.domain.StatsView
import dev.penned.app.domain.StatsWindow
import dev.penned.app.domain.TrendAxis
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.components.PennedSegmentedTabs
import dev.penned.app.ui.components.YearMonthPickerDialog
import dev.penned.app.ui.components.PennedStatCells
import dev.penned.app.ui.components.StatCell
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedPigment
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.PennedTheme
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.iconMutedColor
import dev.penned.app.ui.theme.primaryInkColor
import dev.penned.app.ui.theme.incomeColor
import java.time.LocalDate
import java.time.YearMonth

/**
 * 统计页（specs/M1.md 第二批起；AI 轮加时间维度与切换式）：
 * 时间维度分段 + 期选择 + 汇总卡 + 「占比 + 数据」两张卡（卡头支出/收入切换）。
 * 图表配色走分类颜料（D024）；首页头像不再吃颜料（AS 轮起统一淡灰绿）。
 */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    modifier: Modifier = Modifier,
    onOpenCategory: (CategoryTarget, StatsWindow) -> Unit = { _, _ -> },
    /** 右上「⋯」→ 统计设置（M3-14）。 */
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsContent(
        view = state.view,
        pages = state.pages,
        type = state.type,
        layout = state.layout,
        onOpenStatsSettings = onOpenSettings,
        stripMonths = state.stripMonths,
        stripYears = state.stripYears,
        onSelectView = viewModel::selectView,
        onSelectType = viewModel::selectType,
        onSelectMonth = viewModel::selectMonth,
        onSelectYear = viewModel::selectYear,
        onPickRange = viewModel::setCustomRange,
        onOpenCategory = onOpenCategory,
        modifier = modifier,
    )
}

/** 四页横向滑动的测试标记（左右滑动切视图的用例要往它上面划）。 */
internal const val STATS_PAGER_TAG = "stats-pager"

/** 占比/数据卡头的切换项：只在这两个域里切（转账不属于收支统计）。 */
private val STATS_TYPES = listOf(TxType.EXPENSE, TxType.INCOME)

@Composable
fun StatsContent(
    view: StatsView,
    pages: Map<StatsView, StatsPage>,
    type: TxType = TxType.EXPENSE,
    stripMonths: List<YearMonth> = emptyList(),
    stripYears: List<Int> = emptyList(),
    onSelectView: (StatsView) -> Unit = {},
    onSelectType: (TxType) -> Unit = {},
    onSelectMonth: (YearMonth) -> Unit = {},
    onSelectYear: (Int) -> Unit = {},
    onPickRange: (Long, Long) -> Unit = { _, _ -> },
    onOpenCategory: (CategoryTarget, StatsWindow) -> Unit = { _, _ -> },
    /** 统计模块的顺序与开关（M3-14）；默认 = 原有四块全开、原有顺序（观感不变）。 */
    layout: StatsLayout = StatsLayout(),
    /** 右上「⋯」：开统计设置（M3-14）。 */
    onOpenStatsSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    // 自定义区间的日期选择：记住点的是「开始」还是「结束」；快速定位弹窗单独一个状态
    var picking by remember { mutableStateOf<RangeEnd?>(null) }
    var locating by remember { mutableStateOf(false) }
    val todayMonth = YearMonth.from(today)
    val current = pages[view] ?: StatsPage()

    // 时间维度分段固定在顶部；下面用 HorizontalPager 装四页，左右滑动切视图
    val pagerState = rememberPagerState(initialPage = view.ordinal) { StatsView.entries.size }
    // 点分段（或外面改了 view）→ 翻到那一页；滑动中不动，免得跟手指抢
    LaunchedEffect(view) {
        if (pagerState.currentPage != view.ordinal && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(view.ordinal)
        }
    }
    // 滑完停稳 → 告诉外面切了视图（外面会把该视图自己记的位置发回来）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            onSelectView(StatsView.entries[page])
        }
    }

    // 不透明页面：下钻推入时父子两页同屏，透明根会让下层页透出来（AP-2）
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 时间维度分段固定当首行（2026-10-07：去掉冗余的「统计」页头大标题——底栏 Tab 已写着"统计"）；
        // 右端「⋯」进统计设置（M3-14：模块开关 + 顺序）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = PennedSpacing.md, start = PennedSpacing.lg, end = PennedSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PennedSegmentedTabs(
                options = StatsView.entries.toList(),
                selected = view,
                onSelect = onSelectView,
                label = { it.label },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenStatsSettings, modifier = Modifier.size(PennedDimens.iconButton)) {
                Icon(
                    PennedIcons.MoreVert,
                    contentDescription = "统计设置",
                    tint = iconMutedColor(),
                    modifier = Modifier.size(PennedDimens.iconInline),
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag(STATS_PAGER_TAG),
        ) { page ->
            val pageView = StatsView.entries[page]
            val data = pages[pageView] ?: StatsPage()
            // 每页自己一段纵向滚动（滚动位置按页各自记）
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = PennedSpacing.xxl),
            ) {
                StatsPageBody(
                    view = pageView,
                    page = data,
                    type = type,
                    today = today,
                    layout = layout,
                    stripMonths = stripMonths,
                    stripYears = stripYears,
                    onSelectType = onSelectType,
                    onSelectMonth = onSelectMonth,
                    onSelectYear = onSelectYear,
                    onPickYearMonth = { locating = true },
                    onPickStart = { picking = RangeEnd.START },
                    onPickEnd = { picking = RangeEnd.END },
                    onOpenCategory = onOpenCategory,
                )
            }
        }
    }

    val target = picking
    if (target != null) {
        val initialDay = when (target) {
            RangeEnd.START -> current.period.startDayOrNull() ?: current.window.startEpochDay
            RangeEnd.END -> current.period.endDayOrNull() ?: current.window.endEpochDay
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = AddDate.dayToPickerMillis(initialDay),
        )
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val day = AddDate.pickerMillisToDay(millis)
                        when (target) {
                            RangeEnd.START -> onPickRange(day, current.window.endEpochDay)
                            RangeEnd.END -> onPickRange(current.window.startEpochDay, day)
                        }
                    }
                    picking = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }) { Text("取消") }
            },
        ) {
            DatePicker(
                state = pickerState,
                title = {
                    Text(
                        if (target == RangeEnd.START) "选择开始日期" else "选择结束日期",
                        modifier = Modifier.padding(PennedSpacing.lg),
                    )
                },
            )
        }
    }

    // 快速定位：点切换条左边的年份（月统计视图）
    if (locating) {
        YearMonthPickerDialog(
            years = stripYears,
            today = todayMonth,
            initial = (current.period as? StatsPeriod.Month)?.yearMonth ?: todayMonth,
            onDismiss = { locating = false },
            onConfirm = { month ->
                onSelectMonth(month)
                locating = false
            },
        )
    }
}

/**
 * 单页内容（四页共用）：期选择行 + 汇总卡 + 趋势/占比/数据三卡。
 * 数据全部来自该页自己的 [StatsPage]（每页各记各的位置），只有支出/收入的选择是全页共享的。
 */
@Composable
private fun StatsPageBody(
    view: StatsView,
    page: StatsPage,
    type: TxType,
    today: LocalDate,
    layout: StatsLayout,
    stripMonths: List<YearMonth>,
    stripYears: List<Int>,
    onSelectType: (TxType) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onSelectYear: (Int) -> Unit,
    onPickYearMonth: () -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onOpenCategory: (CategoryTarget, StatsWindow) -> Unit,
) {
    PeriodSelector(
        period = page.period,
        window = page.window,
        today = today,
        stripMonths = stripMonths,
        stripYears = stripYears,
        onSelectMonth = onSelectMonth,
        onSelectYear = onSelectYear,
        onPickYearMonth = onPickYearMonth,
        onPickStart = onPickStart,
        onPickEnd = onPickEnd,
    )

    // 模块全关：整页空态（规格 M3-14 的"含全关的空态"）——统计页可以一块都不放
    if (layout.visibleModules.isEmpty()) {
        EmptyState(
            title = "统计页没有模块",
            hint = "点右上「⋯」去统计设置里打开几块",
            icon = PennedIcons.Pie,
            modifier = Modifier.padding(top = PennedSpacing.xxl),
        )
        return
    }

    // 汇总卡（模块顺序里排到哪就画到哪；不再固定在最上面）
    val summary: @Composable () -> Unit = {
        PennedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg)) {
            PennedStatCells(
                listOf(
                    StatCell("支出", Money.format(page.totals.expenseCents), expenseColor()),
                    StatCell("收入", Money.format(page.totals.incomeCents), incomeColor()),
                    StatCell("结余", Money.format(page.totals.incomeCents - page.totals.expenseCents)),
                ),
            )
        }
    }

    if (page.expenseCategories.isEmpty() && page.incomeCategories.isEmpty()) {
        // 有数据才有得算：本期一条都没有时，除了汇总（恒为 0）与资产走势（期初还在），其余模块给一句空态
        layout.visibleModules.forEach { module ->
            when (module) {
                StatsModule.SUMMARY -> summary()
                StatsModule.ASSET_TREND -> StatsAssetTrendCard(page = page)
                else -> Unit
            }
        }
        EmptyState(
            title = StatsPeriods.emptyTitle(page.period, today = today),
            hint = "记几笔之后回来看统计",
            icon = PennedIcons.Pie,
            modifier = Modifier.padding(top = PennedSpacing.xxl),
        )
        return
    }

    // 占比 + 数据：一张卡头的「支出|收入」切换驱动两张卡（参考图口径）
    val isExpense = type == TxType.EXPENSE
    val unit = if (isExpense) "支出" else "收入"
    val unitColor = if (isExpense) expenseColor() else incomeColor()
    val shown = if (isExpense) page.expenseCategories else page.incomeCategories
    val total = if (isExpense) page.totals.expenseCents else page.totals.incomeCents
    val toggle: @Composable () -> Unit = {
        PennedSegmentedTabs(
            options = STATS_TYPES,
            selected = type,
            onSelect = onSelectType,
            label = { if (it == TxType.EXPENSE) "支出" else "收入" },
            modifier = Modifier.width(PennedDimens.segmentToggle),
        )
    }
    // 这一张图里分配好的槽位：环、图例、数据行三处共用，段段不同色。
    // 撞色时要在 P(8,k) 个排列里搜最优（AR 轮）——拿 remember 记住，别在每次重组里重搜
    val names = shown.map { it.name }
    val slots = remember(names) { PennedPigment.assignSlots(names) }
    val tagNames = page.tagTotals.map { it.name }
    val tagSlots = remember(tagNames) { PennedPigment.assignSlots(tagNames) }

    // 模块按用户设置的顺序渲染；关掉的模块**不渲染也不计算**（页面这一层就不进 when 分支）
    layout.visibleModules.forEach { module ->
        when (module) {
            StatsModule.SUMMARY -> summary()
            StatsModule.TREND -> StatsBarChartCard(
                title = StatsTrend.title(type),
                bars = page.trendBars,
                xLabels = trendAxisLabels(page.trendAxis, today),
                color = unitColor,
                seriesLabel = unit,
            )
            StatsModule.DONUT -> if (shown.isEmpty()) {
                // 该侧本期没数据：卡内给句话，切换器留在原处（一点即可看另一侧）
                EmptyUnitCard(title = "${unit}占比", unit = unit, toggle = toggle)
            } else {
                StatsDonutCard(
                    title = "${unit}占比",
                    centerLabel = "总$unit",
                    categories = shown,
                    totalCents = total,
                    totalColor = unitColor,
                    slots = slots,
                    trailing = toggle,
                )
            }
            StatsModule.DATA -> if (shown.isNotEmpty()) {
                StatsDataCard(
                    title = "${unit}数据",
                    categories = shown,
                    type = type,
                    slots = slots,
                    onOpenCategory = { name -> onOpenCategory(CategoryTarget(type, name, parent = null), page.window) },
                )
            }
            StatsModule.TAG_DONUT -> if (page.tagTotals.isEmpty()) {
                EmptyUnitCard(title = "标签占比", unit = "标签", toggle = toggle)
            } else {
                StatsDonutCard(
                    title = "标签占比",
                    centerLabel = "标签合计",
                    categories = page.tagTotals,
                    totalCents = page.tagTotals.sumOf { it.cents },
                    totalColor = unitColor,
                    slots = tagSlots,
                    trailing = toggle,
                )
            }
            StatsModule.TAG_DATA -> if (page.tagTotals.isNotEmpty()) {
                StatsDataCard(
                    title = "标签数据",
                    categories = page.tagTotals,
                    type = type,
                    slots = tagSlots,
                    // 标签没有详细页可下钻（drillable = false），点击不接任何目标
                    onOpenCategory = {},
                )
            }
            StatsModule.ASSET_TREND -> StatsAssetTrendCard(page = page)
        }
    }
}

/** 该侧本期没数据时的占位卡（标题 + 切换器 + 一句说明）。 */
@Composable
private fun EmptyUnitCard(title: String, unit: String, toggle: @Composable () -> Unit) {
    PennedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = PennedTextStyles.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            toggle()
        }
        Text(
            "本期没有${unit}记录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 资产走势卡（M3-14）：账户合计余额的期初/期末/最高/最低 + 变化。
 *
 * **不画折线**（M3-10 的取舍：自绘折线留 M6 统一打磨，避免与统计页图表口径打架）——
 * 这里先把四数摆清楚，副标题写明"账户合计"（债务的应付/应收不在账户里，不算）。
 */
@Composable
private fun StatsAssetTrendCard(page: StatsPage) {
    val summary = AssetTrendMath.summary(page.assetOpeningCents, page.assetSeries)
    val changeColor = if (summary.changeCents < 0) expenseColor() else primaryInkColor()
    PennedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm)) {
        Text(
            "资产走势",
            style = PennedTextStyles.cardTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "账户合计余额（转账对冲、手续费计入；债务不在账户里，不计入）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(PennedSpacing.md))
        Text(
            "期末",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            Money.format(summary.closingCents),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(PennedSpacing.sm))
        PennedStatCells(
            listOf(
                StatCell("期初", Money.format(summary.openingCents)),
                StatCell("变化", Money.format(summary.changeCents), changeColor),
                StatCell("最高", Money.format(summary.highestCents)),
                StatCell("最低", Money.format(summary.lowestCents)),
            ),
        )
    }
}

private enum class RangeEnd { START, END }

/**
 * 期选择行：月/年出**时间切换条**（可横滑，AK 轮替代箭头翻页）、自定义出两个日期 chip。
 * 近七日**不出行**——窗口就是"含今天的 7 天"，区间信息交给趋势卡的横轴（`9/24`…）表达。
 */
@Composable
private fun PeriodSelector(
    period: StatsPeriod,
    window: StatsWindow,
    today: LocalDate,
    stripMonths: List<YearMonth>,
    stripYears: List<Int>,
    onSelectMonth: (YearMonth) -> Unit,
    onSelectYear: (Int) -> Unit,
    onPickYearMonth: () -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
) {
    when (period) {
        is StatsPeriod.Month -> MonthStrip(
            months = stripMonths,
            selected = period.yearMonth,
            today = YearMonth.from(today),
            onSelect = onSelectMonth,
            onPickYearMonth = onPickYearMonth,
        )
        is StatsPeriod.Year -> YearStrip(
            years = stripYears,
            selected = period.year,
            onSelect = onSelectYear,
        )
        is StatsPeriod.Custom -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PennedDimens.iconButton + PennedSpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm)) {
                PennedChip(
                    label = StatsPeriods.dayLabel(period.startEpochDay, today),
                    leading = "开始",
                    onClick = onPickStart,
                )
                PennedChip(
                    label = StatsPeriods.dayLabel(period.endEpochDay, today),
                    leading = "结束",
                    onClick = onPickEnd,
                )
            }
        }
        is StatsPeriod.Last7Days -> Unit
    }
}

/** 自定义区间在期状态里的起止日（近七日/月/年没有"用户选的日期"，返回 null 走窗口端点）。 */
private fun StatsPeriod.startDayOrNull(): Long? = (this as? StatsPeriod.Custom)?.startEpochDay

private fun StatsPeriod.endDayOrNull(): Long? = (this as? StatsPeriod.Custom)?.endEpochDay

@Preview(showBackground = true, name = "统计-月统计", widthDp = 400, heightDp = 780)
@Composable
private fun StatsMonthPreview() {
    val period = StatsPeriod.Month(YearMonth.of(2026, 10))
    val window = StatsPeriods.window(period)
    val today = LocalDate.of(2026, 10, 7)
    PennedTheme {
        StatsContent(
            view = StatsView.MONTH,
            pages = mapOf(
                StatsView.MONTH to StatsPage(
                    period = period,
                    window = window,
                    totals = StatsMath.PeriodTotals(incomeCents = 350000, expenseCents = 34200),
                    expenseCategories = listOf(
                        StatsMath.CategoryTotal("食品餐饮", 15600, 110, 0.456f),
                        StatsMath.CategoryTotal("购物消费", 9500, 15, 0.278f),
                        StatsMath.CategoryTotal("出行交通", 4200, 6, 0.123f),
                        StatsMath.CategoryTotal("其他", 3000, 4, 0.088f),
                        StatsMath.CategoryTotal("未分类", 1900, 2, 0.055f, drillable = false),
                    ),
                    incomeCategories = listOf(StatsMath.CategoryTotal("工资", 350000, 1, 1f)),
                    trendBars = List(31) { 0L }.toMutableList().also { it[3] = 1600; it[19] = 9500 },
                    trendAxis = StatsTrend.axisOf(period, window),
                ),
            ),
            today = today,
        )
    }
}
