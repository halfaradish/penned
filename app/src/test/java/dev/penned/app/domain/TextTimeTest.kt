package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 宽松时间解析（M4-7 账单列表导入，D111）——表驱动。
 *
 * 样张就是实测的真实形态：微信写 `10月18日 09:23`、支付宝写 `10-16 22:42` 与 `昨天 23:38`、
 * 有年份的完整时间也要照收。**都是墙钟 → 真 epoch 分钟**（换算在断言里用同一个时区算出来）。
 */
class TextTimeTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定"现在"：2026-10-18 13:00（周日），跨年那几条单独再给 now。 */
    private fun now(date: LocalDate = LocalDate.of(2026, 10, 18), time: LocalTime = LocalTime.of(13, 0)): ZonedDateTime =
        ZonedDateTime.of(date, time, zone)

    private fun expected(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute), zone).toEpochSecond() / 60

    private fun parse(text: String, at: ZonedDateTime = now()): Long? =
        TextTime.minuteFromLooseText(text, at, zone)

    @Test
    fun `带年份的四种写法`() {
        val cases = listOf(
            "2026-10-17 12:30",
            "2026-10-17 12:30:45",
            "2026/10/17 12:30",
            "2026-10-17T12:30",
        )
        for (text in cases) {
            assertEquals("该条应解析成 2026-10-17 12:30：$text", expected(2026, 10, 17, 12, 30), parse(text))
        }
    }

    @Test
    fun `微信列表的时间 月日无年份`() {
        // 真实样张：10月18日 09:23 / 10月2日 18:17 / 10月15日 17:46
        assertEquals(expected(2026, 10, 18, 9, 23), parse("10月18日 09:23"))
        assertEquals(expected(2026, 10, 2, 18, 17), parse("10月2日 18:17"))
        assertEquals(expected(2026, 10, 15, 17, 46), parse("10月15日 17:46"))
        assertEquals(expected(2026, 10, 1, 20, 26), parse("10月1日20:26")) // 没空格也认
    }

    @Test
    fun `支付宝列表的 月-日 与 昨天`() {
        // 真实样张：10-16 22:42 / 昨天 23:38
        assertEquals(expected(2026, 10, 16, 22, 42), parse("10-16 22:42"))
        assertEquals(expected(2026, 10, 17, 23, 38), parse("昨天 23:38"))
        assertEquals(expected(2026, 10, 16, 22, 42), parse("10/16 22:42"))
    }

    @Test
    fun `相对日与只有时分`() {
        assertEquals(expected(2026, 10, 18, 8, 5), parse("今天 08:05"))
        assertEquals(expected(2026, 10, 18, 8, 5), parse("今日 08:05"))
        assertEquals(expected(2026, 10, 17, 23, 38), parse("昨天23:38")) // 无空格
        assertEquals(expected(2026, 10, 16, 23, 30), parse("前天 23:30"))
        assertEquals(expected(2026, 10, 15, 0, 0), parse("大前天")) // 没时分 = 当天 00:00
        assertEquals(expected(2026, 10, 18, 9, 23), parse("09:23")) // 只有时分 = 今天
    }

    @Test
    fun `有日期没时分取当天零点`() {
        assertEquals(expected(2026, 10, 17, 0, 0), parse("2026-10-17"))
        assertEquals(expected(2026, 10, 17, 0, 0), parse("10月17日"))
        assertEquals(expected(2026, 10, 17, 0, 0), parse("10-17"))
    }

    @Test
    fun `无年份按最近一次过去推`() {
        // 今天 = 2026-10-18：10-16 是今年 ✓
        assertEquals(expected(2026, 10, 16, 22, 42), parse("10-16 22:42"))
        // "12月31日" 在今天之后 → 推去年（账单只记已经发生的事）
        assertEquals(expected(2025, 12, 31, 20, 0), parse("12月31日 20:00"))
        // 跨年场景：1 月看 12 月的账 → 去年
        assertEquals(
            expected(2025, 12, 30, 21, 21),
            parse("12-30 21:21", at = now(LocalDate.of(2026, 1, 5))),
        )
        // 9 月的账在 10 月看 → 今年
        assertEquals(expected(2026, 9, 30, 17, 43), parse("9月30日 17:43"))
    }

    @Test
    fun `12 小时制的下午晚上`() {
        assertEquals(expected(2026, 10, 18, 15, 20), parse("10月18日 下午 3:20"))
        assertEquals(expected(2026, 10, 18, 20, 5), parse("10月18日 晚上 8:05"))
        assertEquals(expected(2026, 10, 18, 12, 30), parse("10月18日 中午 12:30"))
        assertEquals(expected(2026, 10, 18, 15, 20), parse("10月18日 15:20")) // 24 小时制原样
    }

    @Test
    fun `全角数字与全角冒号也认`() {
        assertEquals(expected(2026, 10, 18, 9, 23), parse("１０月１８日 ０９：２３"))
        assertEquals(expected(2026, 10, 17, 12, 30), parse("2026-10-17 12：30"))
    }

    @Test
    fun `认不出就不猜`() {
        val bad = listOf(
            null, "", "   ", "看不清", "昨天中午", "刚刚", "前天下午",
            "2026-02-30 10:00", // 不存在的日期
            "10月32日 10:00",
            "2026-10-17 25:00", // 不存在的时刻
            "13月1日 10:00",
            "25:00",
        )
        for (text in bad) {
            assertNull("这条不许解析出时间：$text", parse(text.orEmpty()))
        }
    }

    @Test
    fun `墙钟走时区换算 不是 epochDay 编码`() {
        val minute = parse("2026-10-17 23:38")!!
        val wallEncoded = LocalDate.of(2026, 10, 17).toEpochDay() * 1440 + 23 * 60 + 38
        assertEquals(expected(2026, 10, 17, 23, 38), minute)
        if (zone.rules.getOffset(java.time.Instant.now()).totalSeconds != 0) {
            assertEquals(true, minute != wallEncoded)
        }
    }
}
