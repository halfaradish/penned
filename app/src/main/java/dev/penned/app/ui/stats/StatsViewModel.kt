package dev.penned.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.StatsPeriod
import dev.penned.app.domain.StatsPeriods
import dev.penned.app.domain.StatsView
import dev.penned.app.domain.StatsWindow
import dev.penned.app.domain.TxType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

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
        val emptyTitle: String = StatsPeriods.emptyTitle(period),
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState> = combine(rows, period, type) { rows, p, type ->
        UiState(
            period = p,
            window = StatsPeriods.window(p),
            type = type,
            totals = StatsMath.totals(rows),
            expenseCategories = StatsMath.categoryTotals(rows, TxType.EXPENSE),
            incomeCategories = StatsMath.categoryTotals(rows, TxType.INCOME),
            rows = rows,
            emptyTitle = StatsPeriods.emptyTitle(p),
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

    fun prev() {
        period.value = StatsPeriods.prev(period.value)
    }

    fun next() {
        period.value = StatsPeriods.next(period.value)
    }

    /** 自定义区间：倒着选由 [StatsPeriods.custom] 交换。 */
    fun setCustomRange(startEpochDay: Long, endEpochDay: Long) {
        period.value = StatsPeriods.custom(startEpochDay, endEpochDay)
    }
}
