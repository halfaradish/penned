package dev.penned.app.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 日历收起/展开状态机（优化轮）表驱动：阈值、方向、边界。
 * 口径：往下看累计够了才收；往回滚只在列表已经到顶时才展开（UI-10 的用户裁决）。
 */
class CalendarCollapseTest {

    private val threshold = 100f

    @Test
    fun `往下看累计到阈值才收起，分段累计跨方向不相加`() {
        val gate = CalendarCollapseGate(threshold)
        // 分两段（30 + 30）不到阈值：形态不动
        assertNull(gate.onScroll(-30f, atTop = true))
        assertNull(gate.onScroll(-30f, atTop = false))
        // 掉头往回滚：只清累计（不展开——还没到顶）
        assertNull(gate.onScroll(10f, atTop = false))
        // 再往下看 40 不够（累计被清过），到 100 才收
        assertNull(gate.onScroll(-40f, atTop = false))
        assertEquals(false, gate.onScroll(-60f, atTop = false))
        // 收起后累计归零：再来一次小位移不会再报"收起"
        assertNull(gate.onScroll(-5f, atTop = false))
    }

    @Test
    fun `往回滚：到顶才展开，没到顶保持原状`() {
        val gate = CalendarCollapseGate(threshold)
        // 列表没到顶：往回滚 N 次都只清累计，不展开
        repeat(3) { assertNull(gate.onScroll(24f, atTop = false)) }
        // 到顶：展开
        assertEquals(true, gate.onScroll(1f, atTop = true))
        // 已经在顶、继续往回滚：仍然是展开（幂等，不会来回抖）
        assertEquals(true, gate.onScroll(50f, atTop = true))
    }

    @Test
    fun `收起态横滑方向：过阈值才切月，方向与展开态 pager 一致`() {
        val threshold = 120f
        // 往右拉 = 看更早的月（-1）；往左拉 = 看更新的月（+1，未来月由 ViewModel 夹住）
        assertEquals(-1L, shiftDirection(threshold, threshold))
        assertEquals(-1L, shiftDirection(threshold + 200f, threshold))
        assertEquals(1L, shiftDirection(-threshold, threshold))
        assertEquals(1L, shiftDirection(-400f, threshold))
        // 没到阈值（一蹭）不算一次切月
        assertNull(shiftDirection(0f, threshold))
        assertNull(shiftDirection(threshold - 0.5f, threshold))
        assertNull(shiftDirection(-threshold + 0.5f, threshold))
    }

    @Test
    fun `零位移与阈值边界`() {
        val gate = CalendarCollapseGate(threshold)
        assertNull(gate.onScroll(0f, atTop = false))
        assertNull(gate.onScroll(0f, atTop = true))
        // 正好等于阈值：收起（>= 阈值）
        assertEquals(false, gate.onScroll(-threshold, atTop = false))
        // 超过阈值
        val gate2 = CalendarCollapseGate(threshold)
        assertEquals(false, gate2.onScroll(-(threshold + 0.5f), atTop = false))
        // 在顶时往下看也要能收起（列表已在顶不是"保持展开"的理由——日历正是从顶部收起）
        val gate3 = CalendarCollapseGate(threshold)
        assertEquals(false, gate3.onScroll(-threshold, atTop = true))
    }
}
