package dev.penned.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.data.db.entity.BookEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<TxWithCategory>> = currentBook
        .flatMapLatest { book ->
            if (book == null) flowOf(emptyList()) else db.transactionDao().observeRecentWithCategory(book.id, limit = 500)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 批量删除模式（探索报告 §一：长按账单可批量编辑）
    val selectionMode = MutableStateFlow(false)
    val selectedIds = MutableStateFlow<Set<String>>(emptySet())

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
        viewModelScope.launch {
            db.transactionDao().softDeleteAll(selectedIds.value.toList(), System.currentTimeMillis())
            exitSelection()
        }
    }
}
