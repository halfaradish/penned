package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.AccountBookRefEntity
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
    val remark: String? = null,
)

@Dao
interface AccountDao {
    /**
     * 资产页账户与余额（D004/D027/D028）。
     *
     * 可见性：所属账本 / 生效账本为"全部账本" / 在 account_books 里勾选了当前账本。
     * 余额：恒等于「初始余额 + 该账户名下全部流水」——账户是同一个钱包，余额不随可见范围开关变化
     * （未共享的账户流水本来只在本账本，行为与老版本一致）。
     */
    @Query(
        """
        SELECT a.id AS id, a.name AS name, a.type AS type, a.groupName AS groupName, a.remark AS remark,
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
        WHERE a.deleted = 0 AND (
            a.bookId = :bookId OR a.scopeAllBooks = 1
            OR EXISTS (SELECT 1 FROM account_books r
                       WHERE r.accountId = a.id AND r.bookId = :bookId AND r.deleted = 0)
        )
        ORDER BY a.groupName, a.name
        """
    )
    fun observeWithBalance(bookId: String): Flow<List<AccountWithBalance>>

    @Query(
        """
        SELECT * FROM accounts WHERE deleted = 0 AND (
            bookId = :bookId OR scopeAllBooks = 1
            OR EXISTS (SELECT 1 FROM account_books r
                       WHERE r.accountId = accounts.id AND r.bookId = :bookId AND r.deleted = 0)
        )
        ORDER BY groupName, name
        """
    )
    fun observeByBook(bookId: String): Flow<List<AccountEntity>>

    @Query(
        """
        SELECT * FROM accounts WHERE deleted = 0 AND (
            bookId = :bookId OR scopeAllBooks = 1
            OR EXISTS (SELECT 1 FROM account_books r
                       WHERE r.accountId = accounts.id AND r.bookId = :bookId AND r.deleted = 0)
        )
        ORDER BY groupName, name
        """
    )
    suspend fun allInBook(bookId: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): AccountEntity?

    @Upsert
    suspend fun upsert(account: AccountEntity)

    /** 单个账户软删（资产页左滑删除）。有流水的账户由 ViewModel 先拦下，不会走到这里。 */
    @Query("UPDATE accounts SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    /** 反软删：左滑删除后 Snackbar「撤销」用（同一批 id 原样恢复）。 */
    @Query("UPDATE accounts SET deleted = 0, updatedAt = :now WHERE id IN (:ids)")
    suspend fun restoreAll(ids: List<String>, now: Long)

    /**
     * 账本软删级联到账户；**生效账本为"全部账本"的共享账户不动**（它在别的账本还在用，
     * 删掉会让那些账本的流水外键悬空）。
     */
    @Query("UPDATE accounts SET deleted = 1, updatedAt = :now WHERE bookId = :bookId AND scopeAllBooks = 0")
    suspend fun softDeleteByBook(bookId: String, now: Long)

    // —— 生效账本多对多（D028）——

    /** 该账户额外可见的账本（不含所属账本）。 */
    @Query("SELECT bookId FROM account_books WHERE accountId = :accountId AND deleted = 0")
    suspend fun bookIdsFor(accountId: String): List<String>

    @Upsert
    suspend fun upsertRefs(refs: List<AccountBookRefEntity>)

    /** 保存生效账本前先整批软删，再 upsert 勾选的那些（复合主键 upsert 会覆盖成 deleted = 0）。 */
    @Query("UPDATE account_books SET deleted = 1, updatedAt = :now WHERE accountId = :accountId")
    suspend fun clearRefs(accountId: String, now: Long)

    /** 账本删除时级联软删指向它的引用。 */
    @Query("UPDATE account_books SET deleted = 1, updatedAt = :now WHERE bookId = :bookId")
    suspend fun softDeleteRefsByBook(bookId: String, now: Long)
}
