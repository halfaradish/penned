package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE deleted = 0 ORDER BY sortOrder")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(book: BookEntity)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE deleted = 0 AND bookId = :bookId ORDER BY groupName, name")
    fun observeByBook(bookId: String): Flow<List<AccountEntity>>

    @Upsert
    suspend fun upsert(account: AccountEntity)
}
