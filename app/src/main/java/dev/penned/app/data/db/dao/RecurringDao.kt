package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.RecurringRuleEntity
import dev.penned.app.domain.RecurringPeriod
import dev.penned.app.domain.TxType
import kotlinx.coroutines.flow.Flow

/**
 * 周期规则列表行（M3-6）：规则 + 分类/账户名（行副标题与「下次 M月D日」用）。
 */
data class RuleWithSummary(
    val id: String,
    val name: String,
    val type: TxType,
    val amountCents: Long,
    val categoryId: String?,
    val accountId: String?,
    val targetAccountId: String?,
    val note: String?,
    val feeCents: Long,
    val periodType: RecurringPeriod,
    val interval: Int,
    val startEpochDay: Long,
    val nextEpochDay: Long,
    val endEpochDay: Long?,
    val enabled: Boolean,
    val categoryName: String?,
    val accountName: String?,
    val targetAccountName: String?,
)

/**
 * 周期记账规则（M3-6；表 M3-0 就建好）。
 *
 * 生成策略 = **打开即补**（D070）：启动时把 `nextEpochDay` 及以前、未生成的期数补成流水
 * （带 `transactions.recurringRuleId`），不预生成未来流水、不引后台任务。
 * 幂等靠 `(recurringRuleId, dateEpochMinute)` 去重（[TransactionDao.minutesForRule] + [advance]）。
 * **删规则 = 软删规则，已生成的流水保留**（钱确实记过）。
 */
@Dao
interface RecurringDao {

    @Query(
        """
        SELECT r.id AS id, r.name AS name, r.type AS type, r.amountCents AS amountCents,
               r.categoryId AS categoryId, r.accountId AS accountId, r.targetAccountId AS targetAccountId,
               r.note AS note, r.feeCents AS feeCents,
               r.periodType AS periodType, r.interval AS interval,
               r.startEpochDay AS startEpochDay, r.nextEpochDay AS nextEpochDay, r.endEpochDay AS endEpochDay,
               r.enabled AS enabled,
               c.name AS categoryName, a.name AS accountName, ta.name AS targetAccountName
        FROM recurring_rules r
        LEFT JOIN categories c ON c.id = r.categoryId
        LEFT JOIN accounts a ON a.id = r.accountId
        LEFT JOIN accounts ta ON ta.id = r.targetAccountId
        WHERE r.deleted = 0 AND r.bookId = :bookId
        ORDER BY r.createdAt
        """
    )
    fun observeByBook(bookId: String): Flow<List<RuleWithSummary>>

    /** 全部启用中的规则（跨账本）：启动补记扫的就是这一份。 */
    @Query("SELECT * FROM recurring_rules WHERE deleted = 0 AND enabled = 1")
    suspend fun enabledAll(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun get(id: String): RecurringRuleEntity?

    @Upsert
    suspend fun upsert(rule: RecurringRuleEntity)

    @Query("UPDATE recurring_rules SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE recurring_rules SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    /** 补记后推进下一期（`lastGeneratedAt` 记下这一次补记的时刻）。 */
    @Query("UPDATE recurring_rules SET nextEpochDay = :nextEpochDay, lastGeneratedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun advance(id: String, nextEpochDay: Long, now: Long)

    @Query("SELECT COUNT(*) FROM recurring_rules WHERE deleted = 0 AND bookId = :bookId")
    suspend fun countInBook(bookId: String): Int
}
