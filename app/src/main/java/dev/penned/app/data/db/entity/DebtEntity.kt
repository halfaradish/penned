package dev.penned.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.penned.app.domain.DebtDirection

/**
 * 债务（D068：实体式）。对方 / 本金 / 到期日 / 借还记录都是债务自己的字段，
 * 借入与归还的资金侧靠 `transactions.debtId` 关联的资金流水表达。
 *
 * 口径（纯函数重算，D004）：「剩余」= 本金 − 已归还（该债务名下的资金流水合计）；
 * 净资产 = Σ账户余额 − 应付（BORROW 未结清） + 应收（LEND 未结清）。
 * 债务生成的资金流水默认 `excludeFromStats = true`（借入不是收入、还款不是支出）。
 */
@Entity(tableName = "debts", indices = [Index("bookId")])
data class DebtEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val direction: DebtDirection,
    /** 对方名称（借给谁 / 向谁借）。 */
    val counterparty: String,
    val principalCents: Long,
    val startEpochDay: Long,
    val dueEpochDay: Long? = null,
    val note: String? = null,
    val closed: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)
