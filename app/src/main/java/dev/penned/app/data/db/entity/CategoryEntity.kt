package dev.penned.app.data.db.entity

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
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)
