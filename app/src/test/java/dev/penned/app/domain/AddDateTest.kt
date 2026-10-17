package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AddDateTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `chip label shows today prefix only for today`() {
        val today = LocalDate.of(2026, 10, 6)
        assertEquals("今天 10月6日", AddDate.chipLabel(null, today))
        assertEquals("今天 10月6日", AddDate.chipLabel(today.toEpochDay(), today))
        assertEquals("10月3日", AddDate.chipLabel(LocalDate.of(2026, 10, 3).toEpochDay(), today))
        assertEquals("11月1日", AddDate.chipLabel(LocalDate.of(2026, 11, 1).toEpochDay(), today))
    }

    @Test
    fun `chip label carries the picked time only when one was picked`() {
        val today = LocalDate.of(2026, 10, 17)
        // 没选过时分 = "现在"：chip 只写日期（时间到分是弹窗里的滚轮，见 D094/D097）
        assertEquals("今天 10月17日", AddDate.chipLabel(null, today, null))
        // 选了时分就带在后面；非今天的日期同理
        assertEquals("今天 10月17日 09:05", AddDate.chipLabel(null, today, LocalTime.of(9, 5)))
        assertEquals("10月3日 16:38", AddDate.chipLabel(LocalDate.of(2026, 10, 3).toEpochDay(), today, LocalTime.of(16, 38)))
    }

    @Test
    fun `time label is zero padded 24h clock`() {
        assertEquals("00:00", AddDate.timeLabel(LocalTime.MIDNIGHT))
        assertEquals("09:05", AddDate.timeLabel(LocalTime.of(9, 5)))
        assertEquals("16:38", AddDate.timeLabel(LocalTime.of(16, 38)))
        assertEquals("23:59", AddDate.timeLabel(LocalTime.of(23, 59)))
    }

    @Test
    fun `weekday label maps all seven days`() {
        // 2026-10-05 是周一，往后一周把七个值走全
        val monday = LocalDate.of(2026, 10, 5)
        assertEquals(
            listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
            (0..6).map { AddDate.weekdayLabel(monday.plusDays(it.toLong())) },
        )
        // 跨年也对（2027-01-01 是周五）
        assertEquals("周五", AddDate.weekdayLabel(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun `epoch minute uses current time with picked day`() {
        val now = ZonedDateTime.of(2026, 10, 6, 21, 5, 30, 0, zone)
        // 日期与时分都没选：当前分钟
        assertEquals(now.toEpochSecond() / 60, AddDate.epochMinuteFor(null, null, now))
        // 选了 10月3日、没选时分：当天 + 当前时分（真 epoch 分钟，与列表/统计解码同约定）
        val day = LocalDate.of(2026, 10, 3).toEpochDay()
        val expected = LocalDate.of(2026, 10, 3).atTime(21, 5).atZone(zone).toEpochSecond() / 60
        assertEquals(expected, AddDate.epochMinuteFor(day, null, now))
        assertEquals(day, AddDate.dayFromMinute(expected, zone))
    }

    @Test
    fun `picked time overrides current time with or without picked day`() {
        val now = ZonedDateTime.of(2026, 10, 6, 21, 5, 0, 0, zone)
        val day = LocalDate.of(2026, 10, 3).toEpochDay()
        // 选了日期 + 时分：所选日 + 所选时分
        val expected = LocalDate.of(2026, 10, 3).atTime(16, 38).atZone(zone).toEpochSecond() / 60
        assertEquals(expected, AddDate.epochMinuteFor(day, LocalTime.of(16, 38), now))
        // 只选时分（没选日期）：今天 + 所选时分
        val todayExpected = LocalDate.of(2026, 10, 6).atTime(16, 38).atZone(zone).toEpochSecond() / 60
        assertEquals(todayExpected, AddDate.epochMinuteFor(null, LocalTime.of(16, 38), now))
    }

    /** 不变量：任何时刻选的日期，解码回来必须还是那一天（曾经在 UTC+8 的 16:00 后偏到次日）。 */
    @Test
    fun `picked day round trips for every hour of the day`() {
        val day = LocalDate.of(2026, 10, 3).toEpochDay()
        for (hour in 0..23) {
            for (minute in listOf(0, 59)) {
                val now = ZonedDateTime.of(2026, 10, 6, hour, minute, 0, 0, zone)
                val saved = AddDate.epochMinuteFor(day, null, now)
                assertEquals(
                    "hour=$hour minute=$minute",
                    day,
                    AddDate.dayFromMinute(saved, zone),
                )
            }
        }
    }

    /** 不变量（优化轮 3）：任何选定时分，保存后再解码必须分毫不差地回到同一个时刻。 */
    @Test
    fun `picked time round trips for every hour and edge minutes`() {
        val day = LocalDate.of(2026, 10, 3).toEpochDay()
        val now = ZonedDateTime.of(2026, 10, 6, 21, 5, 0, 0, zone)
        for (hour in 0..23) {
            for (minute in listOf(0, 30, 59)) {
                val picked = LocalTime.of(hour, minute)
                val saved = AddDate.epochMinuteFor(day, picked, now)
                assertEquals("hour=$hour minute=$minute", day, AddDate.dayFromMinute(saved, zone))
                assertEquals("hour=$hour minute=$minute", picked, AddDate.timeFromMinute(saved, zone))
            }
        }
    }

    @Test
    fun `day from minute round trip respects zone`() {
        val minute = ZonedDateTime.of(2026, 10, 6, 23, 30, 0, 0, zone).toEpochSecond() / 60
        assertEquals(LocalDate.of(2026, 10, 6).toEpochDay(), AddDate.dayFromMinute(minute, zone))
    }

    @Test
    fun `picker millis conversion handles partial-day millis`() {
        val day = LocalDate.of(2026, 10, 6).toEpochDay()
        assertEquals(day, AddDate.pickerMillisToDay(AddDate.dayToPickerMillis(day)))
        // DatePicker 返回值带当日 0 点以外的偏移（含时区修正），向下取整到天
        assertEquals(day, AddDate.pickerMillisToDay(day * 86_400_000L + 123))
        assertEquals(day - 1, AddDate.pickerMillisToDay(day * 86_400_000L - 123))
    }

    @Test
    fun `edit keeps original minute when day unchanged or null`() {
        val day = LocalDate.of(2026, 9, 2).toEpochDay()
        val original = LocalDate.of(2026, 9, 2).atTime(16, 38).atZone(zone).toEpochSecond() / 60
        // 日期没动（点开选择器又选同一天 / 压根没点）：原值原样，分钟精度不漂移
        assertEquals(original, AddDate.epochMinuteForEdit(original, day, zone = zone))
        assertEquals(original, AddDate.epochMinuteForEdit(original, null, zone = zone))
    }

    @Test
    fun `edit keeps original minute when time untouched or resolved to the same minute`() {
        val day = LocalDate.of(2026, 9, 2).toEpochDay()
        val original = LocalDate.of(2026, 9, 2).atTime(16, 38).atZone(zone).toEpochSecond() / 60
        // 时分没动（null = 没点过时间 chip）：原值原样
        assertEquals(original, AddDate.epochMinuteForEdit(original, day, null, zone))
        // 点开了时间轮、但选回同一时刻（等同没改）：也原值原样
        assertEquals(original, AddDate.epochMinuteForEdit(original, day, LocalTime.of(16, 38), zone))
        // 日期没给、时分等同没改：同样原值原样
        assertEquals(original, AddDate.epochMinuteForEdit(original, null, LocalTime.of(16, 38), zone))
    }

    @Test
    fun `edit moves day but keeps original time of day`() {
        val newDay = LocalDate.of(2026, 10, 6).toEpochDay()
        val original = LocalDate.of(2026, 9, 2).atTime(16, 38).atZone(zone).toEpochSecond() / 60
        // 换日期：只挪天，时分保留（不是"当前时刻"）
        val moved = AddDate.epochMinuteForEdit(original, newDay, zone = zone)
        assertEquals(newDay, AddDate.dayFromMinute(moved, zone))
        assertEquals(LocalTime.of(16, 38), AddDate.timeFromMinute(moved, zone))
    }

    @Test
    fun `edit changes only the side that was picked`() {
        val day = LocalDate.of(2026, 9, 2).toEpochDay()
        val newDay = LocalDate.of(2026, 10, 6).toEpochDay()
        val original = LocalDate.of(2026, 9, 2).atTime(16, 38).atZone(zone).toEpochSecond() / 60

        // 只改时分（日期没给）：原日期 + 新时分
        val timeOnly = AddDate.epochMinuteForEdit(original, null, LocalTime.of(9, 5), zone)
        assertEquals(day, AddDate.dayFromMinute(timeOnly, zone))
        assertEquals(LocalTime.of(9, 5), AddDate.timeFromMinute(timeOnly, zone))

        // 只改日期（时分没给）：新日期 + 原时分
        val dayOnly = AddDate.epochMinuteForEdit(original, newDay, null, zone)
        assertEquals(newDay, AddDate.dayFromMinute(dayOnly, zone))
        assertEquals(LocalTime.of(16, 38), AddDate.timeFromMinute(dayOnly, zone))

        // 都改：都按所选
        val both = AddDate.epochMinuteForEdit(original, newDay, LocalTime.of(9, 5), zone)
        assertEquals(newDay, AddDate.dayFromMinute(both, zone))
        assertEquals(LocalTime.of(9, 5), AddDate.timeFromMinute(both, zone))
    }
}
