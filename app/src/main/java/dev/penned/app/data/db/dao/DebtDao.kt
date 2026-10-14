package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.DebtEntity
import dev.penned.app.domain.DebtBalance
import dev.penned.app.domain.DebtDirection
import kotlinx.coroutines.flow.Flow

/**
 * 债务列表行：债务 + 已归还（名下"清偿方向"的流水合计，D068/D077）。
 * 「剩余」= 本金 − 已归还，是纯函数 [dev.penned.app.domain.DebtMath.remainingCents] 的事，
 * 这里只把两个原料取出来（[toBalance] 交给域层算）。
 */
data class DebtWithRepaid(
    val id: String,
    val direction: DebtDirection,
    val counterparty: String,
    val principalCents: Long,
    val startEpochDay: Long,
    val dueEpochDay: Long?,
    val note: String?,
    val closed: Boolean,
    val sortOrder: Int,
    val repaidCents: Long,
) {
    fun toBalance(): DebtBalance = DebtBalance(direction, principalCents, repaidCents, closed)

    val remainingCents: Long get() = toBalance().remainingCents
}

@Dao
interface DebtDao {

    /**
     * 本账本债务（含已归还），列表页与资产页汇总共用。
     *
     * 已归还的口径（与 [dev.penned.app.domain.DebtMath.settleType] 是**一对孪生实现**，改一处必须改另一处）：
     * 名下流水中**清偿方向**的那些——BORROW 认支出（我还款）、LEND 认收入（对方还我）；
     * 借入/借出那一笔（钱到手/出手）不算归还。软删流水不计。
     */
    @Query(
        """
        SELECT d.id AS id, d.direction AS direction, d.counterparty AS counterparty,
               d.principalCents AS principalCents, d.startEpochDay AS startEpochDay,
               d.dueEpochDay AS dueEpochDay, d.note AS note, d.closed AS closed, d.sortOrder AS sortOrder,
               IFNULL(SUM(CASE WHEN t.type = (CASE WHEN d.direction = 'BORROW' THEN 'EXPENSE' ELSE 'INCOME' END)
                               THEN t.amountCents ELSE 0 END), 0) AS repaidCents
        FROM debts d
        LEFT JOIN transactions t ON t.debtId = d.id AND t.deleted = 0
        WHERE d.deleted = 0 AND d.bookId = :bookId
        GROUP BY d.id
        ORDER BY d.sortOrder, d.createdAt
        """
    )
    fun observeByBook(bookId: String): Flow<List<DebtWithRepaid>>

    /** 单笔债务（详情页）；口径同上。 */
    @Query(
        """
        SELECT d.id AS id, d.direction AS direction, d.counterparty AS counterparty,
               d.principalCents AS principalCents, d.startEpochDay AS startEpochDay,
               d.dueEpochDay AS dueEpochDay, d.note AS note, d.closed AS closed, d.sortOrder AS sortOrder,
               IFNULL(SUM(CASE WHEN t.type = (CASE WHEN d.direction = 'BORROW' THEN 'EXPENSE' ELSE 'INCOME' END)
                               THEN t.amountCents ELSE 0 END), 0) AS repaidCents
        FROM debts d
        LEFT JOIN transactions t ON t.debtId = d.id AND t.deleted = 0
        WHERE d.deleted = 0 AND d.id = :debtId
        GROUP BY d.id
        """
    )
    fun observeById(debtId: String): Flow<DebtWithRepaid?>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun get(id: String): DebtEntity?

    @Upsert
    suspend fun upsert(debt: DebtEntity)

    @Query("UPDATE debts SET closed = :closed, updatedAt = :now WHERE id = :id")
    suspend fun setClosed(id: String, closed: Boolean, now: Long)

    /** 软删债务：名下的资金流水**保留**（钱确实动过），只是债务本身从列表与应付/应收里消失。 */
    @Query("UPDATE debts SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT COUNT(*) FROM debts")
    suspend fun count(): Int
}
