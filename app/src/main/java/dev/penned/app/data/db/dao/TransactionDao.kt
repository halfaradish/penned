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
        WHERE t.deleted = 0
        ORDER BY t.dateEpochMinute DESC
        LIMIT :limit
        """
    )
    fun observeRecentWithCategory(limit: Int): Flow<List<TxWithCategory>>

    @Query("SELECT * FROM transactions WHERE deleted = 0")
    suspend fun all(): List<TransactionEntity>

    @Upsert
    suspend fun upsert(tx: TransactionEntity)

    @Query("UPDATE transactions SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}
