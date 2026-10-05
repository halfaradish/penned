package dev.penned.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BudgetEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.domain.BudgetMath
import dev.penned.app.domain.CategoryType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 预算管理：总预算 + 分类预算（月度），支出一律由当月流水重算（D004）。
 */
class BudgetsViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    data class BudgetRow(
        val budget: BudgetEntity,
        val categoryName: String?,
        val state: BudgetMath.BudgetState,
    )

    data class UiState(
        val total: BudgetRow? = null,
        val categoryRows: List<BudgetRow> = emptyList(),
        /** 支出域分类树（扁平，供新增分类预算选择）。 */
        val expenseCategories: List<CategoryEntity> = emptyList(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentBook = db.bookDao().observeAll()
        .flatMapLatest { books ->
            app.settings.currentBookId.map { id -> id?.let { wanted -> books.firstOrNull { it.id == wanted } } ?: books.firstOrNull() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<UiState> = currentBook.flatMapLatest { book ->
        if (book == null) {
            flowOf(UiState())
        } else {
            val window = BudgetMath.monthWindowMinute(java.time.LocalDate.now().year, java.time.LocalDate.now().monthValue)
            combine(
                db.budgetDao().observeByBook(book.id),
                db.categoryDao().observeByBook(book.id),
                db.transactionDao().observeMonthSpendByCategory(book.id, window.first, window.last),
            ) { budgets, categories, spend ->
                val spendMap = spend.associate { it.categoryId to it.cents }
                fun subsOf(id: String): Set<String> = categories.filter { it.parentId == id }.map { it.id }.toSet()
                fun rowOf(budget: BudgetEntity): BudgetRow {
                    val subs = budget.categoryId?.let { subsOf(it) } ?: emptySet()
                    val spent = BudgetMath.spentForBudget(spendMap, budget.categoryId, subs)
                    return BudgetRow(
                        budget = budget,
                        categoryName = categories.firstOrNull { it.id == budget.categoryId }?.name,
                        state = BudgetMath.state(budget.amountCents, spent),
                    )
                }
                UiState(
                    total = budgets.firstOrNull { it.categoryId == null }?.let { rowOf(it) },
                    categoryRows = budgets.filter { it.categoryId != null }
                        .map { rowOf(it) }
                        .sortedBy { it.categoryName ?: "" },
                    expenseCategories = categories.filter { it.type == CategoryType.EXPENSE }.sortedWith(
                        compareBy({ it.parentId ?: it.id }, { it.sortOrder }),
                    ),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** 设置预算金额；同分类已有预算则复用行（保持唯一）。 */
    fun setBudget(categoryId: String?, amountCents: Long) {
        viewModelScope.launch {
            val bookId = app.settings.currentBookId.firstOrNull()
                ?: db.bookDao().observeAll().first().firstOrNull()?.id
            if (bookId == null || amountCents <= 0) return@launch
            val existing = db.budgetDao().getByCategory(bookId, categoryId)
            val now = System.currentTimeMillis()
            db.budgetDao().upsert(
                (existing?.copy(amountCents = amountCents, updatedAt = now) ?: BudgetEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    categoryId = categoryId,
                    amountCents = amountCents,
                    startEpochDay = BudgetMath.monthAnchorEpochDay(),
                    createdAt = now,
                    updatedAt = now,
                    deviceId = app.deviceId,
                ))
            )
        }
    }

    fun deleteBudget(id: String) {
        viewModelScope.launch {
            db.budgetDao().softDelete(id, System.currentTimeMillis())
        }
    }
}
