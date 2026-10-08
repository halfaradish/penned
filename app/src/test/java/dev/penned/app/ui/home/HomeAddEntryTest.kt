package dev.penned.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 悬浮入口的形变几何（AQ 轮）：把测试时钟停下来量**形变中途**的宽度——
 * 从通栏一路单调收到正圆、两端精确、标签同期收窄到 0，就是"无跳变"的证据
 * （做法照 `NavTransitionTest` 的停表量几何）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeAddEntryTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 测试窗口 400dp 宽：展开 = 通栏（左右各 16dp），收起 = 56dp 正圆。 */
    private val expandedWidth = 368f
    private val collapsedWidth = AddEntryMotion.collapsedSize.value

    private fun widthOf(tag: String): Float =
        composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().width.value

    /** 入口标签：胶囊整体可点，标签语义会被并到胶囊上，所以量它要走未合并树。 */
    private fun labelWidth(): Float =
        composeRule.onNodeWithTag(HOME_ADD_LABEL_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .width
            .value

    private fun centerXOf(node: SemanticsNodeInteraction): Float {
        val bounds = node.getUnclippedBoundsInRoot()
        return (bounds.left.value + bounds.right.value) / 2
    }

    @Test
    fun `collapse shrinks monotonically from pill to circle`() {
        var expanded by mutableStateOf(true)
        composeRule.setContent {
            PennedTheme {
                Box(Modifier.fillMaxSize()) {
                    HomeAddEntry(
                        visible = true,
                        expanded = expanded,
                        onClick = {},
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(expandedWidth, widthOf(HOME_ADD_TAG), 0.5f)
        assertTrue("标签没量到宽度（${labelWidth()}）", labelWidth() > 0f)

        // 形变开始：沿途取 8 个采样（动画起帧比状态写入晚一帧，所以只看"一路不收窄"）
        expanded = false
        composeRule.mainClock.advanceTimeByFrame()
        val widths = mutableListOf<Float>()
        val labelWidths = mutableListOf<Float>()
        repeat(8) { step ->
            composeRule.mainClock.advanceTimeBy(AddEntryMotion.DURATION / 6L)
            widths += widthOf(HOME_ADD_TAG)
            labelWidths += labelWidth()
            if (step == 2) {
                // 中途留一张取证图（人工评审用）
                composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/aq_home_add_morph_mid.png")
            }
        }

        assertTrue("宽度越界（$widths）", widths.all { it <= expandedWidth + 0.5f })
        widths.zipWithNext { previous, next ->
            assertTrue("宽度不是一路收（$widths）", next <= previous)
        }
        // 真正的"中途"：至少三个采样落在两端之间（不是"一步跳过去"）
        val midCourse = widths.count { it > collapsedWidth + 0.5f && it < expandedWidth - 0.5f }
        assertTrue("只量到 $midCourse 个中途宽度（$widths）", midCourse >= 3)
        labelWidths.zipWithNext { previous, next ->
            assertTrue("标签没跟着收窄（$labelWidths）", next <= previous)
        }

        // 收完：正好是右下角的正圆，标签收没、＋落在圆心
        composeRule.mainClock.advanceTimeBy(AddEntryMotion.DURATION.toLong())
        assertEquals(collapsedWidth, widthOf(HOME_ADD_TAG), 0.5f)
        assertEquals(0f, labelWidth(), 0.5f)
        val bounds = composeRule.onNodeWithTag(HOME_ADD_TAG).getUnclippedBoundsInRoot()
        assertEquals(384f, bounds.right.value, 0.5f)
        assertEquals(784f, bounds.bottom.value, 0.5f)
        val icon = composeRule.onNodeWithContentDescription("记一笔", useUnmergedTree = true)
        assertEquals("＋ 没落在圆心", centerXOf(icon), (bounds.left.value + bounds.right.value) / 2, 1f)
    }

    @Test
    fun `entry is gone while invisible`() {
        composeRule.setContent {
            PennedTheme {
                Box(Modifier.fillMaxSize()) {
                    HomeAddEntry(visible = false, expanded = true, onClick = {})
                }
            }
        }
        composeRule.onNodeWithTag(HOME_ADD_TAG).assertDoesNotExist()
    }
}
