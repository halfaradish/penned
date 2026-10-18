package dev.penned.app.ui.intelligence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.LlmProvider
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AI 设置页（M4-2）的渲染与三条门槛：**默认关**、**http 地址当场报错**、**未配齐时「测试连接」不可点**；
 * 外加"测试连接"三态与隐私说明在场。
 *
 * 页面不碰网络与设置存储（`AiSettingsContent` 无状态），所以这里纯渲染 + 语义断言。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(
        enabled: Boolean = false,
        baseUrl: String = "",
        apiKey: String = "",
        model: String = "",
        visionModel: String = "",
        showKey: Boolean = false,
        testState: TestState = TestState.Idle,
        provider: LlmProvider = LlmProvider.DEFAULT,
        showProviders: Boolean = false,
        onOpenProviders: () -> Unit = {},
        onPickProvider: (LlmProvider) -> Unit = {},
    ) {
        composeRule.setContent {
            PennedTheme {
                AiSettingsContent(
                    enabled = enabled, baseUrl = baseUrl, apiKey = apiKey, model = model,
                    visionModel = visionModel, showKey = showKey, testState = testState, onBack = {},
                    provider = provider, showProviders = showProviders,
                    onOpenProviders = onOpenProviders, onPickProvider = onPickProvider,
                )
            }
        }
    }

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    @Test
    fun captureAiSettingsEmpty() {
        render()
        composeRule.onNodeWithText("AI 设置").assertIsDisplayed()
        // 默认关（AI 默认关是硬口径，§二·19）
        composeRule.onAllNodes(isToggleable()).onFirst().assertIsOff()
        composeRule.onNodeWithText("测试连接").assertIsNotEnabled()
        composeRule.onNodeWithText("清除 Key").assertIsNotEnabled()
        // 隐私说明在场（一处常量）
        composeRule.onNodeWithText(AiPrivacyCopy.FOOTER).assertIsDisplayed()
        capture("m4_ai_settings_empty")
    }

    @Test
    fun captureAiSettingsFilled() {
        render(
            enabled = true,
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            apiKey = "sk-abcdef123456",
            model = "glm-4.6",
            visionModel = "glm-4v",
        )
        composeRule.onAllNodes(isToggleable()).onFirst().assertIsOn()
        composeRule.onNodeWithText("测试连接").assertIsEnabled()
        composeRule.onNodeWithText("清除 Key").assertIsEnabled()
        capture("m4_ai_settings_filled")
    }

    @Test
    fun showKeyRevealsPlainText() {
        composeRule.setContent {
            PennedTheme {
                AiSettingsContent(
                    enabled = true, baseUrl = "https://a.example/v1", apiKey = "sk-abcdef123456",
                    model = "m", visionModel = "", showKey = true, testState = TestState.Idle, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("sk-abcdef123456").assertIsDisplayed()
        capture("m4_ai_settings_key_shown")
    }

    @Test
    fun httpUrlShowsErrorAndDisablesTest() {
        render(enabled = true, baseUrl = "http://192.168.1.9:11434/v1", apiKey = "sk-x", model = "qwen")
        composeRule.onNodeWithText("只支持 https 地址：明文 http 在正式版里连不上").assertIsDisplayed()
        composeRule.onNodeWithText("测试连接").assertIsNotEnabled()
        capture("m4_ai_settings_http_error")
    }

    @Test
    fun testStateOk() {
        render(enabled = true, baseUrl = "https://a.example/v1", apiKey = "sk-x", model = "glm-4.6", testState = TestState.Ok("glm-4.6"))
        composeRule.onNodeWithText("连上了：glm-4.6 回话正常").assertIsDisplayed()
        capture("m4_ai_settings_test_ok")
    }

    @Test
    fun testStateFailed() {
        render(
            enabled = true, baseUrl = "https://a.example/v1", apiKey = "sk-x", model = "m",
            testState = TestState.Failed("Key 无效（401）"),
        )
        composeRule.onNodeWithText("Key 无效（401）").assertIsDisplayed()
        capture("m4_ai_settings_test_failed")
    }

    @Test
    fun testingState() {
        render(enabled = true, baseUrl = "https://a.example/v1", apiKey = "sk-x", model = "m", testState = TestState.Testing)
        composeRule.onNodeWithText("测试中…").assertIsNotEnabled()
        composeRule.onNodeWithText("正在连…").assertIsDisplayed()
    }

    // ---- 供应商预选（D113）----

    @Test
    fun providerRowShowsCurrentAndHint() {
        var opened = false
        render(provider = LlmProvider.DEEPSEEK, onOpenProviders = { opened = true })
        composeRule.onNodeWithText("供应商").assertIsDisplayed()
        composeRule.onNodeWithText("DeepSeek").assertIsDisplayed()
        // 口径提示跟着当前那家走
        composeRule.onNodeWithText(LlmProvider.DEEPSEEK.hint).assertIsDisplayed()
        capture("m4_ai_settings_provider_deepseek")

        composeRule.onNodeWithTag(AI_PROVIDER_ROW_TAG).performClick()
        assert(opened) { "点「供应商」行没有打开选择抽屉" }
    }

    @Test
    fun providerRowZen() {
        render(
            enabled = true,
            baseUrl = "https://opencode.ai/zen/v1",
            apiKey = "sk-abcdef123456",
            model = "deepseek-v4.1-flash",
            provider = LlmProvider.OPENCODE_ZEN,
        )
        composeRule.onNodeWithText("OpenCode Zen").assertIsDisplayed()
        composeRule.onNodeWithText(LlmProvider.OPENCODE_ZEN.hint).assertIsDisplayed()
        capture("m4_ai_settings_provider_zen")
    }

    @Test
    fun providerRowCustomWithoutPreset() {
        render(provider = LlmProvider.CUSTOM)
        composeRule.onNodeWithText("自定义").assertIsDisplayed()
        composeRule.onNodeWithText(LlmProvider.CUSTOM.hint).assertIsDisplayed()
        capture("m4_ai_settings_provider_custom")
    }

    /** 抽屉内容单独渲染（`ModalBottomSheet` 挂在另一个窗口上，主窗口节点搜不到——既有先例 `PennedSheetTest`）。 */
    @Test
    fun providerSheetMarksCurrentAndPicks() {
        var picked: LlmProvider? = null
        composeRule.setContent {
            PennedTheme {
                PennedSheet(onDismiss = {}, title = "选择供应商") {
                    AiProviderSheetContent(current = LlmProvider.DEEPSEEK) { picked = it }
                }
            }
        }
        // 四档都在，副标题 = 预置地址（自定义那档给一句说明）
        LlmProvider.entries.forEach { composeRule.onNodeWithText(it.label).assertIsDisplayed() }
        composeRule.onNodeWithText("https://opencode.ai/zen/go/v1").assertIsDisplayed()
        composeRule.onNodeWithText("地址、模型、Key 都自己填").assertIsDisplayed()
        // 「已选」只出现在当前那行，且只有一处
        assertEquals(1, composeRule.onAllNodesWithText("已选").fetchSemanticsNodes().size)
        capture("m4_ai_settings_provider_sheet")

        composeRule.onNodeWithText("OpenCode Go").performClick()
        assertEquals(LlmProvider.OPENCODE_GO, picked)
    }
}
