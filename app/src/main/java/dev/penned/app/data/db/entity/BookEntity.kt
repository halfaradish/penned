package dev.penned.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.penned.app.domain.AccountType

/** 账本。D003：全表 uuid 主键 + 软删 + 时间戳 + 设备来源，为 op-log 同步预留。 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)

/** 账户。D005/D006：流水对账户可空引用；账户归属账本。余额 = initial + 流水重算（D004），不落库。 */
@Entity(tableName = "accounts", indices = [Index("bookId")])
data class AccountEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val name: String,
    val type: AccountType,
    val groupName: String? = null,
    val initialBalanceCents: Long = 0,
    val currencyCode: String = "CNY",
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)
