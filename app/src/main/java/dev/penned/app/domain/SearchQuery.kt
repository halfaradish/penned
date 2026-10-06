package dev.penned.app.domain

/**
 * 首页搜索的查询解析（纯函数）。
 * 关键词按字面匹配备注/分类名；能解析成金额的数字额外按金额精确匹配。
 */
object SearchQuery {

    /** 是否值得发查询（全空白不发）。 */
    fun isSearchable(input: String): Boolean = input.trim().isNotEmpty()

    /**
     * 转成 SQL LIKE 的 pattern：首尾加 %，并把用户输入里的 `%` `_` `\` 转义成字面量
     * （配合 SQL 的 `ESCAPE '\'`，否则输入 `%` 会匹配全部）。
     */
    fun likePattern(input: String): String {
        val escaped = input.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    /** 数字关键词的金额精确匹配（"16" → 1600 分）；非数字/0 返回 null。 */
    fun amountCentsOrNull(input: String): Long? =
        Money.parseToCents(input.trim())?.takeIf { it > 0 }
}
