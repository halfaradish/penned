package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 报销金额口径（M3-3，D008）：缺省 0 = 按流水净额全额；填了就按填的（超额不拦）。
 * 表驱动——同一张表也是资产页 SQL 的对照（DAO 测试跑真 SQL，这里钉口径本身）。
 */
class ReimburseMathTest {

    private data class Case(
        val reimburseCents: Long,
        val amountCents: Long,
        val claim: Long,
        val why: String,
    )

    private val cases = listOf(
        Case(0L, 10_000L, 10_000L, "缺省 0 = 按净额全额"),
        Case(3_000L, 10_000L, 3_000L, "部分报销"),
        Case(10_000L, 10_000L, 10_000L, "报销金额恰好等于净额（部分与全额的边界）"),
        Case(15_000L, 10_000L, 15_000L, "超额：照填的算，不夹不拦"),
        Case(0L, 0L, 0L, "净额 0（全额退款）缺省也是 0"),
        Case(1L, 10_000L, 1L, "一分钱"),
    )

    @Test
    fun `报销金额 = 填了就按填的，缺省按净额全额`() {
        cases.forEach { c ->
            assertEquals(
                "${c.why}（填 ${c.reimburseCents} / 净额 ${c.amountCents}）",
                c.claim,
                ReimburseMath.claimCents(c.reimburseCents, c.amountCents),
            )
        }
    }

    @Test
    fun `是否按全额只看报销金额那一列`() {
        assertTrue(ReimburseMath.isFullClaim(0L))
        assertTrue("负数当缺省处理（键盘打不出，防外部来源）", ReimburseMath.isFullClaim(-1L))
        assertFalse(ReimburseMath.isFullClaim(1L))
        assertFalse(ReimburseMath.isFullClaim(10_000L))
    }

    @Test
    fun `金额行文案：全额带上净额，自定就只写数`() {
        assertEquals("全额 100.00", ReimburseMath.claimLabel(0L, 10_000L))
        assertEquals("全额 0.00", ReimburseMath.claimLabel(0L, 0L))
        assertEquals("30.00", ReimburseMath.claimLabel(3_000L, 10_000L))
        assertEquals("150.00", ReimburseMath.claimLabel(15_000L, 10_000L))
    }
}
