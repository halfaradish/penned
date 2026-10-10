package dev.penned.app.data.update

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.ServerSocket

/**
 * OkHttp 实现的取数行为（规格 M2 S2）：本地假服务（[FakeHttpServer]，不引 mockwebserver）。
 *
 * 覆盖：正常取回 + 固定 User-Agent、非 2xx 返回 null、跟随重定向、连接被拒、读取超时。
 */
class OkHttpFetcherTest {

    private val server = FakeHttpServer()

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `getText returns body and sends fixed user agent`() = runTest {
        server.on("/update.json", body = """{"versionCode": 2}""")
        server.start()

        val text = OkHttpFetcher().getText(server.url("/update.json"), "penned-update/0.2.0")

        assertEquals("""{"versionCode": 2}""", text)
        assertEquals(listOf("/update.json"), server.requests.map { it.path })
        assertEquals("penned-update/0.2.0", server.requests.first().headers["user-agent"])
    }

    @Test
    fun `non 2xx returns null`() = runTest {
        server.on("/missing.json", status = 404, body = "not found")
        server.on("/boom.json", status = 500, body = "boom")
        server.start()

        val fetcher = OkHttpFetcher()
        assertNull(fetcher.getText(server.url("/missing.json"), "ua"))
        assertNull(fetcher.getText(server.url("/boom.json"), "ua"))
    }

    @Test
    fun `follows redirect like mirror sites do`() = runTest {
        server.on("/moved.json", status = 302, headers = mapOf("Location" to "/real.json"))
        server.on("/real.json", body = """{"ok": true}""")
        server.start()

        assertEquals("""{"ok": true}""", OkHttpFetcher().getText(server.url("/moved.json"), "ua"))
    }

    @Test
    fun `connection refused returns null`() = runTest {
        // 刚释放的端口当"没人监听"
        val freePort = ServerSocket(0).use { it.localPort }
        assertNull(
            OkHttpFetcher(connectTimeoutSeconds = 2).getText("http://127.0.0.1:$freePort/update.json", "ua"),
        )
    }

    @Test
    fun `read timeout returns null`() = runTest {
        server.on("/slow.json") {
            Thread.sleep(3_000)
            FakeHttpServer.Response(body = """{"slow": true}""".toByteArray())
        }
        server.start()

        assertNull(OkHttpFetcher(readTimeoutSeconds = 1).getText(server.url("/slow.json"), "ua"))
    }
}
