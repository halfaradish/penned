package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.BillFilterEntity
import kotlinx.coroutines.flow.Flow

/**
 * 账单组合（M3-8；表 M3-0 就建好）。
 * 条件的编解码在 `BillFilterCodec`（多值列逗号分隔，空列表 ↔ null）；这里只管存取与重命名。
 * **软删组合不碰任何流水**（组合只是筛选条件的存档）。
 */
@Dao
interface BillFilterDao {

    @Query("SELECT * FROM bill_filters WHERE deleted = 0 AND bookId = :bookId ORDER BY sortOrder, createdAt")
    fun observeByBook(bookId: String): Flow<List<BillFilterEntity>>

    @Query("SELECT * FROM bill_filters WHERE id = :id")
    suspend fun get(id: String): BillFilterEntity?

    @Upsert
    suspend fun upsert(filter: BillFilterEntity)

    @Query("UPDATE bill_filters SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE bill_filters SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    /** 同账本内是否已有同名组合（新建/重命名时的重名提示）。 */
    @Query("SELECT COUNT(*) FROM bill_filters WHERE deleted = 0 AND bookId = :bookId AND name = :name AND id != :excludeId")
    suspend fun nameTaken(bookId: String, name: String, excludeId: String): Int

    @Query("SELECT COUNT(*) FROM bill_filters WHERE deleted = 0 AND bookId = :bookId")
    suspend fun countInBook(bookId: String): Int
}
