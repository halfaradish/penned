package dev.penned.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 左滑动作区的**输入口径**（M4-1 实踩）：行**没滑开**时，藏在内容层底下的按钮不能被点到。
 *
 * 背景：动作面板与内容层同框、压在底下，关闭态只是"被盖住"（视觉 + `invisibleToUser` 语义），
 * 命中测试上仍然在那儿。内容层若自己没有点击处理（典型的"开关行"：整行只有一枚 Switch），
 * 点击就穿过去落在底下那排按钮上——**真机后果**：点一下行的右侧会悄悄「上移」甚至「删除」这一行
 * （M4-1 模拟机走查实拍：两次误点把两条规则的优先级调了个个儿）。
 *
 * 修法见 `ActionBlock(enabled = isOpen)`；本测试**按坐标点行右侧**（点文本中心是打不到那个洞的——
 * 那条弯路也踩过）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class SwipeActionRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var clicks = 0

    /** 内容层只有一个占位文本（**没有整行点击**）——就是踩坑的那个形态。 */
    private fun rowClosed() {
        composeRule.setContent {
            PennedTheme {
                Column(Modifier.fillMaxWidth()) {
                    SwipeActionRow(
                        isOpen = false,
                        onOpenChange = {},
                        actions = listOf(
                            RowAction(
                                label = "上移",
                                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                onClick = { clicks++ },
                            ),
                            RowAction(
                                label = "删除",
                                container = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                onClick = { clicks += 100 },
                            ),
                        ),
                    ) {
                        Text("只有文字的行内容", Modifier.fillMaxWidth().height(56.dp))
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** 点某个 y 上、从右侧数 [fromRight] 像素的位置（动作按钮就在右端）。 */
    private fun clickRightSide(fromRight: Float, y: Float) {
        val width = composeRule.onRoot().fetchSemanticsNode().size.width.toFloat()
        composeRule.onRoot().performTouchInput { click(Offset(width - fromRight, y)) }
        composeRule.waitForIdle()
    }

    @Test
    fun `关闭态点行右侧不触发隐藏动作`() {
        clicks = 0
        rowClosed()
        // 「上移」块与「删除」块都在右端：两处各点一下，都不该有任何动作发生
        clickRightSide(fromRight = 96f, y = 28f)
        clickRightSide(fromRight = 24f, y = 28f)
        assertEquals(0, clicks)
    }

    @Test
    fun `滑开后可点`() {
        clicks = 0
        composeRule.setContent {
            PennedTheme {
                SwipeActionRow(
                    isOpen = true,
                    onOpenChange = {},
                    actions = listOf(
                        RowAction(
                            label = "上移",
                            container = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            onClick = { clicks++ },
                        ),
                    ),
                ) {
                    Text("只有文字的行内容", Modifier.fillMaxWidth().height(56.dp))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("上移").onFirst().performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }
}
