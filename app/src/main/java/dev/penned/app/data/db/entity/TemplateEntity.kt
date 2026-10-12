package dev.penned.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.penned.app.domain.TxType

/**
 * 记账模板（M3-5）。字段照记一笔的表单；**用模板 = 进记一笔页预填**，确认后才入库（D068 同族的"模板不是流水"）。
 * 转账模板的两端 = [accountId]（转出）+ [targetAccountId]（转入）。
 */
@Entity(tableName = "tx_templates", indices = [Index("bookId")])
data class TxTemplateEntity(
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
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)

/** 模板-标签多对多（M3-5）。照 `transaction_tags` 的先例：连接表不带同步字段。 */
@Entity(tableName = "template_tags", primaryKeys = ["templateId", "tagId"])
data class TemplateTagRefEntity(
    val templateId: String,
    val tagId: String,
)
