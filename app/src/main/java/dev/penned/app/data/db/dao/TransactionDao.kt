package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.DebtDirection
import dev.penned.app.domain.TxType
import kotlinx.coroutines.flow.Flow

/** 列表行：流水 + 分类名（含父分类名），供首页展示。 */
data class TxWithCategory(
    val id: String,
    val type: TxType,
    val amountCents: Long,
    val accountId: String?,
    val categoryId: String?,
    val dateEpochMinute: Long,
    val note: String?,
    val categoryName: String?,
    val categoryParentName: String?,
    val categoryIconKey: String?,
    /** 转账行的转入账户名（非转账为 null），首页行显示"转账 · 转入 X"。 */
    val targetAccountName: String?,
    /** D020「不计入收支」：仍显示在列表里，但不计入日/月/分类合计。 */
    val excludeFromStats: Boolean,
    /** 退款额 / 优惠额（M3-2）：行上给一个「退款」标记，说明金额为什么变小甚至是 0。 */
    val refundCents: Long,
    val discountCents: Long,
    /**
     * 债务资金流水（M3-4，D068）：行标题写「借入 · 张三」而不是"未分类"。手记为 null。
     * 这两个是**展示投影**（Room 一定填上），给默认值只为截图/单测的 fixture 少写两行；
     * 与上面几个"钱"字段不给默认值的理由不同。
     */
    val debtDirection: DebtDirection? = null,
    val debtCounterparty: String? = null,
)

/** 债务详情页的还款记录行（M3-4）：一笔资金流水 + 账户名；动作词由 `DebtMath.flowLabel` 推。 */
data class DebtFlowRow(
    val id: String,
    val type: TxType,
    val amountCents: Long,
    val accountId: String?,
    val dateEpochMinute: Long,
    val note: String?,
    val accountName: String?,
)

@Dao
interface TransactionDao {

    @Query(
        """
        SELECT t.id AS id, t.type AS type, t.amountCents AS amountCents,
               t.accountId AS accountId, t.categoryId AS categoryId,
               t.dateEpochMinute AS dateEpochMinute, t.note AS note,
               c.name AS categoryName, p.name AS categoryParentName, c.iconKey AS categoryIconKey,
               ta.name AS targetAccountName,
               t.excludeFromStats AS excludeFromStats,
               t.refundCents AS refundCents, t.discountCents AS discountCents,
               dt.direction AS debtDirection, dt.counterparty AS debtCounterparty
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN categories p ON p.id = c.parentId
        LEFT JOIN accounts ta ON ta.id = t.targetAccountId
        LEFT JOIN debts dt ON dt.id = t.debtId
        WHERE t.deleted = 0 AND t.bookId = :bookId
        ORDER BY t.dateEpochMinute DESC
        LIMIT :limit
        """
    )
    fun observeRecentWithCategory(bookId: String, limit: Int): Flow<List<TxWithCategory>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: String): Flow<TransactionEntity?>

    /** 债务名下的资金流水（债务详情页的还款记录）。 */
    @Query(
        """
        SELECT t.id AS id, t.type AS type, t.amountCents AS amountCents, t.accountId AS accountId,
               t.dateEpochMinute AS dateEpochMinute, t.note AS note, a.name AS accountName
        FROM transactions t
        LEFT JOIN accounts a ON a.id = t.accountId
        WHERE t.deleted = 0 AND t.debtId = :debtId
        ORDER BY t.dateEpochMinute DESC
        """
    )
    fun observeByDebt(debtId: String): Flow<List<DebtFlowRow>>

    /**
     * 首页搜索：备注 / 一级分类名 / 二级分类名 模糊匹配，数字输入额外按金额精确匹配。
     * pattern 由 [dev.penned.app.domain.SearchQuery.likePattern] 转义（% _ \ 按字面）。
     */
    @Query(
        """
        SELECT t.id AS id, t.type AS type, t.amountCents AS amountCents,
               t.accountId AS accountId, t.categoryId AS categoryId,
               t.dateEpochMinute AS dateEpochMinute, t.note AS note,
               c.name AS categoryName, p.name AS categoryParentName, c.iconKey AS categoryIconKey,
               ta.name AS targetAccountName,
               t.excludeFromStats AS excludeFromStats,
               t.refundCents AS refundCents, t.discountCents AS discountCents,
               dt.direction AS debtDirection, dt.counterparty AS debtCounterparty
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN categories p ON p.id = c.parentId
        LEFT JOIN accounts ta ON ta.id = t.targetAccountId
        LEFT JOIN debts dt ON dt.id = t.debtId
        WHERE t.deleted = 0 AND t.bookId = :bookId
          AND (
            t.note LIKE :pattern ESCAPE '\'
            OR c.name LIKE :pattern ESCAPE '\'
            OR p.name LIKE :pattern ESCAPE '\'
            OR (:cents IS NOT NULL AND t.amountCents = :cents)
          )
        ORDER BY t.dateEpochMinute DESC
        LIMIT 200
        """
    )
    fun observeSearchWithCategory(bookId: String, pattern: String, cents: Long?): Flow<List<TxWithCategory>>

    /**
     * 组合筛选（M3-7）：条件全可空，`null`/空列表 = 不限（口径在 `TxFilter`）。
     *
     * 几个写法上的坑（都踩过）：
     * - **多值条件不能直接 `IN (:list)`**：空列表会生成 `IN ()` 语法错，所以配一个 `hasX` 标志位
     *   （`0` = 这个条件没用），调用方在没有选中项时传 `listOf("")` + `hasX = 0`；
     * - 关键词用 `LIKE :pattern ESCAPE '\\'`，pattern 由 `SearchQuery.likePattern` 转义（字面量匹配）；
     * - 分类的"含二级"在 Kotlin 侧展开（`TxFilters.expandCategoryIds`）后传进来，SQL 不递归；
     * - **不设 LIMIT**：结果集就是导出集（"精准导出"必须与屏幕上看到的一致，截断会静默丢数据）；
     *   列表侧是 LazyColumn，几千行也不打紧。
     */
    @Query(
        """
        SELECT t.id AS id, t.type AS type, t.amountCents AS amountCents,
               t.accountId AS accountId, t.categoryId AS categoryId,
               t.dateEpochMinute AS dateEpochMinute, t.note AS note,
               c.name AS categoryName, p.name AS categoryParentName, c.iconKey AS categoryIconKey,
               ta.name AS targetAccountName,
               t.excludeFromStats AS excludeFromStats,
               t.refundCents AS refundCents, t.discountCents AS discountCents,
               dt.direction AS debtDirection, dt.counterparty AS debtCounterparty
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN categories p ON p.id = c.parentId
        LEFT JOIN accounts ta ON ta.id = t.targetAccountId
        LEFT JOIN debts dt ON dt.id = t.debtId
        WHERE t.deleted = 0 AND t.bookId = :bookId
          AND (:pattern IS NULL OR t.note LIKE :pattern ESCAPE '\'
               OR c.name LIKE :pattern ESCAPE '\' OR p.name LIKE :pattern ESCAPE '\'
               OR (:keywordCents IS NOT NULL AND t.amountCents = :keywordCents))
          AND (:type IS NULL OR t.type = :type)
          AND (:fromMinute IS NULL OR t.dateEpochMinute >= :fromMinute)
          AND (:toMinute IS NULL OR t.dateEpochMinute <= :toMinute)
          AND (:hasCategories = 0 OR t.categoryId IN (:categoryIds))
          AND (:hasAccounts = 0 OR t.accountId IN (:accountIds))
          AND (:hasTags = 0 OR EXISTS (SELECT 1 FROM transaction_tags r
                                       WHERE r.transactionId = t.id AND r.tagId IN (:tagIds)))
          AND (:minCents IS NULL OR t.amountCents >= :minCents)
          AND (:maxCents IS NULL OR t.amountCents <= :maxCents)
          AND (:excludeFromStats IS NULL OR t.excludeFromStats = :excludeFromStats)
          AND (:excludeFromBudget IS NULL OR t.excludeFromBudget = :excludeFromBudget)
          AND (:includeTransfer IS NULL
               OR (:includeTransfer = 1 AND t.type = 'TRANSFER')
               OR (:includeTransfer = 0 AND t.type != 'TRANSFER'))
        ORDER BY t.dateEpochMinute DESC
        """
    )
    fun observeFiltered(
        bookId: String,
        pattern: String?,
        keywordCents: Long?,
        type: TxType?,
        fromMinute: Long?,
        toMinute: Long?,
        hasCategories: Int,
        categoryIds: List<String>,
        hasAccounts: Int,
        accountIds: List<String>,
        hasTags: Int,
        tagIds: List<String>,
        minCents: Long?,
        maxCents: Long?,
        excludeFromStats: Boolean?,
        excludeFromBudget: Boolean?,
        includeTransfer: Int?,
    ): Flow<List<TxWithCategory>>

    /** 导出用：按 id 取整行实体（筛选结果集 → CSV，`PennedCsv.export` 吃实体）。 */
    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun entitiesByIds(ids: List<String>): List<TransactionEntity>

    /** 批次明细（导入批次管理页钻取）。 */
    @Query(
        """
        SELECT t.id AS id, t.type AS type, t.amountCents AS amountCents,
               t.accountId AS accountId, t.categoryId AS categoryId,
               t.dateEpochMinute AS dateEpochMinute, t.note AS note,
               c.name AS categoryName, p.name AS categoryParentName, c.iconKey AS categoryIconKey,
               ta.name AS targetAccountName,
               t.excludeFromStats AS excludeFromStats,
               t.refundCents AS refundCents, t.discountCents AS discountCents,
               dt.direction AS debtDirection, dt.counterparty AS debtCounterparty
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN categories p ON p.id = c.parentId
        LEFT JOIN accounts ta ON ta.id = t.targetAccountId
        LEFT JOIN debts dt ON dt.id = t.debtId
        WHERE t.deleted = 0 AND t.importBatchId = :batchId
        ORDER BY t.dateEpochMinute DESC
        """
    )
    fun observeByBatch(batchId: String): Flow<List<TxWithCategory>>

    /**
     * 报销汇总（资产页）：可报 = 待报销金额，已报 = 已报销金额。
     *
     * 口径（M3-3，D008；`ReimburseMath.claimCents` 的 SQL 版）：**`reimburseCents` 缺省 0 = 按流水净额全额**
     * ——`CASE WHEN reimburseCents > 0 THEN reimburseCents ELSE amountCents END`。
     * 改这里必须同时改 `ReimburseMath`（口径的表驱动测试在那边，这段 SQL 由 DAO 测试钉）。
     */
    data class ReimburseSums(val pendingCents: Long, val doneCents: Long)

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN reimburseState = 'PENDING'
                                 THEN CASE WHEN reimburseCents > 0 THEN reimburseCents ELSE amountCents END END), 0) AS pendingCents,
               COALESCE(SUM(CASE WHEN reimburseState = 'DONE'
                                 THEN CASE WHEN reimburseCents > 0 THEN reimburseCents ELSE amountCents END END), 0) AS doneCents
        FROM transactions
        WHERE deleted = 0 AND bookId = :bookId AND reimburseState != 'NONE'
        """
    )
    fun observeReimburseSums(bookId: String): Flow<ReimburseSums>

    /** 某周期规则已生成的期次（分钟）——打开即补的幂等判据（同一期只生成一条）。 */
    @Query("SELECT dateEpochMinute FROM transactions WHERE deleted = 0 AND recurringRuleId = :ruleId")
    suspend fun minutesForRule(ruleId: String): List<Long>

    @Query("SELECT * FROM transactions WHERE deleted = 0")
    suspend fun all(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND bookId = :bookId")
    suspend fun allInBook(bookId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    @Upsert
    suspend fun upsert(tx: TransactionEntity)

    @Upsert
    suspend fun upsertAll(txs: List<TransactionEntity>)

    @Query("UPDATE transactions SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE transactions SET deleted = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDeleteAll(ids: List<String>, now: Long)

    /** 反软删：左滑删除后 Snackbar「撤销」用（同一批 id 原样恢复）。 */
    @Query("UPDATE transactions SET deleted = 0, updatedAt = :now WHERE id IN (:ids)")
    suspend fun restoreAll(ids: List<String>, now: Long)

    /** 按导入批次整体软删（导入批次管理页）。 */
    @Query("UPDATE transactions SET deleted = 1, updatedAt = :now WHERE importBatchId = :batchId")
    suspend fun softDeleteByBatch(batchId: String, now: Long)

    /** 月度分类支出（不含 excludeFromBudget），供预算与统计。 */
    data class CategorySpend(val categoryId: String?, val cents: Long)

    @Query(
        """
        SELECT categoryId, SUM(amountCents) AS cents
        FROM transactions
        WHERE deleted = 0 AND bookId = :bookId AND type = 'EXPENSE' AND excludeFromBudget = 0
          AND dateEpochMinute BETWEEN :fromMinute AND :toMinute
        GROUP BY categoryId
        """
    )
    fun observeMonthSpendByCategory(bookId: String, fromMinute: Long, toMinute: Long): Flow<List<CategorySpend>>

    /**
     * 任意时间窗口（分钟，闭区间）的流水，统计页与分类详细页共用。
     * 分组/汇总一律在 Kotlin 侧做（[dev.penned.app.domain.StatsMath]），这里只取窗口。
     */
    @Query(
        """
        SELECT t.id AS id, t.type AS type, t.amountCents AS amountCents,
               t.accountId AS accountId, t.categoryId AS categoryId,
               t.dateEpochMinute AS dateEpochMinute, t.note AS note,
               c.name AS categoryName, p.name AS categoryParentName, c.iconKey AS categoryIconKey,
               ta.name AS targetAccountName,
               t.excludeFromStats AS excludeFromStats,
               t.refundCents AS refundCents, t.discountCents AS discountCents,
               dt.direction AS debtDirection, dt.counterparty AS debtCounterparty
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN categories p ON p.id = c.parentId
        LEFT JOIN accounts ta ON ta.id = t.targetAccountId
        LEFT JOIN debts dt ON dt.id = t.debtId
        WHERE t.deleted = 0 AND t.bookId = :bookId
          AND t.dateEpochMinute BETWEEN :fromMinute AND :toMinute
        ORDER BY t.dateEpochMinute DESC
        """
    )
    fun observeRangeTxsWithCategory(bookId: String, fromMinute: Long, toMinute: Long): Flow<List<TxWithCategory>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /**
     * 本账本最早一条流水的时间（epoch 分钟，无流水为 null）。
     * 统计页的时间切换条用它当跨度起点（[dev.penned.app.domain.StatsStrip]）。
     */
    @Query("SELECT MIN(dateEpochMinute) FROM transactions WHERE deleted = 0 AND bookId = :bookId")
    fun observeEarliestMinute(bookId: String): Flow<Long?>

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted = 0 AND bookId = :bookId")
    suspend fun countInBook(bookId: String): Int

    /** 账户名下流水数（转出算 accountId、转入算 targetAccountId）：有流水就不给删账户。 */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE deleted = 0 AND (accountId = :accountId OR targetAccountId = :accountId)
        """
    )
    suspend fun countByAccount(accountId: String): Int

    @Query("UPDATE transactions SET deleted = 1, updatedAt = :now WHERE bookId = :bookId")
    suspend fun softDeleteByBook(bookId: String, now: Long)
}
