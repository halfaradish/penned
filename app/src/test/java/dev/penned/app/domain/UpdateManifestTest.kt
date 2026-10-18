package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 更新清单解析与版本比较（规格 M2 S1/S2）。
 *
 * 解析走平台自带 `org.json`（D058），故挂 Robolectric（D016 口径：sdk 35 + 真机视口）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class UpdateManifestTest {

    private val full = """
        {
          "versionCode": 2,
          "versionName": "0.2.0",
          "channel": "stable",
          "publishedAt": "2026-10-20T12:34:56Z",
          "tag": "v0.2.0",
          "minVersionCode": 1,
          "changelog": ["新增：应用内更新", "修复：若干"],
          "sha256": "ABC123def",
          "size": 8123456,
          "urls": ["https://a.example/penned-0.2.0.apk", "https://b.example/penned-0.2.0.apk"]
        }
    """.trimIndent()

    @Test
    fun `parse full manifest`() {
        val m = UpdateManifest.parse(full)
        assertNotNull(m)
        m!!
        assertEquals(2, m.versionCode)
        assertEquals("0.2.0", m.versionName)
        assertEquals(1, m.minVersionCode)
        assertEquals(listOf("新增：应用内更新", "修复：若干"), m.changelog)
        assertEquals("abc123def", m.sha256) // 统一存小写
        assertEquals(8_123_456L, m.size)
        assertEquals(2, m.urls.size)
        assertEquals(UpdateChannel.STABLE, m.channel)
        assertEquals("2026-10-20T12:34:56Z", m.publishedAt)
        assertEquals("v0.2.0", m.tag)
    }

    @Test
    fun `parse tolerates missing optional fields and unknown fields`() {
        val m = UpdateManifest.parse(
            """{"versionCode": 7, "sha256": "aa", "size": 10, "urls": ["https://x/y.apk"], "futureField": {"a": 1}}""",
        )
        assertNotNull(m)
        m!!
        assertEquals("7", m.versionName) // 缺 versionName → 退回 versionCode 文案
        assertEquals(0, m.minVersionCode)
        assertTrue(m.changelog.isEmpty())
        assertNull("老清单没有 channel", m.channel)
        assertNull(m.publishedAt)
        assertNull(m.tag)
    }

    @Test
    fun `parse ignores non-string changelog and url entries`() {
        val m = UpdateManifest.parse(
            """{"versionCode": 3, "sha256": "bb", "size": 10,
                "changelog": ["ok", null, 42, "  "],
                "urls": [null, "https://x/y.apk", 7]}""",
        )
        assertNotNull(m)
        assertEquals(listOf("ok"), m!!.changelog)
        assertEquals(listOf("https://x/y.apk"), m.urls)
    }

    /** 表驱动：坏 JSON / 缺必填 / 值不合法 一律 null（当作检查失败，不崩）。 */
    @Test
    fun `parse invalid manifests return null`() {
        val cases = listOf(
            "不是 JSON" to "坏 JSON",
            "" to "空串",
            "[]" to "顶层不是对象",
            """{"sha256": "aa", "size": 10, "urls": ["u"]}""" to "缺 versionCode",
            """{"versionCode": 0, "sha256": "aa", "size": 10, "urls": ["u"]}""" to "versionCode 非正",
            """{"versionCode": 2, "size": 10, "urls": ["u"]}""" to "缺 sha256",
            """{"versionCode": 2, "sha256": "  ", "size": 10, "urls": ["u"]}""" to "sha256 空白",
            """{"versionCode": 2, "sha256": "aa", "urls": ["u"]}""" to "缺 size",
            """{"versionCode": 2, "sha256": "aa", "size": 0, "urls": ["u"]}""" to "size 非正",
            """{"versionCode": 2, "sha256": "aa", "size": 10}""" to "缺 urls",
            """{"versionCode": 2, "sha256": "aa", "size": 10, "urls": []}""" to "urls 空数组",
            """{"versionCode": 2, "sha256": "aa", "size": 10, "urls": ["  "]}""" to "urls 全是空白",
            """{"versionCode": 2, "sha256": "aa", "size": 10, "urls": "https://x"}""" to "urls 不是数组",
            """{"versionCode": "二", "sha256": "aa", "size": 10, "urls": ["u"]}""" to "versionCode 不是数字",
            """{"versionCode": 2, "versionName": "0.2.0-beta.1", "channel": "beta", "sha256": "aa", "size": 10, "urls": ["u"]}""" to "channel 认不出",
            """{"versionCode": 2, "versionName": "0.2.0", "channel": 1, "sha256": "aa", "size": 10, "urls": ["u"]}""" to "channel 不是字符串",
        )
        cases.forEach { (json, note) ->
            assertNull("「$note」应当判为无效清单", UpdateManifest.parse(json))
        }
    }

    /** 表驱动：版本比较与"强烈建议"分级（只影响文案，从不强制，D059）。 */
    @Test
    fun `statusFor table`() {
        fun manifest(code: Int, min: Int) = UpdateManifest(
            versionCode = code,
            versionName = code.toString(),
            minVersionCode = min,
            changelog = emptyList(),
            sha256 = "aa",
            size = 1,
            urls = listOf("https://x/y.apk"),
        )

        // 本机版本 to 清单 to 期望（null = UpToDate）
        val cases = listOf(
            Triple(1, manifest(2, 1), false) to "有新版，普通提示",
            Triple(1, manifest(2, 3), true) to "本机低于 minVersionCode → 强烈建议",
            Triple(2, manifest(2, 1), null) to "同版本 → 已是最新",
            Triple(3, manifest(2, 1), null) to "本机更新（开发包）→ 已是最新",
            Triple(1, manifest(1, 1), null) to "清单与本机同版本 → 已是最新",
            Triple(0, manifest(1, 0), false) to "minVersionCode 为 0 时不算强烈建议",
            Triple(2, manifest(3, 2), false) to "刚好等于 minVersionCode → 普通提示",
        )
        cases.forEach { (input, note) ->
            val (current, m, expectedStrong) = input
            val status = m.statusFor(current)
            if (expectedStrong == null) {
                assertEquals("「$note」应为已是最新", UpdateStatus.UpToDate, status)
            } else {
                assertTrue("「$note」应为有新版", status is UpdateStatus.Available)
                assertEquals(
                    "「$note」的强烈建议标记",
                    expectedStrong,
                    (status as UpdateStatus.Available).stronglyRecommended,
                )
                assertEquals(m, status.manifest)
            }
        }
    }
}
