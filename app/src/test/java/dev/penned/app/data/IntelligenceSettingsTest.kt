package dev.penned.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M4 的智能设置项（M4-0）：键一次到位，**AI 与自动记账默认都是关的**（规格 §二·19 / R3 / R5）。
 *
 * 注意：`preferencesDataStore` 是**进程内静态单例**，同一个 JVM 里别的测试可能已经写过这些键，
 * 所以每条用例先显式复位（既有先例：ImportRepositoryTest 里"DataStore 单例在同类测试间共享"）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class IntelligenceSettingsTest {

    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settings = SettingsRepository(context)
    }

    @Test
    fun `AI 与自动记账默认关 且没有 Key 与端点`() = runTest {
        settings.setLlmEnabled(false)
        settings.setAutoEntryEnabled(false)
        settings.setLlmBaseUrl(null)
        settings.setLlmApiKey(null)
        settings.setLlmModel(null)
        settings.setLlmVisionModel(null)
        settings.setAutoEntryPackages("")

        assertFalse(settings.llmEnabled.first())
        assertFalse(settings.autoEntryEnabled.first())
        assertNull(settings.llmBaseUrl.first())
        assertNull(settings.llmApiKey.first())
        assertNull(settings.llmModel.first())
        assertNull(settings.llmVisionModel.first())
        assertEquals("", settings.autoEntryPackages.first())
    }

    @Test
    fun `开关与配置读写往返`() = runTest {
        settings.setLlmEnabled(true)
        settings.setLlmBaseUrl("https://open.bigmodel.cn/api/paas/v4")
        settings.setLlmApiKey("sk-test-123")
        settings.setLlmModel("glm-4.6")
        settings.setLlmVisionModel("glm-4v")
        settings.setAutoEntryEnabled(true)
        settings.setAutoEntryPackages("com.tencent.mm,com.eg.android.AlipayGphone")

        assertTrue(settings.llmEnabled.first())
        assertTrue(settings.autoEntryEnabled.first())
        assertEquals("https://open.bigmodel.cn/api/paas/v4", settings.llmBaseUrl.first())
        assertEquals("sk-test-123", settings.llmApiKey.first())
        assertEquals("glm-4.6", settings.llmModel.first())
        assertEquals("glm-4v", settings.llmVisionModel.first())
        assertEquals("com.tencent.mm,com.eg.android.AlipayGphone", settings.autoEntryPackages.first())
    }

    @Test
    fun `清除 Key 与端点回到未配置`() = runTest {
        settings.setLlmApiKey("sk-test-456")
        settings.setLlmBaseUrl("https://example.com/v1")
        assertEquals("sk-test-456", settings.llmApiKey.first())

        settings.setLlmApiKey(null)
        settings.setLlmBaseUrl(null)
        assertNull(settings.llmApiKey.first())
        assertNull(settings.llmBaseUrl.first())
        assertNull(settings.llmVisionModel.first()) // 视觉模型没配过 = 跟文本模型（M4-2 取兜底）
    }

    @Test
    fun `关掉总开关不丢配置`() = runTest {
        settings.setLlmBaseUrl("https://example.com/v1")
        settings.setLlmApiKey("sk-test-789")
        settings.setLlmEnabled(false)

        assertFalse(settings.llmEnabled.first())
        // 关掉只是不发请求，Key 与端点还在（下次打开不用重填）
        assertEquals("sk-test-789", settings.llmApiKey.first())
        assertEquals("https://example.com/v1", settings.llmBaseUrl.first())
    }
}
