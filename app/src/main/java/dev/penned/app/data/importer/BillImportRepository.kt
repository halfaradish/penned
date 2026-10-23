package dev.penned.app.data.importer

import dev.penned.app.data.db.PennedDatabase
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.ImportBatchEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.AccountGuess
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.BillRow
import dev.penned.app.domain.CategoryAssignment
import dev.penned.app.domain.CategoryResolution
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import java.util.UUID

/**
 * 账户映射表的一行（预览页）：一个"支付方式" → 我们的哪个账户 / 新建 / 不指定。
 *
 * [targetAccountId] 为空且有 [newAccountName] = 导入时新建；两者都空 = 不指定（账户留空，
 * 与既有"纯现金无账户记账"同一口径，D005）。
 */
data class AccountMapping(
    val clue: String,
    val matchedAccountId: String? = null,
    val matchedAccountName: String? = null,
    /** 非空 = 导入时按这个名字新建账户（类型见 [newAccountType]）。 */
    val newAccountName: String? = null,
    val newAccountType: AccountType = AccountType.DEBIT,
) {
    val willCreate: Boolean get() = matchedAccountId == null && newAccountName != null
    val skip: Boolean get() = matchedAccountId == null && newAccountName == null
}

/** 导入结果（预览页与提示条都用它汇报）。 */
data class BillImportResult(
    val batchId: String,
    val imported: Int,
    val skipped: Int,
    val duplicate: Int,
)

/**
 * 账单导入的落库（M4-5，规格 §三 M4-5）：**只做"文件里确有的东西"**——
 * 分类按名匹配（**绝不新建**，B1）、账户按映射表（可新建，B2/B3）、单号落 `externalId`、
 * 批次落 `sourceKind` 与跳过/重复计数。
 *
 * 分块写库（500 条一块）并回调进度：以年为单位的账单动辄几百上千行，一次性写会卡主线程。
 * 跳过与重复的行**不写库**（只在预览页计数，§二·16）。
 */
class BillImportRepository(
    private val db: PennedDatabase,
    private val deviceId: String,
) {

    /** 预览页要用：把"这一批里出现过的支付方式"整理成映射表（同名账户优先，无则给"新建"建议）。 */
    suspend fun buildAccountMappings(bookId: String, rows: List<BillRow>): List<AccountMapping> {
        val accounts = db.accountDao().allInBook(bookId)
        return rows.mapNotNull { it.accountClue }.distinct().sorted().map { clue ->
            val hit = accounts.firstOrNull { it.name == clue }
            if (hit != null) {
                AccountMapping(clue = clue, matchedAccountId = hit.id, matchedAccountName = hit.name)
            } else {
                AccountMapping(clue = clue, newAccountName = clue, newAccountType = AccountGuess.typeFor(clue))
            }
        }
    }

    /**
     * 分类线索 → 我们的分类（**只按名字匹配，匹配不上返回 null**；B1 裁决绝不新建）。
     *
     * 判定顺序收口在 [CategoryAssignment.resolve]：平台分类列 → 关键词规则 → AI 建议（D117）。
     */
    suspend fun resolveCategoryId(
        row: BillRow,
        categories: List<CategoryEntity>,
        rules: List<dev.penned.app.data.db.entity.KeywordRuleEntity>,
    ): String? = resolveCategory(row, categories, rules).categoryId

    /** 带来源的判定（预览页"按名命中 / 关键词规则 / AI / 待补"四类计数用）。 */
    fun resolveCategory(
        row: BillRow,
        categories: List<CategoryEntity>,
        rules: List<dev.penned.app.data.db.entity.KeywordRuleEntity>,
    ): CategoryResolution = CategoryAssignment.resolve(row, categories, rules)

    /**
     * 落库：先按 [mappings] 备好账户（新建的写进去），再分块写流水与批次。
     *
     * [rows] 只传"将导入"的行（已去重、已跳过的都不在这里）；[duplicateCount]/[skippedCount]
     * 只用于批次记账。
     */
    suspend fun import(
        bookId: String,
        fileName: String,
        sourceKind: String,
        rows: List<BillRow>,
        mappings: List<AccountMapping>,
        duplicateCount: Int,
        skippedCount: Int,
        /**
         * 落库流水的记录方式：文件导入是 `IMPORT`，**截图识别一批是 `SCREENSHOT`**（D111），
         * 详情页据此显示「账单导入」/「截图导入」。
         */
        txSource: TxSource = TxSource.IMPORT,
        /** 「这批记到」选的账户：**只对没有账户线索的行生效**（文件导入靠映射表，截图没有线索）。 */
        fallbackAccountId: String? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): BillImportResult {
        val now = System.currentTimeMillis()
        val batchId = UUID.randomUUID().toString()

        // 1) 账户：已有映射直接用；"新建"的按名字找一次（同批内同名只建一个），没有就建
        val accounts = db.accountDao().allInBook(bookId).toMutableList()
        val accountIdByClue = HashMap<String, String>()
        for (mapping in mappings) {
            mapping.matchedAccountId?.let { accountIdByClue[mapping.clue] = it; return@let }
            val name = mapping.newAccountName ?: continue
            val existing = accounts.firstOrNull { it.name == name }
            val id = existing?.id ?: run {
                val created = AccountEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    name = name,
                    type = mapping.newAccountType,
                    initialBalanceCents = 0,
                    createdAt = now,
                    updatedAt = now,
                    deviceId = deviceId,
                )
                db.accountDao().upsert(created)
                accounts += created
                created.id
            }
            accountIdByClue[mapping.clue] = id
        }

        // 2) 分类与关键词规则（一次取好，逐行匹配；**不新建分类**）
        val categories = db.categoryDao().allInBook(bookId)
        val rules = db.keywordRuleDao().allInBook(bookId)

        // 3) 分块写流水
        var done = 0
        rows.chunked(CHUNK).forEach { chunk ->
            val entities = chunk.map { row ->
                TransactionEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    type = if (row.expense) TxType.EXPENSE else TxType.INCOME,
                    amountCents = row.amountCents,
                    accountId = row.accountClue?.let { accountIdByClue[it] } ?: fallbackAccountId,
                    targetAccountId = null,
                    categoryId = resolveCategoryId(row, categories, rules),
                    dateEpochMinute = row.dateEpochMinute,
                    note = row.note,
                    source = txSource,
                    importBatchId = batchId,
                    externalId = row.externalId,
                    creatorDeviceId = deviceId,
                    createdAt = now,
                    updatedAt = now,
                    deviceId = deviceId,
                )
            }
            entities.forEach { db.transactionDao().upsert(it) }
            done += chunk.size
            onProgress(done, rows.size)
        }

        // 4) 批次记账（来源 + 跳过/重复计数都落库，预览页与批次页可查）
        db.importBatchDao().upsert(
            ImportBatchEntity(
                id = batchId,
                bookId = bookId,
                fileName = fileName,
                count = rows.size,
                importedAt = now,
                sourceKind = sourceKind,
                skippedCount = skippedCount,
                duplicateCount = duplicateCount,
                createdAt = now,
                updatedAt = now,
                deviceId = deviceId,
            )
        )

        return BillImportResult(
            batchId = batchId,
            imported = rows.size,
            skipped = skippedCount,
            duplicate = duplicateCount,
        )
    }

    /**
     * 库内已有的单号（**分批查全**）：SQLite 的变量上限是 999，账单文件动辄上千行——
     * 只查前 500 个的话，后面的行会被当成"新行"重复导入（M4-5 真机走查实拍：
     * 同一份 507 行的文件导第二次，只认出 500 条重复、又插进去 7 条）。
     */
    suspend fun existingExternalIds(bookId: String, ids: List<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        val out = HashSet<String>()
        ids.chunked(CHUNK).forEach { chunk ->
            db.transactionDao().externalIdsInBook(bookId, chunk).let { out += it }
        }
        return out
    }

    /**
     * 库内已有的兜底键（无单号流水的 `时间|金额|方向|备注`）：只算本账本这一批时间跨度内的行，
     * 免得把整个账本当参数传进 SQL。
     */
    suspend fun existingFallbackKeys(bookId: String, minMinute: Long, maxMinute: Long): Set<String> {
        val rows = db.transactionDao().allInBook(bookId)
        return rows.asSequence()
            .filter { it.externalId == null && it.dateEpochMinute in minMinute..maxMinute }
            .map { "${it.dateEpochMinute}|${it.amountCents}|${if (it.type == TxType.INCOME) "I" else "E"}|${it.note.orEmpty()}" }
            .toHashSet()
    }

    /** 分类名 → 分类（预览页"分类来源说明"用：分类列命中几个 / 关键词规则命中几个 / 待补几个）。 */
    fun categoryTypeOf(expense: Boolean): CategoryType =
        if (expense) CategoryType.EXPENSE else CategoryType.INCOME

    companion object {
        /** 分块写库的大小（与 `TransactionDao.externalIdsInBook` 的分批口径一致）。 */
        const val CHUNK = 500
    }
}
