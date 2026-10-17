package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BYOK 配置与端点校验（M4-2，C2 裁决：**只接受 https**）。
 *
 * 这条口径要挡的是"填了 http 却连不上、还看不出为什么"——页面与 `LlmClient` 共用这一份判断，
 * 所以这里断的是"会不会让请求出去"。
 */
class LlmEndpointsTest {

    @Test
    fun `https 地址合法`() {
        assertNull(LlmEndpoints.validate("https://open.bigmodel.cn/api/paas/v4", "sk-x", "glm-4.6"))
        assertNull(LlmEndpoints.validate("https://api.deepseek.com", "sk-x", "deepseek-chat"))
        assertNull(LlmEndpoints.validate("https://example.com:8443/v1", "sk-x", "m"))
    }

    @Test
    fun `http 地址当场拒绝`() {
        assertEquals(
            LlmConfigError.INSECURE_URL,
            LlmEndpoints.validate("http://192.168.1.9:11434/v1", "sk-x", "qwen"),
        )
        assertEquals(LlmConfigError.INSECURE_URL, LlmEndpoints.validateUrl("http://example.com/v1"))
    }

    @Test
    fun `缺哪一样都是未配置`() {
        assertEquals(LlmConfigError.NOT_CONFIGURED, LlmEndpoints.validate(null, "sk", "m"))
        assertEquals(LlmConfigError.NOT_CONFIGURED, LlmEndpoints.validate("https://a.com", null, "m"))
        assertEquals(LlmConfigError.NOT_CONFIGURED, LlmEndpoints.validate("https://a.com", "sk", null))
        assertEquals(LlmConfigError.NOT_CONFIGURED, LlmEndpoints.validate("   ", "  ", "  "))
    }

    @Test
    fun `不是 http 也不是 https 的地址算格式错`() {
        assertEquals(LlmConfigError.MALFORMED_URL, LlmEndpoints.validate("open.bigmodel.cn/v4", "sk", "m"))
        assertEquals(LlmConfigError.MALFORMED_URL, LlmEndpoints.validateUrl("ftp://a.com"))
        assertEquals(LlmConfigError.MALFORMED_URL, LlmEndpoints.validateUrl("https://"))
    }

    @Test
    fun `归一：去空白与尾斜杠_端点拼得出`() {
        val config = LlmEndpoints.config("  https://a.com/v1/  ", " sk-x ", " glm-4.6 ", "  ")
        assertEquals("https://a.com/v1", config.baseUrl)
        assertEquals("sk-x", config.apiKey)
        assertEquals("glm-4.6", config.model)
        assertEquals("https://a.com/v1/chat/completions", config.chatCompletionsUrl)
        // 视觉模型留空 = 跟文本模型
        assertEquals("glm-4.6", config.effectiveVisionModel)
        assertEquals("a.com", config.host)
    }

    @Test
    fun `视觉模型填了就用它`() {
        val config = LlmEndpoints.config("https://a.com/v1", "k", "glm-4.6", "glm-4v")
        assertEquals("glm-4v", config.effectiveVisionModel)
    }

    @Test
    fun `每个失败原因都有一句话`() {
        LlmConfigError.entries.forEach { error ->
            assertEquals(true, LlmEndpoints.message(error).isNotBlank())
        }
    }
}
