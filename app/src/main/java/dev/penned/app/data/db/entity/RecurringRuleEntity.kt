package dev.penned.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.penned.app.domain.RecurringPeriod
import dev.penned.app.domain.TxType

/**
 * 周期记账规则（M3-6）。生成策略 = **打开即补**（D070）：`nextEpochDay` 及以前的期数在 App 启动时补生成，
 * 不预生成未来流水。生成的流水带 `transactions.recurringRuleId`，同规则同期幂等去重。
 */
@Entity(tableName = "recurring_rules", indices = [Index("bookId")])
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val name: String,
    val type: TxType,
    val amountCents: Long = 0,
    val categoryId: String? = null,
    val accountId: String? = null,
    val targetAccountId: String? = null,
    val note: String? = null,
    val feeCents: Long = 0,
    val periodType: RecurringPeriod = RecurringPeriod.MONTHLY,
    /** 每几个周期一次（1 = 每期）。 */
    val interval: Int = 1,
    /** 起始日（epochDay，本地墙钟的日期）。 */
    val startEpochDay: Long,
    /** 下一期待生成日（epochDay）；月对齐规则：29/30/31 日遇短月取当月最后一天（M3-6 纯函数）。 */
    val nextEpochDay: Long,
    val endEpochDay: Long? = null,
    val enabled: Boolean = true,
    /** 最近一次补生成的时刻（epochMillis）；没补过为 null。 */
    val lastGeneratedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)
