package dev.penned.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.width
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 转场几何（AP 轮）：把 NavHost 的时钟停下，量**滑动中途**各页的实际位置——
 * 子页在右半边往左来、父页确实左退让位、Tab 之间只有淡入淡出没有水平位移。
 * 转场口径直接调生产代码（[screenMotion]/[toTransition]），测试不另抄一份数字。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavTransitionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tabRoutes = setOf("home", "calendar")

    /** 测试窗口 400dp 宽（`w400dp-h800dp`、密度 1）。 */
    private val pageWidth = 400f

    @Composable
    private fun Page(tag: String) {
        // 真机上每个目的地都是不透明的（AP-2 补齐）；这里给个实底，方便肉眼看截图
        Box(Modifier.fillMaxSize().background(Color.White).testTag(tag))
    }

    /** 迷你 NavHost：路由与转场 lambda 的接法跟 PennedApp 一致。 */
    private fun render(motion: Boolean = true): NavHostController {
        lateinit var nav: NavHostController
        composeRule.setContent {
            nav = rememberNavController()
            NavHost(
                navController = nav,
                startDestination = "home",
                enterTransition = { screenMotion(tabRoutes, isPop = false, motion).enter.toTransition() },
                exitTransition = { screenMotion(tabRoutes, isPop = false, motion).exit.toTransition() },
                popEnterTransition = { screenMotion(tabRoutes, isPop = true, motion).enter.toTransition() },
                popExitTransition = { screenMotion(tabRoutes, isPop = true, motion).exit.toTransition() },
            ) {
                composable("home") { Page("home") }
                composable("calendar") { Page("calendar") }
                composable("detail") { Page("detail") }
            }
        }
        return nav
    }

    private fun leftOf(tag: String): Float =
        composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().left.value

    private fun widthOf(tag: String): Float =
        composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().width.value

    @Test
    fun `点开子页：子页从右边滑进来、父页左退让位`() {
        val nav = render()
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeByFrame()

        nav.navigate("detail")
        composeRule.mainClock.advanceTimeBy((MotionSpec.PAGE / 2).toLong())

        val child = leftOf("detail")
        val parent = leftOf("home")
        assertTrue("子页还在屏幕里没滑进来（left=$child）", child > 0f)
        assertTrue("父页没让位（left=$parent）", parent < 0f)
        assertTrue("父页退过头（left=$parent）", parent >= -pageWidth / MotionSpec.PARALLAX - 1f)

        // 推完：子页铺满、父页退场
        composeRule.mainClock.advanceTimeBy(MotionSpec.PAGE.toLong())
        assertEquals(0f, leftOf("detail"), 0.01f)
        assertEquals(pageWidth, widthOf("detail"), 0.01f)
        composeRule.onNodeWithTag("home").assertDoesNotExist()
    }

    @Test
    fun `返回：子页右滑出、父页滑回原位`() {
        val nav = render()
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeByFrame()

        nav.navigate("detail")
        // 动画从"导航后的第一帧"才开始，正好推 300ms 会差一两个像素
        composeRule.mainClock.advanceTimeBy((MotionSpec.PAGE + 100).toLong())
        assertEquals(0f, leftOf("detail"), 0.01f)

        nav.popBackStack()
        composeRule.mainClock.advanceTimeBy((MotionSpec.PAGE / 2).toLong())

        val child = leftOf("detail")
        val parent = leftOf("home")
        assertTrue("子页没往右退（left=$child）", child > 0f)
        assertTrue("父页没从 -1/3 往回滑（left=$parent）", parent < 0f)
        assertTrue("父页超过原位（left=$parent）", parent >= -pageWidth / MotionSpec.PARALLAX - 1f)

        composeRule.mainClock.advanceTimeBy((MotionSpec.PAGE + 100).toLong())
        assertEquals(0f, leftOf("home"), 0.01f)
        composeRule.onNodeWithTag("detail").assertDoesNotExist()
    }

    @Test
    fun `切一级界面：只淡入淡出、不位移`() {
        val nav = render()
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeByFrame()

        nav.navigate("calendar") {
            popUpTo(nav.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        // 淡入 180ms 的中途（160ms 的淡出还没结束 → 两页都在场）
        composeRule.mainClock.advanceTimeBy((MotionSpec.TAB_FADE_IN / 2).toLong())

        assertEquals("新 Tab 不该被推开", 0f, leftOf("calendar"), 0.01f)
        assertEquals("旧 Tab 不该被推走", 0f, leftOf("home"), 0.01f)
        assertEquals(pageWidth, widthOf("calendar"), 0.01f)
        assertEquals(pageWidth, widthOf("home"), 0.01f)
    }

    @Test
    fun `系统动画关闭：没有位移，直接落位`() {
        val nav = render(motion = false)
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeByFrame()

        nav.navigate("detail")
        composeRule.mainClock.advanceTimeBy(100)

        assertEquals(0f, leftOf("detail"), 0.01f)
        assertEquals(pageWidth, widthOf("detail"), 0.01f)
    }
}
