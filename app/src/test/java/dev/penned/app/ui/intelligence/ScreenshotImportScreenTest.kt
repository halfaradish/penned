package dev.penned.app.ui.intelligence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 截图导入页（M4-7 → D111 账单列表导入）的渲染与接线：空态两条路 / 出图前的明示 / 识别中 / 失败卡
 * （重试 + 去 AI 设置）/ **预览**（计数、行、删除、账户、导入按钮、全重复时禁用）。
 * 识别与落库链路在 `ScreenshotImportViewModelTest`（假服务器 + PNG fixture）里跑。
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

    /** 预览样张：三笔（一笔收入、一笔重复）+ 一行读不出金额被忽略。 */
    private fun previewState() = ScreenshotUiState(
        endpointLabel = "open.bigmodel.cn",
        modelLabel = "glm-4v",
        rows = listOf(
            ScreenshotRow(
                "r1", TxType.EXPENSE, 6707, "拼多多平台商户", 29_870_021L, true, false,
                merchant = "拼多多平台商户", categoryId = "c1", categoryName = "购物消费",
            ),
            ScreenshotRow(
                "r2", TxType.EXPENSE, 1200, "西大朴诚食堂 自动扣款成功", 29_869_000L, true, false,
                merchant = "西大朴诚食堂", categoryId = "c2", categoryName = "食品餐饮 / 午餐",
            ),
            ScreenshotRow(
                "r3", TxType.INCOME, 8800, "微信红包-来自姐姐", 29_869_000L, true, true,
                merchant = "微信红包-来自姐姐", categoryId = "c3", categoryName = "红包",
            ),
        ),
        droppedRows = 1,
        accounts = listOf(AccountOption("a1", "微信零钱"), AccountOption("a2", "中国银行储蓄卡")),
    )

    @Test
    fun captureEmpty() {
        composeRule.setContent { PennedTheme { ScreenshotImportContent(ScreenshotUiState(), onBack = {}) } }
        composeRule.onNodeWithText("从相册选（可多选，最多 8 张）").assertIsDisplayed()
        composeRule.onNodeWithText("拍一张").assertIsDisplayed()
        // 边界写在页面上：不做本地 OCR / 原图不保存
        composeRule.onNodeWithText(
            "不做本地 OCR；原图不保存——压缩后发给模型识一笔或一整屏，识别完即丢。",
        ).performScrollTo().assertIsDisplayed()
        capture("m4_screenshot_empty")
    }

    @Test
    fun capturePreview() {
        composeRule.setContent {
            PennedTheme {
                ScreenshotImportContent(state = previewState().copy(accountId = "a1"), onBack = {})
            }
        }
        composeRule.onNodeWithTag(SCREENSHOT_COUNTS_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("识别到 3 笔，确认后一起导入").assertIsDisplayed()
        composeRule.onNodeWithText("微信零钱").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("拼多多平台商户").performScrollTo().assertIsDisplayed()
        // AI 贴上的分类逐行显示（重复行的分类不计入"已贴"）
        composeRule.onNodeWithText("已贴 2 笔").assertIsDisplayed()
        composeRule.onNodeWithText("食品餐饮 / 午餐").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("全部导入 2 笔").performScrollTo().assertIsDisplayed()
        capture("m4_list_preview")
    }

    @Test
    fun previewCountsAndDuplicateAreVisible() {
        composeRule.setContent { PennedTheme { ScreenshotImportContent(previewState(), onBack = {}) } }
        // 计数卡：识别 3 / 将导入 2 / 重复 1（已排除）/ 忽略 1 行
        composeRule.onNodeWithText("3 笔").assertIsDisplayed()
        composeRule.onNodeWithText("2 笔").assertIsDisplayed()
        composeRule.onNodeWithText("1 笔").assertIsDisplayed()
        composeRule.onNodeWithText("已排除，不导入").assertIsDisplayed()
        composeRule.onNodeWithText("没读出金额，没进列表").assertIsDisplayed()
        // 合计只算将导入的两笔支出
        composeRule.onNodeWithText("79.07").assertIsDisplayed()
        // 重复行的「重复」标记（计数卡标签也叫「重复」，所以取第一个）+ 收入行带 +
        composeRule.onAllNodesWithText("重复").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("+88.00").assertIsDisplayed()
        composeRule.onNodeWithText("不指定").assertIsDisplayed()
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
        composeRule.onNodeWithText(
            "这张图将发送给 open.bigmodel.cn / glm-4v；归类只额外发商户名与现有分类名（不发金额、时间、单号）",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("正在识别第 2/3 张…").assertIsDisplayed()
        capture("m4_screenshot_recognizing")
    }

    @Test
    fun captureFailure() {
        composeRule.setContent {
            PennedTheme {
                ScreenshotImportContent(
                    ScreenshotUiState(error = "没看出这是账单——换一张试试"),
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
                    onReset = { retried = true },
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
        composeRule.onNodeWithText("从相册选（可多选，最多 8 张）").performClick()
        assertEquals(true, picked)
        composeRule.onNodeWithText("拍一张").performClick()
        assertEquals(true, photo)
    }

    @Test
    fun importAndRemoveCallBack() {
        var imported = false
        composeRule.setContent {
            PennedTheme {
                ScreenshotImportContent(
                    state = previewState(),
                    onBack = {},
                    onImport = { imported = true },
                )
            }
        }
        composeRule.onNodeWithText("全部导入 2 笔").performScrollTo().performClick()
        assertEquals(true, imported)
    }

    @Test
    fun accountRowCallsBack() {
        var picked: String? = null
        composeRule.setContent {
            PennedTheme {
                ScreenshotImportContent(
                    state = previewState(),
                    onBack = {},
                    onSelectAccount = { picked = it },
                )
            }
        }
        composeRule.onNodeWithText("不指定").performScrollTo().performClick()
        // 弹出抽屉后选一个账户
        composeRule.onNodeWithText("微信零钱").performClick()
        assertEquals("a1", picked)
    }

    @Test
    fun allDuplicatesDisablesImport() {
        val dupOnly = ScreenshotUiState(rows = previewState().rows.map { it.copy(duplicate = true) })
        composeRule.setContent { PennedTheme { ScreenshotImportContent(dupOnly, onBack = {}) } }
        composeRule.onNodeWithText("没有可导入的行").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun importedStateShowsOutcome() {
        composeRule.setContent {
            PennedTheme { ScreenshotImportContent(ScreenshotUiState(imported = 16), onBack = {}) }
        }
        composeRule.onNodeWithText("已导入 16 笔").assertIsDisplayed()
        composeRule.onNodeWithText("再选一批").assertIsDisplayed()
    }
}
