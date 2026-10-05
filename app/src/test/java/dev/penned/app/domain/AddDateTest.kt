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
        // 选了 10月3日：当天 + 当前时分
        val day = LocalDate.of(2026, 10, 3).toEpochDay()
        assertEquals(day * 1440 + (21 * 60 + 5), AddDate.epochMinuteFor(day, now))
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
}
