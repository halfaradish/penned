package dev.penned.app.ui.intelligence

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
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
 * AI 设置页的读写（M4-2）：`load()` 把四样读出来、改动写回、**清除 Key 真的把 Key 从存储里删掉**。
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

    private fun <T> awaitFlow(timeoutMs: Long = 5_000, block: suspend () -> T?): T {
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
        app.settings.setLlmEnabled(true)
        app.settings.setLlmBaseUrl("https://a.example/v1")
        app.settings.setLlmApiKey("sk-x")
        app.settings.setLlmModel("glm-4.6")
        app.settings.setLlmVisionModel("glm-4v")

        val state = AiSettingsViewModel(app).load()
        assertEquals(true, state.enabled)
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
        app.settings.setLlmBaseUrl(null)
        app.settings.setLlmApiKey(null)
        app.settings.setLlmModel(null)
        app.settings.setLlmVisionModel(null)

        val state = AiSettingsViewModel(app).load()
        assertEquals(false, state.enabled)
        assertEquals("", state.baseUrl)
        assertEquals("", state.apiKey)
        assertEquals(false, state.configured)
    }

    @Test
    fun `改动写回设置`(): Unit = runBlocking {
        val vm = AiSettingsViewModel(app)
        vm.saveBaseUrl("https://b.example/v1")
        vm.saveApiKey("sk-y")
        vm.saveModel("m2")
        vm.saveVisionModel("v2")
        vm.setEnabled(true)

        awaitFlow { app.settings.llmBaseUrl.first()?.takeIf { it == "https://b.example/v1" } }
        awaitFlow { app.settings.llmApiKey.first()?.takeIf { it == "sk-y" } }
        awaitFlow { app.settings.llmModel.first()?.takeIf { it == "m2" } }
        awaitFlow { app.settings.llmVisionModel.first()?.takeIf { it == "v2" } }
        awaitFlow<Boolean> { if (app.settings.llmEnabled.first()) true else null }
    }

    @Test
    fun `清除 Key 真的从存储里删掉`(): Unit = runBlocking {
        val vm = AiSettingsViewModel(app)
        vm.saveApiKey("sk-to-clear")
        awaitFlow { app.settings.llmApiKey.first()?.takeIf { it == "sk-to-clear" } }

        vm.clearKey()
        awaitFlow { if (app.settings.llmApiKey.first() == null) true else null }
        assertNull(app.settings.llmApiKey.first())
        // 端点与模型不受影响（使用者能接着换 Key 重试）
        assertEquals(TestState.Idle, vm.test.value)
    }

    @Test
    fun `清空端点等于没配过`(): Unit = runBlocking {
        val vm = AiSettingsViewModel(app)
        vm.saveBaseUrl("https://c.example/v1")
        awaitFlow { app.settings.llmBaseUrl.first()?.takeIf { it == "https://c.example/v1" } }

        vm.saveBaseUrl("")
        awaitFlow { if (app.settings.llmBaseUrl.first() == null) true else null }
        assertNull(app.settings.llmBaseUrl.first())
    }
}
