package dev.penned.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 预算计算纯函数（D004：从流水重算，不落库）。
 * 支出口径：type=EXPENSE 且 excludeFromBudget=0（后者由查询层过滤）；
 * 分类预算含其直接子分类的支出。
 */
object BudgetMath {

    data class BudgetState(
        val amountCents: Long,
        val spentCents: Long,
        val remainingCents: Long,
        /** 已用百分比 0..1；amount=0 时为 0。 */
        val usedFraction: Float,
    ) {
        val overspent: Boolean get() = remainingCents < 0
    }

    /** 当月窗口 [月初, 月末] 的 epochMinute（本地时区，含端点）。 */
    fun monthWindowMinute(year: Int, month: Int, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val ym = YearMonth.of(year, month)
        val start = ym.atDay(1).atStartOfDay(zone).toEpochSecond() / 60
        val endExclusive = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toEpochSecond() / 60
        return start until endExclusive
    }

    fun state(amountCents: Long, spentCents: Long): BudgetState = BudgetState(
        amountCents = amountCents,
        spentCents = spentCents,
        remainingCents = amountCents - spentCents,
        usedFraction = if (amountCents <= 0) 0f else (spentCents.toFloat() / amountCents).coerceIn(0f, 1f),
    )

    /**
     * 按月度分类支出聚合计算某预算的已用金额。
     * [spend] 为 (categoryId → cents)（categoryId 可空 = 未分类）；
     * 总预算（categoryId=null）取全部支出（含未分类）；分类预算取该分类 + 直接子分类。
     */
    fun spentForBudget(
        spendByCategory: Map<String?, Long>,
        budgetCategoryId: String?,
        subCategoryIds: Set<String> = emptySet(),
    ): Long {
        if (budgetCategoryId == null) return spendByCategory.values.sum()
        return spendByCategory.filterKeys { it == budgetCategoryId || it in subCategoryIds }.values.sum()
    }

    /** 本月 1 日的 epochDay（BudgetEntity.startEpochDay 锚点）。 */
    fun monthAnchorEpochDay(today: LocalDate = LocalDate.now()): Long = today.withDayOfMonth(1).toEpochDay()
}
