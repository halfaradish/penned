package dev.penned.app.data.update

import dev.penned.app.domain.Sha256
import dev.penned.app.domain.UpdateManifest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 下载器（规格 M2 S3/S4）：完整下载 + 摘要校验、断线续传、服务端忽略 Range 时重下、
 * 摘要不符即删、多地址回退、已下满时不再联网。
 *
 * HTTP 用 [FakeHttpServer] + 真的 [OkHttpFetcher]——不 mock 网络栈，连 `Range` 头都真发真收。
 */
class UpdateDownloaderTest {

    private val server = FakeHttpServer()
    private val dir = File(System.getProperty("java.io.tmpdir"), "penned-dl-${System.nanoTime()}")

    private val content = ByteArray(200_000) { (it % 251).toByte() }
    private val url = "/penned-0.2.0.apk"

    private fun target() = File(dir, "penned-2.apk")

    private fun manifest(
        urls: List<String>,
        sha256: String = Sha256.ofBytes(content),
        size: Long = content.size.toLong(),
    ) = UpdateManifest(
        versionCode = 2,
        versionName = "0.2.0",
        minVersionCode = 1,
        changelog = emptyList(),
        sha256 = sha256,
        size = size,
        urls = urls,
    )

    private fun downloader() = UpdateDownloader(OkHttpFetcher(), "penned-update/0.1.0")

    @After
    fun tearDown() {
        server.stop()
        dir.deleteRecursively()
    }

    @Test
    fun `downloads whole file and verifies digest`() = runTest {
        server.onFile(url, content)
        server.start()

        val progress = mutableListOf<Pair<Long, Long>>()
        val result = downloader().download(manifest(listOf(server.url(url))), target()) { done, total ->
            progress += done to total
        }

        assertTrue("应当成功，实际 $result", result is DownloadResult.Success)
        assertEquals(content.toList(), target().readBytes().toList())
        assertEquals(0L to content.size.toLong(), progress.first())
        assertEquals(content.size.toLong() to content.size.toLong(), progress.last())
        assertEquals("进度必须单调不减", progress.sortedBy { it.first }, progress)
    }

    @Test
    fun `resumes from where the connection was cut`() = runTest {
        // 第一次：服务端只发 1000 字节就断开
        server.onFile(url, content, cutAfterBytes = 1_000)
        server.start()

        val first = downloader().download(manifest(listOf(server.url(url))), target())
        assertTrue("应当判为没下完，实际 $first", first is DownloadResult.Incomplete)
        assertEquals(1_000L, (first as DownloadResult.Incomplete).downloaded)
        assertEquals(1_000L, target().length()) // 已下的部分留着

        // 第二次：网络恢复，应当带 Range 续传，只补剩下的
        server.onFile(url, content)
        val second = downloader().download(manifest(listOf(server.url(url))), target())

        assertTrue("续传后应当成功，实际 $second", second is DownloadResult.Success)
        assertEquals("bytes=1000-", server.requests.last().headers["range"])
        assertEquals(content.toList(), target().readBytes().toList())
    }

    @Test
    fun `restarts when server ignores Range`() = runTest {
        // 先留一段来路不明的残缺文件（500 字节），服务端又不认 Range（永远 200 全量）
        dir.mkdirs()
        target().writeBytes(ByteArray(500) { 7 })
        server.on(url) { FakeHttpServer.Response(body = content) }
        server.start()

        val result = downloader().download(manifest(listOf(server.url(url))), target())

        assertTrue("应当成功，实际 $result", result is DownloadResult.Success)
        assertEquals("不能把两段拼起来", content.toList(), target().readBytes().toList())
    }

    @Test
    fun `rejects and deletes file when digest mismatches`() = runTest {
        val other = ByteArray(200_000) { 1 } // 长度一样、内容不同
        server.onFile(url, other)
        server.start()

        val result = downloader().download(manifest(listOf(server.url(url))), target())

        assertTrue("应当判为校验失败，实际 $result", result is DownloadResult.Rejected)
        assertEquals("安装包校验失败，已删除", (result as DownloadResult.Rejected).reason)
        assertFalse("坏包必须删掉", target().exists())
    }

    @Test
    fun `falls back to the next url`() = runTest {
        val missing = "/missing.apk"
        server.onFile(url, content)
        server.start()

        val result = downloader().download(
            manifest(listOf(server.url(missing), server.url(url))),
            target(),
        )

        assertTrue("应当成功，实际 $result", result is DownloadResult.Success)
        assertEquals(listOf(missing, url), server.requests.map { it.path })
    }

    @Test
    fun `already complete file is verified without network`() = runTest {
        dir.mkdirs()
        target().writeBytes(content)
        server.start()

        val result = downloader().download(manifest(listOf(server.url(url))), target())

        assertTrue("应当成功，实际 $result", result is DownloadResult.Success)
        assertTrue("已下满就不该再联网", server.requests.isEmpty())
    }
}
