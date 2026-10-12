package dev.penned.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.penned.app.domain.TxType

/**
 * 账单组合（M3-8）：把筛选页的一组条件存下来复用。
 *
 * 条件列一次性建齐（M3-7 的筛选维度就是这些），编解码是 M3-8 的纯函数：
 * 多值列（[categoryIds]/[accountIds]/[tagIds]）存逗号分隔的 id；`null` = 该条件不限，
 * 空串与非空串的语义由编解码函数定义并写进表驱动测试。
 * 三态开关（[excludeFromStats]/[excludeFromBudget]/[includeTransfer]）：`null` = 不限、true/false = 明确要求。
 */
@Entity(tableName = "bill_filters", indices = [Index("bookId")])
data class BillFilterEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val name: String,
    /** 关键词（备注/分类名/金额，与现有搜索同口径）。 */
    val keyword: String? = null,
    /** null = 支出/收入/转账全部。 */
    val type: TxType? = null,
    val fromEpochDay: Long? = null,
    val toEpochDay: Long? = null,
    val categoryIds: String? = null,
    val accountIds: String? = null,
    val tagIds: String? = null,
    val amountMinCents: Long? = null,
    val amountMaxCents: Long? = null,
    val excludeFromStats: Boolean? = null,
    val excludeFromBudget: Boolean? = null,
    /** null = 不限；true = 结果含转账、false = 排除转账。 */
    val includeTransfer: Boolean? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)
