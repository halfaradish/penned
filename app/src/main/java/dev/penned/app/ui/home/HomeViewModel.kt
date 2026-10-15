package dev.penned.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.domain.HomeDisplaySettings
import dev.penned.app.domain.HomeStatItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class HomeViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    val books: StateFlow<List<BookEntity>> = db.bookDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentBook: StateFlow<BookEntity?> = combine(books, app.settings.currentBookId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 首页流水。初值与"账本还没读到"都是 null——用来区分"确实没有记账"，
     * 否则冷启动首帧会先闪一下"还没有记账"空态（看板待办）。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<TxWithCategory>?> = currentBook
        .flatMapLatest { book ->
            if (book == null) {
                flowOf(null)
            } else {
                db.transactionDao().observeRecentWithCategory(book.id, limit = 500).map { it }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 首页「剩余预算」：总预算（分类预算不进首页汇总）。 */
    data class BudgetSummary(val totalCents: Long, val spentCents: Long) {
        val remainingCents: Long get() = totalCents - spentCents
    }

    /**
     * 月卡头显示项（M3-13）：DataStore 存逗号串，**没存过 = 全开**。
     * 渲染一律走 `HomeDisplaySettings.isVisible`（兜底在里面：全不勾至少留「支出」）。
     */
    val display: StateFlow<HomeDisplaySettings> = app.settings.homeStatItems
        .map { dev.penned.app.domain.HomeDisplay.decode(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeDisplaySettings())

    /** 勾选/取消一项（写 DataStore；全关时的兜底在渲染层，不在这里硬塞回来）。 */
    fun setStatItem(item: HomeStatItem, on: Boolean) {
        viewModelScope.launch {
            val next = display.value.toggle(item, on)
            app.settings.setHomeStatItems(dev.penned.app.domain.HomeDisplay.encode(next.items))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val budgetSummary: StateFlow<BudgetSummary?> = currentBook
        .flatMapLatest { book ->
            if (book == null) {
                flowOf(null)
            } else {
                val today = java.time.LocalDate.now()
                val window = dev.penned.app.domain.BudgetMath.monthWindowMinute(today.year, today.monthValue)
                combine(
                    db.budgetDao().observeByBook(book.id),
                    db.transactionDao().observeMonthSpendByCategory(book.id, window.first, window.last),
                ) { budgets, spend ->
                    val totalBudget = budgets.firstOrNull { it.categoryId == null }?.amountCents ?: return@combine null
                    BudgetSummary(totalBudget, spend.sumOf { it.cents })
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // 批量删除模式（探索报告 §一：长按账单可批量编辑）
    val selectionMode = MutableStateFlow(false)
    val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    /** 待撤销的软删 id（左滑删除/批量删除后进 Snackbar「撤销」，撤销条消失即失效）。 */
    private val _deletedIds = MutableStateFlow<List<String>>(emptyList())
    val deletedIds: StateFlow<List<String>> = _deletedIds

    fun switchBook(id: String) {
        viewModelScope.launch { app.settings.setCurrentBook(id) }
    }

    fun addBook(name: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val book = BookEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifEmpty { "新账本" },
                sortOrder = books.value.size,
                createdAt = now,
                updatedAt = now,
                deviceId = app.deviceId,
            )
            db.bookDao().upsert(book)
            app.settings.setCurrentBook(book.id)
            onDone()
        }
    }

    fun renameBook(id: String, name: String) {
        viewModelScope.launch {
            val original = db.bookDao().get(id) ?: return@launch
            db.bookDao().upsert(original.copy(name = name.trim().ifEmpty { original.name }, updatedAt = System.currentTimeMillis()))
        }
    }

    fun enterSelection(id: String) {
        selectionMode.value = true
        selectedIds.value = setOf(id)
    }

    fun toggleSelect(id: String) {
        selectedIds.value = selectedIds.value.let { if (id in it) it - id else it + id }
    }

    fun exitSelection() {
        selectionMode.value = false
        selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = selectedIds.value.toList()
        viewModelScope.launch {
            db.transactionDao().softDeleteAll(ids, System.currentTimeMillis())
            _deletedIds.value = _deletedIds.value + ids
            exitSelection()
        }
    }

    /** 行左滑删除（单条）。 */
    fun deleteRow(id: String) {
        viewModelScope.launch {
            db.transactionDao().softDeleteAll(listOf(id), System.currentTimeMillis())
            _deletedIds.value = _deletedIds.value + id
        }
    }

    /** 撤销删除：把累计待撤销的 id 一次恢复。 */
    fun undoDelete() {
        val ids = _deletedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            db.transactionDao().restoreAll(ids, System.currentTimeMillis())
            _deletedIds.value = emptyList()
        }
    }

    /** 撤销条消失后调用：过期的撤销不能再生效。 */
    fun clearUndo() {
        _deletedIds.value = emptyList()
    }
}
