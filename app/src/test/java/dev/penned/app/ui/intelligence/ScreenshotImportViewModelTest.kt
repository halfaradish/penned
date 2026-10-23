package dev.penned.app.ui.intelligence

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.domain.LlmProvider
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.ImportBatchEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.data.llm.LlmClient
import dev.penned.app.data.update.FakeHttpServer
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 账单列表导入的端到端（M4-7 → D111，假服务器 + PNG fixture）：
 * 一张（或多张）图 → **一批预览行**（内存态，不落候选池）→「全部导入」才写库（一条批次，可撤销）。
 *
 * 覆盖：多行识别与合并 / 备注拼商户与状态 / 时间去宽松解析 / 坏行与截断计数 /
 * **去重（库内已有 + 批内自身）** / 部分失败不影响已认出的行 / 落库字段（来源、批次、账户、单号为空）/ 撤销整批。
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
            app.settings.setCurrentBook(bookId)
            app.settings.setLlmEnabled(true)
            app.settings.setLlmProvider(LlmProvider.CUSTOM)
            app.settings.setLlmBaseUrl(LlmProvider.CUSTOM, "https://example.test/v1")
            app.settings.setLlmApiKey(LlmProvider.CUSTOM, "sk-test")
            app.settings.setLlmModel(LlmProvider.CUSTOM, "glm-4.6")
        }
    }

    @After
    fun tearDown() {
        server.stop()
        runBlocking {
            app.settings.setLlmEnabled(false)
            app.settings.setLlmProvider(null)
            app.settings.setLlmBaseUrl(LlmProvider.CUSTOM, null)
            app.settings.setLlmApiKey(LlmProvider.CUSTOM, null)
            app.settings.setLlmModel(LlmProvider.CUSTOM, null)
        }
    }

    private fun pngFile(name: String = "shot_test.png"): Uri {
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        val file = File(app.cacheDir, name)
        file.writeBytes(out.toByteArray())
        return Uri.fromFile(file)
    }

    private fun okBody(content: String): String = JSONObject()
        .put(
            "choices",
            org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", content))),
        )
        .toString()

    /** 取请求里的提示词（Android 的 org.json 会把非 ASCII 写成 \uXXXX，必须解析回来再断中文）。 */
    private fun promptOf(body: String): String =
        JSONObject(body).getJSONArray("messages").getJSONObject(0)
            .getJSONArray("content").getJSONObject(0).getString("text")

    /** 真实列表图的形状：两笔支出 + 一笔收入（脱敏后的样张）。 */
    private val listRows = """{"rows":[
        {"type":"EXPENSE","amount":67.07,"merchant":"拼多多平台商户","time":"10月18日 09:23"},
        {"type":"EXPENSE","amount":12.00,"merchant":"西大朴诚食堂","time":"10月17日 13:19","note":"自动扣款成功"},
        {"type":"INCOME","amount":88.00,"merchant":"微信红包-来自姐姐","time":"10月14日 13:18"}
    ]}"""

    // ---- 识别 ----

    @Test
    fun `一张列表图识别成一批预览行`() = runBlocking {
        server.on("/v1/chat/completions", body = okBody(listRows))
        val count = viewModel.recognize(listOf(pngFile()))

        assertEquals(3, count)
        val state = viewModel.state.value
        assertEquals(3, state.rows.size)
        assertEquals(3, state.importable)
        assertEquals(0, state.duplicateCount)
        assertEquals(6707L + 1200L, state.expenseTotalCents)
        assertEquals(8800L, state.incomeTotalCents)
        // 备注 = 商户 + 状态（状态文字不能丢）
        assertEquals("西大朴诚食堂 自动扣款成功", state.rows[1].note)
        // 时间从图里读出来了（无年份也能落成真 epoch 分钟）
        assertTrue(state.rows.all { it.timeFromImage })

        // 请求体：**提示词在前、压缩后的 JPEG 在后**（少了提示词模型既没 schema 也没规则，D113）
        val content = JSONObject(server.requests.single().body).getJSONArray("messages")
            .getJSONObject(0).getJSONArray("content")
        assertEquals(2, content.length())
        assertEquals("text", content.getJSONObject(0).getString("type"))
        assertTrue(content.getJSONObject(0).getString("text").contains("rows"))
        assertEquals("image_url", content.getJSONObject(1).getString("type"))
        assertTrue(content.getJSONObject(1).getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))
    }

    @Test
    fun `识别结果不进候选池 也不发记一笔预填`() = runBlocking {
        server.on("/v1/chat/completions", body = okBody(listRows))
        viewModel.recognize(listOf(pngFile()))
        // 预览就是"确认"这一步：没点导入之前库里什么都没有
        assertEquals(0, app.database.pendingEntryDao().countPending())
        assertEquals(0, app.database.transactionDao().allInBook(bookId).size)
    }

    /** 现有分类（支出两棵树 + 收入一个）。 */
    private suspend fun seedCategories() {
        val now = System.currentTimeMillis()
        app.database.categoryDao().upsertAll(
            listOf(
                CategoryEntity(
                    id = "cat-shop", bookId = bookId, name = "购物消费", type = CategoryType.EXPENSE,
                    createdAt = now, updatedAt = now, deviceId = "t",
                ),
                CategoryEntity(
                    id = "cat-food", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE,
                    createdAt = now, updatedAt = now, deviceId = "t",
                ),
                CategoryEntity(
                    id = "cat-lunch", bookId = bookId, parentId = "cat-food", name = "午餐",
                    type = CategoryType.EXPENSE, createdAt = now, updatedAt = now, deviceId = "t",
                ),
                CategoryEntity(
                    id = "cat-red", bookId = bookId, name = "红包", type = CategoryType.INCOME,
                    createdAt = now, updatedAt = now, deviceId = "t",
                ),
            )
        )
    }

    /** 识别请求（带图）回 rows，归类请求（纯文本）回商户 → 分类名。 */
    private fun routeRecognitionAndCategories(
        rowsBody: String = listRows,
        categoriesBody: String,
    ) {
        server.on("/v1/chat/completions") { request ->
            val isImage = request.body.contains("\"image_url\"")
            val content = if (isImage) rowsBody else categoriesBody
            FakeHttpServer.Response(body = okBody(content).toByteArray())
        }
    }

    @Test
    fun `识别后自动按商户贴现有分类 认不出的留未分类`() = runBlocking {
        seedCategories()
        val rows = """{"rows":[
            {"type":"EXPENSE","amount":67.07,"merchant":"拼多多平台商户","time":"10月18日 09:23"},
            {"type":"EXPENSE","amount":12.00,"merchant":"西大朴诚食堂","time":"10月17日 13:19","note":"自动扣款成功"},
            {"type":"INCOME","amount":88.00,"merchant":"微信红包-来自姐姐","time":"10月14日 13:18"},
            {"type":"EXPENSE","amount":9.90,"merchant":"陌生商户","time":"10月14日 10:00"}
        ]}"""
        routeRecognitionAndCategories(
            rowsBody = rows,
            categoriesBody = """{"拼多多平台商户":"购物消费","西大朴诚食堂":"食品餐饮 / 午餐","微信红包-来自姐姐":"红包"}""",
        )

        assertEquals(4, viewModel.recognize(listOf(pngFile())))
        val state = viewModel.state.value
        assertEquals("cat-shop", state.rows[0].categoryId)
        assertEquals("cat-lunch", state.rows[1].categoryId)
        assertEquals("食品餐饮 / 午餐", state.rows[1].categoryName)
        assertEquals("cat-red", state.rows[2].categoryId)
        // 模型没给"陌生商户"分类 → 保持未分类（不硬猜）
        assertNull(state.rows[3].categoryId)
        assertEquals(3, state.categorized)
        assertEquals(1, state.uncategorized)
        assertTrue(state.warnings.isEmpty())

        // 归类请求只带商户名与分类名：金额、时间、状态文字都不出设备
        val categoryPrompt = server.requests
            .map { promptOf(it.body) }
            .filterNot { it.contains("这是一张支付/账单截图") }
            .joinToString("\n")
        assertTrue(categoryPrompt.contains("拼多多平台商户"))
        assertTrue(categoryPrompt.contains("食品餐饮 / 午餐"))
        listOf("67.07", "12.00", "88.00", "09:23", "13:19", "10月18日", "自动扣款成功", "2026-").forEach { secret ->
            assertTrue("归类请求里不该出现「$secret」", !categoryPrompt.contains(secret))
        }

        // 导入后分类真的落库
        viewModel.import()
        awaitState { !it.importing && it.imported == 4 }
        val txs = app.database.transactionDao().allInBook(bookId)
        assertEquals(4, txs.size)
        assertEquals("cat-shop", txs.first { it.amountCents == 6707L }.categoryId)
        assertEquals("cat-lunch", txs.first { it.amountCents == 1200L }.categoryId)
        assertEquals("cat-red", txs.first { it.amountCents == 8800L }.categoryId)
        assertNull(txs.first { it.amountCents == 990L }.categoryId)
    }

    @Test
    fun `归类失败不清空识别结果 只留提示 导入仍可用`() = runBlocking {
        seedCategories()
        server.on("/v1/chat/completions") { request ->
            if (request.body.contains("\"image_url\"")) {
                FakeHttpServer.Response(body = okBody(listRows).toByteArray())
            } else {
                FakeHttpServer.Response(status = 401, body = "{}".toByteArray())
            }
        }
        assertEquals(3, viewModel.recognize(listOf(pngFile())))
        val state = viewModel.state.value
        assertEquals(3, state.rows.size)
        assertEquals(0, state.categorized)
        assertEquals(3, state.uncategorized)
        assertTrue(state.warnings.single().contains("分类没贴成"))

        viewModel.import()
        awaitState { !it.importing && it.imported == 3 }
        assertTrue(app.database.transactionDao().allInBook(bookId).all { it.categoryId == null })
    }

    @Test
    fun `坏行与截断都计数 不静默丢`() = runBlocking {
        val body = """{"rows":[
            {"type":"EXPENSE","amount":12.00,"merchant":"某商户"},
            {"type":"EXPENSE","merchant":"读不出金额的一行"},
            {"type":"EXPENSE","amount":"??","merchant":"金额坏的一行"}
        ]}"""
        server.on("/v1/chat/completions", body = okBody(body))
        viewModel.recognize(listOf(pngFile()))
        val state = viewModel.state.value
        assertEquals(1, state.rows.size)
        assertEquals(2, state.droppedRows)
    }

    @Test
    fun `模型说不是账单_给一句话`() = runBlocking {
        server.on("/v1/chat/completions", body = okBody("""{"rows":[]}"""))
        assertEquals(0, viewModel.recognize(listOf(pngFile())))
        assertEquals("没看出这是账单——换一张试试", viewModel.state.value.error)
    }

    @Test
    fun `单张失败不影响别的张已认出的行`() = runBlocking {
        // 第一次请求 401，第二次正常
        var calls = 0
        server.on("/v1/chat/completions") {
            calls++
            if (calls == 1) {
                FakeHttpServer.Response(status = 401, body = "{}".toByteArray())
            } else {
                FakeHttpServer.Response(body = okBody(listRows).toByteArray())
            }
        }
        val count = viewModel.recognize(listOf(pngFile("a.png"), pngFile("b.png")))
        assertEquals(3, count)
        assertEquals(2, server.requests.size)
        assertTrue(viewModel.state.value.warnings.single().contains("第 1 张"))
        assertTrue(viewModel.state.value.warnings.single().contains("401"))
    }

    @Test
    fun `多张合并 同一笔在两张图里只算一笔`() = runBlocking {
        // 两张微信截图重叠：第二张多一笔，另一笔重复
        val second = """{"rows":[
            {"type":"EXPENSE","amount":67.07,"merchant":"拼多多平台商户","time":"10月18日 09:23"},
            {"type":"EXPENSE","amount":6.00,"merchant":"西大朴诚食堂","time":"10月17日 20:38"}
        ]}"""
        var calls = 0
        server.on("/v1/chat/completions") {
            calls++
            FakeHttpServer.Response(body = okBody(if (calls == 1) listRows else second).toByteArray())
        }
        val count = viewModel.recognize(listOf(pngFile("a.png"), pngFile("b.png")))
        assertEquals(5, count) // 第一张 3 笔 + 第二张 2 笔（其中一笔与第一张重叠）
        val state = viewModel.state.value
        assertEquals(5, state.rows.size)
        assertEquals(1, state.duplicateCount)
        assertEquals(4, state.importable)
    }

    @Test
    fun `库里已有同时间同金额同备注的流水_标重复不导入`() = runBlocking {
        val minute = java.time.ZonedDateTime.of(
            java.time.LocalDate.of(2026, 10, 17), java.time.LocalTime.of(13, 19), java.time.ZoneId.systemDefault(),
        ).toEpochSecond() / 60
        val now = System.currentTimeMillis()
        app.database.transactionDao().upsert(
            TransactionEntity(
                id = "tx-existing", bookId = bookId, type = TxType.EXPENSE, amountCents = 1200,
                categoryId = null, dateEpochMinute = minute, note = "西大朴诚食堂 自动扣款成功",
                source = TxSource.MANUAL, creatorDeviceId = "t", createdAt = now, updatedAt = now, deviceId = "t",
            )
        )
        server.on("/v1/chat/completions", body = okBody(listRows))
        viewModel.recognize(listOf(pngFile()))

        val state = viewModel.state.value
        assertEquals(1, state.duplicateCount)
        assertEquals(2, state.importable)
    }

    @Test
    fun `没开 AI 时给引导 一个包都不发`() = runBlocking {
        app.settings.setLlmEnabled(false)
        assertEquals(0, viewModel.recognize(listOf(pngFile())))
        assertTrue(viewModel.state.value.error!!.contains("AI 设置"))
        assertEquals(0, server.requests.size)
    }

    // ---- 导入与撤销 ----

    @Test
    fun `全部导入 落库字段与批次都对`() = runBlocking {
        val now = System.currentTimeMillis()
        app.database.accountDao().upsert(
            AccountEntity(
                id = "acc-wechat", bookId = bookId, name = "微信零钱", type = AccountType.DEBIT,
                initialBalanceCents = 0, createdAt = now, updatedAt = now, deviceId = "t",
            )
        )
        server.on("/v1/chat/completions", body = okBody(listRows))
        viewModel.recognize(listOf(pngFile()))
        viewModel.selectAccount("acc-wechat")
        viewModel.import()
        awaitState { !it.importing && it.imported == 3 }

        val rows = app.database.transactionDao().allInBook(bookId)
        assertEquals(3, rows.size)
        assertTrue("截图来的流水记 SCREENSHOT", rows.all { it.source == TxSource.SCREENSHOT })
        assertTrue("截图没有单号", rows.all { it.externalId == null })
        assertTrue("这批记到选的账户", rows.all { it.accountId == "acc-wechat" })
        val pix = rows.single { it.amountCents == 6707L }
        assertEquals(TxType.EXPENSE, pix.type)
        assertEquals("拼多多平台商户", pix.note)
        assertEquals(true, pix.importBatchId != null)

        val batch = app.database.importBatchDao().get(pix.importBatchId!!)!!
        assertEquals(ImportBatchEntity.SOURCE_SCREENSHOT, batch.sourceKind)
        assertEquals(3, batch.count)
        assertEquals(0, batch.duplicateCount)
        assertEquals(0, batch.skippedCount)
        assertTrue(batch.fileName.startsWith("截图账单"))
    }

    @Test
    fun `重复行不进库 skip 与 duplicate 计数落批次`() = runBlocking {
        // 同一张列表图选两次 = 3 笔全重叠（真实场景：两张微信截图覆盖同一个月）
        server.on("/v1/chat/completions", body = okBody(listRows))
        viewModel.recognize(listOf(pngFile("a.png"), pngFile("b.png")))
        val state = viewModel.state.value
        assertEquals(6, state.rows.size)
        assertEquals(3, state.duplicateCount)
        assertEquals(3, state.importable)

        viewModel.import()
        awaitState { !it.importing && it.imported == 3 }
        val rows = app.database.transactionDao().allInBook(bookId)
        assertEquals("重复的那 3 笔没进库", 3, rows.size)
        val batch = app.database.importBatchDao().get(rows.first().importBatchId!!)!!
        assertEquals(3, batch.duplicateCount)
    }

    @Test
    fun `忽略的行数落进批次的 skipped`() = runBlocking {
        val body = """{"rows":[{"type":"EXPENSE","amount":5.00,"merchant":"某商户"},{"merchant":"没金额"}]}"""
        server.on("/v1/chat/completions", body = okBody(body))
        viewModel.recognize(listOf(pngFile()))
        viewModel.import()
        awaitState { !it.importing && it.imported == 1 }
        val tx = app.database.transactionDao().allInBook(bookId).single()
        val batch = app.database.importBatchDao().get(tx.importBatchId!!)!!
        assertEquals(1, batch.count)
        assertEquals(1, batch.skippedCount)
    }

    @Test
    fun `撤销把这一批整批删掉`() = runBlocking {
        server.on("/v1/chat/completions", body = okBody(listRows))
        viewModel.recognize(listOf(pngFile()))
        viewModel.import()
        awaitState { !it.importing && it.imported == 3 }
        assertEquals(3, app.database.transactionDao().allInBook(bookId).size)

        var removed = 0
        viewModel.undoImport { removed = it }
        awaitState { it.imported == 0 }
        assertEquals(3, removed)
        assertEquals(0, app.database.transactionDao().allInBook(bookId).size)
    }

    @Test
    fun `删掉一行之后导入的笔数跟着少`() = runBlocking {
        server.on("/v1/chat/completions", body = okBody(listRows))
        viewModel.recognize(listOf(pngFile()))
        val victim = viewModel.state.value.rows.first { it.amountCents == 6707L }
        viewModel.removeRow(victim.id)
        assertEquals(2, viewModel.state.value.importable)

        viewModel.import()
        awaitState { !it.importing && it.imported == 2 }
        assertEquals(2, app.database.transactionDao().allInBook(bookId).size)
        assertNull(app.database.transactionDao().allInBook(bookId).firstOrNull { it.amountCents == 6707L })
    }

    /** 等状态条件成立（VM 的写入跑在 Robolectric 主 looper 上，要 idle 推它）。 */
    private fun awaitState(predicate: (ScreenshotUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if (predicate(viewModel.state.value)) return
            Thread.sleep(10)
        }
        throw AssertionError("状态没等到：${viewModel.state.value}")
    }
}
