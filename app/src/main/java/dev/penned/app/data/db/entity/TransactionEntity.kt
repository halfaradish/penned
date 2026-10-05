package dev.penned.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType

/**
 * 流水，单表多态（D007）：支出/收入/转账共用；金额恒为正，符号由 type 表达（D001）。
 * 退款/优惠为流水属性（D007）；报销为状态机字段（D008）。
 */
@Entity(
    tableName = "transactions",
    indices = [Index("bookId"), Index("dateEpochMinute"), Index("categoryId"), Index("accountId")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val type: TxType,
    val amountCents: Long,
    /** 可空（D005：支持纯现金无账户记账）。 */
    val accountId: String? = null,
    /** 转账收款账户。 */
    val targetAccountId: String? = null,
    val categoryId: String? = null,
    /** 本地墙钟，分钟精度（D002）。 */
    val dateEpochMinute: Long,
    val note: String? = null,
    val refundCents: Long = 0,
    val discountCents: Long = 0,
    val reimburseState: ReimburseState = ReimburseState.NONE,
    val reimburseAccountId: String? = null,
    val currencyCode: String? = null,
    val rate: Double? = null,
    val address: String? = null,
    /** 转账手续费（D022），记在转出方。 */
    @ColumnInfo(defaultValue = "0") val feeCents: Long = 0,
    /** 不计入收支（D020）。 */
    @ColumnInfo(defaultValue = "0") val excludeFromStats: Boolean = false,
    /** 不计入预算（D020）。 */
    @ColumnInfo(defaultValue = "0") val excludeFromBudget: Boolean = false,
    /** 记录方式（D021）。 */
    @ColumnInfo(defaultValue = "MANUAL") val source: TxSource = TxSource.MANUAL,
    val creatorDeviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)

/** 两级标签（M3 启用 UI）。 */
@Entity(tableName = "tags", indices = [Index("bookId"), Index("parentId")])
data class TagEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val parentId: String? = null,
    val name: String,
    val sortOrder: Int = 0,
    val hidden: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)

/** 流水-标签多对多。 */
@Entity(tableName = "transaction_tags", primaryKeys = ["transactionId", "tagId"])
data class TransactionTagRefEntity(
    val transactionId: String,
    val tagId: String,
)

/** 流水附件，上限 5 张由逻辑保证（一木导出反分析）。M5 启用。 */
@Entity(tableName = "attachments", indices = [Index("transactionId")])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val orderIndex: Int,
    val uri: String,
    val sizeBytes: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)
