package dev.penned.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 更新页的文案口径（规格 M2 S6）——纯函数，表驱动单测。 */
object UpdateText {

    private val CHECK_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm")

    /** 「上次检查 10月10日 21:30」。 */
    fun checkTimeLabel(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(CHECK_TIME)

    /** 下载百分比（0~100，整数；总长为 0 或未知时按 0）。 */
    fun progressPercent(downloaded: Long, total: Long): Int =
        if (total <= 0L) 0 else ((downloaded * 100) / total).toInt().coerceIn(0, 100)

    /** 安装包大小文案（MB，一位小数）：`8.1 MB`。 */
    fun sizeLabel(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}
