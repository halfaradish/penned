package dev.penned.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.StatsPeriod
import dev.penned.app.domain.StatsPeriods
import dev.penned.app.domain.StatsStrip
import dev.penned.app.domain.StatsTrend
import dev.penned.app.domain.StatsView
import dev.penned.app.domain.StatsWindow
import dev.penned.app.domain.TrendAxis
import dev.penned.app.domain.TxType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 一个时间视图的全部展示数据（四个视图各一份，页面滑动时随页取用）。
 */
data class StatsPage(
    val period: StatsPeriod = StatsPeriod.Month(YearMonth.now()),
    val window: StatsWindow = StatsPeriods.window(period),
    val totals: StatsMath.PeriodTotals = StatsMath.PeriodTotals(0, 0),
    val expenseCategories: List<StatsMath.CategoryTotal> = emptyList(),
    val incomeCategories: List<StatsMath.CategoryTotal> = emptyList(),
    /** 趋势卡的柱高（按日 / 年统计按 12 个月），跟着当前支出/收入选择走。 */
    val trendBars: List<Long> = emptyList(),
    /** 趋势卡的横轴类型（按日窗口 / 年统计的 12 个月）。 */
    val trendAxis: TrendAxis = TrendAxis.Months(YearMonth.now().year),
)

/**
 * 统计页：时间维度（近七日/月/年/自定义）+ 汇总 + 分类占比/数据。
 * 聚合复用 domain/StatsMath（D004：从流水重算，不落库）。
 *
 * **四个视图各记各的位置**（会话内）：切视图/左右滑动/切 Tab 回来都停在原处，
 * 不再"从年统计切回月统计就跳到 12 月"。只有**首次**进入某个视图才锚一次（见 [StatsPeriods.seed]）。
 */
class StatsViewModel(private val app: PennedApplication) : ViewModel() {

    data class UiState(
        /** 当前所在的视图（页）。 */
        val view: StatsView = StatsView.MONTH,
        /** 四个视图各自的数据（页面向右滑时邻页要立刻有内容，所以一次性都算好）。 */
        val pages: Map<StatsView, StatsPage> = emptyMap(),
        val type: TxType = TxType.EXPENSE,
        /** 时间切换条：月（新→旧，含选中月），月统计视图用。 */
        val stripMonths: List<YearMonth> = emptyList(),
        /** 时间切换条 + 快速定位弹窗的年列：新→旧，年统计视图与弹窗共用。 */
        val stripYears: List<Int> = emptyList(),
    ) {
        val currentPage: StatsPage get() = pages[view] ?: StatsPage()
    }

    /** 四个视图各自记住的位置（默认：本月 / 今年 / 本月 1 日…今天 / 滚动 7 天）。 */
    val periods = MutableStateFlow(StatsPeriods.initial())

    /** 已经"锚过"的视图：首次进入才锚，之后一律回到它自己记的位置。 */
    private val anchored = MutableStateFlow(setOf(StatsView.MONTH))

    /** 当前所在的视图。 */
    val view = MutableStateFlow(StatsView.MONTH)

    /**
     * 占比/数据卡看的是支出还是收入。用户的选择在切期后保留
     * （该侧本期没数据时卡内给提示，不自动抢用户的选择）。
     */
    val type = MutableStateFlow(TxType.EXPENSE)

    /**
     * 四个视图窗口的流水各拉一份（DAH 侧只取窗口，聚合仍在 Kotlin 侧，D004）。
     * 邻页在滑动过程中就要有内容，所以不能只查当前页。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pageRows: Flow<Map<StatsView, List<TxWithCategory>>> =
        combine(app.settings.currentBookId, periods) { bookId, ps -> bookId to ps }
            .flatMapLatest { (bookId, ps) ->
                if (bookId == null) {
                    flowOf(StatsView.entries.associateWith { emptyList<TxWithCategory>() })
                } else {
                    val dao = app.database.transactionDao()
                    val flows = StatsView.entries.map { each ->
                        val window = StatsPeriods.window(ps.getValue(each))
                        dao.observeRangeTxsWithCategory(bookId, window.startMinute, window.lastMinute)
                    }
                    combine(flows) { lists -> StatsView.entries.zip(lists).toMap() }
                }
            }

    /**
     * 本账本最早有流水的月份：时间切换条的跨度起点。
     * 从 epoch 分钟按本地墙钟换算（与 StatsPeriods 的窗口口径一致）。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val earliestMonth: Flow<YearMonth?> = app.settings.currentBookId.flatMapLatest { bookId ->
        if (bookId == null) {
            flowOf(null)
        } else {
            app.database.transactionDao().observeEarliestMinute(bookId).map { minute ->
                minute?.let {
                    YearMonth.from(
                        Instant.ofEpochSecond(it * 60).atZone(ZoneId.systemDefault()).toLocalDate(),
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState> = combine(
        pageRows,
        periods,
        view,
        type,
        earliestMonth,
    ) { rowsByView, ps, current, type, earliest ->
        val asOf = YearMonth.now()
        UiState(
            view = current,
            pages = StatsView.entries.associateWith { each ->
                val period = ps.getValue(each)
                val window = StatsPeriods.window(period)
                val rows = rowsByView[each].orEmpty()
                StatsPage(
                    period = period,
                    window = window,
                    totals = StatsMath.totals(rows),
                    expenseCategories = StatsMath.categoryTotals(rows, TxType.EXPENSE),
                    incomeCategories = StatsMath.categoryTotals(rows, TxType.INCOME),
                    trendBars = StatsTrend.bars(rows, type, period, window),
                    trendAxis = StatsTrend.axisOf(period, window),
                )
            },
            type = type,
            // 切换条的跨度按"月视图/年视图各自的位置"补上选中项，不跟着当前页走
            stripMonths = StatsStrip.months(
                earliest,
                asOf,
                include = (ps[StatsView.MONTH] as? StatsPeriod.Month)?.yearMonth,
            ),
            stripYears = StatsStrip.years(
                earliest,
                asOf,
                include = (ps[StatsView.YEAR] as? StatsPeriod.Year)?.year,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun selectType(value: TxType) {
        if (value == TxType.EXPENSE || value == TxType.INCOME) type.value = value
    }

    /**
     * 切时间视图：**首次**进入锚一次（当前期的最后一天 / 当前窗口，见 [StatsPeriods.seed]），
     * 之后回到该视图自己记的位置——不会因为"从年统计切过来"就跳到 12 月。
     */
    fun selectView(target: StatsView) {
        val current = view.value
        if (current == target) return
        if (target !in anchored.value) {
            val seeded = StatsPeriods.seed(target, periods.value.getValue(current))
            periods.update { it + (target to seeded) }
            anchored.update { it + target }
        }
        view.value = target
    }

    /** 时间切换条上点某个月（月统计视图）。 */
    fun selectMonth(month: YearMonth) {
        setPeriod(StatsView.MONTH) { StatsPeriod.Month(month) }
    }

    /** 时间切换条上点某一年（年统计视图）。 */
    fun selectYear(year: Int) {
        setPeriod(StatsView.YEAR) { StatsPeriod.Year(year) }
    }

    /** 自定义区间：倒着选由 [StatsPeriods.custom] 交换。 */
    fun setCustomRange(startEpochDay: Long, endEpochDay: Long) {
        setPeriod(StatsView.CUSTOM) { StatsPeriods.custom(startEpochDay, endEpochDay) }
    }

    private fun setPeriod(target: StatsView, next: (StatsPeriod) -> StatsPeriod) {
        periods.update { it + (target to next(it.getValue(target))) }
    }
}
