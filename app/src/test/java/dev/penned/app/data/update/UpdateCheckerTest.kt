package dev.penned.app.data.update

import dev.penned.app.domain.UpdateStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 检查器：候选地址回退 + 版本结论（规格 M2 S2）。
 *
 * 挂 Robolectric 是因为清单解析走平台自带 `org.json`（D058）；HTTP 走 fake，不碰网络。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class UpdateCheckerTest {

    private val manifestJson = """
        {"versionCode": 2, "versionName": "0.2.0", "minVersionCode": 1,
         "changelog": ["新增：应用内更新"], "sha256": "aa", "size": 10,
         "urls": ["https://mirror.example/penned-0.2.0.apk"]}
    """.trimIndent()

    /** 记录试过哪些地址、按地址给响应的 fake（null = 该地址失败）。 */
    private class FakeFetcher(private val responses: Map<String, String?>) : HttpFetcher {
        val tried = mutableListOf<String>()
        var lastUserAgent: String? = null
        override suspend fun getText(url: String, userAgent: String): String? {
            tried += url
            lastUserAgent = userAgent
            return responses[url]
        }

        override suspend fun openStream(url: String, userAgent: String, resumeFrom: Long): HttpStream =
            error("检查器不该发起下载")
    }

    @Test
    fun `first working manifest wins and reports available`() = runTest {
        val primary = "https://primary.example/update.json"
        val mirror = "https://mirror.example/update.json"
        val fetcher = FakeFetcher(mapOf(primary to manifestJson, mirror to manifestJson))
        val checker = UpdateChecker(fetcher, listOf(primary, mirror), "penned-update/0.1.0", currentVersionCode = 1)

        val result = checker.check()
        assertTrue(result is UpdateCheckResult.Checked)
        val status = (result as UpdateCheckResult.Checked).status
        assertTrue(status is UpdateStatus.Available)
        status as UpdateStatus.Available
        assertEquals(2, status.manifest.versionCode)
        assertEquals(false, status.stronglyRecommended)
        assertEquals(listOf(primary), fetcher.tried) // 第一个就成，不再试第二个
        assertEquals("penned-update/0.1.0", fetcher.lastUserAgent)
    }

    @Test
    fun `falls back to mirror when primary fails or is garbage`() = runTest {
        val primary = "https://primary.example/update.json"
        val mirror = "https://mirror.example/update.json"

        // 主地址网络失败
        val down = FakeFetcher(mapOf(primary to null, mirror to manifestJson))
        val r1 = UpdateChecker(down, listOf(primary, mirror), "ua", 1).check()
        assertTrue(r1 is UpdateCheckResult.Checked)
        assertEquals(listOf(primary, mirror), down.tried)

        // 主地址拿到坏 JSON
        val garbage = FakeFetcher(mapOf(primary to "<html>404</html>", mirror to manifestJson))
        val r2 = UpdateChecker(garbage, listOf(primary, mirror), "ua", 1).check()
        assertTrue(r2 is UpdateCheckResult.Checked)
        assertEquals(listOf(primary, mirror), garbage.tried)
    }

    @Test
    fun `all candidates failing reports failure with reason`() = runTest {
        val a = "https://a.example/update.json"
        val b = "https://b.example/update.json"

        val fetcher = FakeFetcher(mapOf(a to null, b to null))
        val result = UpdateChecker(fetcher, listOf(a, b), "ua", 1).check()
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals("更新清单下载失败", (result as UpdateCheckResult.Failed).reason)
        assertEquals(listOf(a, b), fetcher.tried)

        val garbage = FakeFetcher(mapOf(a to "not json", b to "{}"))
        val r2 = UpdateChecker(garbage, listOf(a, b), "ua", 1).check()
        assertEquals("更新清单格式不对", (r2 as UpdateCheckResult.Failed).reason)

        val none = UpdateChecker(FakeFetcher(emptyMap()), emptyList(), "ua", 1).check()
        assertEquals("没有配置更新地址", (none as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `already newest and strongly recommended grading`() = runTest {
        val url = "https://a.example/update.json"

        val upToDate = UpdateChecker(FakeFetcher(mapOf(url to manifestJson)), listOf(url), "ua", currentVersionCode = 2).check()
        assertEquals(UpdateStatus.UpToDate, (upToDate as UpdateCheckResult.Checked).status)

        val old = UpdateChecker(FakeFetcher(mapOf(url to manifestJson)), listOf(url), "ua", currentVersionCode = 1)
        assertEquals(false, ((old.check() as UpdateCheckResult.Checked).status as UpdateStatus.Available).stronglyRecommended)

        // 本机版本低于 minVersionCode（2）→ 只升级文案，不强制（D059）
        val older = UpdateChecker(FakeFetcher(mapOf(url to manifestJson)), listOf(url), "ua", currentVersionCode = 0)
        assertEquals(true, ((older.check() as UpdateCheckResult.Checked).status as UpdateStatus.Available).stronglyRecommended)
    }
}
