package dev.penned.app.data.llm

import dev.penned.app.domain.LlmConfig
import dev.penned.app.domain.LlmConfigError
import dev.penned.app.domain.LlmDraft
import dev.penned.app.domain.LlmDraftParser
import dev.penned.app.domain.LlmEndpoints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/** 一次调用的结果：要么是解析好的草稿，要么是一个**能落到不同文案**的原因（规格 §三 M4-2）。 */
sealed interface LlmResult {
    data class Ok(val draft: LlmDraft, val model: String) : LlmResult
    data class Err(val reason: LlmError) : LlmResult
}

/**
 * 只要"模型回的那段 JSON 原文"的结果（M4-6 的两条导入建议用）。
 *
 * 与 [LlmResult] 分开是因为出参形态不同：那边严格校验"一笔账"（缺金额即失败），
 * 这边的建议 JSON 长的是另一个样子（字段→列名 / 商户→分类名），得由各自的纯函数去校验。
 */
sealed interface LlmContentResult {
    data class Ok(val content: String, val model: String) : LlmContentResult
    data class Err(val reason: LlmError) : LlmContentResult
}

/** 图像识别的出参：**一批账**（账单列表截图，D111）。 */
sealed interface LlmRowsResult {
    data class Ok(val rows: dev.penned.app.domain.LlmRows, val model: String) : LlmRowsResult
    data class Err(val reason: LlmError) : LlmRowsResult
}

enum class LlmError {
    /** 地址 / Key / 模型没配齐（或总开关关着）。 */
    NOT_CONFIGURED,
    INSECURE_URL,
    MALFORMED_URL,
    /** 网络不可达 / DNS / 连接被拒。 */
    NETWORK,
    TIMEOUT,
    UNAUTHORIZED,
    FORBIDDEN,
    RATE_LIMITED,
    /** 服务端 5xx 或其它非 2xx。 */
    SERVER,
    /** 回来了但不是能用的 JSON（闲聊 / 缺字段 / 缺金额）。 */
    BAD_RESPONSE,
}

/**
 * BYOK 的 LLM 客户端（M4-2，规格 §三 M4-2 / D058）。
 *
 * - 形态：OpenAI 兼容 `POST {base}/chat/completions` + `Authorization: Bearer`；文本与图像共用一份
 *   出参 schema（图像走 `image_url` 的 data URL）
 * - 超时：连接 10s / 读 60s（图像调用慢）；协程可取消
 * - **只认 `choices[0].message.content` → JSON**：模型回的闲聊、多余字段一律丢弃（`LlmDraft.parse`）
 * - 不重试（重试 = 用户点）、不流式、不留调用记录
 *
 * [okHttp] 由调用方注入：生产给普通客户端、测试给假服务器（M2 `FakeHttpServer` 先例）。
 * 本类**不读设置**：配置由调用方（ViewModel/页面）从 `SettingsRepository` 取好后传进来。
 */
class LlmClient(private val okHttp: OkHttpClient = defaultClient()) {

    /** 文本入参（一句话 / 表头 / 商户名列表等）→ 严格按"一笔账"的 schema 校验。 */
    suspend fun extractText(config: LlmConfig, prompt: String): LlmResult =
        when (val result = complete(config, prompt)) {
            is LlmContentResult.Ok ->
                dev.penned.app.domain.LlmDraftParser.parse(result.content)
                    ?.let { LlmResult.Ok(it, result.model) }
                    ?: LlmResult.Err(LlmError.BAD_RESPONSE)

            is LlmContentResult.Err -> LlmResult.Err(result.reason)
        }

    /** 只取模型回的 JSON 原文（建议类出参自己校验；同一条 HTTP 链路）。 */
    suspend fun complete(config: LlmConfig, prompt: String): LlmContentResult =
        call(config, model = config.model, prompt = prompt, imageJpegBase64 = null, imageDetail = null)

    /**
     * 图像入参（截图导入）：[jpegBase64] 是已压缩过的 JPEG base64（不带 data 前缀）。
     *
     * 出参是**一批账**（`LlmRowsParser`）——账单列表截图一屏十几行，一笔一个对象；
     * 单笔支付截图就是"一批里的一条"。解析不出 JSON 结构 → `BAD_RESPONSE`；
     * 解析出了但 `rows` 为空 = 模型说"这不是账单"（调用方给"没看出这是账单"的文案）。
     */
    suspend fun extractImageRows(config: LlmConfig, prompt: String, jpegBase64: String): LlmRowsResult =
        when (
            val result = call(
                config, model = config.effectiveVisionModel, prompt = prompt,
                imageJpegBase64 = jpegBase64, imageDetail = "high",
            )
        ) {
            is LlmContentResult.Ok ->
                dev.penned.app.domain.LlmRowsParser.parse(result.content)
                    ?.let { LlmRowsResult.Ok(it, result.model) }
                    ?: LlmRowsResult.Err(LlmError.BAD_RESPONSE)

            is LlmContentResult.Err -> LlmRowsResult.Err(result.reason)
        }

    /**
     * 「测试连接」：发一句最省 token 的话，只关心**通不通**。
     * 回 null = 通；否则给原因（页面按原因给文案）。
     */
    suspend fun testConnection(config: LlmConfig): LlmError? =
        when (val result = extractText(config, TEST_PROMPT)) {
            is LlmResult.Ok -> null
            is LlmResult.Err -> result.reason
        }

    private suspend fun call(
        config: LlmConfig,
        model: String,
        prompt: String,
        imageJpegBase64: String?,
        imageDetail: String?,
    ): LlmContentResult = withContext(Dispatchers.IO) {
        if (LlmEndpoints.validate(config.baseUrl, config.apiKey, config.model) != null) {
            return@withContext LlmContentResult.Err(
                LlmEndpoints.validate(config.baseUrl, config.apiKey, config.model)
                    ?.toLlmError() ?: LlmError.NOT_CONFIGURED
            )
        }
        try {
            val response = okHttp.newCall(request(config, model, prompt, imageJpegBase64, imageDetail))
                .execute()
            response.use {
                val text = it.body?.string().orEmpty()
                when {
                    it.code == 401 -> LlmContentResult.Err(LlmError.UNAUTHORIZED)
                    it.code == 403 -> LlmContentResult.Err(LlmError.FORBIDDEN)
                    it.code == 429 -> LlmContentResult.Err(LlmError.RATE_LIMITED)
                    it.code !in 200..299 -> LlmContentResult.Err(LlmError.SERVER)
                    else -> contentOf(text)?.let { content -> LlmContentResult.Ok(content, model) }
                        ?: LlmContentResult.Err(LlmError.BAD_RESPONSE)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: InterruptedIOException) {
            LlmContentResult.Err(LlmError.TIMEOUT)
        } catch (_: java.net.SocketTimeoutException) {
            LlmContentResult.Err(LlmError.TIMEOUT)
        } catch (_: Exception) {
            LlmContentResult.Err(LlmError.NETWORK)
        }
    }

    private fun request(
        config: LlmConfig,
        model: String,
        prompt: String,
        imageJpegBase64: String?,
        imageDetail: String?,
    ): Request {
        val content = if (imageJpegBase64 == null) {
            JSONObject().put("type", "text").put("text", prompt)
        } else {
            JSONObject()
                .put("type", "image_url")
                .put(
                    "image_url",
                    JSONObject()
                        .put("url", "data:image/jpeg;base64,$imageJpegBase64")
                        .apply { if (imageDetail != null) put("detail", imageDetail) }
                )
        }
        val messages = JSONArray().put(
            JSONObject().put("role", "user").put("content", JSONArray().put(content))
        )
        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0)
            // 只要它回一段 JSON，不要闲聊（本地照样强校验）
            .put("response_format", JSONObject().put("type", "json_object"))

        return Request.Builder()
            .url(config.chatCompletionsUrl)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
    }

    /** `choices[0].message.content`（缺任一环 = null）。 */
    private fun contentOf(body: String): String? = try {
        JSONObject(body)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun LlmConfigError.toLlmError(): LlmError = when (this) {
        LlmConfigError.NOT_CONFIGURED -> LlmError.NOT_CONFIGURED
        LlmConfigError.INSECURE_URL -> LlmError.INSECURE_URL
        LlmConfigError.MALFORMED_URL -> LlmError.MALFORMED_URL
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** 「测试连接」用的最小 prompt。 */
        internal const val TEST_PROMPT = "只回一个 JSON：{\"type\":\"EXPENSE\",\"amount\":0.01}"

        /** 连接 10s / 读 60s（图像调用慢），与规格一致。 */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
