package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.PendingEntryEntity
import dev.penned.app.domain.PendingKind
import dev.penned.app.domain.PendingStatus
import kotlinx.coroutines.flow.Flow

/**
 * 机器识别候选池（M4-0）：截图识别（M4-7）与通知解析（M4-8）共用。
 *
 * 落库只发生在"识别成功"时（失败当场报错、可重试——**没有"解析失败"状态**，规格 §二·17）；
 * 入账 = 打开记一笔页预填，保存成功调 [markAccepted] 回填 `txId`。
 *
 * **硬删**：[clearProcessed] 是 `DELETE`，不是软删——本表是候选池、不是账目、不入同步
 * （**全项目唯一一处硬删例外**，依据 D104；别照抄到别的表上）。
 */
@Dao
interface PendingEntryDao {

    /** 「待确认」页的列表（按新→旧）。 */
    @Query("SELECT * FROM pending_entries WHERE deleted = 0 AND status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: PendingStatus = PendingStatus.PENDING): Flow<List<PendingEntryEntity>>

    /** 我的页「自动记账」行的「N 条待确认」副标题。 */
    @Query("SELECT COUNT(*) FROM pending_entries WHERE deleted = 0 AND status = :status")
    fun observeCount(status: PendingStatus = PendingStatus.PENDING): Flow<Int>

    @Query("SELECT * FROM pending_entries WHERE deleted = 0 AND id = :id")
    suspend fun getActive(id: String): PendingEntryEntity?

    @Query("SELECT * FROM pending_entries WHERE deleted = 0 AND kind = :kind AND status = :status ORDER BY createdAt DESC")
    suspend fun listByKind(kind: PendingKind, status: PendingStatus = PendingStatus.PENDING): List<PendingEntryEntity>

    @Upsert
    suspend fun upsert(entry: PendingEntryEntity)

    /** 入账成功：标记已入账并回填流水 id。 */
    @Query("UPDATE pending_entries SET status = 'ACCEPTED', txId = :txId, updatedAt = :now WHERE id = :id")
    suspend fun markAccepted(id: String, txId: String, now: Long)

    /** 丢弃（用户放弃这笔候选）：留在列表里可再记，也能被「清空已处理」清掉。 */
    @Query("UPDATE pending_entries SET status = 'DISCARDED', updatedAt = :now WHERE id = :id")
    suspend fun discard(id: String, now: Long)

    @Query("UPDATE pending_entries SET status = 'DISCARDED', updatedAt = :now WHERE deleted = 0 AND status = 'PENDING'")
    suspend fun discardAllPending(now: Long)

    /**
     * 「清空已处理」：把 `ACCEPTED` / `DISCARDED` 的行**真删掉**（D104——候选池里留着原文没有任何好处，
     * 软删 + 抹字段会让"一键清空"名不副实）。
     */
    @Query("DELETE FROM pending_entries WHERE status IN ('ACCEPTED', 'DISCARDED')")
    suspend fun clearProcessed(): Int

    @Query("SELECT COUNT(*) FROM pending_entries WHERE deleted = 0 AND status = 'PENDING'")
    suspend fun countPending(): Int
}
