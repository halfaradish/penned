package dev.penned.app.domain

/**
 * BYOK 的「供应商」预置（D113）。四档：DeepSeek / OpenCode Zen / OpenCode Go / 自定义。
 *
 * 四家都是 **OpenAI 兼容**的 `POST {base}/chat/completions` + `Authorization: Bearer`
 * （与 [LlmConfig.chatCompletionsUrl] 的拼法一致），所以**客户端一行都不用为某家写分支**；
 * 这里只描述"预置什么"，不描述鉴权细节。
 *
 * 两条 OpenCode 地址取自其控制台「Base URL」栏（`/chat/completions` 拼在后面正好是 chat 路由）；
 * 但它的 GPT / Claude / Gemini 系列走的是 Responses / Anthropic / Google 协议，本客户端**够不着**——
 * 所以 [hint] 里把这条明说，免得用户填了那些模型名去撞 4xx（D112 的 `REJECTED` 文案就是给这种时候兜底）。
 *
 * **每家各存一份配置**：[id] 就是存储槽位（见 `SettingsRepository` 的 `llmFields/setLlm*`）；
 * [CUSTOM] 沿用 M4 的四个老键——老用户零迁移（旧配置的归属由 [inferByBaseUrl] 认领）。
 */
enum class LlmProvider(
    val id: String,
    val label: String,
    /** 预置地址；null = 不预置（自定义那档手填）。 */
    val presetBaseUrl: String?,
    /** 预置模型；null = 不预置。**只填默认值，字段照样可改**。 */
    val presetModel: String?,
    /** 设置页「供应商」行下面那行口径提示（只讲"这家有什么不一样"）。 */
    val hint: String,
    /**
     * 这家网关要求的**额外请求头**（头名；值由设置层生成稳定 ID）：null = 不要求。
     *
     * OpenCode Go 是唯一一家要的：缺 `x-opencode-session` 时它在鉴权**之前**就回 400
     * `MissingSessionID`（D116 实测），所以这个头不是可选项。
     */
    val sessionHeader: String? = null,
) {
    DEEPSEEK(
        id = "deepseek",
        label = "DeepSeek",
        presetBaseUrl = "https://api.deepseek.com",
        presetModel = "deepseek-flash",
        hint = "官方直连；模型填 deepseek-flash（能看图，截图识别可用）或 deepseek-v4-pro",
    ),
    OPENCODE_ZEN(
        id = "opencode-zen",
        label = "OpenCode Zen",
        presetBaseUrl = "https://opencode.ai/zen/v1",
        presetModel = "deepseek-v4.1-flash",
        hint = "按量网关；只支持 OpenAI 兼容那批模型，GPT / Claude / Gemini 走别的协议、用不了",
    ),
    OPENCODE_GO(
        id = "opencode-go",
        label = "OpenCode Go",
        // Go 的 OpenAI 兼容基址在 /inference/go/openai/v1；旧预置的 /zen/go/v1 会被 repair 换掉（D116）
        presetBaseUrl = "https://opencode.ai/inference/go/openai/v1",
        presetModel = "deepseek-v4.1-flash",
        hint = "订阅版网关（Go / Go Plus）；Key 要建在已订阅 Go 的 workspace 上，只走 OpenAI 兼容路由",
        sessionHeader = "x-opencode-session",
    ),
    CUSTOM(
        id = "custom",
        label = "自定义",
        presetBaseUrl = null,
        presetModel = null,
        hint = "任何 OpenAI 兼容端点：地址、模型、Key 都自己填",
    ),
    ;

    companion object {
        /** 没选过时的默认档（= 老用户那份扁平配置的落点）。 */
        val DEFAULT: LlmProvider = CUSTOM

        /**
         * D113 里 Go 预置错的地址（D116 换成 [OPENCODE_GO] 的正式地址）。
         * 只用来**精确匹配**旧值做一次性修复——用户自己填的其它地址一概不碰。
         */
        const val LEGACY_OPENCODE_GO_BASE_URL = "https://opencode.ai/zen/go/v1"

        /** 认不出的 id 一律落到 [DEFAULT]（存储里存的是 [id]，将来加/删档也不能炸）。 */
        fun fromId(id: String?): LlmProvider = entries.firstOrNull { it.id == id } ?: DEFAULT

        /**
         * 老配置归属推断（D113）：M4 只有一份扁平配置，第一次进 AI 设置页时按地址把它认领到对应的家。
         *
         * 只认**明确认得出**的（整段地址里含某个 host）：认不出的保持 [CUSTOM]，绝不猜。
         */
        fun inferByBaseUrl(baseUrl: String?): LlmProvider {
            val url = baseUrl?.trim()?.lowercase().orEmpty()
            return when {
                url.isEmpty() -> CUSTOM
                url.contains("api.deepseek.com") -> DEEPSEEK
                url.contains("opencode.ai") && url.contains("/go/") -> OPENCODE_GO
                url.contains("opencode.ai") -> OPENCODE_ZEN
                else -> CUSTOM
            }
        }
    }
}
