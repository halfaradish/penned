package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.domain.AccountType
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE deleted = 0 ORDER BY sortOrder")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(book: BookEntity)

    /** 账本软删（M1 S3）；其下数据由 BooksViewModel 在同一事务里级联软删。 */
    @Query("UPDATE books SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

/** 列表行：账户 + 重算余额（D004：初始余额 + 收支 ± 转账，手续费记转出方）。 */
data class AccountWithBalance(
    val id: String,
    val name: String,
    val type: AccountType,
    val groupName: String?,
    val balanceCents: Long,
)

@Dao
interface AccountDao {
    @Query(
        """
        SELECT a.id AS id, a.name AS name, a.type AS type, a.groupName AS groupName,
            a.initialBalanceCents
            + IFNULL((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amountCents ELSE -t.amountCents END)
                      FROM transactions t
                      WHERE t.deleted = 0 AND t.accountId = a.id
                        AND t.type IN ('INCOME', 'EXPENSE')), 0)
            + IFNULL((SELECT SUM(-(t.amountCents + t.feeCents)) FROM transactions t
                      WHERE t.deleted = 0 AND t.accountId = a.id AND t.type = 'TRANSFER'), 0)
            + IFNULL((SELECT SUM(t.amountCents) FROM transactions t
                      WHERE t.deleted = 0 AND t.targetAccountId = a.id AND t.type = 'TRANSFER'), 0)
            AS balanceCents
        FROM accounts a
        WHERE a.deleted = 0 AND a.bookId = :bookId
        ORDER BY a.groupName, a.name
        """
    )
    fun observeWithBalance(bookId: String): Flow<List<AccountWithBalance>>

    @Query("SELECT * FROM accounts WHERE deleted = 0 AND bookId = :bookId ORDER BY groupName, name")
    fun observeByBook(bookId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deleted = 0 AND bookId = :bookId ORDER BY groupName, name")
    suspend fun allInBook(bookId: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): AccountEntity?

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("UPDATE accounts SET deleted = 1, updatedAt = :now WHERE bookId = :bookId")
    suspend fun softDeleteByBook(bookId: String, now: Long)
}
