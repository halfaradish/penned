package dev.penned.app.ui.calendar

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日历收起/展开的纯逻辑（优化轮 2）表驱动：
 * - **跟手进度**（位移 ↔ 进度 1:1 换算、收满后剩余位移交出去）
 * - **松手吸附**（过半 / 速度方向明确）
 * - **格子侧几何**（视口高 / 本周行上移 / 非本周行透明度）
 * 收起态不再有"周条"这个第二形态：pager 常驻，整页网格按同一根 p 位移 + 裁剪。
 */
class CalendarCollapseTest {

    // —— 跟手进度：拖动位移 ↔ 进度 ——

    @Test
    fun `上滑按行程一比一推进进度，收满后剩余位移交给列表`() {
        val span = 200f
        // 进度、上滑位移、期望进度、期望吃掉的位移
        val cases = listOf(
            listOf(0f, 50f, 0.25f, 50f),      // 划了多少就收多少（1:1）
            listOf(0f, 200f, 1f, 200f),       // 正好一个行程 → 收满
            listOf(0f, 500f, 1f, 200f),       // 甩过头：只吃一个行程，剩下 300 给列表
            listOf(0.75f, 50f, 1f, 50f),      // 只剩 1/4 行程：吃 50 就满，多的一点不给
            listOf(0.75f, 10f, 0.8f, 10f),    // 没吃满：全吃
            listOf(1f, 80f, 1f, 0f),          // 已收满：一点不吃（全给列表滚）
            listOf(0f, 0f, 0f, 0f),           // 零位移
            listOf(0f, -30f, 0f, 0f),         // 方向不对（这是下滑）不吃
        )
        cases.forEach { (start, distance, wantProgress, wantUsed) ->
            val result = CalendarCollapse.consumeUp(start, distance, span)
            assertEquals("从 $start 划 $distance", wantProgress, result.progress, 1e-4f)
            assertEquals("从 $start 划 $distance", wantUsed, result.consumed, 1e-4f)
        }
    }

    @Test
    fun `下滑按行程一比一退回，回到展开后剩余位移不再吃`() {
        val span = 200f
        val cases = listOf(
            listOf(1f, 50f, 0.75f, 50f),
            listOf(1f, 200f, 0f, 200f),
            listOf(1f, 500f, 0f, 200f),       // 甩过头：展开完就不再吃（列表已到顶也没得更滚）
            listOf(0.25f, 50f, 0f, 50f),
            listOf(0.25f, 10f, 0.2f, 10f),
            listOf(0f, 80f, 0f, 0f),          // 已展开：一点不吃
            listOf(1f, 0f, 1f, 0f),
            listOf(1f, -30f, 1f, 0f),         // 方向不对（这是上滑）不吃
        )
        cases.forEach { (start, distance, wantProgress, wantUsed) ->
            val result = CalendarCollapse.consumeDown(start, distance, span)
            assertEquals("从 $start 划 $distance", wantProgress, result.progress, 1e-4f)
            assertEquals("从 $start 划 $distance", wantUsed, result.consumed, 1e-4f)
        }
    }

    @Test
    fun `行程没量到（宽高为 0）时不吃位移，进度原样`() {
        listOf(0f, -1f).forEach { span ->
            val up = CalendarCollapse.consumeUp(0.3f, 100f, span)
            assertEquals(0.3f, up.progress, 1e-4f)
            assertEquals(0f, up.consumed, 1e-4f)
            val down = CalendarCollapse.consumeDown(0.3f, 100f, span)
            assertEquals(0.3f, down.progress, 1e-4f)
            assertEquals(0f, down.consumed, 1e-4f)
        }
    }

    @Test
    fun `进度越界被夹住（调用方传了脏值也不会跑出去）`() {
        assertEquals(1f, CalendarCollapse.consumeUp(1.4f, 100f, 200f).progress, 1e-4f)
        assertEquals(0f, CalendarCollapse.consumeDown(-0.4f, 100f, 200f).progress, 1e-4f)
        assertEquals(1f, CalendarCollapse.snapTarget(1.7f, 0f, 800f), 1e-4f)
        assertEquals(1f, CalendarCollapse.otherRowAlpha(-0.5f), 1e-4f)
        assertEquals(0f, CalendarCollapse.otherRowAlpha(1.5f), 1e-4f)
    }

    // —— 松手吸附 ——

    @Test
    fun `松手吸附：过半收满、不过半弹回、速度方向明确时以速度为准`() {
        val fling = 800f
        // 进度、松手速度（正 = 往下滑）、期望目标
        val cases = listOf(
            listOf(0f, 0f, 0f),
            listOf(0.2f, 0f, 0f),
            listOf(0.49f, 0f, 0f),
            listOf(0.5f, 0f, 1f),             // "过半"含正好一半
            listOf(0.8f, 0f, 1f),
            listOf(1f, 0f, 1f),
            listOf(0.1f, -2000f, 1f),         // 快速上甩：位置没过半也收满
            listOf(0.9f, 2000f, 0f),          // 快速下甩：位置过半也弹回
            listOf(0.5f, -fling, 1f),         // 正好到阈值：按速度
            listOf(0.5f, fling, 0f),
            listOf(0.5f, -fling + 1f, 1f),    // 差一点没到阈值：按位置
            listOf(0.4f, -fling + 1f, 0f),
        )
        cases.forEach { (progress, velocity, want) ->
            assertEquals(
                "进度 $progress 速度 $velocity",
                want,
                CalendarCollapse.snapTarget(progress, velocity, fling),
                1e-4f,
            )
        }
    }

    // —— 收起几何（格子侧，全部由 p 一个值推导） ——

    @Test
    fun `视口高：p = 0 是整月高、p = 1 只剩一行`() {
        val expanded = 400.dp
        val row = 80.dp
        listOf(
            Triple(0f, 400f, "展开"),
            Triple(1f, 80f, "收起"),
            Triple(0.5f, 240f, "一半"),
            Triple(0.25f, 320f, "四分之一"),
        ).forEach { (p, want, label) ->
            assertDp("$label（p=$p）", want, CalendarCollapse.viewportHeight(expanded, row, p))
        }
        // 越界夹住
        assertDp("p 小于 0", 400f, CalendarCollapse.viewportHeight(expanded, row, -1f))
        assertDp("p 大于 1", 80f, CalendarCollapse.viewportHeight(expanded, row, 2f))
    }

    @Test
    fun `本周行上移：p = 1 时正好贴住视口顶（行序号 × 行高）`() {
        val row = 80.dp
        assertDp("展开态不动", 0f, CalendarCollapse.weekShift(row, 3, 0f))
        assertDp("第 0 行（本月从周一开头）不动", 0f, CalendarCollapse.weekShift(row, 0, 1f))
        assertDp("第 3 行", 240f, CalendarCollapse.weekShift(row, 3, 1f))
        assertDp("第 3 行一半", 120f, CalendarCollapse.weekShift(row, 3, 0.5f))
        assertDp("行序号越界夹到 0", 0f, CalendarCollapse.weekShift(row, -2, 1f))
    }

    @Test
    fun `非本周行透明度：随 p 渐隐到 0，本周行不参与`() {
        assertEquals(1f, CalendarCollapse.otherRowAlpha(0f), 1e-4f)
        assertEquals(0.75f, CalendarCollapse.otherRowAlpha(0.25f), 1e-4f)
        assertEquals(0f, CalendarCollapse.otherRowAlpha(1f), 1e-4f)
        // 与视口高/位移同一根 p：p = 1 时非本周行既被裁掉（视口只剩一行）也彻底透明
        assertTrue(CalendarCollapse.otherRowAlpha(CalendarCollapse.snapTarget(0.6f, 0f, 800f)) == 0f)
    }

    private fun assertDp(label: String, want: Float, actual: Dp) {
        assertEquals("$label：${actual.value}dp", want, actual.value, 0.01f)
    }
}
