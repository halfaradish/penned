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
)

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT t.id AS id, t.type AS type, t.amountCents AS amountCents,
               t.accountId AS accountId, t.categoryId AS categoryId,
               t.dateEpochMinute AS dateEpochMinute, t.note AS note,
               c.name AS categoryName, p.name AS categoryParentName, c.iconKey AS categoryIconKey
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN categories p ON p.id = c.parentId
        WHERE t.deleted = 0 AND t.bookId = :bookId
        ORDER BY t.dateEpochMinute DESC
        LIMIT :limit
        """
    )
    fun observeRecentWithCategory(bookId: String, limit: Int): Flow<List<TxWithCategory>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: String): Flow<TransactionEntity?>

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

    /** 按导入批次整体软删（导入批次管理页）。 */
    @Query("UPDATE transactions SET deleted = 1, updatedAt = :now WHERE importBatchId = :batchId")
    suspend fun softDeleteByBatch(batchId: String, now: Long)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}
