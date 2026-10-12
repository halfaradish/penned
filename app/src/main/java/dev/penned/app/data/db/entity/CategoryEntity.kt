package dev.penned.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.penned.app.domain.CategoryType

/** 分类树：parentId 自引用，收/支两域（D009）。二级可空挂空父。 */
@Entity(tableName = "categories", indices = [Index("bookId"), Index("parentId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val parentId: String? = null,
    val name: String,
    val type: CategoryType,
    val sortOrder: Int = 0,
    val iconKey: String? = null,
    /**
     * 隐藏（M3-1，D074）：不出现在记账宫格与筛选里，**已记账的流水仍照常显示**。
     * 与软删 [deleted] 的区别：隐藏只是"不再用"，历史数据与统计口径一律不变。
     */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)
