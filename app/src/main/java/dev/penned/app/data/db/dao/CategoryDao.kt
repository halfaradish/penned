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

    @Query("UPDATE categories SET deleted = 1, updatedAt = :now WHERE bookId = :bookId")
    suspend fun softDeleteByBook(bookId: String, now: Long)
}
