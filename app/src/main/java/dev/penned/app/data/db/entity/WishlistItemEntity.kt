package dev.penned.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.penned.app.domain.WishlistStatus

/**
 * 愿望清单（M3-15，D072 裁决 = **攒钱目标**）：名称 + 目标金额 + 已存金额 + 可选截止日。
 *
 * **它是规划工具，不生成流水**：不写 `transactions`、不进统计与净资产。
 * 「已存」两种口径（`WishlistMath.savedCents`）：
 * - `accountId == null`：手动 ± （`savedCents` 是唯一真相）
 * - `accountId != null`：**跟随该账户余额重算**（`savedCents` 留着当"上次手动存的值"，不参与显示）
 *
 * 本表由 `MIGRATION_7_8` 建（M3 唯一一次迁移）。
 */
@Entity(tableName = "wishlist_items", indices = [Index("bookId"), Index("accountId")])
data class WishlistItemEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val name: String,
    /** 目标金额（分）。 */
    val targetCents: Long,
    /** 手动存入的金额（分）；跟随账户时这个值不参与显示（见类注释）。 */
    val savedCents: Long = 0,
    /** 开始日（可空，选填）。 */
    val startEpochDay: Long? = null,
    /** 截止日（可空 = 没定什么时候攒够）。 */
    val dueEpochDay: Long? = null,
    /** 跟随哪个账户的余额（可空 = 手动存）。 */
    val accountId: String? = null,
    val note: String? = null,
    /** ACTIVE / DONE（DONE = 用户手动标记达成，与"金额够了"不是一回事）。 */
    val status: WishlistStatus = WishlistStatus.ACTIVE,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)
