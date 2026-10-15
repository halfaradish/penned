package dev.penned.app.domain

import androidx.compose.runtime.Immutable

/**
 * 攒钱目标的进度口径（M3-15，纯函数 + 表驱动测试在 `WishlistMathTest`）。
 *
 * - **已存**：手动模式看 `savedCents`；跟随账户模式看该账户的**重算余额**（D004：余额从不落库）
 * - **进度**：`saved / target`，夹在 0…1（超目标也只画满格）；目标为 0 时进度 0（没目标可言）
 * - **剩余**：`target − saved`，**超了给负数**（"还差 −100"读着别扭，所以超出的部分单独用 [overCents] 表达，
 *   文案层自己决定说"还差 X"还是"超出 X"）
 * - **达成**：`saved >= target`（金额够了）**或**用户手动标了 DONE（两回事，都算达成）
 */
object WishlistMath {

    /** 已存金额（分）：跟随账户时用账户余额，否则用手动值。 */
    fun savedCents(manualSavedCents: Long, followedAccountBalanceCents: Long?): Long =
        followedAccountBalanceCents ?: manualSavedCents

    /** 进度（0…1）：目标 ≤ 0 或已存 ≤ 0 时给 0。 */
    fun progress(savedCents: Long, targetCents: Long): Float {
        if (targetCents <= 0L || savedCents <= 0L) return 0f
        return (savedCents.toFloat() / targetCents).coerceIn(0f, 1f)
    }

    /** 还差多少（分）：够了给 0（超出的部分看 [overCents]）。 */
    fun remainingCents(savedCents: Long, targetCents: Long): Long =
        (targetCents - savedCents).coerceAtLeast(0L)

    /** 超出目标多少（分）：没超给 0。 */
    fun overCents(savedCents: Long, targetCents: Long): Long =
        (savedCents - targetCents).coerceAtLeast(0L)

    /** 金额上是否够了。 */
    fun reachedByAmount(savedCents: Long, targetCents: Long): Boolean =
        targetCents > 0L && savedCents >= targetCents

    /** 达成（金额够 或 手动标记 DONE）。 */
    fun achieved(savedCents: Long, targetCents: Long, status: WishlistStatus): Boolean =
        status == WishlistStatus.DONE || reachedByAmount(savedCents, targetCents)

    /** 列表行的两数文案：`已存 300.00 / 目标 1,000.00`。 */
    fun amountsLabel(savedCents: Long, targetCents: Long): String =
        "已存 ${Money.format(savedCents)} / 目标 ${Money.format(targetCents)}"

    /**
     * 列表行的状态文案（按优先级）：
     * 达成 → `已达成`；超目标但没标记 → `已达成`（金额够了就是够了）；否则 `还差 X`；有截止日再加 `· 截至 M月D日`。
     */
    fun statusLabel(
        savedCents: Long,
        targetCents: Long,
        status: WishlistStatus,
        dueEpochDay: Long?,
        today: java.time.LocalDate,
    ): String {
        val base = if (achieved(savedCents, targetCents, status)) {
            "已达成"
        } else {
            "还差 ${Money.format(remainingCents(savedCents, targetCents))}"
        }
        if (dueEpochDay == null || achieved(savedCents, targetCents, status)) return base
        return "$base · 截至 ${DebtText.dueLabel(dueEpochDay, today).removePrefix("未设到期")}"
    }
}

/** 一条攒钱目标（带"跟随账户"的余额投影，DAO 查出来就有）。 */
@Immutable
data class WishlistProgress(
    val id: String,
    val name: String,
    val targetCents: Long,
    val savedCents: Long,
    val dueEpochDay: Long?,
    val status: WishlistStatus,
    /** 跟随的账户名（null = 手动存）。 */
    val accountName: String? = null,
) {
    val progress: Float get() = WishlistMath.progress(savedCents, targetCents)
    val remainingCents: Long get() = WishlistMath.remainingCents(savedCents, targetCents)
    val achieved: Boolean get() = WishlistMath.achieved(savedCents, targetCents, status)
}
