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
    /** 备注（选填）。D027：只作个人备忘，不参与任何计算。 */
    val remark: String? = null,
    /**
     * 生效账本（D027/D028）：true = 每个账本都能看到该账户（含以后新建的）；
     * false（默认，也是迁移前所有老账户的行为）= 只在 [bookId] 所属账本 + [AccountBookRefEntity] 里勾选的账本可见。
     */
    val scopeAllBooks: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)

/**
 * 账户-生效账本多对多（D028）。
 *
 * 只在 `AccountEntity.scopeAllBooks = false` 时有意义：每行表示"该账户也在这个账本里可见"。
 * 所属账本（`AccountEntity.bookId`）恒定可见、不进这张表。
 */
@Entity(
    tableName = "account_books",
    primaryKeys = ["accountId", "bookId"],
    indices = [Index("bookId")],
)
data class AccountBookRefEntity(
    val accountId: String,
    val bookId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)
