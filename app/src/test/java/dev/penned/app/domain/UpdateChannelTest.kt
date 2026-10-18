package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 更新通道口径（D114）：纯数字 = 正式版、其余 = 预发布；订阅状态由 DataStore 决定，安装包版本名
 * 只用于首次初始化与手动装 beta 的自动升舱。
 */
class UpdateChannelTest {

    @Test
    fun `pure numeric is stable and everything else is prerelease`() {
        val cases = listOf(
            "0.2.0" to false,
            "1.10.3" to false,
            "0.2.0-dev" to true,
            "0.2.0-test" to true,
            "0.2.0-test.2" to true,
            "0.2.0-beta" to true,
            "0.2.0-beta.1" to true,
            "0.2.0-rc.1" to true,
            "0.2" to true, // 不是三段：fail-safe 当预发布
            "0.2.0.1" to true,
            "" to true,
        )
        cases.forEach { (name, expected) ->
            assertEquals("「$name」的通道判断", expected, UpdateChannel.isPrerelease(name))
        }
    }

    @Test
    fun `ofKey table`() {
        assertEquals(UpdateChannel.STABLE, UpdateChannel.ofKey("stable"))
        assertEquals(UpdateChannel.PRERELEASE, UpdateChannel.ofKey("prerelease"))
        assertEquals(null, UpdateChannel.ofKey(null))
        assertEquals(null, UpdateChannel.ofKey(""))
        assertEquals(null, UpdateChannel.ofKey("beta"))
    }

    @Test
    fun `urlsFor prefers the subscribed channel and falls back to stable`() {
        val stable = listOf("https://s1/update.json", "https://s2/update.json")
        val beta = listOf("https://b1/update-prerelease.json", "https://b2/update-prerelease.json")

        assertEquals(stable, UpdateChannel.urlsFor(UpdateChannel.STABLE, stable, beta))
        assertEquals(
            "预发布先读自己的，再拿正式地址兜底",
            beta + stable,
            UpdateChannel.urlsFor(UpdateChannel.PRERELEASE, stable, beta),
        )
        assertEquals(
            "没有预发布地址时退回正式地址",
            stable,
            UpdateChannel.urlsFor(UpdateChannel.PRERELEASE, stable, emptyList()),
        )
        assertEquals(
            "地址去重",
            listOf("https://same/update.json"),
            UpdateChannel.urlsFor(
                UpdateChannel.PRERELEASE,
                listOf("https://same/update.json"),
                listOf("https://same/update.json"),
            ),
        )
    }

    @Test
    fun `policy table`() {
        data class Case(
            val stored: String?,
            val explicit: Boolean,
            val installed: String,
            val channel: UpdateChannel,
            val outExplicit: Boolean,
            val changed: Boolean,
            val note: String,
        )

        val cases = listOf(
            Case(null, false, "0.2.0", UpdateChannel.STABLE, false, true, "全新装正式版 → 首次初始化"),
            Case(null, false, "0.2.0-beta.1", UpdateChannel.PRERELEASE, false, true, "全新装 beta → 首次初始化"),
            Case("stable", false, "0.2.0", UpdateChannel.STABLE, false, false, "正式版日常"),
            Case("prerelease", false, "0.2.0-beta.1", UpdateChannel.PRERELEASE, false, false, "beta 日常"),
            Case("stable", false, "0.2.0-beta.1", UpdateChannel.PRERELEASE, false, true, "正式版设备手动装 beta → 自动升舱"),
            Case("prerelease", false, "0.2.0", UpdateChannel.PRERELEASE, false, false, "beta 升正式版 → 不降级"),
            Case("stable", true, "0.2.0-beta.1", UpdateChannel.STABLE, true, false, "用户显式关掉 → 优先"),
            Case("prerelease", true, "0.2.0", UpdateChannel.PRERELEASE, true, false, "用户显式打开 → 优先"),
            Case("bogus", false, "0.2.0", UpdateChannel.STABLE, false, true, "认不出的值按没存过"),
            Case(null, true, "0.2.0-beta.1", UpdateChannel.PRERELEASE, false, true, "explicit 但没存值 → 仍按安装包初始化"),
        )

        cases.forEach { case ->
            val choice = UpdateChannelPolicy.decide(case.stored, case.explicit, case.installed)
            assertEquals("「${case.note}」通道", case.channel, choice.channel)
            assertEquals("「${case.note}」explicit", case.outExplicit, choice.explicit)
            assertEquals("「${case.note}」是否需要回写", case.changed, choice.changed)
        }
    }
}
