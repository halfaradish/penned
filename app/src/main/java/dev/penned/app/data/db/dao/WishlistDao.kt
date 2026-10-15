package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.WishlistItemEntity
import dev.penned.app.domain.WishlistProgress
import dev.penned.app.domain.WishlistStatus
import kotlinx.coroutines.flow.Flow

/** 攒钱目标的列表行：清单项 + 跟随账户的**重算余额**（不跟随则为 null）。 */
data class WishlistRow(
    @androidx.room.Embedded val item: WishlistItemEntity,
    /** 跟随账户的余额（分）；`accountId` 为空或账户被删则为 null → 退回手动 `savedCents`。 */
    val accountBalanceCents: Long?,
    val accountName: String?,
)

/**
 * 愿望清单（M3-15）。**不写任何流水**：攒钱目标是规划工具，不进统计与净资产（规格的明确边界）。
 *
 * 跟随账户的余额走 `observeWithBalance` 那套重算（D004：余额从不落库），
 * 这里用子查询按账户重算一次（口径与 `AccountDao` 同一份 SQL：初始余额 + 收支 ± 转账，手续费记转出方）。
 */
@Dao
interface WishlistDao {

    @Query(
        """
        SELECT w.*,
            (SELECT a.name FROM accounts a WHERE a.id = w.accountId AND a.deleted = 0) AS accountName,
            (SELECT a.initialBalanceCents
                + IFNULL((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amountCents ELSE -t.amountCents END)
                          FROM transactions t
                          WHERE t.deleted = 0 AND t.accountId = a.id AND t.type IN ('INCOME', 'EXPENSE')), 0)
                + IFNULL((SELECT SUM(-(t.amountCents + t.feeCents)) FROM transactions t
                          WHERE t.deleted = 0 AND t.accountId = a.id AND t.type = 'TRANSFER'), 0)
                + IFNULL((SELECT SUM(t.amountCents) FROM transactions t
                          WHERE t.deleted = 0 AND t.targetAccountId = a.id AND t.type = 'TRANSFER'), 0)
             FROM accounts a WHERE a.id = w.accountId AND a.deleted = 0) AS accountBalanceCents
        FROM wishlist_items w
        WHERE w.deleted = 0 AND w.bookId = :bookId
        ORDER BY w.sortOrder, w.createdAt
        """
    )
    fun observeByBook(bookId: String): Flow<List<WishlistRow>>

    @Query("SELECT * FROM wishlist_items WHERE id = :id")
    suspend fun get(id: String): WishlistItemEntity?

    @Query("SELECT * FROM wishlist_items WHERE deleted = 0 AND id = :id")
    suspend fun getActive(id: String): WishlistItemEntity?

    @Upsert
    suspend fun upsert(item: WishlistItemEntity)

    @Query("UPDATE wishlist_items SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE wishlist_items SET deleted = 0, updatedAt = :now WHERE id IN (:ids)")
    suspend fun restoreAll(ids: List<String>, now: Long)

    @Query("UPDATE wishlist_items SET deleted = 1, updatedAt = :now WHERE bookId = :bookId")
    suspend fun softDeleteByBook(bookId: String, now: Long)

    /** 手动存入（可负 = 取出来）：只动 `savedCents`，**不生成流水**。 */
    @Query("UPDATE wishlist_items SET savedCents = savedCents + :deltaCents, updatedAt = :now WHERE id = :id")
    suspend fun addSaved(id: String, deltaCents: Long, now: Long)

    @Query("UPDATE wishlist_items SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: WishlistStatus, now: Long)

    @Query("SELECT COUNT(*) FROM wishlist_items WHERE deleted = 0 AND bookId = :bookId")
    suspend fun countInBook(bookId: String): Int

    @Query("SELECT IFNULL(MAX(sortOrder), -1) + 1 FROM wishlist_items WHERE bookId = :bookId")
    suspend fun nextSortOrder(bookId: String): Int
}

/** 投影 → 领域模型（进度/剩余/达成都由 `WishlistMath` 算，UI 不自己算）。 */
fun WishlistRow.toProgress(): WishlistProgress {
    val followed = item.accountId?.let { accountBalanceCents }
    return WishlistProgress(
        id = item.id,
        name = item.name,
        targetCents = item.targetCents,
        savedCents = dev.penned.app.domain.WishlistMath.savedCents(item.savedCents, followed),
        dueEpochDay = item.dueEpochDay,
        status = item.status,
        accountName = item.accountId?.let { accountName },
    )
}
