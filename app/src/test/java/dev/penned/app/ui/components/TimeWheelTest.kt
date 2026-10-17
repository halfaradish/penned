package dev.penned.app.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 记账时刻的「时 | 分」双列滚轮（优化轮 3）：与「年 | 月」共用同一枚 [WheelColumn]。
 * 它嵌在**日期弹窗**里用（D097：点「日期」一起调，不再有单独的「时间」chip），
 * 弹窗窗口在 Robolectric 里不便截图，所以滚轮本体单独测（同 YearMonthPickerTest）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimeWheelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureWheel() {
        composeRule.setContent {
            PennedTheme {
                TimeWheel(
                    selectedHour = 16,
                    selectedMinute = 38,
                    onHourChange = {},
                    onMinuteChange = {},
                    modifier = Modifier.width(240.dp),
                )
            }
        }
        // 居中那格就是当前选中（两列同时成立；可见格是 14–18 时 / 36–40 分，不会撞名）
        composeRule.onNodeWithText("16").assertIsDisplayed()
        composeRule.onNodeWithText("38").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_add_time_wheel.png")
    }

    @Test
    fun captureCompactWheel() {
        // 日期弹窗里用的就是 3 行紧凑形态：居中那格 = 选中的时/分，上下各一格做渐隐
        composeRule.setContent {
            PennedTheme {
                TimeWheel(
                    selectedHour = 16,
                    selectedMinute = 38,
                    onHourChange = {},
                    onMinuteChange = {},
                    modifier = Modifier.width(240.dp),
                    rows = 3,
                )
            }
        }
        composeRule.onNodeWithText("16").assertIsDisplayed()
        composeRule.onNodeWithText("38").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_add_time_wheel_compact.png")
    }

    @Test
    fun `wheel keeps the selection it was opened with`() {
        var hour = 16
        var minute = 38
        composeRule.setContent {
            PennedTheme {
                TimeWheel(
                    selectedHour = hour,
                    selectedMinute = minute,
                    onHourChange = { hour = it },
                    onMinuteChange = { minute = it },
                    modifier = Modifier.width(240.dp),
                    rows = 3,
                )
            }
        }
        composeRule.waitForIdle()
        // 首帧不许把选中顶成第一项（两列都是中段）
        assertEquals(16, hour)
        assertEquals(38, minute)
    }

    @Test
    fun `wheel keeps the selection when it sits at either end`() {
        // 时列底端（23）、分列顶端（00）：内容 padding 对称性不够时这里会被顶到邻格
        var hour = 23
        var minute = 0
        composeRule.setContent {
            PennedTheme {
                TimeWheel(
                    selectedHour = hour,
                    selectedMinute = minute,
                    onHourChange = { hour = it },
                    onMinuteChange = { minute = it },
                    modifier = Modifier.width(240.dp),
                    rows = 3,
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(23, hour)
        assertEquals(0, minute)
    }

    @Test
    fun `wheel keeps the selection at the other end too`() {
        // 另一头：时列顶端（00）、分列底端（59）
        var hour = 0
        var minute = 59
        composeRule.setContent {
            PennedTheme {
                TimeWheel(
                    selectedHour = hour,
                    selectedMinute = minute,
                    onHourChange = { hour = it },
                    onMinuteChange = { minute = it },
                    modifier = Modifier.width(240.dp),
                    rows = 3,
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, hour)
        assertEquals(59, minute)
    }

    @Test
    fun `tapping a number brings it to the middle and reports it`() {
        var hour = 10
        var minute = 0
        composeRule.setContent {
            PennedTheme {
                TimeWheel(
                    selectedHour = hour,
                    selectedMinute = minute,
                    onHourChange = { hour = it },
                    onMinuteChange = { minute = it },
                    modifier = Modifier.width(240.dp),
                    rows = 3,
                )
            }
        }
        // 先滚到目标（performScrollToNode 只保证可见），点一下才落位到正中。
        // 按列限定：时/分两列都是数字，值可能重名（比如选 10:12 时两列各有一个「12」）
        composeRule.onNodeWithTag(TIME_WHEEL_HOUR_TAG).performScrollToNode(hasText("12"))
        composeRule.onNode(hasText("12") and hasAnyAncestor(hasTestTag(TIME_WHEEL_HOUR_TAG))).performClick()
        composeRule.waitForIdle()
        assertEquals(12, hour)

        composeRule.onNodeWithTag(TIME_WHEEL_MINUTE_TAG).performScrollToNode(hasText("45"))
        composeRule.onNode(hasText("45") and hasAnyAncestor(hasTestTag(TIME_WHEEL_MINUTE_TAG))).performClick()
        composeRule.waitForIdle()
        assertEquals(45, minute)
    }
}
