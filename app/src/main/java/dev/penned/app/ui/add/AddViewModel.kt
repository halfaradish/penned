package dev.penned.app.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.DebtWithRepaid
import dev.penned.app.data.db.dao.TemplateWithTags
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.AddDate
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.DebtAction
import dev.penned.app.domain.DebtMath
import dev.penned.app.domain.Money
import dev.penned.app.domain.MoneyExpression
import dev.penned.app.domain.RefundMath
import dev.penned.app.domain.ReimburseMath
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.TemplatePrefill
import dev.penned.app.domain.Templates
import dev.penned.app.ui.assets.selectableAccounts as visibleAccounts
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
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID

class AddViewModel(
    private val app: PennedApplication,
    private val editId: String? = null,
    /** 「记一笔还款」预填（M3-4）：从债务详情页跳进来时带着债务与那一格（缺省 = 债务发生）。 */
    private val presetDebtId: String? = null,
    private val presetDebtAction: DebtAction? = null,
    /**
     * 房贷首期预填（M3-11）：分期房贷页「按首期记一笔」带金额（分）/ 日期（epochDay）/ 备注进来。
     * 只预填这一笔，**不生成整个还款计划**（规格 M3-11 的边界；要每期循环用周期记账）。
     */
    private val presetCents: Long? = null,
    private val presetDay: Long? = null,
    private val presetNote: String? = null,
) : ViewModel() {

    private val db = app.database

    var type = MutableStateFlow(TxType.EXPENSE)
        private set
    /**
     * 第四 Tab（债务，M3-4）：它不是一种流水类型（落库的仍是收入/支出），所以单独一位而不是加进 [TxType]
     * ——`TxType` 是要落库的，加值会污染 `type = 'EXPENSE'` 一类的 SQL 与所有 `when`。
     */
    var debtTab = MutableStateFlow(false)
        private set
    /** 债务 Tab 选中的债务（D068：债务是实体，流水只是它名下的资金侧）。 */
    var debtId = MutableStateFlow<String?>(null)
        private set
    /** 债务 Tab 的两格：OPEN 债务发生（借入/借出）、SETTLE 债务清偿（还款/收到还款）。 */
    var debtAction = MutableStateFlow(DebtAction.OPEN)
        private set
    var amountText = MutableStateFlow("")
        private set
    /** 退款额（M3-2）：键盘输入的**原价**不动，落库的金额是净额（D069）。 */
    var refundText = MutableStateFlow("")
        private set
    /** 优惠额（M3-2），同退款。 */
    var discountText = MutableStateFlow("")
        private set
    /** 报销状态（M3-3，D008）：NONE 不报销 / PENDING 待报销 / DONE 已报销。 */
    var reimburseState = MutableStateFlow(ReimburseState.NONE)
        private set
    /** 收到报销款的账户（M3-3），可空。 */
    var reimburseAccountId = MutableStateFlow<String?>(null)
        private set
    /** 报销金额（M3-3）文本：空 = 缺省 0 = 按流水净额全额（`ReimburseMath`）。 */
    var reimburseText = MutableStateFlow("")
        private set
    /** 金额键盘当前在编辑哪一格——退款/优惠/报销金额复用同一个键盘，不另做输入控件（M3 §二·3 交互）。 */
    var amountField = MutableStateFlow(AmountField.AMOUNT)
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
    /** 记账时间（时:分，优化轮 3）；null = 现在（保存时取当前时刻）。编辑时按原流水反推。 */
    var timeOfDay = MutableStateFlow<LocalTime?>(null)
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
     * 账户选择器里可见的账户（M3-9）：**停用的不列**，但本笔已选中的那些（支出/收入账户、转账两端、
     * 报销账户）一律保留——否则编辑一条用停用账户记的流水时看不到自己（UI-13 同型坑）。
     */
    private val selectedAccountIds: StateFlow<Set<String?>> =
        combine(accountId, transferFromId, transferToId, reimburseAccountId) { a, from, to, reimb ->
            setOf(a, from, to, reimb)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val selectableAccounts: StateFlow<List<AccountEntity>> =
        combine(accounts, selectedAccountIds) { all, keep -> visibleAccounts(all, keep) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 「模板」抽屉的选择器（M3-5，本账本模板）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<TemplateWithTags>> = currentBook
        .flatMapLatest { book ->
            if (book == null) flowOf(emptyList()) else db.templateDao().observeByBook(book.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 债务 Tab 的选择器（本账本债务，含已归还——剩余在行上显示）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val debts: StateFlow<List<DebtWithRepaid>> = currentBook
        .flatMapLatest { book ->
            if (book == null) flowOf(emptyList()) else db.debtDao().observeByBook(book.id)
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

    /**
     * 能不能保存：看的是**输入框里的原价**（退款/优惠只把落库金额压小，不拦保存——
     * 全额退款要能存成净额 0 的流水）。
     */
    private val canSaveExpenseLike: StateFlow<Boolean> = combine(
        amountText, selectedCategoryId, type, transferFromId, transferToId,
    ) { amount, categoryId, txType, from, to ->
        val cents = effectiveCents(amount) ?: 0L
        when (txType) {
            TxType.TRANSFER -> cents > 0 && from != null && to != null && from != to
            else -> cents > 0 && categoryId != null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 债务 Tab：金额 + 债务 + 资金账户三者齐了才能存（钱往哪边流由方向与那一格决定）。 */
    private val canSaveDebt: StateFlow<Boolean> = combine(
        amountText, debtId, accountId,
    ) { amount, debt, account ->
        (effectiveCents(amount) ?: 0L) > 0 && debt != null && account != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val canSave: StateFlow<Boolean> =
        combine(canSaveExpenseLike, canSaveDebt, debtTab) { normal, debt, isDebtTab ->
            if (isDebtTab) debt else normal
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 信息条的「实付」行（M3-2）：把输入的三个数交给 [RefundMath.summaryText] 写成算式。
     * 金额还没输（或输的是半截算式）时不显示——算式缺项会比不显示更让人看不懂。
     */
    val paySummary: StateFlow<String?> = combine(
        amountText, refundText, discountText,
    ) { amount, refund, discount ->
        val original = parseCents(amount) ?: return@combine null
        if (original <= 0L) return@combine null
        RefundMath.summaryText(original, centsOrZero(refund), centsOrZero(discount))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 表达式待求值时，完成键显示为 "="（只在编辑主金额时；退款/优惠/报销金额的算式按保存键时结算） */
    val showEquals: StateFlow<Boolean> = combine(amountText, amountField) { text, field ->
        field == AmountField.AMOUNT && MoneyExpression.hasPendingOperator(text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 报销抽屉里「报销金额」行的文案（M3-3）：`全额 100.00`（缺省）/ `30.00`（自定）。
     * 全额里的那个数是**净额**（退款/优惠扣完之后的落库额）——报销按实付算，不按标价算。
     */
    val claimLabel: StateFlow<String?> = combine(
        amountText, refundText, discountText, reimburseText,
    ) { amount, refund, discount, claim ->
        val original = parseCents(amount) ?: return@combine null
        val net = RefundMath.netCents(original, centsOrZero(refund), centsOrZero(discount))
        if (net <= 0L) return@combine null
        ReimburseMath.claimLabel(centsOrZero(claim), net)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (editId != null) {
            loadForEdit(editId)
        } else if (presetDebtId != null) {
            prefillDebt(presetDebtId, presetDebtAction ?: DebtAction.OPEN)
        } else if (presetCents != null) {
            applyLoanPrefill()
        }
    }

    /** 房贷首期预填（M3-11）：金额（分）+ 日期 + 备注；分类与账户留给用户选。 */
    private fun applyLoanPrefill() {
        val cents = presetCents ?: return
        type.value = TxType.EXPENSE
        amountText.value = Money.format(cents, grouped = false)
        presetDay?.takeIf { it > 0L }?.let { dateEpochDay.value = it }
        presetNote?.takeIf { it.isNotBlank() }?.let { note.value = it }
    }

    /**
     * 「记一笔还款」预填（M3-4）：债务详情页跳进来时带着债务与那一格——顺带预填**剩余额**
     * （全额还清是最常见形态）与这笔债务上次用过的资金账户（还款一般走同一个账户）。
     */
    private fun prefillDebt(id: String, action: DebtAction) {
        viewModelScope.launch {
            val debt = db.debtDao().get(id) ?: return@launch
            debtTab.value = true
            debtId.value = debt.id
            debtAction.value = action
            if (action == DebtAction.SETTLE) {
                val remaining = db.debtDao().observeById(debt.id).first()?.remainingCents
                    ?: DebtMath.remainingCents(debt.principalCents, 0L)
                if (remaining > 0L) amountText.value = Money.format(remaining, grouped = false)
            }
            db.transactionDao().observeByDebt(debt.id).first().firstOrNull()?.accountId?.let { accountId.value = it }
        }
    }

    private fun loadForEdit(id: String) {
        viewModelScope.launch {
            val tx = db.transactionDao().get(id) ?: return@launch
            type.value = tx.type
            // 金额框回填的是**原价**：落库金额是净额，反推回用户当初输入的形态（D069）
            amountText.value = Money.format(
                RefundMath.originalCents(tx.amountCents, tx.refundCents, tx.discountCents),
                grouped = false,
            )
            refundText.value = if (tx.refundCents > 0L) Money.format(tx.refundCents, grouped = false) else ""
            discountText.value = if (tx.discountCents > 0L) Money.format(tx.discountCents, grouped = false) else ""
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
            // 时间 chip 直接回填原时分（显示 HH:mm、进选中态）；不碰它就是原值原样
            timeOfDay.value = AddDate.timeFromMinute(tx.dateEpochMinute)
            // 标签引用直查关系表（`allTags` 与 `categories` 同病：WhileSubscribed 的 stateIn 在刚开页时是空表）
            selectedTagIds.value = db.tagDao().tagsForTx(id).map { it.id }.toSet()
            // 报销三字段（M3-3）：状态/账户直接回填；金额 0 = 按全额，输入框留空（0 是缺省不是"报 0 元"）
            reimburseState.value = tx.reimburseState
            reimburseAccountId.value = tx.reimburseAccountId
            reimburseText.value = if (tx.reimburseCents > 0L) Money.format(tx.reimburseCents, grouped = false) else ""
            // 债务流水（M3-4）：带着 debtId 的流水在「债务」Tab 上编辑——哪一格由债务方向 + 流水类型反推
            val debtRef = tx.debtId
            if (debtRef != null) {
                db.debtDao().get(debtRef)?.let { debt ->
                    debtTab.value = true
                    debtId.value = debt.id
                    debtAction.value = DebtMath.actionOf(debt.direction, tx.type)
                }
            }
        }
    }

    fun selectType(t: TxType) {
        debtTab.value = false
        debtId.value = null
        debtAction.value = DebtAction.OPEN
        type.value = t
        if (t != TxType.TRANSFER) selectedCategoryId.value = null
        expandedParentId.value = null
        // 转账没有退款/优惠/报销，键盘也就没有另外几格可指
        amountField.value = AmountField.AMOUNT
        if (t == TxType.TRANSFER) {
            refundText.value = ""
            discountText.value = ""
            reimburseState.value = ReimburseState.NONE
            reimburseAccountId.value = null
            reimburseText.value = ""
        }
    }

    /**
     * 切到「债务」Tab（M3-4）：清掉债务面板没有的字段（分类/退款/优惠/报销/标签）——
     * 留着看不见却会被保存的残留最坑人；债务与那一格**保留**（切走再切回来不丢选择）。
     */
    fun selectDebtTab() {
        debtTab.value = true
        selectedCategoryId.value = null
        expandedParentId.value = null
        amountField.value = AmountField.AMOUNT
        refundText.value = ""
        discountText.value = ""
        reimburseState.value = ReimburseState.NONE
        reimburseAccountId.value = null
        reimburseText.value = ""
        selectedTagIds.value = emptySet()
    }

    fun pickDebt(id: String?) {
        debtId.value = id
    }

    /** 用模板（按 id，抽屉里点一行走这条）：读实体 + 标签引用再预填。 */
    fun applyTemplateById(id: String) {
        viewModelScope.launch {
            val template = db.templateDao().get(id) ?: return@launch
            applyTemplate(Templates.of(template, db.templateDao().tagsForTemplate(id).map { it.id }))
        }
    }

    /**
     * 用模板（M3-5）：把模板字段填进表单——**不直接入库**，用户在记账页确认后再保存。
     * 与编辑回填同一套字段（金额文本不带千分位）；父分类到库里查（`categories` 是 WhileSubscribed 的
     * stateIn，刚开页时读到的必然是空表——M3-2/M3-3 的老坑）。
     */
    fun applyTemplate(prefill: TemplatePrefill) {
        selectType(prefill.type)
        amountText.value = prefill.amountText
        selectedCategoryId.value = prefill.categoryId
        accountId.value = prefill.accountId
        transferFromId.value = prefill.accountId
        transferToId.value = prefill.targetAccountId
        feeText.value = if (prefill.feeCents > 0L) Money.format(prefill.feeCents, grouped = false) else ""
        note.value = prefill.note.orEmpty()
        selectedTagIds.value = prefill.tagIds.toSet()
        amountField.value = AmountField.AMOUNT
        viewModelScope.launch {
            expandedParentId.value = prefill.categoryId?.let { cid -> db.categoryDao().get(cid)?.parentId }
        }
    }

    fun pickDebtAction(action: DebtAction) {
        debtAction.value = action
    }

    /** 报销状态三选（M3-3）：改状态时顺手清掉不适用的附带项（不报销 = 没有账户/金额可言）。 */
    fun pickReimburseState(state: ReimburseState) {
        reimburseState.value = state
        if (state == ReimburseState.NONE) {
            reimburseAccountId.value = null
            reimburseText.value = ""
            if (amountField.value == AmountField.REIMBURSE) amountField.value = AmountField.AMOUNT
        }
    }

    fun pickReimburseAccount(id: String?) {
        reimburseAccountId.value = id
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

    /** 「时间」chip：选定 时:分（精确到分；没选就是"现在"）。 */
    fun setTime(time: LocalTime) {
        timeOfDay.value = time
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

    /** 键盘切格（M3-2/M3-3）：点信息条上的 chip 或抽屉里的「报销金额」行时把键盘指过去。 */
    fun focusAmountField(field: AmountField) {
        amountField.value = field
    }

    /** 键盘那一格对应的输入文本。 */
    private fun textOf(field: AmountField): String = when (field) {
        AmountField.AMOUNT -> amountText.value
        AmountField.REFUND -> refundText.value
        AmountField.DISCOUNT -> discountText.value
        AmountField.REIMBURSE -> reimburseText.value
    }

    private fun writeText(field: AmountField, text: String) {
        when (field) {
            AmountField.AMOUNT -> amountText.value = text
            AmountField.REFUND -> refundText.value = text
            AmountField.DISCOUNT -> discountText.value = text
            AmountField.REIMBURSE -> reimburseText.value = text
        }
    }

    /** 键位落到当前格：主金额 / 退款 / 优惠 / 报销金额共用一个键盘。 */
    fun pressKey(key: String) {
        val current = textOf(amountField.value)
        val next = when (key) {
            KEY_BACKSPACE -> current.dropLast(1)
            KEY_CLEAR -> ""
            else -> MoneyExpression.append(current, key)
        }
        writeText(amountField.value, next)
    }

    fun evaluate() {
        val field = amountField.value
        val expr = textOf(field)
        if (!MoneyExpression.hasPendingOperator(expr)) return
        val cents = MoneyExpression.evaluate(expr) ?: return
        writeText(field, Money.format(cents, grouped = false))
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

    /** 输入框文本 → 分；待求值的算式先求值。空/非法 → null。 */
    private fun parseCents(text: String): Long? =
        if (MoneyExpression.hasPendingOperator(text)) MoneyExpression.evaluate(text) else Money.parseToCents(text)

    /** 退款/优惠位的取值：没填就是 0，负数（键盘打不出，防外部来源）夹到 0。 */
    private fun centsOrZero(text: String): Long = (parseCents(text) ?: 0L).coerceAtLeast(0L)

    private fun effectiveCents(expr: String): Long? = parseCents(expr)?.takeIf { it > 0 }

    /** 保存并返回（把落库的流水 id 交出去：首页提示条上的「存为模板」要用它，M3-5）。 */
    fun save(onSaved: (String) -> Unit) {
        viewModelScope.launch {
            saveInternal()?.let(onSaved)
        }
    }

    /** 再记：保存并清空金额/备注/退款/优惠，保留分类，继续下一笔 */
    fun saveAgain() {
        viewModelScope.launch {
            if (saveInternal() != null) {
                amountText.value = ""
                note.value = ""
                refundText.value = ""
                discountText.value = ""
                // 报销金额也清（它和金额/退款一样是"本笔的数"）；报销状态与账户**留着**——
                // 一摞要报销的单子连着记，每笔都重新选待报销才叫烦（分类/账户/日期/时分同理保留）
                reimburseText.value = ""
                amountField.value = AmountField.AMOUNT
            }
        }
    }

    /**
     * 记一笔页能改的字段；编辑时只覆盖这些，其余字段由 [TxEdit.applyEdit] 从原记录带过来。
     */
    private typealias EditableTx = TxEdit.Editable

    private suspend fun saveInternal(): String? {
        // 金额框里是**原价**；落库金额 = 净额（D069——余额与统计只读 amountCents，口径一行不改）
        val originalCents = effectiveCents(amountText.value) ?: return null
        val refundCents = centsOrZero(refundText.value)
        val discountCents = centsOrZero(discountText.value)
        // 报销（M3-3）：状态/账户照选；金额空 = 0 = 按流水净额全额（ReimburseMath 的口径）
        val claimState = reimburseState.value
        val claimAccountId = reimburseAccountId.value
        val claimCents = centsOrZero(reimburseText.value)
        val now = System.currentTimeMillis()
        val book = currentBook.value ?: db.bookDao().observeAll().first().firstOrNull() ?: return null
        val original = editId?.let { db.transactionDao().get(it) }

        val editable = when {
            // 债务 Tab（M3-4）：落库的仍是普通收入/支出流水，只是带着 debtId——
            // 钱往哪边走由「债务方向 × 那一格」决定（DebtMath.flowType）；没有分类、没有退款/报销
            debtTab.value -> {
                val debt = debtId.value?.let { db.debtDao().get(it) } ?: return null
                val account = accountId.value ?: return null
                EditableTx(
                    type = DebtMath.flowType(debt.direction, debtAction.value),
                    amountCents = originalCents,
                    accountId = account,
                    targetAccountId = null,
                    categoryId = null,
                    dateEpochMinute = dateMinuteForSave(original),
                    note = note.value.trim().ifEmpty { null },
                    feeCents = 0L,
                    refundCents = 0L,
                    discountCents = 0L,
                    reimburseState = ReimburseState.NONE,
                    reimburseAccountId = null,
                    reimburseCents = 0L,
                    debtId = debt.id,
                )
            }
            type.value == TxType.TRANSFER -> {
                val from = transferFromId.value ?: return null
                val to = transferToId.value ?: return null
                if (from == to) return null
                EditableTx(
                    type = TxType.TRANSFER,
                    // 转账面板没有退款/优惠/报销：原价即落库额
                    amountCents = originalCents,
                    accountId = from,
                    targetAccountId = to,
                    categoryId = null,
                    dateEpochMinute = dateMinuteForSave(original),
                    note = note.value.trim().ifEmpty { null },
                    feeCents = Money.parseToCents(feeText.value) ?: 0L,
                    refundCents = 0L,
                    discountCents = 0L,
                    reimburseState = ReimburseState.NONE,
                    reimburseAccountId = null,
                    reimburseCents = 0L,
                    debtId = null,
                )
            }
            else -> {
                val categoryId = selectedCategoryId.value ?: return null
                val category = categories.value.firstOrNull { it.id == categoryId } ?: return null
                EditableTx(
                    type = if (category.type == CategoryType.INCOME) TxType.INCOME else TxType.EXPENSE,
                    amountCents = RefundMath.netCents(originalCents, refundCents, discountCents),
                    accountId = accountId.value,
                    targetAccountId = null,
                    categoryId = category.id,
                    dateEpochMinute = dateMinuteForSave(original),
                    note = note.value.trim().ifEmpty { null },
                    feeCents = 0L,
                    refundCents = refundCents,
                    discountCents = discountCents,
                    reimburseState = claimState,
                    reimburseAccountId = claimAccountId,
                    reimburseCents = claimCents,
                    debtId = null,
                )
            }
        }

        // 编辑走 TxEdit.applyEdit：不计收支/不计预算/导入批次/报销等
        // 本页管不着的字段一律原样保留（重建实体曾把它们全部重置——复查 P0 bug）。
        var tx = original?.let { TxEdit.applyEdit(it, editable, now, app.deviceId) }
            ?: TxEdit.newTransaction(book.id, editable, now, app.deviceId)
        // 借入不是收入、还款不是支出：**新建**的债务流水默认两个口径都不计
        // （D068；模拟机走查抓到只关"收支"时还款仍会算进预算已用——两个开关是两回事）；
        // 编辑时不动这两位——用户可能在详情页手动改过，不该被保存动作悄悄重置。
        if (original == null && debtTab.value) {
            tx = tx.copy(excludeFromStats = true, excludeFromBudget = true)
        }
        // 流水与标签关系一次提交（单事务）：否则"保存了流水、标签还没写"之间有半成品窗口，
        // 首页列表会先冒出一条没有标签的行（测试也抓到过这个竞态）
        db.withTransaction {
            db.transactionDao().upsert(tx)
            // 转账信息条没有标签 chip，所以保存转账时不动已有关系——
            // 外部来源（将来的导入/同步）若给转账带了标签，不该被本页静默清掉。
            // 债务流水同理（债务 Tab 上根本没有标签入口，更不该顺手清掉外部来源的关系）。
            if (tx.type != TxType.TRANSFER && !debtTab.value) {
                db.tagDao().setTagsForTx(tx.id, selectedTagIds.value.toList())
            }
        }
        return tx.id
    }

    /** 保存用时间：新建 = 所选日 + 所选时分（没选就当前时刻）；编辑 = 原时间点（换日期/换时分只动一头，都没动则原值原样）。 */
    private fun dateMinuteForSave(original: TransactionEntity?): Long = if (original == null) {
        AddDate.epochMinuteFor(dateEpochDay.value, timeOfDay.value, ZonedDateTime.now())
    } else {
        AddDate.epochMinuteForEdit(original.dateEpochMinute, dateEpochDay.value, timeOfDay.value)
    }

    companion object {
        const val KEY_BACKSPACE = "⌫"
        const val KEY_CLEAR = "C"
    }
}

/** 金额键盘当前在编辑的格（M3-2/M3-3）：退款/优惠/报销金额与主金额共用一个键盘，只是键位落到不同的文本上。 */
enum class AmountField { AMOUNT, REFUND, DISCOUNT, REIMBURSE }
