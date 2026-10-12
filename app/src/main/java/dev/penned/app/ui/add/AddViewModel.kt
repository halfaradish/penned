package dev.penned.app.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TagEntity
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
    /** 本笔选中的标签 id（多选，M3-1）。 */
    var selectedTagIds = MutableStateFlow<Set<String>>(emptySet())
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val allTags: StateFlow<List<TagEntity>> = currentBook
        .flatMapLatest { book ->
            if (book == null) flowOf(emptyList()) else db.tagDao().observeByBook(book.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 记账宫格可见的分类（M3-1 分类隐藏）：隐藏的分类不进选择器，**隐藏的一级分类连它的子树一起收起来**
     * （父格都不画了，二级没有入口）；但**本笔已选中的那一个（及其父级）要留着**——否则编辑一条用隐藏分类
     * 记的流水时，宫格显示不出所属分类、[saveInternal] 也查不到它（UI-13 同型坑）。
     */
    val selectableCategories: StateFlow<List<CategoryEntity>> =
        combine(categories, selectedCategoryId) { all, selectedId ->
            val hiddenIds = all.filter { it.hidden }.map { it.id }.toSet()
            val keep = buildSet {
                selectedId?.let { id ->
                    all.firstOrNull { it.id == id }?.let { c ->
                        add(c.id)
                        c.parentId?.let { add(it) }
                    }
                }
            }
            all.filter { c ->
                c.id in keep || (!c.hidden && (c.parentId == null || c.parentId !in hiddenIds))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 标签选择器可见的标签：隐藏的不出现，**隐藏的一级标签连它的子树一起收起来**（父 chip 都不画了，
     * 二级没有入口——与分类同一口径，模拟机走查时发现"只有二级可见"会让它无处显示）；
     * 本笔已选中的（及其父级）一律保留。
     */
    val selectableTags: StateFlow<List<TagEntity>> =
        combine(allTags, selectedTagIds) { all, selected ->
            val hiddenIds = all.filter { it.hidden }.map { it.id }.toSet()
            val keep = buildSet {
                selected.forEach { id ->
                    all.firstOrNull { it.id == id }?.let { t ->
                        add(t.id)
                        t.parentId?.let { add(it) }
                    }
                }
            }
            all.filter { t ->
                t.id in keep || (!t.hidden && (t.parentId == null || t.parentId !in hiddenIds))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            // 父分类要**到库里查**：`categories` 是 WhileSubscribed 的 stateIn(…, emptyList())，
            // 编辑页刚开时它还没被订阅、读到的必然是空表，`?: cid` 回落成子分类 id——
            // 宫格判"哪一行展开"用的是「父格里有没有这个 id」，于是父格不选中、子分类条带也不展开（UI-13）
            expandedParentId.value = tx.categoryId?.let { cid ->
                db.categoryDao().get(cid)?.parentId ?: cid
            }
            accountId.value = tx.accountId
            transferFromId.value = tx.accountId
            transferToId.value = tx.targetAccountId
            feeText.value = if (tx.feeCents > 0) Money.format(tx.feeCents, grouped = false) else ""
            note.value = tx.note.orEmpty()
            dateEpochDay.value = AddDate.dayFromMinute(tx.dateEpochMinute)
            // 标签引用直查关系表（`allTags` 与 `categories` 同病：WhileSubscribed 的 stateIn 在刚开页时是空表）
            selectedTagIds.value = db.tagDao().tagsForTx(id).map { it.id }.toSet()
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

    /** 多选：点一下切换选中（M3-1 标签抽屉）。 */
    fun toggleTag(id: String) {
        val current = selectedTagIds.value
        selectedTagIds.value = if (id in current) current - id else current + id
    }

    /** 记一笔里新建标签并直接选中（M3-1；parentId 为空 = 新增一级标签）。 */
    fun addTagAndSelect(name: String, parentId: String?) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@launch
            val book = currentBook.value ?: return@launch
            val siblings = db.tagDao().allInBook(book.id).filter { it.parentId == parentId }
            val now = System.currentTimeMillis()
            val tag = TagEntity(
                id = UUID.randomUUID().toString(),
                bookId = book.id,
                parentId = parentId,
                name = trimmed,
                sortOrder = siblings.size,
                createdAt = now,
                updatedAt = now,
                deviceId = app.deviceId,
            )
            db.tagDao().upsert(tag)
            selectedTagIds.value = selectedTagIds.value + tag.id
        }
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
        // 流水与标签关系一次提交（单事务）：否则"保存了流水、标签还没写"之间有半成品窗口，
        // 首页列表会先冒出一条没有标签的行（测试也抓到过这个竞态）
        db.withTransaction {
            db.transactionDao().upsert(tx)
            // 转账信息条没有标签 chip，所以保存转账时不动已有关系——
            // 外部来源（将来的导入/同步）若给转账带了标签，不该被本页静默清掉。
            if (tx.type != TxType.TRANSFER) {
                db.tagDao().setTagsForTx(tx.id, selectedTagIds.value.toList())
            }
        }
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
