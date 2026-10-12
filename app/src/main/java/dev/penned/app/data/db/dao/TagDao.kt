package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.data.db.entity.TransactionTagRefEntity
import kotlinx.coroutines.flow.Flow

/**
 * 两级标签（M3-1；表 v1 就建好，本片接 DAO/UI）。
 *
 * 关系表 `transaction_tags` 无软删字段（照 v1 建表先例），所以"换标签"是**整批删+插**：
 * [setTagsForTx] 单事务全量替换，天然幂等；标签本身走软删，删标签时连带摘掉关系行。
 */
@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE deleted = 0 AND bookId = :bookId ORDER BY sortOrder, name")
    fun observeByBook(bookId: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE deleted = 0 AND bookId = :bookId ORDER BY sortOrder, name")
    suspend fun allInBook(bookId: String): List<TagEntity>

    /** 不滤 deleted：编辑页要能读回已被软删标签的引用（与 `categoryDao().get` 同口径）。 */
    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun get(id: String): TagEntity?

    @Upsert
    suspend fun upsert(tag: TagEntity)

    @Query("UPDATE tags SET hidden = :hidden, updatedAt = :now WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, now: Long)

    @Query("UPDATE tags SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT id FROM tags WHERE deleted = 0 AND parentId = :parentId")
    suspend fun childIds(parentId: String): List<String>

    @Query("UPDATE tags SET deleted = 1, updatedAt = :now WHERE parentId = :parentId")
    suspend fun softDeleteChildren(parentId: String, now: Long)

    @Query("DELETE FROM transaction_tags WHERE tagId IN (:tagIds)")
    suspend fun clearRefs(tagIds: List<String>)

    /** 软删一个标签：连同它的二级标签一起删，并摘掉与流水的关联（单事务）。 */
    @Transaction
    suspend fun deleteTag(id: String, now: Long) {
        clearRefs(listOf(id) + childIds(id))
        softDelete(id, now)
        softDeleteChildren(id, now)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRef(ref: TransactionTagRefEntity)

    @Query("DELETE FROM transaction_tags WHERE transactionId = :txId")
    suspend fun clearTxRefs(txId: String)

    /** 全量替换某条流水的标签（单事务；重复 id 只留一条，幂等）。 */
    @Transaction
    suspend fun setTagsForTx(txId: String, tagIds: List<String>) {
        clearTxRefs(txId)
        tagIds.distinct().forEach { insertRef(TransactionTagRefEntity(txId, it)) }
    }

    /** 某条流水的标签（编辑页回填）。 */
    @Query(
        """
        SELECT t.* FROM tags t INNER JOIN transaction_tags r ON r.tagId = t.id
        WHERE r.transactionId = :txId AND t.deleted = 0 ORDER BY t.sortOrder, t.name
        """
    )
    suspend fun tagsForTx(txId: String): List<TagEntity>

    /** 某条流水的标签（详情页实时展示）。 */
    @Query(
        """
        SELECT t.* FROM tags t INNER JOIN transaction_tags r ON r.tagId = t.id
        WHERE r.transactionId = :txId AND t.deleted = 0 ORDER BY t.sortOrder, t.name
        """
    )
    fun observeTagsForTx(txId: String): Flow<List<TagEntity>>

    /** 标签挂在多少条未删流水上（删除前提示"已用于 N 笔流水"）。 */
    @Query(
        """
        SELECT COUNT(*) FROM transaction_tags r INNER JOIN transactions t ON t.id = r.transactionId
        WHERE r.tagId = :tagId AND t.deleted = 0
        """
    )
    suspend fun usageCount(tagId: String): Int

    /** 按标签查流水（M3-7 组合筛选用；同一个标签只算一次，按时间倒序）。 */
    @Query(
        """
        SELECT t.* FROM transactions t INNER JOIN transaction_tags r ON r.transactionId = t.id
        WHERE r.tagId IN (:tagIds) AND t.deleted = 0
        GROUP BY t.id
        ORDER BY t.dateEpochMinute DESC
        """
    )
    fun observeByTags(tagIds: List<String>): Flow<List<dev.penned.app.data.db.entity.TransactionEntity>>
}
