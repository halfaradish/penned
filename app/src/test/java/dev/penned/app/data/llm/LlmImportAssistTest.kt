package dev.penned.app.data.llm

import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.update.FakeHttpServer
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.LlmEndpoints
import dev.penned.app.domain.LlmSuggestions
import dev.penned.app.domain.MerchantNeed
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 导入里的 LLM 辅助（M4-6）：走假服务器跑通两条建议，并**逐条核对外发边界**——
 * 请求体里不许出现金额、时间、交易单号（§二·13）。
 *
 * 配置填 `https://example.test/v1`（过 C2 的 https 校验），由拦截器改写到本地假服务器；
 * 生产代码里没有测试开关。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class LlmImportAssistTest {

    private lateinit var server: FakeHttpServer
    private lateinit var assist: LlmImportAssist

    private companion object {
        const val TEST_BASE = "https://example.test/v1"
        const val CHAT_PATH = "/v1/chat/completions"
    }

    @Before
    fun setUp() {
        server = FakeHttpServer()
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                chain.proceed(original.newBuilder().url(server.url(original.url.encodedPath)).build())
            }
            .build()
        assist = LlmImportAssist(LlmClient(client))
    }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun config() = LlmEndpoints.config(TEST_BASE, "sk-test", "glm-4.6", null)

    private fun okBody(content: String): String = JSONObject()
        .put(
            "choices",
            org.json.JSONArray().put(
                JSONObject().put("message", JSONObject().put("content", content))
            )
        )
        .toString()

    private fun lastRequestBody(): String = server.requests.last().body

    /**
     * 请求体里那段 prompt 的原文。
     *
     * **坑**：Android 的 `org.json` 会把非 ASCII 写成 \uXXXX，所以不能拿原始 body 断中文——
     * 一定要先解析回 JSON 再取 `messages[0].content[0].text`。
     */
    private fun lastPrompt(): String = promptOf(lastRequestBody())

    private fun promptOf(body: String): String {
        val payload = JSONObject(body)
        return payload.getJSONArray("messages").getJSONObject(0)
            .getJSONArray("content").getJSONObject(0).getString("text")
    }

    @Test
    fun `列映射建议：只发表头 请求体里没有明细`() {
        server.on(CHAT_PATH, body = okBody("""{"time":"交易日期","amount":"金额(元)"}"""))
        val result = runBlocking {
            assist.suggestColumnMapping(config(), listOf("交易日期", "交易对方", "金额(元)"))
        }
        assertTrue(result is LlmContentResult.Ok)
        val prompt = lastPrompt()
        assertTrue(prompt.contains("交易日期"))
        assertTrue(prompt.contains("金额(元)"))
        // 默认只发表头：没有任何数据行的痕迹
        assertTrue(!prompt.contains("样例"))
    }

    @Test
    fun `列映射建议：看 3 行样例时数字已被掩码（金额 时间 单号不出设备）`() {
        server.on(CHAT_PATH, body = okBody("""{"time":"交易日期","amount":"金额(元)"}"""))
        val samples = listOf(
            listOf("2026-10-17 13:19:14", "某商户", "1234.56", "4200003225202610171528367827"),
            listOf("2026-10-16 18:44:28", "另一商户", "52", "53010003475243202610163523170887"),
        )
        runBlocking { assist.suggestColumnMapping(config(), listOf("交易日期", "交易对方", "金额(元)", "订单号"), samples) }
        val prompt = lastPrompt()
        val rawBody = lastRequestBody()

        // 边界：金额、时间、单号的具体值一个都不许出现（prompt 与原始请求体都断一遍）
        listOf(
            "2026-10-17", "13:19:14", "2026-10-16", "18:44:28",
            "1234.56", "4200003225202610171528367827", "53010003475243202610163523170887",
        ).forEach { secret ->
            assertTrue("prompt 里不该出现「$secret」", !prompt.contains(secret))
            assertTrue("请求体里不该出现「$secret」", !rawBody.contains(secret))
        }
        // 形状留着（模型仍能看出哪列是时间、哪列是金额）
        assertTrue(prompt.contains("####-##-## ##:##:##"))
        assertTrue(prompt.contains("####.##"))
    }

    @Test
    fun `商户分类建议：只发商户名与分类名 不发金额时间单号`() {
        server.on(CHAT_PATH, body = okBody("""{"肯打鸡":"食品餐饮"}"""))
        runBlocking {
            assist.suggestMerchantCategories(
                config(),
                merchants = listOf("肯打鸡", "某咖啡"),
                categoryPaths = listOf("食品餐饮", "食品餐饮 / 早餐", "出行交通"),
            )
        }
        val prompt = lastPrompt()
        assertTrue(prompt.contains("肯打鸡"))
        assertTrue(prompt.contains("食品餐饮 / 早餐"))
        // 没有金额/时间/单号这类东西（这两条建议链路的入参里根本不该有）
        assertTrue(!prompt.contains("金额"))
        assertTrue(!prompt.contains("订单号"))
        assertTrue(!prompt.contains("2026-"))
    }

    @Test
    fun `商户分批：超过 100 个会分成多次调用`() {
        server.on(CHAT_PATH, body = okBody("""{}"""))
        val merchants = (1..250).map { "商户$it" }
        val batches = LlmSuggestions.batchMerchants(merchants)
        runBlocking {
            batches.forEach { batch -> assist.suggestMerchantCategories(config(), batch, listOf("食品餐饮")) }
        }
        assertEquals(3, server.requests.size)
        // 每批的商户数不超过 100（数一下请求体里的 "- " 行）
        val counts = server.requests.map { request ->
            JSONObject(request.body).getJSONArray("messages").getJSONObject(0)
                .getJSONArray("content").getJSONObject(0).getString("text")
                .split("\n").count { it.trim().startsWith("- ") }
        }
        assertEquals(listOf(100, 100, 50), counts)
    }

    private fun category(id: String, name: String, parentId: String? = null, income: Boolean = false) =
        CategoryEntity(
            id = id, bookId = "b1", parentId = parentId, name = name,
            type = if (income) CategoryType.INCOME else CategoryType.EXPENSE,
            createdAt = 1L, updatedAt = 1L, deviceId = "d",
        )

    @Test
    fun `批量贴分类：按收支分两批 只回现有分类 匹配不上留空`() {
        val categories = listOf(
            category("c-food", "食品餐饮"),
            category("c-breakfast", "早餐", parentId = "c-food"),
            category("c-redpacket", "红包", income = true),
        )
        server.on(CHAT_PATH) { request ->
            val prompt = promptOf(request.body)
            val body = when {
                prompt.contains("支出商户") ->
                    okBody("""{"肯打鸡":"食品餐饮 / 早餐","某陌生商户":""}""")
                prompt.contains("收入商户") -> okBody("""{"微信红包-来自姐姐":"红包"}""")
                else -> okBody("{}")
            }
            FakeHttpServer.Response(body = body.toByteArray())
        }

        val outcome = runBlocking {
            assist.suggestCategories(
                config(),
                needs = listOf(
                    MerchantNeed("肯打鸡", expense = true),
                    MerchantNeed("某陌生商户", expense = true),
                    MerchantNeed("微信红包-来自姐姐", expense = false),
                ),
                categories = categories,
            )
        }
        assertNull(outcome.error)
        assertEquals(2, server.requests.size)

        val byMerchant = outcome.suggestions.associateBy { it.merchant }
        assertEquals("c-breakfast", byMerchant.getValue("肯打鸡").categoryId)
        assertEquals(true, byMerchant.getValue("肯打鸡").expense)
        // 模型给了空串 → 本地留空（未分类）
        assertNull(byMerchant.getValue("某陌生商户").categoryId)
        assertEquals("c-redpacket", byMerchant.getValue("微信红包-来自姐姐").categoryId)
        assertEquals(false, byMerchant.getValue("微信红包-来自姐姐").expense)

        // 两批各自只看到本域的分类
        val expensePrompt = server.requests.map { promptOf(it.body) }.first { it.contains("支出商户") }
        val incomePrompt = server.requests.map { promptOf(it.body) }.first { it.contains("收入商户") }
        assertTrue(expensePrompt.contains("食品餐饮 / 早餐"))
        assertTrue(!expensePrompt.contains("红包"))
        assertTrue(incomePrompt.contains("红包"))
        assertTrue(!incomePrompt.contains("食品餐饮"))
    }

    @Test
    fun `批量贴分类：某一批失败保留前面的建议并带错误`() {
        val categories = listOf(
            category("c-food", "食品餐饮"),
            category("c-redpacket", "红包", income = true),
        )
        server.on(CHAT_PATH) { request ->
            val prompt = promptOf(request.body)
            if (prompt.contains("支出商户")) {
                FakeHttpServer.Response(status = 500, body = "{}".toByteArray())
            } else {
                FakeHttpServer.Response(body = okBody("""{"微信红包-来自姐姐":"红包"}""").toByteArray())
            }
        }
        val outcome = runBlocking {
            assist.suggestCategories(
                config(),
                needs = listOf(
                    MerchantNeed("肯打鸡", expense = true),
                    MerchantNeed("微信红包-来自姐姐", expense = false),
                ),
                categories = categories,
            )
        }
        assertEquals(dev.penned.app.data.llm.LlmError.SERVER, outcome.error)
        // 支出那批先失败 → 后面的收入批不再发（不无谓地多花调用）
        assertEquals(1, server.requests.size)
        assertTrue(outcome.suggestions.isEmpty())
    }
}
