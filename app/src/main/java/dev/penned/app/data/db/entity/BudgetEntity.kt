package dev.penned.app.data.db.entity

import androidx.room.ColumnInfo
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
    /**
     * 批次来源（M4-0）：YIMU | PENNED_CSV | WECHAT | ALIPAY | GENERIC。
     * **旧批次默认全是 YIMU**（迁移前只有"一木 xls"这一条批量导入路径），语义不变。
     */
    @ColumnInfo(defaultValue = "YIMU") val sourceKind: String = SOURCE_YIMU,
    /**
     * 跳过的行数（M4-0 / 规格 §二·16）：交易关闭 / 已退款 / 不计收支 / 坏行——预览页分列计数，
     * 落库只存总数（分列口径见 `BillRowCounts`，预览页当场展示）。
     */
    @ColumnInfo(defaultValue = "0") val skippedCount: Int = 0,
    /** 判定为重复而跳过的行数（M4-0：单号命中 / 疑似重复 / 文件内自身重复）。 */
    @ColumnInfo(defaultValue = "0") val duplicateCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
) {
    companion object {
        const val SOURCE_YIMU = "YIMU"
        const val SOURCE_PENNED_CSV = "PENNED_CSV"
        const val SOURCE_WECHAT = "WECHAT"
        const val SOURCE_ALIPAY = "ALIPAY"
        const val SOURCE_GENERIC = "GENERIC"

        /** 截图识别一批（账单列表导入，D111）——`sourceKind` 是普通 TEXT 列，加值不改 schema。 */
        const val SOURCE_SCREENSHOT = "SCREENSHOT"
    }
}
