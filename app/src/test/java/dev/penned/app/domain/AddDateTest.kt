package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
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
    fun `epoch minute uses current time with picked day`() {
        val now = ZonedDateTime.of(2026, 10, 6, 21, 5, 30, 0, zone)
        // 未选日期：当前分钟
        assertEquals(now.toEpochSecond() / 60, AddDate.epochMinuteFor(null, now))
        // 选了 10月3日：当天 + 当前时分（真 epoch 分钟，与列表/统计解码同约定）
        val day = LocalDate.of(2026, 10, 3).toEpochDay()
        val expected = LocalDate.of(2026, 10, 3).atTime(21, 5).atZone(zone).toEpochSecond() / 60
        assertEquals(expected, AddDate.epochMinuteFor(day, now))
        assertEquals(day, AddDate.dayFromMinute(expected, zone))
    }

    /** 不变量：任何时刻选的日期，解码回来必须还是那一天（曾经在 UTC+8 的 16:00 后偏到次日）。 */
    @Test
    fun `picked day round trips for every hour of the day`() {
        val day = LocalDate.of(2026, 10, 3).toEpochDay()
        for (hour in 0..23) {
            for (minute in listOf(0, 59)) {
                val now = ZonedDateTime.of(2026, 10, 6, hour, minute, 0, 0, zone)
                val saved = AddDate.epochMinuteFor(day, now)
                assertEquals(
                    "hour=$hour minute=$minute",
                    day,
                    AddDate.dayFromMinute(saved, zone),
                )
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
        assertEquals(original, AddDate.epochMinuteForEdit(original, day, zone))
        assertEquals(original, AddDate.epochMinuteForEdit(original, null, zone))
    }

    @Test
    fun `edit moves day but keeps original time of day`() {
        val newDay = LocalDate.of(2026, 10, 6).toEpochDay()
        val original = LocalDate.of(2026, 9, 2).atTime(16, 38).atZone(zone).toEpochSecond() / 60
        // 换日期：只挪天，时分保留（不是"当前时刻"）
        val moved = AddDate.epochMinuteForEdit(original, newDay, zone)
        assertEquals(newDay, AddDate.dayFromMinute(moved, zone))
        val time = java.time.Instant.ofEpochSecond(moved * 60).atZone(zone).toLocalTime()
        assertEquals(16 * 60 + 38, time.hour * 60 + time.minute)
    }
}
