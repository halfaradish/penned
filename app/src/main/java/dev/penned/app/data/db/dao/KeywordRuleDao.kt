package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.penned.app.data.db.entity.KeywordRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * 关键词规则（M4-0 / M4-1）。列表按 `priority` 升序（上下移改的就是它），同序按 `createdAt`——
 * 与 `KeywordRules.match` 的匹配顺序**同一口径**（列表第一行就是最先命中的那条）。
 */
@Dao
interface KeywordRuleDao {

    @Query("SELECT * FROM keyword_rules WHERE deleted = 0 AND bookId = :bookId ORDER BY priority, createdAt")
    fun observeByBook(bookId: String): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE deleted = 0 AND bookId = :bookId ORDER BY priority, createdAt")
    suspend fun allInBook(bookId: String): List<KeywordRuleEntity>

    @Query("SELECT * FROM keyword_rules WHERE id = :id")
    suspend fun get(id: String): KeywordRuleEntity?

    @Upsert
    suspend fun upsert(rule: KeywordRuleEntity)

    @Query("UPDATE keyword_rules SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE keyword_rules SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    /**
     * 上下移：**交换两条规则的优先级**。
     *
     * 为什么不是一个 `UPDATE ... CASE` 语句：SQLite 逐行更新，CASE 里的子查询会读到**同一语句里
     * 已被改写过的另一行**，结果是"两个都变成后者"（实测踩到）。所以拆成"读两条 → 事务内两次写"。
     */
    @Transaction
    suspend fun swapPriority(a: String, b: String, now: Long) {
        val pa = priorityOf(a) ?: return
        val pb = priorityOf(b) ?: return
        setPriority(a, pb, now)
        setPriority(b, pa, now)
    }

    @Query("SELECT priority FROM keyword_rules WHERE id = :id AND deleted = 0")
    suspend fun priorityOf(id: String): Int?

    @Query("UPDATE keyword_rules SET priority = :priority, updatedAt = :now WHERE id = :id")
    suspend fun setPriority(id: String, priority: Int, now: Long)

    /** 新规则排在最后（`MAX + 1`；空表返回 0）。 */
    @Query("SELECT IFNULL(MAX(priority), -1) + 1 FROM keyword_rules WHERE bookId = :bookId")
    suspend fun nextPriority(bookId: String): Int

    @Query("SELECT COUNT(*) FROM keyword_rules WHERE deleted = 0 AND bookId = :bookId")
    suspend fun countInBook(bookId: String): Int
}
