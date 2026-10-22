package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 供应商预置（D113）：四档的预置值必须**自己就是合法配置**（地址过 https 校验、模型非空），
 * id 唯一且能被认回来，老配置的归属推断按地址表走。
 *
 * 纯函数测试，不挂 Robolectric（不碰 Android 类）。
 */
class LlmProviderTest {

    @Test
    fun `预置地址都能过 https 校验`() {
        LlmProvider.entries.filter { it.presetBaseUrl != null }.forEach { provider ->
            assertNull(
                "${provider.label} 的预置地址不是 https 或写错了：${provider.presetBaseUrl}",
                LlmEndpoints.validateUrl(provider.presetBaseUrl),
            )
        }
    }

    @Test
    fun `预置了地址就预置模型 名字都不带空格`() {
        LlmProvider.entries.forEach { provider ->
            if (provider.presetBaseUrl != null) {
                assertNotNull("${provider.label} 有预置地址却没预置模型", provider.presetModel)
            }
            listOfNotNull(provider.presetBaseUrl, provider.presetModel).forEach {
                assertEquals("${provider.label} 的预置值两端有空白", it.trim(), it)
            }
            assertTrue("${provider.label} 的提示是空的", provider.hint.isNotBlank())
        }
    }

    @Test
    fun `四档的 id 唯一且能原样认回来`() {
        val ids = LlmProvider.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertEquals(LlmProvider.fromId(it).id, it) }
    }

    @Test
    fun `认不出的 id 落到自定义`() {
        assertEquals(LlmProvider.CUSTOM, LlmProvider.fromId(null))
        assertEquals(LlmProvider.CUSTOM, LlmProvider.fromId(""))
        assertEquals(LlmProvider.CUSTOM, LlmProvider.fromId("opencode"))
        assertEquals(LlmProvider.CUSTOM, LlmProvider.fromId("openai"))
        assertEquals(LlmProvider.DEFAULT, LlmProvider.CUSTOM)
    }

    @Test
    fun `只有 Go 要求会话头_地址是文档给的 OpenAI 兼容基址`() {
        // D116：Go 的 OpenAI 兼容基址在 /inference/go/openai/v1（不是旧预置的 /zen/go/v1）
        assertEquals("https://opencode.ai/inference/go/openai/v1", LlmProvider.OPENCODE_GO.presetBaseUrl)
        assertEquals("x-opencode-session", LlmProvider.OPENCODE_GO.sessionHeader)
        LlmProvider.entries.filter { it != LlmProvider.OPENCODE_GO }.forEach {
            assertNull("${it.label} 不该要求会话头", it.sessionHeader)
        }
        // 旧预置仍能被认回 Go（D113 装过的用户要能平滑升级）
        assertEquals(
            LlmProvider.OPENCODE_GO,
            LlmProvider.inferByBaseUrl(LlmProvider.LEGACY_OPENCODE_GO_BASE_URL),
        )
        assertTrue(LlmProvider.LEGACY_OPENCODE_GO_BASE_URL != LlmProvider.OPENCODE_GO.presetBaseUrl)
    }

    @Test
    fun `老配置按地址认领_表驱动`() {
        val cases = listOf(
            "https://api.deepseek.com" to LlmProvider.DEEPSEEK,
            "https://api.deepseek.com/v1" to LlmProvider.DEEPSEEK,
            "https://API.DeepSeek.com/" to LlmProvider.DEEPSEEK,
            "https://opencode.ai/zen/v1" to LlmProvider.OPENCODE_ZEN,
            "https://opencode.ai/zen/go/v1" to LlmProvider.OPENCODE_GO,
            // 文档里给的另一种写法（/inference/go/openai/v1）也归 Go
            "https://opencode.ai/inference/go/openai/v1" to LlmProvider.OPENCODE_GO,
            "https://open.bigmodel.cn/api/paas/v4" to LlmProvider.CUSTOM,
            "https://example.com/v1" to LlmProvider.CUSTOM,
            "" to LlmProvider.CUSTOM,
            null to LlmProvider.CUSTOM,
        )
        cases.forEach { (url, expected) ->
            assertEquals("地址 $url 应归 ${expected.label}", expected, LlmProvider.inferByBaseUrl(url))
        }
    }
}
