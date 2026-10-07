package dev.penned.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 记一笔日期 chip 的纯逻辑（specs/M1.md 第二批：日期选择 chip）。
 * - 未选择（null）= 今天，落库用当前时刻；
 * - 选了某天 = 该天 + 当前时刻的时分（与一木行为一致）；
 * - 编辑已有流水时按原 minute 反推日期，日期没动则原值原样。
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

    /** 保存用的 epochMinute：null=当前时刻；否则所选日期 + 当前时刻的时分。 */
    fun epochMinuteFor(epochDay: Long?, now: ZonedDateTime): Long =
        if (epochDay == null) {
            now.toEpochSecond() / 60
        } else {
            LocalDate.ofEpochDay(epochDay).atTime(now.hour, now.minute).atZone(now.zone).toEpochSecond() / 60
        }

    /** 从既有流水的 minute 反推本地日期的 epochDay（编辑预填用）。 */
    fun dayFromMinute(minute: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochSecond(minute * 60).atZone(zone).toLocalDate().toEpochDay()

    /**
     * 编辑保存用的 epochMinute：日期没动 → 原值原样（分钟精度不漂移）；
     * 换了日期 → 所选日期 + 原流水的时分（只挪天，不动时间点）。epochDay 为 null 视为没动。
     */
    fun epochMinuteForEdit(
        originalMinute: Long,
        epochDay: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val originalDay = dayFromMinute(originalMinute, zone)
        val targetDay = epochDay ?: return originalMinute
        if (targetDay == originalDay) return originalMinute
        val time = Instant.ofEpochSecond(originalMinute * 60).atZone(zone).toLocalTime()
        return LocalDate.ofEpochDay(targetDay).atTime(time.hour, time.minute).atZone(zone).toEpochSecond() / 60
    }

    /** 周几：`周一`…`周日`（首页日头条与统计趋势图的横轴共用同一份映射）。 */
    fun weekdayLabel(date: LocalDate): String = "周" + "一二三四五六日"[date.dayOfWeek.value - 1]

    /** LocalDate ↔ DatePicker 的 UTC 毫秒换算。 */
    fun dayToPickerMillis(epochDay: Long): Long = epochDay * 86_400_000L

    fun pickerMillisToDay(millis: Long): Long = Math.floorDiv(millis, 86_400_000L)
}
