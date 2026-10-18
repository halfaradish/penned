package dev.penned.app.ui.intelligence

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.domain.LlmProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * AI 设置页的读写（M4-2 + D113 的供应商分区）：`load()` 读出**当前那家**的四样与总开关、
 * 改动写回那一家、**清除 Key 真的把 Key 从存储里删掉**（且只清那一家）。
 *
 * 为什么单列一个 VM 测试：模拟机上的「清除 Key」看不出结果（`Secondary` 按钮的**禁用态与可用态同色**，
 * 截图分辨不了；`uiautomator` 对 Compose Button 也不报 `clickable`）——所以这条链在 VM 层钉死。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class AiSettingsViewModelTest {

    private val app: PennedApplication = ApplicationProvider.getApplicationContext()

    /** `viewModelScope` 跑在 Robolectric 的主 looper 上（paused）——每轮必须 idle() 才推得动写入。 */
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun <T> waitLlm(timeoutMs: Long = 5_000, block: suspend () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            idle()
            runBlocking { block() }?.let { return it }
            Thread.sleep(20)
        }
        error("等设置写回超时")
    }

    @Test
    fun `load 读出四样与总开关`(): Unit = runBlocking {
        app.settings.setLlmProvider(LlmProvider.CUSTOM)
        app.settings.setLlmEnabled(true)
        app.settings.setLlmBaseUrl(LlmProvider.CUSTOM, "https://a.example/v1")
        app.settings.setLlmApiKey(LlmProvider.CUSTOM, "sk-x")
        app.settings.setLlmModel(LlmProvider.CUSTOM, "glm-4.6")
        app.settings.setLlmVisionModel(LlmProvider.CUSTOM, "glm-4v")

        val state = AiSettingsViewModel(app).load()
        assertEquals(true, state.enabled)
        assertEquals(LlmProvider.CUSTOM, state.provider)
        assertEquals("https://a.example/v1", state.baseUrl)
        assertEquals("sk-x", state.apiKey)
        assertEquals("glm-4.6", state.model)
        assertEquals("glm-4v", state.visionModel)
        assertEquals(true, state.configured)
        assertNull(state.urlError)
    }

    @Test
    fun `默认是关的且没配置`(): Unit = runBlocking {
        app.settings.setLlmEnabled(false)
        app.settings.setLlmProvider(null)
        LlmProvider.entries.forEach { provider ->
            app.settings.setLlmBaseUrl(provider, null)
            app.settings.setLlmApiKey(provider, null)
            app.settings.setLlmModel(provider, null)
            app.settings.setLlmVisionModel(provider, null)
        }

        val state = AiSettingsViewModel(app).load()
        assertEquals(false, state.enabled)
        assertEquals(LlmProvider.DEFAULT, state.provider)
        assertEquals("", state.baseUrl)
        assertEquals("", state.apiKey)
        assertEquals(false, state.configured)
    }

    @Test
    fun `改动写回当前那一家`(): Unit = runBlocking {
        val vm = AiSettingsViewModel(app)
        vm.saveBaseUrl(LlmProvider.OPENCODE_ZEN, "https://b.example/v1")
        vm.saveApiKey(LlmProvider.OPENCODE_ZEN, "sk-y")
        vm.saveModel(LlmProvider.OPENCODE_ZEN, "m2")
        vm.saveVisionModel(LlmProvider.OPENCODE_ZEN, "v2")
        vm.setEnabled(true)

        waitLlm { app.settings.llmFields(LlmProvider.OPENCODE_ZEN).baseUrl?.takeIf { it == "https://b.example/v1" } }
        waitLlm { app.settings.llmFields(LlmProvider.OPENCODE_ZEN).apiKey?.takeIf { it == "sk-y" } }
        waitLlm { app.settings.llmFields(LlmProvider.OPENCODE_ZEN).model?.takeIf { it == "m2" } }
        waitLlm { app.settings.llmFields(LlmProvider.OPENCODE_ZEN).visionModel?.takeIf { it == "v2" } }
        waitLlm<Boolean> { if (app.settings.llmEnabled.first()) true else null }
        // 别家一个字没动
        assertNull(app.settings.llmFields(LlmProvider.DEEPSEEK).baseUrl)
    }

    @Test
    fun `切换供应商后 load 读到的是那一家的`(): Unit = runBlocking {
        app.settings.setLlmBaseUrl(LlmProvider.DEEPSEEK, "https://api.deepseek.com")
        app.settings.setLlmApiKey(LlmProvider.DEEPSEEK, "sk-deep")
        app.settings.setLlmModel(LlmProvider.DEEPSEEK, "deepseek-flash")

        app.settings.setLlmProvider(LlmProvider.OPENCODE_GO)
        val go = AiSettingsViewModel(app).load()
        assertEquals(LlmProvider.OPENCODE_GO, go.provider)
        assertEquals("", go.baseUrl)

        app.settings.setLlmProvider(LlmProvider.DEEPSEEK)
        val deepseek = AiSettingsViewModel(app).load()
        assertEquals(LlmProvider.DEEPSEEK, deepseek.provider)
        assertEquals("https://api.deepseek.com", deepseek.baseUrl)
        assertEquals("sk-deep", deepseek.apiKey)
        assertEquals("deepseek-flash", deepseek.model)
    }

    @Test
    fun `load 会把老配置认领到对应的家`(): Unit = runBlocking {
        app.settings.setLlmProvider(null)
        LlmProvider.entries.forEach { app.settings.setLlmApiKey(it, null) }
        app.settings.setLlmBaseUrl(LlmProvider.CUSTOM, "https://opencode.ai/zen/v1")
        app.settings.setLlmApiKey(LlmProvider.CUSTOM, "sk-legacy")
        app.settings.setLlmModel(LlmProvider.CUSTOM, "deepseek-v4.1-flash")

        val state = AiSettingsViewModel(app).load()
        assertEquals(LlmProvider.OPENCODE_ZEN, state.provider)
        assertEquals("https://opencode.ai/zen/v1", state.baseUrl)
        assertEquals("sk-legacy", state.apiKey)
    }

    @Test
    fun `清除 Key 真的从存储里删掉 且只清那一家`(): Unit = runBlocking {
        val vm = AiSettingsViewModel(app)
        vm.saveApiKey(LlmProvider.DEEPSEEK, "sk-clear-me")
        vm.saveApiKey(LlmProvider.OPENCODE_ZEN, "sk-keep-me")
        waitLlm { app.settings.llmFields(LlmProvider.DEEPSEEK).apiKey?.takeIf { it == "sk-clear-me" } }
        waitLlm { app.settings.llmFields(LlmProvider.OPENCODE_ZEN).apiKey?.takeIf { it == "sk-keep-me" } }

        vm.clearKey(LlmProvider.DEEPSEEK)
        waitLlm { if (app.settings.llmFields(LlmProvider.DEEPSEEK).apiKey == null) true else null }
        assertNull(app.settings.llmFields(LlmProvider.DEEPSEEK).apiKey)
        assertEquals("sk-keep-me", app.settings.llmFields(LlmProvider.OPENCODE_ZEN).apiKey)
        // 测试结果行回到空（使用者能接着换 Key 重试）
        assertEquals(TestState.Idle, vm.test.value)
    }

    @Test
    fun `清空端点等于没配过`(): Unit = runBlocking {
        val vm = AiSettingsViewModel(app)
        vm.saveBaseUrl(LlmProvider.CUSTOM, "https://c.example/v1")
        waitLlm { app.settings.llmFields(LlmProvider.CUSTOM).baseUrl?.takeIf { it == "https://c.example/v1" } }

        vm.saveBaseUrl(LlmProvider.CUSTOM, "")
        waitLlm { if (app.settings.llmFields(LlmProvider.CUSTOM).baseUrl == null) true else null }
        assertNull(app.settings.llmFields(LlmProvider.CUSTOM).baseUrl)
    }
}
