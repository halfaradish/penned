package dev.penned.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.BudgetMath
import dev.penned.app.ui.home.HomeGroups
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

/**
 * 统计页第一版（specs/M1.md 第二批）：月汇总 + 分类占比 + 分类排行。
 * 聚合复用 HomeGroups（D004：从流水重算）。
 */
class StatsViewModel(private val app: PennedApplication) : ViewModel() {

    data class UiState(
        val month: YearMonth = YearMonth.now(),
        val totals: HomeGroups.MonthTotals = HomeGroups.MonthTotals(0, 0),
        val expenseCategories: List<HomeGroups.CategoryTotal> = emptyList(),
        val incomeCategories: List<HomeGroups.CategoryTotal> = emptyList(),
        val rows: List<TxWithCategory> = emptyList(),
    )

    val month = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rows = combine(app.settings.currentBookId, month) { bookId, ym -> bookId to ym }
        .flatMapLatest { (bookId, ym) ->
            if (bookId == null) {
                flowOf(emptyList())
            } else {
                val window = BudgetMath.monthWindowMinute(ym.year, ym.monthValue)
                app.database.transactionDao().observeMonthTxsWithCategory(bookId, window.first, window.last)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState> = combine(rows, month) { rows, ym ->
        UiState(
            month = ym,
            totals = HomeGroups.monthTotals(rows, ym.year, ym.monthValue),
            expenseCategories = HomeGroups.categoryTotals(rows, dev.penned.app.domain.TxType.EXPENSE),
            incomeCategories = HomeGroups.categoryTotals(rows, dev.penned.app.domain.TxType.INCOME),
            rows = rows,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun prevMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }
}
