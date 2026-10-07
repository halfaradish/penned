package dev.penned.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 统计页时间维度（AI 轮）：近七日 / 月统计 / 年统计 / 自定义。 */
enum class StatsView(val label: String) {
    LAST_7_DAYS("近七日"),
    MONTH("月统计"),
    YEAR("年统计"),
    CUSTOM("自定义"),
}

/**
 * 一期统计窗口：查询用分钟区间 `[startMinute, endMinuteExclusive)` 与展示文案。
 * [startEpochDay]…[endEpochDay] 两端都含，[dayCount] 是自然日数（日均的分母）。
 */
data class StatsWindow(
    val startMinute: Long,
    val endMinuteExclusive: Long,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val label: String,
) {
    /** 自然日数，至少 1（同日自定义区间也不会除零）。 */
    val dayCount: Int get() = (endEpochDay - startEpochDay + 1).toInt().coerceAtLeast(1)

    /** DAO 的 `BETWEEN :from AND :to` 是闭区间，上界要减一分钟。 */
    val lastMinute: Long get() = endMinuteExclusive - 1
}

/**
 * 统计期（AI 轮设计）。窗口一律按本地墙钟算——与 [BudgetMath.monthWindowMinute] 同一约定；
 * 不能写成 `epochDay * 1440` 那种墙钟编码（M1 收尾修的时区坑）或 UTC 固定偏移。
 */
sealed interface StatsPeriod {
    val view: StatsView

    /** 近七日：含今天的滚动 7 天（今天−6 … 今天）。 */
    data object Last7Days : StatsPeriod {
        override val view: StatsView get() = StatsView.LAST_7_DAYS
    }

    data class Month(val yearMonth: YearMonth) : StatsPeriod {
        override val view: StatsView get() = StatsView.MONTH
    }

    data class Year(val year: Int) : StatsPeriod {
        override val view: StatsView get() = StatsView.YEAR
    }

    /** 自定义区间：起止日都含；倒着选由 [StatsPeriods.of] / [StatsPeriods.custom] 纠正。 */
    data class Custom(val startEpochDay: Long, val endEpochDay: Long) : StatsPeriod {
        override val view: StatsView get() = StatsView.CUSTOM
    }

    /** 只有月/年能前后翻；近七日固定"到今天"，自定义由用户自己选。 */
    val navigable: Boolean get() = view == StatsView.MONTH || view == StatsView.YEAR
}

/** 统计期的构造与换算（纯函数，表驱动测试）。 */
object StatsPeriods {

    /** 切到某个视图时的初始期：以当前窗口的最后一天为锚（保留"我正在看哪一段"的上下文）。 */
    fun of(view: StatsView, asOf: LocalDate): StatsPeriod = when (view) {
        StatsView.LAST_7_DAYS -> StatsPeriod.Last7Days
        StatsView.MONTH -> StatsPeriod.Month(YearMonth.from(asOf))
        StatsView.YEAR -> StatsPeriod.Year(asOf.year)
        // 自定义初始成"锚所在月的 1 日 … 锚当天"
        StatsView.CUSTOM -> custom(asOf.withDayOfMonth(1).toEpochDay(), asOf.toEpochDay())
    }

    /** 自定义区间：倒着选（起 > 止）自动交换。 */
    fun custom(startEpochDay: Long, endEpochDay: Long): StatsPeriod.Custom =
        if (startEpochDay <= endEpochDay) {
            StatsPeriod.Custom(startEpochDay, endEpochDay)
        } else {
            StatsPeriod.Custom(endEpochDay, startEpochDay)
        }

    fun prev(period: StatsPeriod): StatsPeriod = when (period) {
        is StatsPeriod.Month -> period.copy(yearMonth = period.yearMonth.minusMonths(1))
        is StatsPeriod.Year -> period.copy(year = period.year - 1)
        else -> period
    }

    fun next(period: StatsPeriod): StatsPeriod = when (period) {
        is StatsPeriod.Month -> period.copy(yearMonth = period.yearMonth.plusMonths(1))
        is StatsPeriod.Year -> period.copy(year = period.year + 1)
        else -> period
    }

    /** 当前期的最后一天（视图切换的锚点）。 */
    fun lastDay(period: StatsPeriod, zone: ZoneId = ZoneId.systemDefault()): LocalDate = when (period) {
        is StatsPeriod.Last7Days -> LocalDate.now(zone)
        is StatsPeriod.Month -> period.yearMonth.atEndOfMonth()
        is StatsPeriod.Year -> LocalDate.of(period.year, 12, 31)
        is StatsPeriod.Custom -> LocalDate.ofEpochDay(period.endEpochDay)
    }

    fun window(
        period: StatsPeriod,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): StatsWindow = when (period) {
        is StatsPeriod.Last7Days -> {
            val start = today.minusDays(6)
            rangeWindow(start, today, rangeLabel(start, today, today), zone)
        }
        is StatsPeriod.Month -> {
            val ym = period.yearMonth
            val start = ym.atDay(1)
            val end = ym.atEndOfMonth()
            val range = BudgetMath.monthWindowMinute(ym.year, ym.monthValue, zone)
            StatsWindow(
                startMinute = range.first,
                endMinuteExclusive = range.last + 1,
                startEpochDay = start.toEpochDay(),
                endEpochDay = end.toEpochDay(),
                label = "${ym.year}年${ym.monthValue}月",
            )
        }
        is StatsPeriod.Year -> {
            val start = LocalDate.of(period.year, 1, 1)
            val end = LocalDate.of(period.year, 12, 31)
            rangeWindow(start, end, "${period.year}年", zone)
        }
        is StatsPeriod.Custom -> {
            val start = LocalDate.ofEpochDay(period.startEpochDay)
            val end = LocalDate.ofEpochDay(period.endEpochDay)
            rangeWindow(start, end, rangeLabel(start, end, today), zone)
        }
    }

    /** 空态标题：文案跟着期走（不然 9 月看"本月没有收支记录"会莫名其妙）。 */
    fun emptyTitle(
        period: StatsPeriod,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): String = when (period) {
        is StatsPeriod.Last7Days -> "近七日没有收支记录"
        is StatsPeriod.Month -> if (period.yearMonth == YearMonth.from(today)) "本月没有收支记录" else "该月没有收支记录"
        is StatsPeriod.Year -> if (period.year == today.year) "本年没有收支记录" else "该年没有收支记录"
        is StatsPeriod.Custom -> "该时间段没有收支记录"
    }

    private fun rangeWindow(
        start: LocalDate,
        end: LocalDate,
        label: String,
        zone: ZoneId,
    ): StatsWindow = StatsWindow(
        startMinute = start.atStartOfDay(zone).toEpochSecond() / 60,
        // 止日整天都含：下一天的 00:00 作为开区间上界
        endMinuteExclusive = end.plusDays(1).atStartOfDay(zone).toEpochSecond() / 60,
        startEpochDay = start.toEpochDay(),
        endEpochDay = end.toEpochDay(),
        label = label,
    )

    /** 区间文案：同年省略年份（跨今天所在年也省），跨年两端都带。 */
    private fun rangeLabel(start: LocalDate, end: LocalDate, today: LocalDate): String {
        val sameYear = start.year == end.year
        val startPart = buildString {
            if (!sameYear || start.year != today.year) append(start.year).append('年')
            append(start.monthValue).append('月').append(start.dayOfMonth).append('日')
        }
        val endPart = buildString {
            if (!sameYear) append(end.year).append('年')
            append(end.monthValue).append('月').append(end.dayOfMonth).append('日')
        }
        return "$startPart–$endPart"
    }
}
