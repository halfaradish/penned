package dev.penned.app.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.AddDate
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.Money
import dev.penned.app.domain.MoneyExpression
import dev.penned.app.domain.TxType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.util.UUID

class AddViewModel(private val app: PennedApplication, private val editId: String? = null) : ViewModel() {

    private val db = app.database

    var type = MutableStateFlow(TxType.EXPENSE)
        private set
    var amountText = MutableStateFlow("")
        private set
    var selectedCategoryId = MutableStateFlow<String?>(null)
        private set
    var expandedParentId = MutableStateFlow<String?>(null)
        private set
    var accountId = MutableStateFlow<String?>(null)
        private set
    var transferFromId = MutableStateFlow<String?>(null)
        private set
    var transferToId = MutableStateFlow<String?>(null)
        private set
    var feeText = MutableStateFlow("")
        private set
    /** 记账日期（epochDay）；null = 今天。编辑时按原流水反推。 */
    var dateEpochDay = MutableStateFlow<Long?>(null)
        private set
    var note = MutableStateFlow("")
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentBook = db.bookDao().observeAll()
        .flatMapLatest { books ->
            app.settings.currentBookId.map { id -> id?.let { wanted -> books.firstOrNull { it.id == wanted } } ?: books.firstOrNull() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<CategoryEntity>> = currentBook
        .flatMapLatest { book ->
            if (book == null) flowOf(emptyList()) else db.categoryDao().observeByBook(book.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val accounts: StateFlow<List<AccountEntity>> = currentBook
        .flatMapLatest { book ->
            if (book == null) flowOf(emptyList()) else db.accountDao().observeByBook(book.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val canSave: StateFlow<Boolean> = combine(
        amountText, selectedCategoryId, type, transferFromId, transferToId,
    ) { amount, categoryId, txType, from, to ->
        val cents = effectiveCents(amount) ?: 0L
        when (txType) {
            TxType.TRANSFER -> cents > 0 && from != null && to != null && from != to
            else -> cents > 0 && categoryId != null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 表达式待求值时，完成键显示为 "=" */
    val showEquals: StateFlow<Boolean> = amountText
        .map { MoneyExpression.hasPendingOperator(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        if (editId != null) loadForEdit(editId)
    }

    private fun loadForEdit(id: String) {
        viewModelScope.launch {
            val tx = db.transactionDao().get(id) ?: return@launch
            type.value = tx.type
            amountText.value = Money.format(tx.amountCents, grouped = false)
            selectedCategoryId.value = tx.categoryId
            expandedParentId.value = tx.categoryId?.let { cid ->
                categories.value.firstOrNull { c -> c.id == cid }?.parentId ?: cid
            }
            accountId.value = tx.accountId
            transferFromId.value = tx.accountId
            transferToId.value = tx.targetAccountId
            feeText.value = if (tx.feeCents > 0) Money.format(tx.feeCents, grouped = false) else ""
            note.value = tx.note.orEmpty()
            dateEpochDay.value = AddDate.dayFromMinute(tx.dateEpochMinute)
        }
    }

    fun selectType(t: TxType) {
        type.value = t
        if (t != TxType.TRANSFER) selectedCategoryId.value = null
        expandedParentId.value = null
    }

    fun selectParent(id: String, hasSubs: Boolean) {
        selectedCategoryId.value = id
        expandedParentId.value = if (hasSubs) id else null
    }

    fun selectSub(id: String) {
        selectedCategoryId.value = id
    }

    fun pickAccount(id: String?) {
        accountId.value = id
    }

    fun pickTransferFrom(id: String?) {
        transferFromId.value = id
    }

    fun pickTransferTo(id: String?) {
        transferToId.value = id
    }

    fun swapTransfer() {
        val f = transferFromId.value
        transferFromId.value = transferToId.value
        transferToId.value = f
    }

    fun setFee(text: String) {
        feeText.value = text
    }

    fun setDate(day: Long?) {
        dateEpochDay.value = day
    }

    /** 记一笔宫格内新增分类并直接选中（M1 第二批）。parentId 为空 = 新增一级。 */
    fun addCategoryAndSelect(name: String, parentId: String?, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@launch
            val book = currentBook.value ?: return@launch
            val domain = if (type.value == TxType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
            val siblings = db.categoryDao().allInBook(book.id).filter { it.parentId == parentId && it.type == domain }
            val now = System.currentTimeMillis()
            val category = CategoryEntity(
                id = UUID.randomUUID().toString(),
                bookId = book.id,
                parentId = parentId,
                name = trimmed,
                type = domain,
                sortOrder = siblings.size,
                createdAt = now,
                updatedAt = now,
                deviceId = app.deviceId,
            )
            db.categoryDao().upsert(category)
            selectedCategoryId.value = category.id
            onDone()
        }
    }

    fun pressKey(key: String) {
        amountText.value = when (key) {
            KEY_BACKSPACE -> amountText.value.dropLast(1)
            KEY_CLEAR -> ""
            else -> MoneyExpression.append(amountText.value, key)
        }
    }

    fun evaluate() {
        val expr = amountText.value
        if (!MoneyExpression.hasPendingOperator(expr)) return
        val cents = MoneyExpression.evaluate(expr) ?: return
        amountText.value = Money.format(cents, grouped = false)
    }

    fun setNote(text: String) {
        note.value = text
    }

    private fun effectiveCents(expr: String): Long? {
        val cents = if (MoneyExpression.hasPendingOperator(expr)) {
            MoneyExpression.evaluate(expr)
        } else {
            Money.parseToCents(expr)
        }
        return cents?.takeIf { it > 0 }
    }

    /** 保存并返回 */
    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            if (saveInternal()) onDone()
        }
    }

    /** 再记：保存并清空金额/备注，保留分类，继续下一笔 */
    fun saveAgain() {
        viewModelScope.launch {
            if (saveInternal()) {
                amountText.value = ""
                note.value = ""
            }
        }
    }

    /**
     * 记一笔页能改的字段；编辑时只覆盖这些，其余字段由 [TxEdit.applyEdit] 从原记录带过来。
     */
    private typealias EditableTx = TxEdit.Editable

    private suspend fun saveInternal(): Boolean {
        val cents = effectiveCents(amountText.value) ?: return false
        val now = System.currentTimeMillis()
        val book = currentBook.value ?: db.bookDao().observeAll().first().firstOrNull() ?: return false
        val original = editId?.let { db.transactionDao().get(it) }

        val editable = when (type.value) {
            TxType.TRANSFER -> {
                val from = transferFromId.value ?: return false
                val to = transferToId.value ?: return false
                if (from == to) return false
                EditableTx(
                    type = TxType.TRANSFER,
                    amountCents = cents,
                    accountId = from,
                    targetAccountId = to,
                    categoryId = null,
                    dateEpochMinute = dateMinuteForSave(original),
                    note = note.value.trim().ifEmpty { null },
                    feeCents = Money.parseToCents(feeText.value) ?: 0L,
                )
            }
            else -> {
                val categoryId = selectedCategoryId.value ?: return false
                val category = categories.value.firstOrNull { it.id == categoryId } ?: return false
                EditableTx(
                    type = if (category.type == CategoryType.INCOME) TxType.INCOME else TxType.EXPENSE,
                    amountCents = cents,
                    accountId = accountId.value,
                    targetAccountId = null,
                    categoryId = category.id,
                    dateEpochMinute = dateMinuteForSave(original),
                    note = note.value.trim().ifEmpty { null },
                    feeCents = 0L,
                )
            }
        }

        // 编辑走 TxEdit.applyEdit：退款/优惠/不计收支/不计预算/导入批次/报销等
        // 本页管不着的字段一律原样保留（重建实体曾把它们全部重置——复查 P0 bug）。
        val tx = original?.let { TxEdit.applyEdit(it, editable, now, app.deviceId) }
            ?: TxEdit.newTransaction(book.id, editable, now, app.deviceId)
        db.transactionDao().upsert(tx)
        return true
    }

    /** 保存用时间：新建 = 今天/所选日 + 当前时分；编辑 = 原时间点（换日期则只挪天）。 */
    private fun dateMinuteForSave(original: TransactionEntity?): Long = if (original == null) {
        AddDate.epochMinuteFor(dateEpochDay.value, ZonedDateTime.now())
    } else {
        AddDate.epochMinuteForEdit(original.dateEpochMinute, dateEpochDay.value)
    }

    companion object {
        const val KEY_BACKSPACE = "⌫"
        const val KEY_CLEAR = "C"
    }
}
