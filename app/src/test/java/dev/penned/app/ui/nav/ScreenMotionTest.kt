package dev.penned.app.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 页面转场口径（AP 轮）：一级界面（底栏五个 Tab）之间淡入淡出**保持现状**；
 * 同一界面点开下钻页时子页整幅从右推入、父页左退 1/3 让位，返回时反向。
 */
class ScreenMotionTest {

    private data class Case(
        val name: String,
        val initialIsTab: Boolean,
        val targetIsTab: Boolean,
        val isPop: Boolean,
        val motion: Boolean = true,
        val enter: Enter,
        val exit: Exit,
    )

    private val cases = listOf(
        // 一级界面之间：淡入淡出、不位移（用户裁决"保持现状"）
        Case("Tab→Tab", initialIsTab = true, targetIsTab = true, isPop = false, enter = Enter.FADE, exit = Exit.FADE),
        // 底栏 navigate 带 popUpTo，框架可能把这次切换算成回退——两个方向都得是淡入淡出
        Case("Tab→Tab（被算成回退）", true, true, isPop = true, enter = Enter.FADE, exit = Exit.FADE),
        // 推入：首页→账单详情、统计→分类详细、详细页逐级下钻
        Case("Tab→下钻页", true, false, isPop = false, enter = Enter.PUSH_RIGHT, exit = Exit.PUSH_LEFT),
        Case("下钻页→下钻页（逐级下钻）", false, false, isPop = false, enter = Enter.PUSH_RIGHT, exit = Exit.PUSH_LEFT),
        // 返回：子页右滑出、父页从 -1/3 滑回
        Case("下钻页→Tab（返回首页）", false, true, isPop = true, enter = Enter.POP_LEFT, exit = Exit.POP_RIGHT),
        Case("下钻页→下钻页（返回上一级）", false, false, isPop = true, enter = Enter.POP_LEFT, exit = Exit.POP_RIGHT),
        // 系统"动画时长缩放"为 0：全关
        Case("动画关闭：Tab→Tab", true, true, isPop = false, motion = false, enter = Enter.NONE, exit = Exit.NONE),
        Case("动画关闭：下钻页推入", true, false, isPop = false, motion = false, enter = Enter.NONE, exit = Exit.NONE),
        Case("动画关闭：返回", false, true, isPop = true, motion = false, enter = Enter.NONE, exit = Exit.NONE),
    )

    @Test
    fun `转场表`() {
        cases.forEach { case ->
            assertEquals(
                case.name,
                ScreenMotion(case.enter, case.exit),
                screenMotion(case.initialIsTab, case.targetIsTab, case.isPop, case.motion),
            )
        }
    }

    @Test
    fun `下钻页不再交叉淡入淡出`() {
        // 旧口径是"滑 1/5 + 两页对淡"，叠在一起像糊——现在两页都是硬位移
        val push = screenMotion(initialIsTab = true, targetIsTab = false, isPop = false, motion = true)
        assertNotEquals(Enter.FADE, push.enter)
        assertNotEquals(Exit.FADE, push.exit)
    }

    @Test
    fun `父页让位必须慢于子页`() {
        // 父页与子页同速（或更快）时，滑动中间会露出底色，所以 PARALLAX 至少 2
        assertTrue(MotionSpec.PARALLAX >= 2)
    }

    @Test
    fun `底栏收展与下钻页推入同时长`() {
        // 底栏收起时父页可用高度在变，两条时间线错开就会看到抖动
        assertEquals(MotionSpec.PAGE, MotionSpec.BAR)
    }
}
