package dev.penned.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 记一笔日期 chip 的纯逻辑（specs/M1.md 第二批：日期选择 chip）。
 * - 未选择（null）= 今天，落库用当前时刻；
 * - 选了某天 = 该天 + 当前时刻的时分（与一木行为一致）；
 * - 编辑已有流水时按原 minute 反推日期，保存不再改动时间。
 */
object AddDate {

    private const val DAY_MINUTES = 1440

    /** chip 文案：今天显示"今天 M月d日"，其他天显示"M月d日"。 */
    fun chipLabel(epochDay: Long?, today: LocalDate): String {
        val d = epochDay?.let { LocalDate.ofEpochDay(it) } ?: today
        val prefix = if (epochDay == null || d == today) "今天 " else ""
        return "$prefix${d.monthValue}月${d.dayOfMonth}日"
    }

    /** 保存用的 epochMinute：null=当前时刻；否则所选日期 + 当前时刻的时分。 */
    fun epochMinuteFor(epochDay: Long?, now: ZonedDateTime): Long =
        if (epochDay == null) {
            now.toEpochSecond() / 60
        } else {
            epochDay * DAY_MINUTES + (now.hour * 60 + now.minute)
        }

    /** 从既有流水的 minute 反推本地日期的 epochDay（编辑预填用）。 */
    fun dayFromMinute(minute: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochSecond(minute * 60).atZone(zone).toLocalDate().toEpochDay()

    /** LocalDate ↔ DatePicker 的 UTC 毫秒换算。 */
    fun dayToPickerMillis(epochDay: Long): Long = epochDay * 86_400_000L

    fun pickerMillisToDay(millis: Long): Long = Math.floorDiv(millis, 86_400_000L)
}
