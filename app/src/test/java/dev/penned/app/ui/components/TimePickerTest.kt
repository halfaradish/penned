package dev.penned.app.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime

/**
 * 记账时间到分（优化轮 3）：自绘「时 | 分」双列滚轮，与「年 | 月」共用同一枚 [WheelColumn]。
 * 滚轮本体单独测——弹窗窗口在 Robolectric 里不便截图，所以内容抽成 [TimeWheel]（同 YearMonthPickerTest）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimePickerTest {

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
                )
            }
        }
        // 先滚到目标（performScrollToNode 只保证可见），点一下才落位到正中
        composeRule.onNodeWithTag(TIME_WHEEL_HOUR_TAG).performScrollToNode(hasText("12"))
        composeRule.onNodeWithText("12").performClick()
        composeRule.waitForIdle()
        assertEquals(12, hour)

        composeRule.onNodeWithTag(TIME_WHEEL_MINUTE_TAG).performScrollToNode(hasText("45"))
        composeRule.onNodeWithText("45").performClick()
        composeRule.waitForIdle()
        assertEquals(45, minute)
    }

    @Test
    fun `dialog opens at the initial time and confirms it unchanged`() {
        var confirmed: LocalTime? = null
        var dismissed = false
        composeRule.setContent {
            PennedTheme {
                TimePickerDialog(
                    initial = LocalTime.of(16, 38),
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = it },
                )
            }
        }
        composeRule.onNodeWithText("选择时间").assertIsDisplayed()
        composeRule.onNodeWithText("16").assertIsDisplayed()
        composeRule.onNodeWithText("38").assertIsDisplayed()
        // 确定前不改值：直接确定就回原时刻
        composeRule.onNodeWithText("确定").performClick()
        assertEquals(LocalTime.of(16, 38), confirmed)
        assertFalse("确定不该走 dismiss", dismissed)
    }

    @Test
    fun `dialog cancel dismisses without confirming`() {
        var confirmed: LocalTime? = null
        var dismissed = false
        composeRule.setContent {
            PennedTheme {
                TimePickerDialog(
                    initial = LocalTime.of(9, 5),
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = it },
                )
            }
        }
        composeRule.onNodeWithText("取消").performClick()
        assertTrue(dismissed)
        org.junit.Assert.assertNull(confirmed)
    }
}
