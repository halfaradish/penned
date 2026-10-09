package dev.penned.app.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 悬浮记账入口的形变与滚动判定（AQ 轮）：都是纯口径，表驱动。
 * 重点是"无跳变"这条验收——宽度与标签透明度都是同一个进度值的函数，端点精确、过程单调。
 */
class AddEntryMotionTest {

    private val full = 368.dp
    private val collapsed = AddEntryMotion.collapsedSize

    @Test
    fun `width endpoints are exact`() {
        assertEquals(full, AddEntryMotion.width(full, collapsed, progress = 0f))
        assertEquals(collapsed, AddEntryMotion.width(full, collapsed, progress = 1f))
    }

    @Test
    fun `width shrinks monotonically between the endpoints`() {
        var previous = full
        (0..10).forEach { step ->
            val width = AddEntryMotion.width(full, collapsed, progress = step / 10f)
            assertTrue("progress=${step / 10f} 宽度没缩（$width）", width <= previous)
            assertTrue("progress=${step / 10f} 宽度越界（$width）", width >= collapsed && width <= full)
            previous = width
        }
    }

    @Test
    fun `width clamps out-of-range progress`() {
        assertEquals(collapsed, AddEntryMotion.width(full, collapsed, progress = 2f))
        assertEquals(full, AddEntryMotion.width(full, collapsed, progress = -1f))
    }

    @Test
    fun `label fades out before the pill finishes shrinking`() {
        assertEquals(1f, AddEntryMotion.labelAlpha(0f), 0.0001f)
        assertEquals(0.5f, AddEntryMotion.labelAlpha(AddEntryMotion.LABEL_FADE / 2), 0.0001f)
        assertEquals(0f, AddEntryMotion.labelAlpha(AddEntryMotion.LABEL_FADE), 0.0001f)
        assertEquals(0f, AddEntryMotion.labelAlpha(1f), 0.0001f)

        var previous = 1f
        (0..10).forEach { step ->
            val alpha = AddEntryMotion.labelAlpha(step / 10f)
            assertTrue("progress=${step / 10f} 透明度没降（$alpha）", alpha <= previous)
            previous = alpha
        }

        // 关键：标签淡完时胶囊还很宽（> 2×圆直径），所以看不到文字被圆角挤扁
        val widthWhenLabelGone = AddEntryMotion.width(full, collapsed, AddEntryMotion.LABEL_FADE)
        assertTrue("标签淡完时胶囊只剩 $widthWhenLabelGone", widthWhenLabelGone > collapsed * 2)
    }

    @Test
    fun `scroll gate collapses after enough forward scroll and restores only at top`() {
        val gate = AddEntryScrollGate(collapseAfterPx = 72f)
        // 往回滚（内容往下走）：不改形态，只清"往下看"的累计（UI-10：复原只认回到顶部）
        assertEquals(null, gate.onScroll(deltaY = 1f, atTop = false))
        assertEquals(null, gate.onScroll(deltaY = 80f, atTop = false))
        // 继续往下看：没累计够就不动
        assertEquals(null, gate.onScroll(deltaY = -40f, atTop = false))
        assertEquals(null, gate.onScroll(deltaY = -31f, atTop = false))
        // 累计够了 → 收起
        assertEquals(false, gate.onScroll(deltaY = -1f, atTop = false))
        // 收起后累计清零：同样的一小段不会再触发一次
        assertEquals(null, gate.onScroll(deltaY = -40f, atTop = false))
        assertEquals(false, gate.onScroll(deltaY = -32f, atTop = false))
        // 往回滚把累计清掉：后面的 -40 不该和前面加在一起（清完仍不到 72）
        assertEquals(null, gate.onScroll(deltaY = -40f, atTop = false))
        assertEquals(null, gate.onScroll(deltaY = 1f, atTop = false))
        assertEquals(null, gate.onScroll(deltaY = -40f, atTop = false))
        // 回到顶部：不看方向，一律展开
        assertEquals(true, gate.onScroll(deltaY = -1000f, atTop = true))
        assertEquals(true, gate.onScroll(deltaY = 5f, atTop = true))
        // 零位移不改状态
        assertEquals(null, gate.onScroll(deltaY = 0f, atTop = false))
    }
}
