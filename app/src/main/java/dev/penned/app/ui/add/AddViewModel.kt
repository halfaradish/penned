package dev.penned.app.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.Money
import dev.penned.app.domain.MoneyInput
import dev.penned.app.domain.TxType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class AddViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    var type = MutableStateFlow(CategoryType.EXPENSE)
        private set
    var amountText = MutableStateFlow("")
        private set
    var selectedCategoryId = MutableStateFlow<String?>(null)
        private set
    var note = MutableStateFlow("")
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<CategoryEntity>> = db.bookDao().observeAll()
        .flatMapLatest { books ->
            if (books.isEmpty()) flowOf(emptyList()) else db.categoryDao().observeByBook(books.first().id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val canSave: StateFlow<Boolean> = MutableStateFlow(false).let { _ ->
        kotlinx.coroutines.flow.combine(amountText, selectedCategoryId) { amount, category ->
            (Money.parseToCents(amount) ?: 0L) > 0 && category != null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    }

    fun selectType(t: CategoryType) {
        type.value = t
        selectedCategoryId.value = null
    }

    fun pressKey(key: Char) {
        amountText.value = MoneyInput.append(amountText.value, key)
    }

    fun pressKey(key: String) {
        if (key == KEY_BACKSPACE) {
            amountText.value = MoneyInput.backspace(amountText.value)
        } else if (key == KEY_CLEAR) {
            amountText.value = MoneyInput.clear()
        } else {
            key.forEach { pressKey(it) }
        }
    }

    fun selectCategory(id: String) {
        selectedCategoryId.value = if (selectedCategoryId.value == id) null else id
    }

    fun setNote(text: String) {
        note.value = text
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val cents = Money.parseToCents(amountText.value) ?: return@launch
            if (cents <= 0) return@launch
            val categoryId = selectedCategoryId.value ?: return@launch
            val book = db.bookDao().observeAll().first().firstOrNull() ?: return@launch
            val category = categories.value.firstOrNull { it.id == categoryId } ?: return@launch
            val now = System.currentTimeMillis()
            db.transactionDao().upsert(
                TransactionEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = book.id,
                    type = if (category.type == CategoryType.INCOME) TxType.INCOME else TxType.EXPENSE,
                    amountCents = cents,
                    categoryId = category.id,
                    dateEpochMinute = now / 60_000,
                    note = note.value.trim().ifEmpty { null },
                    creatorDeviceId = app.deviceId,
                    createdAt = now,
                    updatedAt = now,
                    deviceId = app.deviceId,
                )
            )
            onDone()
        }
    }

    companion object {
        const val KEY_BACKSPACE = "⌫"
        const val KEY_CLEAR = "C"
        const val KEY_SAVE = "保存"
    }
}
