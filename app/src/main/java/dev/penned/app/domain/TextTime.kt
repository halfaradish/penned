package dev.penned.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 宽松时间文本 → `dateEpochMinute`（M4-7 改账单列表导入时新增，D111）。
 *
 * 为什么要有它：**真实账单列表里的时间没有年份，也不长成 `yyyy-MM-dd HH:mm`**——实测
 * 微信写 `10月18日 09:23`、支付宝写 `10-16 22:42` 与 `昨天 23:38`。原来的 `parseTimeMinute`
 * 只认四位年，这些一律解析成 null，于是每笔账的日期都退化成"现在"。
 *
 * 口径（逐条钉在 `TextTimeTest`）：
 * - **墙钟 → 真 epoch 分钟**：一律过系统时区换算（与 `AddDate` / `LlmDraftParser` 同一约定），
 *   **不是** `epochDay * 1440 + 时分`（那在西半球会记到隔壁日期）
 * - **无年份按"最近一次过去"推**：今年的这个月日若晚于今天 → 去年（与 `QuickParse` 的
 *   `M月D日` 同一条先例：记账记的是已经发生的事）
 * - **有日期没时分 → 当天 00:00**（列表里时分会一起读到；真缺了也不许瞎编一个"现在几点"）
 * - **只有时分 → 今天**；**认不出 → null（不猜）**，由调用方退回"今天/现在"
 * - 容全角数字、全角冒号、`T` 分隔、多余空白
 */
object TextTime {

    /** `2026-10-17 12:30[:45]` / `2026/10/17 12:30` / `2026-10-17T12:30` / `2026-10-17` */
    private val WITH_YEAR = Regex("""^(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:[ T](\d{1,2}):(\d{2})(?::\d{2})?)?$""")

    /** `10月18日 09:23` / `10月2日18:17` / `10月18日`；带上午/下午/晚上/中午/凌晨 */
    private val CN_DATE = Regex(
        """^(\d{1,2})月(\d{1,2})[日号](?:\s*(上午|下午|晚上|中午|凌晨)?\s*(\d{1,2}):(\d{2}))?$"""
    )

    /** `10-16 22:42` / `10/16 22:42` / `10-16` */
    private val DASH_DATE = Regex("""^(\d{1,2})[-/](\d{1,2})(?:\s+(\d{1,2}):(\d{2}))?$""")

    private val TIME_ONLY = Regex("""^(\d{1,2}):(\d{2})$""")

    /** **先长后短**：`大前天` 里含 `前天`，短的先判会读成 -2（`QuickParse` 踩过同型的坑）。 */
    private val RELATIVE = listOf(
        "大前天" to -3L,
        "今天" to 0L, "今日" to 0L,
        "昨天" to -1L, "昨日" to -1L,
        "前天" to -2L,
    )

    fun minuteFromLooseText(
        text: String?,
        now: ZonedDateTime = ZonedDateTime.now(),
        zone: ZoneId = now.zone,
    ): Long? {
        val value = normalize(text) ?: return null

        WITH_YEAR.find(value)?.let { match ->
            val date = safeDate(
                match.groupValues[1].toIntOrNull() ?: return@let,
                match.groupValues[2].toIntOrNull() ?: return@let,
                match.groupValues[3].toIntOrNull() ?: return@let,
            ) ?: return@let
            val hour = match.groupValues[4].toIntOrNull() ?: 0
            val minute = match.groupValues[5].toIntOrNull() ?: 0
            return toMinute(date, hour, minute, zone)
        }

        CN_DATE.find(value)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            val date = inferYear(month, day, now) ?: return@let
            val hour = applyPeriod(match.groupValues[3], match.groupValues[4].toIntOrNull() ?: 0)
            val minute = match.groupValues[5].toIntOrNull() ?: 0
            return toMinute(date, hour, minute, zone)
        }

        DASH_DATE.find(value)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            val date = inferYear(month, day, now) ?: return@let
            val hour = match.groupValues[3].toIntOrNull() ?: 0
            val minute = match.groupValues[4].toIntOrNull() ?: 0
            return toMinute(date, hour, minute, zone)
        }

        for ((word, delta) in RELATIVE) {
            if (!value.startsWith(word)) continue
            val rest = value.removePrefix(word).trim()
            val date = now.toLocalDate().plusDays(delta)
            if (rest.isEmpty()) return toMinute(date, 0, 0, zone)
            TIME_ONLY.find(rest)?.let { match ->
                return toMinute(
                    date,
                    match.groupValues[1].toIntOrNull() ?: return@let,
                    match.groupValues[2].toIntOrNull() ?: return@let,
                    zone,
                )
            }
            return null // 「昨天中午」这类：日期认得、时分认不出 → 整条不猜
        }

        TIME_ONLY.find(value)?.let { match ->
            return toMinute(
                now.toLocalDate(),
                match.groupValues[1].toIntOrNull() ?: return@let,
                match.groupValues[2].toIntOrNull() ?: return@let,
                zone,
            )
        }

        return null
    }

    /** 归一：全角数字/冒号/点/减号 → 半角，连续空白压成一个空格。全空白或空串 → null。 */
    private fun normalize(text: String?): String? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            sb.append(
                when {
                    ch in '０'..'９' -> '0' + (ch - '０')
                    ch == '：' -> ':'
                    ch == '．' -> '.'
                    ch == '－' || ch == '−' -> '-'
                    else -> ch
                }
            )
        }
        val collapsed = sb.toString().replace(Regex("""\s+"""), " ").trim()
        return collapsed.ifEmpty { null }
    }

    /** 下午/晚上 的 12 小时制补成 24 小时（`下午 3:20` → 15:20）；上午/中午/凌晨照写。 */
    private fun applyPeriod(period: String, hour: Int): Int = when (period) {
        "下午", "晚上" -> if (hour < 12) hour + 12 else hour
        else -> hour
    }

    /** 今年的这个月日若还没到 → 去年（"最近一次过去"）。不存在的日期 → null。 */
    private fun inferYear(month: Int, day: Int, now: ZonedDateTime): LocalDate? {
        val thisYear = safeDate(now.year, month, day) ?: return null
        if (!thisYear.isAfter(now.toLocalDate())) return thisYear
        return safeDate(now.year - 1, month, day) ?: thisYear
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (_: Exception) {
        null // 2月30日 / 13月1日 这类不存在的日期
    }

    private fun toMinute(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long? = try {
        date.atTime(LocalTime.of(hour, minute)).atZone(zone).toEpochSecond() / 60
    } catch (_: Exception) {
        null // 25:99 这类不存在的时刻
    }
}
