package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deleted = 0 AND bookId = :bookId ORDER BY sortOrder")
    fun observeByBook(bookId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deleted = 0 AND bookId = :bookId ORDER BY sortOrder")
    suspend fun allInBook(bookId: String): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("UPDATE categories SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    /** 隐藏/取消隐藏（M3-1）：只影响记账宫格与筛选里的可见性，历史流水与统计口径不变。 */
    @Query("UPDATE categories SET hidden = :hidden, updatedAt = :now WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, now: Long)

    @Query("UPDATE categories SET deleted = 1, updatedAt = :now WHERE bookId = :bookId")
    suspend fun softDeleteByBook(bookId: String, now: Long)
}
