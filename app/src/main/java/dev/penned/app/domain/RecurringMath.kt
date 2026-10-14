package dev.penned.app.domain

import java.time.LocalDate

/**
 * 周期记账的期次推进（M3-6，纯函数，表驱动测试在 `RecurringMathTest`）。
 *
 * **月对齐规则**（规格）：以规则起始日的「日」为**锚定日**——起始 29/30/31 时，
 * 短月取当月最后一天（1/31 → 2/28，闰年 2/29），**后续期回到锚定日**（3/31 而不是 3/28）。
 * 这一条不能直接用 `LocalDate.plusMonths` 连推（它会把 1/31→2/28→3/28 一路塌下去），
 * 所以推进时先算目标月、再把日夹到 `min(锚定日, 当月天数)`。
 */
object RecurringMath {

    /** 一期的长度（用于「下次 M月D日」这类文案与列表排序，不参与日期计算）。 */
    fun advance(fromEpochDay: Long, period: RecurringPeriod, interval: Int, anchorDay: Int): Long {
        val step = interval.coerceAtLeast(1).toLong()
        val base = LocalDate.ofEpochDay(fromEpochDay)
        return when (period) {
            RecurringPeriod.DAILY -> base.plusDays(step).toEpochDay()
            RecurringPeriod.WEEKLY -> base.plusWeeks(step).toEpochDay()
            RecurringPeriod.MONTHLY -> {
                val target = base.plusMonths(step)
                LocalDate.of(target.year, target.monthValue, anchorDay.coerceAtMost(target.lengthOfMonth())).toEpochDay()
            }
            RecurringPeriod.YEARLY -> {
                val target = base.plusYears(step)
                LocalDate.of(target.year, target.monthValue, anchorDay.coerceAtMost(target.lengthOfMonth())).toEpochDay()
            }
        }
    }

    /** 周期中文（列表行与编辑页共用一处）：单期写「每天/每周/每月/每年」，多期写「每 3 个月」。 */
    fun periodLabel(period: RecurringPeriod, interval: Int): String {
        if (interval <= 1) {
            return when (period) {
                RecurringPeriod.DAILY -> "每天"
                RecurringPeriod.WEEKLY -> "每周"
                RecurringPeriod.MONTHLY -> "每月"
                RecurringPeriod.YEARLY -> "每年"
            }
        }
        val unit = when (period) {
            RecurringPeriod.DAILY -> "天"
            RecurringPeriod.WEEKLY -> "周"
            RecurringPeriod.MONTHLY -> "个月"
            RecurringPeriod.YEARLY -> "年"
        }
        return "每 $interval $unit"
    }

    /**
     * 打开即补（D070）：从 [nextEpochDay] 起，列出所有 **≤ today** 的期次日期（epochDay）。
     *
     * - 不产生未来日期的流水（超过 today 的一律不列）
     * - 有 [endEpochDay] 时超过它的不列
     * - 单次最多补 [maxCount] 期：规则放了很久没打开（或起始日填了很早的过去）也不至于一次写爆——
     *   剩下的期次下次打开继续补（`nextEpochDay` 只推进到已生成的那一期）
     */
    fun dueDates(
        nextEpochDay: Long,
        period: RecurringPeriod,
        interval: Int,
        anchorDay: Int,
        todayEpochDay: Long,
        endEpochDay: Long? = null,
        maxCount: Int = MAX_CATCH_UP,
    ): List<Long> {
        val dates = mutableListOf<Long>()
        var cursor = nextEpochDay
        while (cursor <= todayEpochDay && (endEpochDay == null || cursor <= endEpochDay) && dates.size < maxCount) {
            dates += cursor
            val next = advance(cursor, period, interval, anchorDay)
            if (next <= cursor) break // 防御：推进不动就停（不写死循环）
            cursor = next
        }
        return dates
    }

    /**
     * 期次落库的分钟（epochMinute）：该期日期当天 **09:00 本地**——固定时点保证同一期幂等
     * （重复启动算出同一个 minute），又按本地时区换算（不能写 `date * 1440`：那是 UTC 午夜，
     * 西半球会落到前一天）。表驱动测试里用 `AddDate.dayFromMinute` 往返断言。
     */
    fun periodMinute(dateEpochDay: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(dateEpochDay).atTime(9, 0).atZone(zone).toEpochSecond() / 60

    /** 单次补记上限：够覆盖"一年没打开"的日周期（366）还留了余量，又不至于写爆。 */
    const val MAX_CATCH_UP = 400
}
