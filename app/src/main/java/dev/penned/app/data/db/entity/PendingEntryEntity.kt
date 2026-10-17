package dev.penned.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.penned.app.domain.PendingKind
import dev.penned.app.domain.PendingStatus
import dev.penned.app.domain.PendingType

/**
 * 机器识别候选池（M4-0，规格 M4 §三 M4-0 / §二·17）：截图识别与通知解析的**非交互管道**落点。
 *
 * 落库的唯一理由是"**杀进程不丢**"：识别成功才落（失败当场报错、可重试，**没有"解析失败"状态**），
 * 入账 = 打开记一笔页预填（`source` = SCREENSHOT / NOTIFICATION），保存成功标 `ACCEPTED` 并回填 `txId`。
 *
 * 两条口径（D104）：①`amountCents` 与 `occurredAtEpochMinute` **可空**——解析不出就是空，**不猜**；
 * ②「清空已处理」= **硬删**（本表是候选池、不是账目、不入同步——全项目唯一一处硬删例外）。
 *
 * 本表由 `MIGRATION_8_9` 建。
 */
@Entity(tableName = "pending_entries", indices = [Index("status")])
data class PendingEntryEntity(
    @PrimaryKey val id: String,
    /** 来源管道：截图 / 通知。 */
    val kind: PendingKind,
    /** 通知来源 App 的包名（截图识别为 null）。 */
    val sourceApp: String? = null,
    /**
     * 通知正文 / 截图里读出的原文（截图侧留识别输入的一段摘要）。
     * 已入账后**保留**（供回看"机器当时读到了什么"），但**不做"用户改过金额/日期后与原文不一致"的留痕**（D104）。
     */
    val rawText: String? = null,
    /** 收支方向；UNKNOWN = 没识别出来（不猜）。 */
    val type: PendingType = PendingType.UNKNOWN,
    /** 金额（分）；识别不出为 null。 */
    val amountCents: Long? = null,
    /** 发生时间（本地墙钟分钟，D002）；识别不出为 null。 */
    val occurredAtEpochMinute: Long? = null,
    /** 商家/交易对方。 */
    val merchant: String? = null,
    /** 备注（预填记一笔页时进备注栏）。 */
    val note: String? = null,
    /** 关键词规则命中的分类（预填用；分类永远本地映射）。 */
    val categoryId: String? = null,
    val status: PendingStatus = PendingStatus.PENDING,
    /** 入账后的流水 id（`ACCEPTED` 时回填）。 */
    val txId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)
