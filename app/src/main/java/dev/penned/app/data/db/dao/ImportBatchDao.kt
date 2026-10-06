package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.ImportBatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportBatchDao {
    @Query("SELECT * FROM import_batches WHERE deleted = 0 ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batches WHERE id = :id")
    suspend fun get(id: String): ImportBatchEntity?

    @Upsert
    suspend fun upsert(batch: ImportBatchEntity)

    @Query("UPDATE import_batches SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE import_batches SET deleted = 1, updatedAt = :now WHERE bookId = :bookId")
    suspend fun softDeleteByBook(bookId: String, now: Long)
}
