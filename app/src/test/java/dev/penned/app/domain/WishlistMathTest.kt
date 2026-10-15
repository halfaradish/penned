package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 攒钱目标的口径（M3-15）表驱动：已存两态（手动 / 跟随账户）、进度夹取、剩余与超出、达成两条路。
 */
class WishlistMathTest {

    private val today = LocalDate.of(2026, 10, 15)

    @Test
    fun `已存：跟随账户时用账户余额，否则用手动值`() {
        assertEquals(320_000L, WishlistMath.savedCents(manualSavedCents = 320_000L, followedAccountBalanceCents = null))
        assertEquals(450_000L, WishlistMath.savedCents(320_000L, followedAccountBalanceCents = 450_000L))
        // 跟随的账户余额可以是负的（信用卡类账户）：照实显示，不 clamp
        assertEquals(-12_000L, WishlistMath.savedCents(320_000L, -12_000L))
    }

    @Test
    fun `进度：夹在 0…1，目标为 0 或已存为 0 时给 0`() {
        assertEquals(0.4f, WishlistMath.progress(400_000L, 1_000_000L), 0.0001f)
        assertEquals(1f, WishlistMath.progress(1_200_000L, 1_000_000L), 0.0001f)   // 超目标只画满格
        assertEquals(0f, WishlistMath.progress(0L, 1_000_000L), 0.0001f)
        assertEquals(0f, WishlistMath.progress(400_000L, 0L), 0.0001f)               // 没设目标
    }

    @Test
    fun `剩余与超出：剩余不为负，超出的部分单独给`() {
        assertEquals(600_000L, WishlistMath.remainingCents(400_000L, 1_000_000L))
        assertEquals(0L, WishlistMath.remainingCents(1_000_000L, 1_000_000L))
        assertEquals(0L, WishlistMath.remainingCents(1_200_000L, 1_000_000L))
        assertEquals(200_000L, WishlistMath.overCents(1_200_000L, 1_000_000L))
        assertEquals(0L, WishlistMath.overCents(400_000L, 1_000_000L))
    }

    @Test
    fun `达成：金额够了或手动标了 DONE，两条路都算`() {
        assertTrue(WishlistMath.achieved(1_000_000L, 1_000_000L, WishlistStatus.ACTIVE))
        assertTrue(WishlistMath.achieved(1_200_000L, 1_000_000L, WishlistStatus.ACTIVE))
        assertFalse(WishlistMath.achieved(400_000L, 1_000_000L, WishlistStatus.ACTIVE))
        // 金额没够但用户自己盖章
        assertTrue(WishlistMath.achieved(400_000L, 1_000_000L, WishlistStatus.DONE))
        // 目标为 0：金额上永远不算达成（没有目标可言）
        assertFalse(WishlistMath.reachedByAmount(1_000L, 0L))
    }

    @Test
    fun `两数文案与状态文案`() {
        assertEquals("已存 3,200.00 / 目标 8,000.00", WishlistMath.amountsLabel(320_000L, 800_000L))
        assertEquals(
            "还差 4,800.00",
            WishlistMath.statusLabel(320_000L, 800_000L, WishlistStatus.ACTIVE, dueEpochDay = null, today = today),
        )
        assertEquals(
            "已达成",
            WishlistMath.statusLabel(800_000L, 800_000L, WishlistStatus.ACTIVE, dueEpochDay = null, today = today),
        )
        // 有截止日：同年的写 M月D日（跨年才带年份，DebtText 的老口径）
        assertEquals(
            "还差 4,800.00 · 截至 1月31日",
            WishlistMath.statusLabel(
                320_000L, 800_000L, WishlistStatus.ACTIVE,
                dueEpochDay = LocalDate.of(2026, 1, 31).toEpochDay(), today = today,
            ),
        )
        assertEquals(
            "还差 4,800.00 · 截至 2027年1月31日",
            WishlistMath.statusLabel(
                320_000L, 800_000L, WishlistStatus.ACTIVE,
                dueEpochDay = LocalDate.of(2027, 1, 31).toEpochDay(), today = today,
            ),
        )
        // 达成后不再追截止日（盖了章就不催了）
        assertEquals(
            "已达成",
            WishlistMath.statusLabel(
                800_000L, 800_000L, WishlistStatus.ACTIVE,
                dueEpochDay = LocalDate.of(2026, 1, 31).toEpochDay(), today = today,
            ),
        )
    }

    @Test
    fun `WishlistProgress 的进度、剩余、达成走同一处口径`() {
        val p = WishlistProgress("w1", "换电脑", 800_000L, 320_000L, null, WishlistStatus.ACTIVE)
        assertEquals(0.4f, p.progress, 0.0001f)
        assertEquals(480_000L, p.remainingCents)
        assertFalse(p.achieved)

        val done = p.copy(savedCents = 900_000L)
        assertTrue(done.achieved)
        assertEquals(1f, done.progress, 0.0001f)
        assertEquals(100_000L, WishlistMath.overCents(done.savedCents, done.targetCents))
    }
}
