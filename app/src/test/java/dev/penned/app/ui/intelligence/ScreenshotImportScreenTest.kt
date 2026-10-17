package dev.penned.app.ui.intelligence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * 截图导入页（M4-7）的渲染与接线：空态两条路 / 出图前的明示 / 识别中 / 失败卡（重试 + 去 AI 设置）。
 * 识别链路在 `ScreenshotImportViewModelTest`（假服务器 + PNG fixture）里跑。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotImportScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    @Test
    fun captureEmpty() {
        composeRule.setContent { PennedTheme { ScreenshotImportContent(ScreenshotUiState(), onBack = {}) } }
        composeRule.onNodeWithText("从相册选（可多选）").assertIsDisplayed()
        composeRule.onNodeWithText("拍一张").assertIsDisplayed()
        // 边界写在页面上：不做本地 OCR / 一图一笔 / 不存原图
        composeRule.onNodeWithText("不做本地 OCR；一张图只取一笔（图里有多笔时只认主笔）；原图不保存。")
            .performScrollTo().assertIsDisplayed()
        capture("m4_screenshot_empty")
    }

    @Test
    fun captureRecognizing() {
        composeRule.setContent {
            PennedTheme {
                ScreenshotImportContent(
                    ScreenshotUiState(
                        busy = true,
                        progressText = "正在识别第 2/3 张…",
                        endpointLabel = "open.bigmodel.cn",
                        modelLabel = "glm-4v",
                    ),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(SCREENSHOT_PRIVACY_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("这张图将发送给 open.bigmodel.cn / glm-4v").assertIsDisplayed()
        composeRule.onNodeWithText("正在识别第 2/3 张…").assertIsDisplayed()
        capture("m4_screenshot_recognizing")
    }

    @Test
    fun captureFailure() {
        composeRule.setContent {
            PennedTheme {
                ScreenshotImportContent(
                    ScreenshotUiState(busy = false, error = "没看出这是账单——换一张试试"),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(SCREENSHOT_FAILURE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
        composeRule.onNodeWithText("去 AI 设置").assertIsDisplayed()
        capture("m4_screenshot_failure")
    }

    @Test
    fun failureActionsCallBack() {
        var retried = false
        var openedSettings = false
        composeRule.setContent {
            PennedTheme {
                ScreenshotImportContent(
                    ScreenshotUiState(error = "连不上：检查网络或地址"),
                    onBack = {},
                    onRetry = { retried = true },
                    onOpenAiSettings = { openedSettings = true },
                )
            }
        }
        composeRule.onNodeWithText("重试").performClick()
        assertEquals(true, retried)
        composeRule.onNodeWithText("去 AI 设置").performClick()
        assertEquals(true, openedSettings)
    }

    @Test
    fun picksAreWired() {
        var picked = false
        var photo = false
        composeRule.setContent {
            PennedTheme {
                ScreenshotImportContent(
                    ScreenshotUiState(),
                    onBack = {},
                    onPickImages = { picked = true },
                    onTakePhoto = { photo = true },
                )
            }
        }
        composeRule.onNodeWithText("从相册选（可多选）").performClick()
        assertEquals(true, picked)
        composeRule.onNodeWithText("拍一张").performClick()
        assertEquals(true, photo)
    }

    @Test
    fun savedStateShowsOutcome() {
        composeRule.setContent {
            PennedTheme { ScreenshotImportContent(ScreenshotUiState(savedCount = 2), onBack = {}) }
        }
        composeRule.onNodeWithText("已识别 2 张，接着填这一张").assertIsDisplayed()
        composeRule.onNodeWithText("其余候选在「待确认」里，可随时再记或丢弃").assertIsDisplayed()
    }
}
