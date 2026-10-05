package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE deleted = 0 AND bookId = :bookId")
    fun observeByBook(bookId: String): Flow<List<BudgetEntity>>

    /** 同一账本同一分类（含 null=总预算）只允许一条预算。 */
    @Query("SELECT * FROM budgets WHERE deleted = 0 AND bookId = :bookId AND categoryId IS :categoryId LIMIT 1")
    suspend fun getByCategory(bookId: String, categoryId: String?): BudgetEntity?

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Query("UPDATE budgets SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
