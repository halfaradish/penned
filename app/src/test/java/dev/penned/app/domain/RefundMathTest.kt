package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 退款 / 优惠口径（M3-2，D069 保存净额）：净额、编辑反推、往返幂等——一张表跑完。
 * 口径只此一处，UI 不许自己算（M3 §二·3）。
 */
class RefundMathTest {

    /** 一行用例：原价 / 退款 / 优惠 → 落库净额。 */
    private data class Case(
        val original: Long,
        val refund: Long,
        val discount: Long,
        val net: Long,
        val why: String,
    )

    private val cases = listOf(
        Case(2000L, 0L, 0L, 2000L, "无退优惠 = 原价"),
        Case(2000L, 500L, 0L, 1500L, "部分退款"),
        Case(1454L, 1454L, 0L, 0L, "全额退款 → 净额 0（一木形态）"),
        Case(2000L, 0L, 300L, 1700L, "只有优惠"),
        Case(2000L, 500L, 300L, 1200L, "退款 + 优惠叠加"),
        Case(100L, 100L, 100L, 0L, "退 + 惠恰好等于原价"),
        Case(100L, 150L, 0L, 0L, "退超了不拦，净额封 0"),
        Case(100L, 150L, 200L, 0L, "两项都超，净额仍封 0"),
        Case(0L, 0L, 0L, 0L, "全 0"),
        Case(1L, 0L, 0L, 1L, "一分钱"),
    )

    @Test
    fun `净额 = 原价 − 退款 − 优惠，下限 0`() {
        cases.forEach { c ->
            assertEquals(
                "${c.why}：${c.original} − ${c.refund} − ${c.discount}",
                c.net,
                RefundMath.netCents(c.original, c.refund, c.discount),
            )
        }
    }

    /**
     * 往返幂等：编辑页反推的原价填回金额框，再存一次必须回到**同一个净额**。
     * （这两行就是记一笔编辑的真实路径：loadForEdit → saveInternal。）
     */
    @Test
    fun `编辑反推原价后重存，净额不变`() {
        cases.forEach { c ->
            val reopened = RefundMath.originalCents(c.net, c.refund, c.discount)
            assertEquals(
                "${c.why}：反推原价 ${c.original} → $reopened 后再存",
                c.net,
                RefundMath.netCents(reopened, c.refund, c.discount),
            )
        }
    }

    @Test
    fun `没有退优惠时反推原价 = 净额本身`() {
        // 绝大多数流水走这条：编辑页金额框就是落库金额，一个数都不许变
        assertEquals(2000L, RefundMath.originalCents(2000L, 0L, 0L))
        assertEquals(0L, RefundMath.originalCents(0L, 0L, 0L))
    }

    @Test
    fun `退超时的反推：金额被抬到退款额（第二次起稳定）`() {
        // 用户输 1.00、退款 1.50：净额封 0，反推原价 = 0 + 1.50 = 1.50（比当初输入的多——
        // 净额口径下这个信息在落库时就没了，不必假装能还原）；再存仍是 0，不会越滚越大
        val net = RefundMath.netCents(100L, 150L, 0L)
        val reopened = RefundMath.originalCents(net, 150L, 0L)
        assertEquals(150L, reopened)
        assertEquals(0L, RefundMath.netCents(reopened, 150L, 0L))
        assertEquals(reopened, RefundMath.originalCents(RefundMath.netCents(reopened, 150L, 0L), 150L, 0L))
    }

    @Test
    fun `全额退款判定：退了钱且净额为 0`() {
        assertTrue(RefundMath.isFullyRefunded(netCents = 0L, refundCents = 1454L))
        assertFalse(RefundMath.isFullyRefunded(netCents = 0L, refundCents = 0L))    // 本来就 0，不算退款
        assertFalse(RefundMath.isFullyRefunded(netCents = 500L, refundCents = 500L)) // 部分退款
    }

    @Test
    fun `实付行文案：没退优惠不占位置，填了就写成算式`() {
        assertNull(RefundMath.summaryText(2000L, 0L, 0L))
        assertEquals("实付 15.00（20.00 − 退款 5.00）", RefundMath.summaryText(2000L, 500L, 0L))
        assertEquals("实付 17.00（20.00 − 优惠 3.00）", RefundMath.summaryText(2000L, 0L, 300L))
        assertEquals("实付 12.00（20.00 − 退款 5.00 − 优惠 3.00）", RefundMath.summaryText(2000L, 500L, 300L))
        assertEquals("实付 0.00（14.54 − 退款 14.54）", RefundMath.summaryText(1454L, 1454L, 0L))
        // 金额还没输、先点了退款：算式照样成立，输完金额就正常了
        assertEquals("实付 0.00（0.00 − 退款 5.00）", RefundMath.summaryText(0L, 500L, 0L))
    }
}
