package dev.penned.app.domain

import dev.penned.app.data.db.entity.CategoryEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * 组合筛选条件（M3-7，纯函数：口径与文案都在这里，UI 与 SQL 都不许自己算）。
 *
 * 空值语义（写死，表驱动测试在 `TxFilterTest`）：
 * - 文本/枚举/数值：`null` = **不限**；空串按不限处理（`keyword.trim().isEmpty()`）
 * - 多值（分类/账户/标签）：**空列表 = 不限**（不是"筛出 0 条"）
 * - 三个开关：`null` = 不限、true/false = 明确要求
 * - 日期：`from`/`to` 各自可空（只设一头就是"某天之后/之前"），两端都含当天
 *
 * 分类的"含二级"：选中一级分类时**连它的子树一起算**（`expandCategoryIds`）——
 * 与记账宫格、统计的钻取口径一致（选了"食品餐饮"就该看到"食品餐饮 · 午餐"）。
 */
data class TxFilter(
    val keyword: String = "",
    val type: TxType? = null,
    val fromEpochDay: Long? = null,
    val toEpochDay: Long? = null,
    val categoryIds: List<String> = emptyList(),
    val accountIds: List<String> = emptyList(),
    val tagIds: List<String> = emptyList(),
    val amountMinCents: Long? = null,
    val amountMaxCents: Long? = null,
    val excludeFromStats: Boolean? = null,
    val excludeFromBudget: Boolean? = null,
    /** null = 不限；true = 结果含转账、false = 排除转账（只在"类型 = 全部"时有意义）。 */
    val includeTransfer: Boolean? = null,
) {
    /** 有没有任何一个条件（全空 = 还没开始筛）。 */
    val isActive: Boolean
        get() = keyword.isNotBlank() || type != null || fromEpochDay != null || toEpochDay != null ||
            categoryIds.isNotEmpty() || accountIds.isNotEmpty() || tagIds.isNotEmpty() ||
            amountMinCents != null || amountMaxCents != null ||
            excludeFromStats != null || excludeFromBudget != null || includeTransfer != null

    /** 有几个条件在用（chip 上的角标与"清空"的判据）。 */
    val activeCount: Int
        get() = listOf(
            keyword.isNotBlank(),
            type != null,
            fromEpochDay != null || toEpochDay != null,
            categoryIds.isNotEmpty(),
            accountIds.isNotEmpty(),
            tagIds.isNotEmpty(),
            amountMinCents != null || amountMaxCents != null,
            excludeFromStats != null,
            excludeFromBudget != null,
            includeTransfer != null,
        ).count { it }
}

object TxFilters {

    /** 文件名里不允许的字符（Windows/Android 都按这套过滤）。 */
    private val ILLEGAL_FILE_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    /** 日期区间 → 分钟窗口（闭区间、按本地墙钟整天）：null 端不加边界。 */
    fun minuteRange(
        fromEpochDay: Long?,
        toEpochDay: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<Long?, Long?> {
        val from = fromEpochDay?.let { day ->
            LocalDate.ofEpochDay(day).atStartOfDay(zone).toEpochSecond() / 60
        }
        val to = toEpochDay?.let { day ->
            // 当天 23:59（分钟精度）
            LocalDate.ofEpochDay(day).atStartOfDay(zone).toEpochSecond() / 60 + 1439
        }
        return from to to
    }

    /**
     * 分类筛选的 id 展开：选中的 id + 它们的**直接子分类**（含二级）。
     * 已经软删的分类不进（父级选了但子级删了就不算它）。
     */
    fun expandCategoryIds(categories: List<CategoryEntity>, selected: List<String>): List<String> {
        if (selected.isEmpty()) return emptyList()
        val alive = categories.filter { !it.deleted }
        val children = alive.filter { it.parentId != null && it.parentId in selected }.map { it.id }
        return (selected + children).distinct()
    }

    /**
     * 条件摘要（筛选页顶部与账单组合列表行共用）：`支出 · 食品餐饮 · 近 30 天 · 2 个标签`。
     * 只列**在用的**条件，顺序固定（类型 → 日期 → 分类 → 账户 → 标签 → 金额 → 开关）。
     */
    fun summary(
        filter: TxFilter,
        categoryNames: Map<String, String> = emptyMap(),
        accountNames: Map<String, String> = emptyMap(),
        today: LocalDate = LocalDate.now(),
    ): String {
        val parts = mutableListOf<String>()
        filter.type?.let { parts += Templates.typeLabel(it) }
        if (filter.type == null && filter.includeTransfer == false) parts += "不含转账"
        filter.categoryIds.takeIf { it.isNotEmpty() }?.let { ids ->
            val names = ids.mapNotNull { categoryNames[it] }
            parts += when {
                names.isEmpty() -> "${ids.size} 个分类"
                names.size <= 2 -> names.joinToString("、")
                else -> "${names.take(2).joinToString("、")} 等 ${names.size} 个分类"
            }
        }
        filter.accountIds.takeIf { it.isNotEmpty() }?.let { ids ->
            val names = ids.mapNotNull { accountNames[it] }
            parts += when {
                names.isEmpty() -> "${ids.size} 个账户"
                names.size <= 2 -> names.joinToString("、")
                else -> "${names.take(2).joinToString("、")} 等 ${names.size} 个账户"
            }
        }
        if (filter.fromEpochDay != null || filter.toEpochDay != null) {
            parts += dateLabel(filter.fromEpochDay, filter.toEpochDay, today)
        }
        filter.tagIds.takeIf { it.isNotEmpty() }?.let { parts += "${it.size} 个标签" }
        if (filter.amountMinCents != null || filter.amountMaxCents != null) {
            parts += amountLabel(filter.amountMinCents, filter.amountMaxCents)
        }
        if (filter.excludeFromStats == true) parts += "不计收支"
        if (filter.excludeFromBudget == true) parts += "不计预算"
        filter.keyword.trim().takeIf { it.isNotEmpty() }?.let { parts += "「$it」" }
        return parts.joinToString(" · ")
    }

    /** 日期段文案：近 N 天 / 某天起 / 某天前 / 区间 / 同一天。 */
    fun dateLabel(fromEpochDay: Long?, toEpochDay: Long?, today: LocalDate): String {
        val from = fromEpochDay?.let { LocalDate.ofEpochDay(it) }
        val to = toEpochDay?.let { LocalDate.ofEpochDay(it) }
        return when {
            from != null && to != null && from == to -> "${from.monthValue}月${from.dayOfMonth}日"
            from != null && to != null && to == today -> "近 ${to.toEpochDay() - from.toEpochDay() + 1} 天"
            from != null && to != null -> "${from.monthValue}月${from.dayOfMonth}日 - ${to.monthValue}月${to.dayOfMonth}日"
            from != null -> "${from.monthValue}月${from.dayOfMonth}日起"
            to != null -> "到 ${to.monthValue}月${to.dayOfMonth}日"
            else -> ""
        }
    }

    /**
     * 精准导出的文件名（M3-7）：`penned-支出·食品餐饮-20261015.csv`——
     * 摘要里的空格与分隔符换成短横/中点，去掉文件名不允许的字符（`/ : * ? " < > |`）。
     */
    fun exportFileName(
        filter: TxFilter,
        categoryNames: Map<String, String> = emptyMap(),
        accountNames: Map<String, String> = emptyMap(),
        today: LocalDate = LocalDate.now(),
    ): String {
        val summary = summary(filter, categoryNames, accountNames, today)
            .ifEmpty { "全部流水" }
            .replace(" · ", "-")
            .replace(" ", "")
        val safe = summary.filterNot { it in ILLEGAL_FILE_CHARS }
        val date = "%04d%02d%02d".format(today.year, today.monthValue, today.dayOfMonth)
        return "penned-$safe-$date.csv"
    }

    /** 金额段文案：`≥ 10.00` / `≤ 100.00` / `10.00 - 100.00`。 */
    fun amountLabel(minCents: Long?, maxCents: Long?): String = when {
        minCents != null && maxCents != null -> "${Money.format(minCents)} - ${Money.format(maxCents)}"
        minCents != null -> "≥ ${Money.format(minCents)}"
        maxCents != null -> "≤ ${Money.format(maxCents)}"
        else -> ""
    }
}
