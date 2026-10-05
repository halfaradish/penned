package dev.penned.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 预算：categoryId 为空 = 总预算。M1 第二批接 UI，本批仅建表。 */
@Entity(tableName = "budgets", indices = [Index("bookId"), Index("categoryId")])
data class BudgetEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    /** 空 = 总预算。 */
    val categoryId: String? = null,
    val amountCents: Long,
    /** 周期类型，当前仅 "MONTH"。 */
    val periodType: String = "MONTH",
    /** 周期锚点（当月 1 日的 epochDay）。 */
    val startEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)

/** 导入批次：记录每次导入的来源文件与数量，供"已导入管理"查看/整体删除。 */
@Entity(tableName = "import_batches", indices = [Index("bookId")])
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val fileName: String,
    val count: Int,
    val importedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)
