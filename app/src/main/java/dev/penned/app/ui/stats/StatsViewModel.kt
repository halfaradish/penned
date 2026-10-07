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

/**
 * 统计页：时间维度（近七日/月/年/自定义）+ 汇总 + 分类占比/数据。
 * 聚合复用 domain/StatsMath（D004：从流水重算，不落库）。
 */
class StatsViewModel(private val app: PennedApplication) : ViewModel() {

    data class UiState(
        val period: StatsPeriod = StatsPeriod.Month(YearMonth.now()),
        val window: StatsWindow = StatsPeriods.window(period),
        val type: TxType = TxType.EXPENSE,
        val totals: StatsMath.PeriodTotals = StatsMath.PeriodTotals(0, 0),
        val expenseCategories: List<StatsMath.CategoryTotal> = emptyList(),
        val incomeCategories: List<StatsMath.CategoryTotal> = emptyList(),
        val rows: List<TxWithCategory> = emptyList(),
        /** 趋势卡的柱高（四个视图都有：按日 / 年统计按 12 个月），跟着 [type] 走。 */
        val trendBars: List<Long> = emptyList(),
        /** 趋势卡的横轴类型（按日窗口 / 年统计的 12 个月）。 */
        val trendAxis: TrendAxis = TrendAxis.Months(YearMonth.now().year),
        val emptyTitle: String = StatsPeriods.emptyTitle(period),
        /** 时间切换条：月（新→旧，含选中月），月统计视图用。 */
        val stripMonths: List<YearMonth> = emptyList(),
        /** 时间切换条 + 快速定位弹窗的年列：新→旧，年统计视图与弹窗共用。 */
        val stripYears: List<Int> = emptyList(),
    )

    /** 当前统计期（默认本月）。 */
    val period = MutableStateFlow<StatsPeriod>(StatsPeriod.Month(YearMonth.now()))

    /**
     * 占比/数据卡看的是支出还是收入。用户的选择在切期后保留
     * （该侧本期没数据时卡内给提示，不自动抢用户的选择）。
     */
    val type = MutableStateFlow(TxType.EXPENSE)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rows = combine(app.settings.currentBookId, period) { bookId, p -> bookId to p }
        .flatMapLatest { (bookId, p) ->
            if (bookId == null) {
                flowOf(emptyList())
            } else {
                val window = StatsPeriods.window(p)
                app.database.transactionDao()
                    .observeRangeTxsWithCategory(bookId, window.startMinute, window.lastMinute)
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
    val state: StateFlow<UiState> = combine(rows, period, type, earliestMonth) { rows, p, type, earliest ->
        val asOf = YearMonth.now()
        val window = StatsPeriods.window(p)
        UiState(
            period = p,
            window = window,
            type = type,
            totals = StatsMath.totals(rows),
            expenseCategories = StatsMath.categoryTotals(rows, TxType.EXPENSE),
            incomeCategories = StatsMath.categoryTotals(rows, TxType.INCOME),
            rows = rows,
            trendBars = StatsTrend.bars(rows, type, p, window),
            trendAxis = StatsTrend.axisOf(p, window),
            emptyTitle = StatsPeriods.emptyTitle(p),
            stripMonths = StatsStrip.months(earliest, asOf, include = (p as? StatsPeriod.Month)?.yearMonth),
            stripYears = StatsStrip.years(earliest, asOf, include = (p as? StatsPeriod.Year)?.year),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun selectType(value: TxType) {
        if (value == TxType.EXPENSE || value == TxType.INCOME) type.value = value
    }

    /** 切时间维度：以当前期的最后一天为锚（点当前维度不自重置）。 */
    fun selectView(view: StatsView) {
        val current = period.value
        if (current.view == view) return
        period.value = StatsPeriods.of(view, StatsPeriods.lastDay(current))
    }

    /** 时间切换条上点某个月（月统计视图）。 */
    fun selectMonth(month: YearMonth) {
        period.value = StatsPeriod.Month(month)
    }

    /** 时间切换条上点某一年（年统计视图）。 */
    fun selectYear(year: Int) {
        period.value = StatsPeriod.Year(year)
    }

    /** 自定义区间：倒着选由 [StatsPeriods.custom] 交换。 */
    fun setCustomRange(startEpochDay: Long, endEpochDay: Long) {
        period.value = StatsPeriods.custom(startEpochDay, endEpochDay)
    }
}
