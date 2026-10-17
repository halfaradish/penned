package dev.penned.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 记一笔日期/时间 chip 的纯逻辑（specs/M1.md 第二批：日期选择 chip；优化轮 3：时间到分）。
 * - 未选择（null）= 今天 / 现在，落库用当前时刻；
 * - 选了某天 = 该天 + 当前时刻的时分（与一木行为一致）；选了时分 = 所选日（没选日就是今天）+ 所选时分；
 * - 编辑已有流水时按原 minute 反推日期与时分，哪头都没动则原值原样。
 *
 * 编码约定：`dateEpochMinute` 是**真 epoch 分钟**（UTC 起算），本地墙钟显示由时区换算得出
 * ——与导入器（YimuXlsParser）、列表分组（HomeGroups）、统计/预算窗口（BudgetMath）、详情页同一约定。
 * 不能写成 `epochDay * 1440 + 时分`（那是"墙钟编码"，在 UTC+8 会偏 8 小时、记到隔壁日期）。
 */
object AddDate {

    /** chip 文案：今天显示"今天 M月d日"，其他天显示"M月d日"。 */
    fun chipLabel(epochDay: Long?, today: LocalDate): String {
        val d = epochDay?.let { LocalDate.ofEpochDay(it) } ?: today
        val prefix = if (epochDay == null || d == today) "今天 " else ""
        return "$prefix${d.monthValue}月${d.dayOfMonth}日"
    }

    /** 「时间」chip 文案：没选 = "现在"（保存时取当前时刻）；选了 = `HH:mm`（24 小时制、补零，与列表行时段同写法）。 */
    fun timeChipLabel(time: LocalTime?): String =
        time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "现在"

    /**
     * 保存用的 epochMinute：[epochDay] 与 [time] 都没给 = 当前时刻；给哪头用哪头
     * （只给日期 = 该天 + 当前时分（M1 老行为）；只给时分 = 今天 + 该时分）。
     */
    fun epochMinuteFor(epochDay: Long?, time: LocalTime?, now: ZonedDateTime): Long {
        if (epochDay == null && time == null) return now.toEpochSecond() / 60
        val day = epochDay?.let { LocalDate.ofEpochDay(it) } ?: now.toLocalDate()
        val hour = time?.hour ?: now.hour
        val minute = time?.minute ?: now.minute
        return day.atTime(hour, minute).atZone(now.zone).toEpochSecond() / 60
    }

    /** 从既有流水的 minute 反推本地日期的 epochDay（编辑预填用）。 */
    fun dayFromMinute(minute: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochSecond(minute * 60).atZone(zone).toLocalDate().toEpochDay()

    /** 从既有流水的 minute 反推本地时分（编辑预填用）。 */
    fun timeFromMinute(minute: Long, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochSecond(minute * 60).atZone(zone).toLocalTime()

    /**
     * 编辑保存用的 epochMinute：**日与时都没动（或没给）→ 原值原样**（分钟精度不漂移）；
     * 只改日 → 所选日 + 原流水的时分；只改时 → 原日期 + 所选时分；都改 → 都按所选。
     * [epochDay] / [time] 为 null 均视为"没动"。
     */
    fun epochMinuteForEdit(
        originalMinute: Long,
        epochDay: Long?,
        time: LocalTime? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val originalDay = dayFromMinute(originalMinute, zone)
        val originalTime = timeFromMinute(originalMinute, zone)
        val targetDay = epochDay ?: originalDay
        val targetTime = time ?: originalTime
        if (targetDay == originalDay &&
            targetTime.hour == originalTime.hour &&
            targetTime.minute == originalTime.minute
        ) {
            return originalMinute
        }
        return LocalDate.ofEpochDay(targetDay).atTime(targetTime.hour, targetTime.minute)
            .atZone(zone).toEpochSecond() / 60
    }

    /** 周几：`周一`…`周日`（首页日头条与统计趋势图的横轴共用同一份映射）。 */
    fun weekdayLabel(date: LocalDate): String = "周" + "一二三四五六日"[date.dayOfWeek.value - 1]

    /** LocalDate ↔ DatePicker 的 UTC 毫秒换算。 */
    fun dayToPickerMillis(epochDay: Long): Long = epochDay * 86_400_000L

    fun pickerMillisToDay(millis: Long): Long = Math.floorDiv(millis, 86_400_000L)
}
