package dev.penned.app.domain

/**
 * BYOK 的配置与端点校验（M4-2，规格 §二·5 / 裁决 C2）。
 *
 * 纯函数：只做"能不能发、发到哪"的判定，不碰网络与设置存储。UI 与 `LlmClient` 共用同一份口径，
 * 免得"页面让填、客户端拒发"这类两套判断。
 */
enum class LlmConfigError {
    /** 总开关开着但三样没配齐（地址 / Key / 模型）。 */
    NOT_CONFIGURED,

    /** 地址不是 https（release 禁明文，填 http 直接连不上——C2）。 */
    INSECURE_URL,

    /** 地址不是合法 URL。 */
    MALFORMED_URL,
}

/** 一份可用的 BYOK 配置（地址已归一：去尾斜杠、去空白）。 */
data class LlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    /** 视觉模型：空 = 跟 [model]（同一家的多模态型号常常就是同一个名字）。 */
    val visionModel: String?,
    /**
     * 供应商网关要求的额外请求头（D116）：目前只有 OpenCode Go 的 `x-opencode-session`。
     * 空 = 只发标准的 Authorization / Content-Type。
     */
    val extraHeaders: Map<String, String> = emptyMap(),
) {
    /** OpenAI 兼容的对话端点。 */
    val chatCompletionsUrl: String get() = "$baseUrl/chat/completions"

    /** 图像调用用哪个模型。 */
    val effectiveVisionModel: String get() = visionModel?.takeIf { it.isNotBlank() } ?: model

    /** 给页面明示"这张图将发给谁"用的域名（解析不出就给归一后的地址）。 */
    val host: String get() = baseUrl.substringAfter("://").substringBefore('/')
}

object LlmEndpoints {

    /**
     * 校验三样配置；齐了返回 null（= 可用），否则给一个可读原因。
     * [enabled] = 总开关：关着时**不报错**（页面显示"未开启"，由 UI 决定文案）。
     */
    fun validate(baseUrl: String?, apiKey: String?, model: String?): LlmConfigError? {
        val url = baseUrl?.trim().orEmpty()
        val key = apiKey?.trim().orEmpty()
        val name = model?.trim().orEmpty()
        if (url.isEmpty() || key.isEmpty() || name.isEmpty()) return LlmConfigError.NOT_CONFIGURED
        return validateUrl(url)
    }

    /** 只校验地址（设置页边填边校验用）。合法则返回 null。 */
    fun validateUrl(baseUrl: String?): LlmConfigError? {
        val url = baseUrl?.trim().orEmpty()
        if (url.isEmpty()) return LlmConfigError.NOT_CONFIGURED
        if (url.startsWith("http://", ignoreCase = true)) return LlmConfigError.INSECURE_URL
        if (!url.startsWith("https://", ignoreCase = true)) return LlmConfigError.MALFORMED_URL
        // https:// 之后必须有主机名（https:// 或 https:///v1 这类不算）
        val host = url.removePrefix("https://").substringBefore('/')
        if (host.isBlank()) return LlmConfigError.MALFORMED_URL
        return null
    }

    /** 归一一份可用配置（调用方已过 [validate]）。 */
    fun config(
        baseUrl: String,
        apiKey: String,
        model: String,
        visionModel: String?,
        extraHeaders: Map<String, String> = emptyMap(),
    ): LlmConfig =
        LlmConfig(
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiKey = apiKey.trim(),
            model = model.trim(),
            visionModel = visionModel?.trim()?.takeIf { it.isNotEmpty() },
            extraHeaders = extraHeaders,
        )

    /** 校验失败时给用户看的一句话。 */
    fun message(error: LlmConfigError): String = when (error) {
        LlmConfigError.NOT_CONFIGURED -> "还没配好：服务地址、API Key、模型都要填"
        LlmConfigError.INSECURE_URL -> "只支持 https 地址：明文 http 在正式版里连不上"
        LlmConfigError.MALFORMED_URL -> "地址要形如 https://open.bigmodel.cn/api/paas/v4"
    }
}
