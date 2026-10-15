package dev.penned.app.domain

import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * 房贷计算（M3-11，纯函数，表驱动测试在 `LoanMathTest`）。
 *
 * 两种方式（口径写死在这里，UI 只负责展示）：
 * - **等额本息**：月供固定 = P·r·(1+r)^n / ((1+r)^n − 1)（r = 年利率/12，n = 期数）；
 *   逐期利息 = 剩余本金·r、本金 = 月供 − 利息；**末期本金 = 剩余本金**（把四舍五入的零头在最后一期抹平）
 * - **等额本金**：每期本金 = P/n（零头放在第一期），利息 = 剩余本金·r；月供逐期递减
 *
 * 金额一律 **Long 分**；利率只在中间计算里用 Double（不是"存钱"），每期都四舍五入到分，
 * 末期用"剩余本金"兜底，保证 Σ本金 = 本金、Σ（本金+利息） = 总还款。
 */
object LoanMath {

    enum class Method { EQUAL_INSTALLMENT, EQUAL_PRINCIPAL }

    /** 一期的还款构成（金额都是分）。 */
    data class Period(
        val index: Int,
        val principalCents: Long,
        val interestCents: Long,
        val remainingCents: Long,
    ) {
        val paymentCents: Long get() = principalCents + interestCents
    }

    /** 计算结果：逐期计划 + 三数汇总。 */
    data class Plan(
        val method: Method,
        val principalCents: Long,
        val periods: List<Period>,
    ) {
        val totalInterestCents: Long get() = periods.sumOf { it.interestCents }
        val totalPaymentCents: Long get() = periods.sumOf { it.paymentCents }
        val firstPaymentCents: Long get() = periods.firstOrNull()?.paymentCents ?: 0L
        val lastPaymentCents: Long get() = periods.lastOrNull()?.paymentCents ?: 0L
    }

    /** 每月利率（年利率百分数 → 小数）。0 = 无息。 */
    fun monthlyRate(annualRatePercent: Double): Double = annualRatePercent / 100.0 / 12.0

    /** 等额本息月供（分）：n = 期数；0 利率时就是本金/n。 */
    fun equalInstallmentPaymentCents(principalCents: Long, annualRatePercent: Double, periods: Int): Long {
        if (periods <= 0) return 0L
        val r = monthlyRate(annualRatePercent)
        if (r <= 0.0) return (principalCents.toDouble() / periods).roundToLong()
        val factor = (1 + r).pow(periods)
        return (principalCents * r * factor / (factor - 1)).roundToLong()
    }

    /** 两种方式的统一入口。 */
    fun plan(
        principalCents: Long,
        annualRatePercent: Double,
        periods: Int,
        method: Method,
    ): Plan {
        if (principalCents <= 0L || periods <= 0) {
            return Plan(method, principalCents, emptyList())
        }
        val r = monthlyRate(annualRatePercent)
        val schedule = mutableListOf<Period>()
        var remaining = principalCents
        when (method) {
            Method.EQUAL_INSTALLMENT -> {
                val payment = equalInstallmentPaymentCents(principalCents, annualRatePercent, periods)
                for (i in 1..periods) {
                    val interest = (remaining * r).roundToLong()
                    val principalPart = if (i == periods) remaining else (payment - interest).coerceAtMost(remaining)
                    remaining -= principalPart
                    schedule += Period(i, principalPart, interest, remaining)
                }
            }
            Method.EQUAL_PRINCIPAL -> {
                // 每期本金：零头放在第一期（首期多还一点，末期刚好归零）
                val base = principalCents / periods
                val extra = principalCents - base * periods
                for (i in 1..periods) {
                    val interest = (remaining * r).roundToLong()
                    val principalPart = if (i == periods) remaining else base + if (i == 1) extra else 0L
                    remaining -= principalPart
                    schedule += Period(i, principalPart, interest, remaining)
                }
            }
        }
        return Plan(method, principalCents, schedule)
    }

    /**
     * 逐期还款日：首期还款日 + (i−1) 个月，**月对齐与周期记账同一套**（`RecurringMath.advance`，
     * 锚定日 29/30/31 遇短月夹到月末、后续期回到锚定日）——月对齐的规则只此一处，改一处改另一处。
     */
    fun periodDates(firstEpochDay: Long, periods: Int): List<Long> {
        if (periods <= 0) return emptyList()
        val anchorDay = LocalDate.ofEpochDay(firstEpochDay).dayOfMonth
        val dates = mutableListOf(firstEpochDay)
        while (dates.size < periods) {
            val next = RecurringMath.advance(dates.last(), RecurringPeriod.MONTHLY, 1, anchorDay)
            if (next <= dates.last()) break // 防御：推进不动就停
            dates += next
        }
        return dates
    }

    /** 中文名（结果卡与计划表头共用）。 */
    fun methodLabel(method: Method): String = when (method) {
        Method.EQUAL_INSTALLMENT -> "等额本息"
        Method.EQUAL_PRINCIPAL -> "等额本金"
    }
}

/**
 * 信用卡年费到期判定（M3-11，纯函数，表驱动测试在 `FeeMathTest`）。
 *
 * 口径：**到期 = 今年已经过了收费日（含当天）且今年还没标记过已记**
 * （`lastPaidYear == today.year` 就不算到期）；收费日 29/30/31 遇短月**夹到当月最后一天**
 * （2/29 在平年按 2/28 到期、31 日在 4 月按 4/30 到期）——与周期记账的月对齐同一套思路。
 * **不做后台通知**：只在打开资产页时判定。
 */
object FeeMath {

    /** 今年的实际收费日（夹到当月最后一天）。 */
    fun feeDateIn(year: Int, month: Int, day: Int): LocalDate {
        val m = month.coerceIn(1, 12)
        val first = LocalDate.of(year, m, 1)
        return first.withDayOfMonth(day.coerceIn(1, first.lengthOfMonth()))
    }

    /** 到期与否；没设年费（feeCents = 0）或没设日期（month/day 为空）一律不到期。 */
    fun isDue(
        feeCents: Long,
        feeMonth: Int?,
        feeDay: Int?,
        lastPaidYear: Int?,
        today: LocalDate,
    ): Boolean {
        if (feeCents <= 0L) return false
        val month = feeMonth ?: return false
        val day = feeDay ?: return false
        if (lastPaidYear == today.year) return false
        val due = feeDateIn(today.year, month, day)
        return !today.isBefore(due)
    }

    /** 行副标题文案：`年费 ¥480.00 · 每年 3 月 12 日`。 */
    fun label(feeCents: Long, feeMonth: Int?, feeDay: Int?): String? {
        if (feeCents <= 0L || feeMonth == null || feeDay == null) return null
        return "年费 ¥${Money.format(feeCents)} · 每年 ${feeMonth} 月 ${feeDay} 日"
    }

    /**
     * 年费流水该落在哪个分类（M3-11）：在**支出**分类里先找名字含「年费」的，再找含「手续费」的；
     * 都没有返回 null（**不擅自新建分类**——用户可以在记一笔里自己补一个）。
     * 入参是 `(id, name)` 对，顺序即优先顺序（分类按 sortOrder 排好的展示序）。
     */
    fun feeCategoryId(expenseCategories: List<Pair<String, String>>): String? =
        expenseCategories.firstOrNull { it.second.contains("年费") }?.first
            ?: expenseCategories.firstOrNull { it.second.contains("手续费") }?.first
}
