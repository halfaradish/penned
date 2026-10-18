package dev.penned.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.domain.LlmProvider
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
 * M4 的智能设置项（M4-0）：键一次到位，**AI 与自动记账默认都是关的**（规格 §二·19 / R3 / R5）；
 * 供应商分区（D113）：四样配置每家各存一份、切家不串台、老配置按地址认领。
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

    /** 把 AI 这块复位成"没配过、没选过供应商"（老键与各家槽位一起清）。 */
    private suspend fun resetLlm() {
        settings.setLlmEnabled(false)
        settings.setLlmProvider(null)
        LlmProvider.entries.forEach { provider ->
            settings.setLlmBaseUrl(provider, null)
            settings.setLlmApiKey(provider, null)
            settings.setLlmModel(provider, null)
            settings.setLlmVisionModel(provider, null)
        }
    }

    @Test
    fun `AI 与自动记账默认关 且没有 Key 与端点`() = runTest {
        resetLlm()
        settings.setAutoEntryEnabled(false)
        settings.setAutoEntryPackages("")

        assertFalse(settings.llmEnabled.first())
        assertFalse(settings.autoEntryEnabled.first())
        assertNull(settings.llmProvider.first())
        assertEquals(LlmProvider.CUSTOM, LlmProvider.fromId(settings.llmProvider.first()))
        assertEquals(LlmFields(), settings.llmFields(LlmProvider.CUSTOM))
        assertNull(settings.activeLlmConfig())
        assertEquals("", settings.autoEntryPackages.first())
    }

    @Test
    fun `开关与配置读写往返`() = runTest {
        resetLlm()
        settings.setLlmProvider(LlmProvider.CUSTOM)
        settings.setLlmEnabled(true)
        settings.setLlmBaseUrl(LlmProvider.CUSTOM, "https://open.bigmodel.cn/api/paas/v4")
        settings.setLlmApiKey(LlmProvider.CUSTOM, "sk-test-123")
        settings.setLlmModel(LlmProvider.CUSTOM, "glm-4.6")
        settings.setLlmVisionModel(LlmProvider.CUSTOM, "glm-4v")
        settings.setAutoEntryEnabled(true)
        settings.setAutoEntryPackages("com.tencent.mm,com.eg.android.AlipayGphone")

        assertTrue(settings.llmEnabled.first())
        assertEquals("custom", settings.llmProvider.first())
        assertEquals(
            LlmFields(
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                apiKey = "sk-test-123",
                model = "glm-4.6",
                visionModel = "glm-4v",
            ),
            settings.llmFields(LlmProvider.CUSTOM),
        )
        assertTrue(settings.autoEntryEnabled.first())
        assertEquals("com.tencent.mm,com.eg.android.AlipayGphone", settings.autoEntryPackages.first())
    }

    @Test
    fun `清除 Key 与端点回到未配置`() = runTest {
        resetLlm()
        settings.setLlmApiKey(LlmProvider.CUSTOM, "sk-test-456")
        settings.setLlmBaseUrl(LlmProvider.CUSTOM, "https://example.com/v1")
        assertEquals("sk-test-456", settings.llmFields(LlmProvider.CUSTOM).apiKey)

        settings.setLlmApiKey(LlmProvider.CUSTOM, null)
        settings.setLlmBaseUrl(LlmProvider.CUSTOM, null)
        assertEquals(LlmFields(), settings.llmFields(LlmProvider.CUSTOM))
        // 视觉模型没配过 = 跟文本模型（M4-2 取兜底）
        assertNull(settings.llmFields(LlmProvider.CUSTOM).visionModel)
    }

    @Test
    fun `关掉总开关不丢配置`() = runTest {
        resetLlm()
        settings.setLlmBaseUrl(LlmProvider.CUSTOM, "https://example.com/v1")
        settings.setLlmApiKey(LlmProvider.CUSTOM, "sk-test-789")
        settings.setLlmEnabled(false)

        assertFalse(settings.llmEnabled.first())
        // 关掉只是不发请求，Key 与端点还在（下次打开不用重填）
        val fields = settings.llmFields(LlmProvider.CUSTOM)
        assertEquals("sk-test-789", fields.apiKey)
        assertEquals("https://example.com/v1", fields.baseUrl)
    }

    // ---- 供应商分区（D113）----

    @Test
    fun `每家各存一份 切家不串台`() = runTest {
        resetLlm()
        settings.setLlmBaseUrl(LlmProvider.DEEPSEEK, "https://api.deepseek.com")
        settings.setLlmApiKey(LlmProvider.DEEPSEEK, "sk-deep")
        settings.setLlmModel(LlmProvider.DEEPSEEK, "deepseek-flash")
        settings.setLlmBaseUrl(LlmProvider.OPENCODE_ZEN, "https://opencode.ai/zen/v1")
        settings.setLlmApiKey(LlmProvider.OPENCODE_ZEN, "sk-zen")
        settings.setLlmModel(LlmProvider.OPENCODE_ZEN, "deepseek-v4.1-flash")

        // 来回切几次，"当前是哪家"变了，两家自己的值一个字不动
        settings.setLlmProvider(LlmProvider.DEEPSEEK)
        assertEquals("sk-deep", settings.llmFields(LlmProvider.DEEPSEEK).apiKey)
        settings.setLlmProvider(LlmProvider.OPENCODE_ZEN)
        assertEquals("sk-zen", settings.llmFields(LlmProvider.OPENCODE_ZEN).apiKey)
        assertEquals("deepseek-flash", settings.llmFields(LlmProvider.DEEPSEEK).model)
        // 没配过的两家仍是空的
        assertEquals(LlmFields(), settings.llmFields(LlmProvider.OPENCODE_GO))
        assertEquals(LlmFields(), settings.llmFields(LlmProvider.CUSTOM))
    }

    @Test
    fun `清除 Key 只清当前那家`() = runTest {
        resetLlm()
        settings.setLlmApiKey(LlmProvider.DEEPSEEK, "sk-deep")
        settings.setLlmApiKey(LlmProvider.OPENCODE_ZEN, "sk-zen")

        settings.setLlmApiKey(LlmProvider.DEEPSEEK, null)
        assertNull(settings.llmFields(LlmProvider.DEEPSEEK).apiKey)
        assertEquals("sk-zen", settings.llmFields(LlmProvider.OPENCODE_ZEN).apiKey)
    }

    // ---- 老配置认领（D113）----

    @Test
    fun `老配置按地址认领到对应的家_老键不删`() = runTest {
        resetLlm()
        // M4 那份扁平配置（= 自定义槽位 + 没写过"当前供应商"）
        settings.setLlmBaseUrl(LlmProvider.CUSTOM, "https://api.deepseek.com")
        settings.setLlmApiKey(LlmProvider.CUSTOM, "sk-legacy")
        settings.setLlmModel(LlmProvider.CUSTOM, "deepseek-flash")

        settings.adoptLegacyLlmConfigIfNeeded()

        assertEquals("deepseek", settings.llmProvider.first())
        val deepseek = settings.llmFields(LlmProvider.DEEPSEEK)
        assertEquals("https://api.deepseek.com", deepseek.baseUrl)
        assertEquals("sk-legacy", deepseek.apiKey)
        assertEquals("deepseek-flash", deepseek.model)
        // 老键留原样（用户切回"自定义"还看得见）
        assertEquals("sk-legacy", settings.llmFields(LlmProvider.CUSTOM).apiKey)
    }

    @Test
    fun `认领只做一次 且只补空不覆盖`() = runTest {
        resetLlm()
        settings.setLlmBaseUrl(LlmProvider.CUSTOM, "https://api.deepseek.com")
        settings.setLlmApiKey(LlmProvider.CUSTOM, "sk-legacy")
        // 目标家自己已经有模型了 —— 不该被老配置覆盖
        settings.setLlmModel(LlmProvider.DEEPSEEK, "deepseek-v4-pro")

        settings.adoptLegacyLlmConfigIfNeeded()
        assertEquals("deepseek-v4-pro", settings.llmFields(LlmProvider.DEEPSEEK).model)
        assertEquals("sk-legacy", settings.llmFields(LlmProvider.DEEPSEEK).apiKey)

        // 第二趟换用户手输的地址 —— "选过"这标记已写上，认领不再发生
        settings.setLlmBaseUrl(LlmProvider.CUSTOM, "https://opencode.ai/zen/v1")
        settings.adoptLegacyLlmConfigIfNeeded()
        assertEquals("deepseek", settings.llmProvider.first())
    }

    @Test
    fun `认不出的地址留在自定义`() = runTest {
        resetLlm()
        settings.setLlmBaseUrl(LlmProvider.CUSTOM, "https://open.bigmodel.cn/api/paas/v4")
        settings.setLlmApiKey(LlmProvider.CUSTOM, "sk-legacy")

        settings.adoptLegacyLlmConfigIfNeeded()
        assertEquals("custom", settings.llmProvider.first())
        assertEquals("sk-legacy", settings.llmFields(LlmProvider.CUSTOM).apiKey)
    }

    @Test
    fun `没有老配置就不认领`() = runTest {
        resetLlm()
        settings.adoptLegacyLlmConfigIfNeeded()
        assertNull(settings.llmProvider.first())
    }

    // ---- 当前供应商的一份可用配置（两条消费路径共用）----

    @Test
    fun `activeLlmConfig 要开着且配齐 才给配置`() = runTest {
        resetLlm()
        settings.setLlmBaseUrl(LlmProvider.DEEPSEEK, "https://api.deepseek.com")
        settings.setLlmApiKey(LlmProvider.DEEPSEEK, "sk-deep")
        settings.setLlmModel(LlmProvider.DEEPSEEK, "deepseek-flash")
        settings.setLlmProvider(LlmProvider.DEEPSEEK)

        assertNull(settings.activeLlmConfig()) // 总开关关着 → 一个包都不发

        settings.setLlmEnabled(true)
        assertEquals("https://api.deepseek.com", settings.activeLlmConfig()?.baseUrl)
        assertEquals("deepseek-flash", settings.activeLlmConfig()?.model)
        // 视觉模型留空 = 跟文本模型
        assertEquals("deepseek-flash", settings.activeLlmConfig()?.effectiveVisionModel)

        // 换到没配过的家 → 又是 null
        settings.setLlmProvider(LlmProvider.OPENCODE_GO)
        assertNull(settings.activeLlmConfig())
    }

    @Test
    fun `activeLlmConfig 明文 http 一个包都不发`() = runTest {
        resetLlm()
        settings.setLlmEnabled(true)
        settings.setLlmProvider(LlmProvider.CUSTOM)
        settings.setLlmApiKey(LlmProvider.CUSTOM, "sk-x")
        settings.setLlmModel(LlmProvider.CUSTOM, "m")
        settings.setLlmBaseUrl(LlmProvider.CUSTOM, "http://127.0.0.1:8080/v1")
        assertNull(settings.activeLlmConfig())
    }
}
