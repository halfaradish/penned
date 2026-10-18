package dev.penned.app.data.llm

import dev.penned.app.data.update.FakeHttpServer
import dev.penned.app.domain.LlmEndpoints
import dev.penned.app.domain.TxType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * `LlmClient`（M4-2）：假服务器（M2 `FakeHttpServer` 先例，本片扩了请求体记录）覆盖
 * 200 正常 / 401 / 403 / 429 / 5xx / 非 JSON / JSON 缺字段 / 超时 / 非 https 不发请求。
 *
 * 顺带钉住请求形态：`POST {base}/chat/completions` + `Authorization: Bearer` + `model` 字段，
 * 且**请求体里只有提示词与模型**（外发边界的雏形；M4-6 用它断"金额/时间/单号不出设备"）。
 *
 * 两处测试基建：① 解析要 org.json → 挂 Robolectric（D058 口径）；
 * ② 客户端**只收 https**（C2），本地假服务器是 http ——所以配置填 `https://example.test/v1`，
 * 由测试自己的拦截器把请求改写到 127.0.0.1 的假服务器上（**生产代码里没有测试开关**）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class LlmClientTest {

    private lateinit var server: FakeHttpServer
    private lateinit var client: LlmClient

    private companion object {
        /** 配置里的 https 地址（只为过校验；实际落到假服务器由拦截器改写）。 */
        const val TEST_BASE = "https://example.test/v1"
        const val CHAT_PATH = "/v1/chat/completions"
    }

    @Before
    fun setUp() {
        server = FakeHttpServer()
        server.start()
        // 读超时 1s：超时用例靠"服务端睡 2s"来演；拦截器把 example.test 改写到假服务器
        client = LlmClient(
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val rewritten = original.newBuilder()
                        .url(server.url(original.url.encodedPath))
                        .build()
                    chain.proceed(rewritten)
                }
                .build()
        )
    }

    @After
    fun tearDown() {
        server.stop()
    }

    /** 配置一律用 **https**（真要出去的那一步由拦截器接管）；只有"非 https"那条用例故意填 http。 */
    private fun config(baseUrl: String = TEST_BASE) =
        LlmEndpoints.config(baseUrl, "sk-test-key", "glm-4.6", null)

    private fun okBody(content: String): String =
        JSONObject()
            .put(
                "choices",
                org.json.JSONArray().put(
                    JSONObject().put("message", JSONObject().put("role", "assistant").put("content", content))
                )
            )
            .toString()

    @Test
    fun `200 正常_解析出草稿`() {
        server.on(
            CHAT_PATH,
            body = okBody("""{"type":"EXPENSE","amount":12.34,"merchant":"肯德基","time":"2026-10-17 12:30"}"""),
        )
        val result = runBlocking { client.extractText(config(), "午饭 12.34") }
        val ok = result as LlmResult.Ok
        assertEquals(1234L, ok.draft.amountCents)
        assertEquals(TxType.EXPENSE, ok.draft.type)
        assertEquals("glm-4.6", ok.model)

        // 请求形态：路径 + Bearer + 模型名
        val request = server.requests.single()
        assertEquals(CHAT_PATH, request.path)
        assertEquals("Bearer sk-test-key", request.headers["authorization"])
        val payload = JSONObject(request.body)
        assertEquals("glm-4.6", payload.getString("model"))
        assertTrue(request.body.contains("午饭 12.34"))
    }

    @Test
    fun `401 是 Key 无效`() {
        server.on(CHAT_PATH, status = 401, body = """{"error":"bad key"}""")
        val result = runBlocking { client.extractText(config(), "x") }
        assertEquals(LlmError.UNAUTHORIZED, (result as LlmResult.Err).reason)
    }

    @Test
    fun `403 与 429 与 400 与 5xx 分得开`() {
        server.on(CHAT_PATH, status = 403, body = "{}")
        assertEquals(LlmError.FORBIDDEN, (runBlocking { client.extractText(config(), "x") } as LlmResult.Err).reason)

        server.on(CHAT_PATH, status = 429, body = "{}")
        assertEquals(LlmError.RATE_LIMITED, (runBlocking { client.extractText(config(), "x") } as LlmResult.Err).reason)

        // 400/404 这类 = 请求被拒（模型名/用法不对），不是"对方挂了"（D113 实测口径）
        server.on(CHAT_PATH, status = 400, body = """{"error":{"message":"bad model"}}""")
        assertEquals(LlmError.REJECTED, (runBlocking { client.extractText(config(), "x") } as LlmResult.Err).reason)

        server.on(CHAT_PATH, status = 404, body = "{}")
        assertEquals(LlmError.REJECTED, (runBlocking { client.extractText(config(), "x") } as LlmResult.Err).reason)

        server.on(CHAT_PATH, status = 500, body = "{}")
        assertEquals(LlmError.SERVER, (runBlocking { client.extractText(config(), "x") } as LlmResult.Err).reason)
    }

    @Test
    fun `非 JSON 与缺字段都是 BAD_RESPONSE`() {
        server.on(CHAT_PATH, body = okBody("这不是 JSON"))
        assertEquals(LlmError.BAD_RESPONSE, (runBlocking { client.extractText(config(), "x") } as LlmResult.Err).reason)

        server.on(CHAT_PATH, body = okBody("""{"merchant":"肯德基"}"""))
        assertEquals(LlmError.BAD_RESPONSE, (runBlocking { client.extractText(config(), "x") } as LlmResult.Err).reason)

        // choices 结构不对（模型直接回 JSON，没有 chat 包装）
        server.on(CHAT_PATH, body = """{"amount":12}""")
        assertEquals(LlmError.BAD_RESPONSE, (runBlocking { client.extractText(config(), "x") } as LlmResult.Err).reason)
    }

    @Test
    fun `超时归 TIMEOUT`() {
        server.on(CHAT_PATH) { _ ->
            Thread.sleep(2_000)
            FakeHttpServer.Response(status = 200, body = okBody("""{"amount":1}""").toByteArray())
        }
        val result = runBlocking { client.extractText(config(), "x") }
        assertEquals(LlmError.TIMEOUT, (result as LlmResult.Err).reason)
    }

    @Test
    fun `连不上的归 NETWORK`() {
        server.stop()
        val result = runBlocking { client.extractText(config(), "x") }
        assertEquals(LlmError.NETWORK, (result as LlmResult.Err).reason)
    }

    @Test
    fun `非 https 直接不发请求`() {
        val result = runBlocking { client.extractText(config("http://127.0.0.1:9"), "x") }
        assertEquals(LlmError.INSECURE_URL, (result as LlmResult.Err).reason)
        assertEquals(0, server.requests.size) // 一个包都没出去
    }

    @Test
    fun `没配齐直接不发请求`() {
        val incomplete = dev.penned.app.domain.LlmConfig("https://a.example/v1", "", "m", null)
        val result = runBlocking { client.extractText(incomplete, "x") }
        assertEquals(LlmError.NOT_CONFIGURED, (result as LlmResult.Err).reason)
        assertEquals(0, server.requests.size)
    }

    @Test
    fun `测试连接_通与不通`() {
        server.on(CHAT_PATH, body = okBody("""{"amount":0.01}"""))
        assertEquals(null, runBlocking { client.testConnection(config()) })
        assertTrue(server.requests.single().body.contains("只回一个 JSON"))

        server.on(CHAT_PATH, status = 401, body = "{}")
        assertEquals(LlmError.UNAUTHORIZED, runBlocking { client.testConnection(config()) })
    }

    @Test
    fun `图像调用走视觉模型_提示词和图一起发`() {
        server.on(CHAT_PATH, body = okBody("""{"rows":[{"amount":9.9,"merchant":"某商家"}]}"""))
        val result = runBlocking {
            client.extractImageRows(LlmEndpoints.config(TEST_BASE, "sk", "glm-4.6", "glm-4v"), "看图", "QUJD")
        }
        val ok = result as LlmRowsResult.Ok
        assertEquals(990L, ok.rows.rows.single().amountCents)
        assertEquals("glm-4v", ok.model)
        // 断结构而不是子串：Android 的 org.json 会把 "/" 转义成 "\/"（子串匹配会假红）
        val payload = JSONObject(server.requests.single().body)
        val content = payload.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        // 一条 content 数组里**两段都要在**：先指令、后图。少了指令模型既没 schema 也没规则，
        // 实测 DeepSeek 会 400 拒掉 json 模式（D113）。
        assertEquals(2, content.length())
        assertEquals("text", content.getJSONObject(0).getString("type"))
        assertEquals("看图", content.getJSONObject(0).getString("text"))
        assertEquals("image_url", content.getJSONObject(1).getString("type"))
        assertEquals("data:image/jpeg;base64,QUJD", content.getJSONObject(1).getJSONObject("image_url").getString("url"))
        assertEquals("high", content.getJSONObject(1).getJSONObject("image_url").getString("detail"))
    }

    @Test
    fun `纯文本调用只有一段文本`() {
        server.on(CHAT_PATH, body = okBody("""{"amount":1}"""))
        runBlocking { client.complete(config(), "只发表头") }
        val payload = JSONObject(server.requests.single().body)
        val content = payload.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals(1, content.length())
        assertEquals("text", content.getJSONObject(0).getString("type"))
    }
}
