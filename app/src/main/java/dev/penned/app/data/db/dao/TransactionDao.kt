package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.TransactionEntity
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
               t.refundCents AS refundCents, t.discountCents AS discountCents
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN categories p ON p.id = c.parentId
        LEFT JOIN accounts ta ON ta.id = t.targetAccountId
        WHERE t.deleted = 0 AND t.bookId = :bookId
        ORDER BY t.dateEpochMinute DESC
        LIMIT :limit
        """
    )
    fun observeRecentWithCategory(bookId: String, limit: Int): Flow<List<TxWithCategory>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: String): Flow<TransactionEntity?>

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
               t.refundCents AS refundCents, t.discountCents AS discountCents
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN categories p ON p.id = c.parentId
        LEFT JOIN accounts ta ON ta.id = t.targetAccountId
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

    /** 批次明细（导入批次管理页钻取）。 */
    @Query(
        """
        SELECT t.id AS id, t.type AS type, t.amountCents AS amountCents,
               t.accountId AS accountId, t.categoryId AS categoryId,
               t.dateEpochMinute AS dateEpochMinute, t.note AS note,
               c.name AS categoryName, p.name AS categoryParentName, c.iconKey AS categoryIconKey,
               ta.name AS targetAccountName,
               t.excludeFromStats AS excludeFromStats,
               t.refundCents AS refundCents, t.discountCents AS discountCents
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN categories p ON p.id = c.parentId
        LEFT JOIN accounts ta ON ta.id = t.targetAccountId
        WHERE t.deleted = 0 AND t.importBatchId = :batchId
        ORDER BY t.dateEpochMinute DESC
        """
    )
    fun observeByBatch(batchId: String): Flow<List<TxWithCategory>>

    /** 报销汇总（资产页）：可报 = 待报销金额，已报 = 已报销金额 */
    data class ReimburseSums(val pendingCents: Long, val doneCents: Long)

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN reimburseState = 'PENDING' THEN amountCents END), 0) AS pendingCents,
               COALESCE(SUM(CASE WHEN reimburseState = 'DONE' THEN amountCents END), 0) AS doneCents
        FROM transactions
        WHERE deleted = 0 AND bookId = :bookId AND reimburseState != 'NONE'
        """
    )
    fun observeReimburseSums(bookId: String): Flow<ReimburseSums>

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
               t.refundCents AS refundCents, t.discountCents AS discountCents
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN categories p ON p.id = c.parentId
        LEFT JOIN accounts ta ON ta.id = t.targetAccountId
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
