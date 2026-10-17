package dev.penned.app.ui.intelligence

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.KeywordRuleEntity
import dev.penned.app.data.llm.LlmClient
import dev.penned.app.data.update.FakeHttpServer
import dev.penned.app.domain.PendingKind
import dev.penned.app.domain.PendingStatus
import dev.penned.app.domain.PendingType
import dev.penned.app.domain.RuleTargetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 截图导入的端到端（M4-7，假服务器 + PNG fixture）：**识别成功才落候选池**、
 * 金额/类型按识别结果填、分类走关键词规则、商户进备注；失败**不落库**。
 *
 * 真图（微信/支付宝支付成功截图等 ≥6 张）没有样本，走查那一档见 board 的 deferred。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotImportViewModelTest {

    private val app: PennedApplication = ApplicationProvider.getApplicationContext()
    private lateinit var server: FakeHttpServer
    private lateinit var viewModel: ScreenshotImportViewModel

    private val bookId = "book-shot"

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
        viewModel = ScreenshotImportViewModel(app, LlmClient(client))

        runBlocking {
            val now = System.currentTimeMillis()
            app.database.bookDao().upsert(
                BookEntity(id = bookId, name = "截图账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "t")
            )
            app.database.categoryDao().upsert(
                dev.penned.app.data.db.entity.CategoryEntity(
                    id = "cat-food", bookId = bookId, name = "食品餐饮",
                    type = dev.penned.app.domain.CategoryType.EXPENSE, createdAt = now, updatedAt = now, deviceId = "t",
                )
            )
            app.settings.setCurrentBook(bookId)
            app.settings.setLlmEnabled(true)
            app.settings.setLlmBaseUrl("https://example.test/v1")
            app.settings.setLlmApiKey("sk-test")
            app.settings.setLlmModel("glm-4.6")
        }
    }

    @After
    fun tearDown() {
        server.stop()
        runBlocking {
            app.settings.setLlmEnabled(false)
            app.settings.setLlmBaseUrl(null)
            app.settings.setLlmApiKey(null)
            app.settings.setLlmModel(null)
        }
    }

    private fun pngFile(): Uri {
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        val file = File(app.cacheDir, "shot_test.png")
        file.writeBytes(out.toByteArray())
        return Uri.fromFile(file)
    }

    private fun okBody(content: String): String = JSONObject()
        .put(
            "choices",
            org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", content))),
        )
        .toString()

    @Test
    fun `识别成功落候选池 分类走关键词规则 请求体里是压缩后的图`() = runBlocking {
        app.database.keywordRuleDao().upsert(
            KeywordRuleEntity(
                id = "r1", bookId = bookId, keyword = "肯打鸡", targetType = RuleTargetType.CATEGORY,
                targetId = "cat-food", priority = 0, enabled = true,
                createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        server.on(
            "/v1/chat/completions",
            body = okBody("""{"type":"EXPENSE","amount":12.34,"merchant":"肯打鸡","time":"2026-10-17 12:30","note":"全家桶"}"""),
        )

        val saved = viewModel.recognize(listOf(pngFile()))
        assertEquals(1, saved.size)
        val entry = app.database.pendingEntryDao().getActive(saved[0].id)!!
        assertEquals(PendingKind.SCREENSHOT, entry.kind)
        assertEquals(PendingType.EXPENSE, entry.type)
        assertEquals(1234L, entry.amountCents)
        assertEquals("肯打鸡", entry.merchant)
        assertEquals("cat-food", entry.categoryId) // 关键词规则给的
        assertEquals(PendingStatus.PENDING, entry.status)

        // 发出去的是 data:image/jpeg;base64（本地已压成 JPEG）
        val body = server.requests.single().body
        val payload = JSONObject(body)
        val content = payload.getJSONArray("messages").getJSONObject(0)
            .getJSONArray("content").getJSONObject(0)
        assertEquals("image_url", content.getString("type"))
        assertTrue(content.getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))
        assertEquals("glm-4.6", payload.getString("model"))
    }

    @Test
    fun `识别失败不落库 并把原因说清楚`() = runBlocking {
        server.on("/v1/chat/completions", status = 401, body = "{}")
        val saved = viewModel.recognize(listOf(pngFile()))
        assertTrue(saved.isEmpty())
        assertEquals(0, app.database.pendingEntryDao().countPending())
        assertEquals("Key 无效（401）", viewModel.state.value.error)
    }

    @Test
    fun `模型说不是账单_不算成功`() = runBlocking {
        server.on("/v1/chat/completions", body = okBody("""{"type":"UNKNOWN"}"""))
        val saved = viewModel.recognize(listOf(pngFile()))
        assertTrue(saved.isEmpty())
        assertEquals("没看出这是账单——换一张试试", viewModel.state.value.error)
    }

    @Test
    fun `没开 AI 时给引导 一个包都不发`() = runBlocking {
        app.settings.setLlmEnabled(false)
        val saved = viewModel.recognize(listOf(pngFile()))
        assertTrue(saved.isEmpty())
        assertTrue(viewModel.state.value.error!!.contains("AI 设置"))
        assertEquals(0, server.requests.size)
    }

    @Test
    fun `多张顺序识别 每张一条候选`() = runBlocking {
        server.on(
            "/v1/chat/completions",
            body = okBody("""{"type":"EXPENSE","amount":9.9,"merchant":"某商户"}"""),
        )
        val saved = viewModel.recognize(listOf(pngFile(), pngFile()))
        assertEquals(2, saved.size)
        assertEquals(2, app.database.pendingEntryDao().countPending())
        assertEquals(2, server.requests.size) // 一张一个请求（不并发）
    }

    @Test
    fun `保存后候选标 ACCEPTED 并回填 txId`() = runBlocking {
        server.on("/v1/chat/completions", body = okBody("""{"type":"EXPENSE","amount":5,"merchant":"某商户"}"""))
        val saved = viewModel.recognize(listOf(pngFile()))
        assertNotNull(saved.firstOrNull())

        // 记一笔页保存回来（导航层做的那一步）
        app.database.pendingEntryDao().markAccepted(saved[0].id, "tx-1", System.currentTimeMillis())
        val entry = app.database.pendingEntryDao().getActive(saved[0].id)!!
        assertEquals(PendingStatus.ACCEPTED, entry.status)
        assertEquals("tx-1", entry.txId)
        assertEquals(0, app.database.pendingEntryDao().countPending())
    }
}
