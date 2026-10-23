package dev.penned.app.domain

import org.json.JSONObject

/** 通用模式下 LLM 给的"我们的字段 → 文件列名"建议（已本地校验）。 */
data class ColumnMappingSuggestion(
    val timeColumn: String,
    val amountColumn: String,
    val directionColumn: String?,
    val merchantColumn: String?,
    val itemColumn: String?,
    val statusColumn: String?,
    val externalIdColumn: String?,
    val paymentColumn: String?,
    val categoryColumn: String?,
    val noteColumn: String?,
)

/** 一条"商户 → 分类"建议（分类已按名字在本地匹配到 id；匹配不上 = null）。 */
data class MerchantCategorySuggestion(
    val merchant: String,
    val categoryName: String?,
    val categoryId: String?,
    /** 这个商户是按支出还是收入归类的（同名商户在两个域里可以是不同分类）。 */
    val expense: Boolean = true,
)

/**
 * 待归类的商户（同一个名字可能在支出与收入里各出现一次，必须分开问，D117）。
 *
 * [key] 是本地查找用的稳定键：方向 + 去首尾空白的小写名字。
 */
data class MerchantNeed(
    val merchant: String,
    val expense: Boolean,
) {
    val key: String get() = keyOf(merchant, expense)

    companion object {
        fun keyOf(merchant: String, expense: Boolean): String =
            "${if (expense) "E" else "I"}|${merchant.trim().lowercase()}"
    }
}

/**
 * LLM 建议的**本地校验与落地**（M4-6，纯函数 + 表驱动）。
 *
 * 两条铁律（规格 §二·4 / §三 M4-6）：
 * - **列名必须在表头里真实存在**，否则那条丢弃；`时间`/`金额` 缺一个 → 整份建议作废（退回手动映射）
 * - **分类只按名字在本地匹配**（一级 / 二级都能对），匹配不上留空；LLM 永远不许回分类 ID
 */
object LlmSuggestions {

    /** 我们的字段名 → 提示词里用的键（顺序与通用模式字段表一致）。 */
    val FIELDS = listOf("time", "amount", "direction", "merchant", "item", "status", "externalId", "payment", "category", "note")

    /**
     * 解析列映射建议：出参形如 `{"time":"交易日期","amount":"金额(元)",…}`。
     * 逐条校验列名在 [header] 里存在（精确匹配、忽略首尾空白）；时间/金额缺一即返回 null。
     */
    fun parseColumnMapping(content: String?, header: List<String>): ColumnMappingSuggestion? {
        val json = parseObject(content) ?: return null
        val cells = header.map { it.trim() }
        fun column(key: String): String? {
            val raw = json.optString(key).trim()
            if (raw.isEmpty()) return null
            return if (cells.any { it == raw }) raw else null // 表头里没有这列 → 丢弃
        }
        val time = column("time") ?: return null
        val amount = column("amount") ?: return null
        return ColumnMappingSuggestion(
            timeColumn = time,
            amountColumn = amount,
            directionColumn = column("direction"),
            merchantColumn = column("merchant"),
            itemColumn = column("item"),
            statusColumn = column("status"),
            externalIdColumn = column("externalId"),
            paymentColumn = column("payment"),
            categoryColumn = column("category"),
            noteColumn = column("note"),
        )
    }

    /**
     * 解析商户分类建议：出参形如 `{"肯打鸡":"食品餐饮","某咖啡":"食品餐饮 / 早餐"}`。
     *
     * **只按名字在本地匹配**（一级名或"一级 / 二级"路径），匹配不上 → `categoryId = null`（留空，不猜）。
     * [type] 非空时只在这一域的分类里匹配（支出商户永远拿不到收入分类）。
     */
    fun parseMerchantCategories(
        content: String?,
        categories: List<dev.penned.app.data.db.entity.CategoryEntity>,
        type: CategoryType? = null,
    ): List<MerchantCategorySuggestion> {
        val json = parseObject(content) ?: return emptyList()
        val scoped = if (type == null) categories else categories.filter { it.type == type }
        val byPath = HashMap<String, String>()
        scoped.forEach { category ->
            byPath[normalizeCategoryPath(category.name)] = category.id
            val parent = categories.firstOrNull { it.id == category.parentId && it.type == category.type }
            if (parent != null) {
                byPath[normalizeCategoryPath("${parent.name} / ${category.name}")] = category.id
            }
        }
        return json.keys().asSequence().map { rawKey ->
            // 模型偶尔会把提示词里的方向标签一起抄回来；剥掉再匹配
            val merchant = rawKey.trim().removePrefix("[支出]").removePrefix("[收入]").trim()
            val name = json.optString(rawKey).trim().ifEmpty { null }
            MerchantCategorySuggestion(
                merchant = merchant,
                categoryName = name,
                categoryId = name?.let { byPath[normalizeCategoryPath(it)] },
            )
        }.toList()
    }

    /** 路径归一：`食品餐饮/午餐`、`食品餐饮 ／ 午餐` 与 `食品餐饮 / 午餐` 都当同一条（模型不保证空格）。 */
    private fun normalizeCategoryPath(text: String): String =
        text.replace('／', '/').split('/').joinToString(" / ") { it.trim() }

    /**
     * 现有分类 → 提示词里的「一级 / 二级」路径（只给同一域；父级找不到就只给名字）。
     *
     * 同一棵树的路径去重（用户可能建了同名子类），保持 `sortOrder` 的先后顺序。
     */
    fun categoryPaths(
        categories: List<dev.penned.app.data.db.entity.CategoryEntity>,
        type: CategoryType,
    ): List<String> = categories
        .asSequence()
        .filter { it.type == type }
        .map { category ->
            categories.firstOrNull { it.id == category.parentId && it.type == type }
                ?.let { "${it.name} / ${category.name}" } ?: category.name
        }
        .distinct()
        .toList()

    /**
     * 商户名去重 + 分批（每批 ≤ [batchSize]，默认 100）：只发**去重后的商户名**，
     * 超出分批调用（规格 §三 M4-6 ②）。
     */
    fun batchMerchants(merchants: List<String>, batchSize: Int = 100): List<List<String>> {
        if (batchSize <= 0) return emptyList()
        return merchants.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .chunked(batchSize)
            .toList()
    }

    /** 只认 JSON 对象（模型爱加围栏与闲聊：剥一层再退"第一个 { 到最后一个 }"）。 */
    private fun parseObject(content: String?): JSONObject? {
        val raw = content?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val stripped = if (raw.startsWith("```")) {
            raw.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
                .removeSuffix("```").trim()
        } else {
            raw
        }
        return try {
            JSONObject(stripped)
        } catch (_: Exception) {
            val start = stripped.indexOf('{')
            val end = stripped.lastIndexOf('}')
            if (start < 0 || end <= start) null else runCatching { JSONObject(stripped.substring(start, end + 1)) }.getOrNull()
        }
    }

    /**
     * 外发边界（§二·13）的**掩码**：把一段文本里的数字全部换成 `#`（保留分隔与符号的形状）。
     *
     * 用途：通用模式里"让 AI 再看 3 行样例"时，样例行**必须**先过这里——不然金额/时间/单号
     * 就跟着出去了（"金额、时间、交易单号一律不出设备"是硬口径，假服务器测试会断请求体）。
     * 掩码后仍然看得出列的形状（`####-##-## ##:##:##` 一眼是时间，`###.##` 一眼是金额）。
     */
    fun maskNumbers(text: String): String = text.map { if (it.isDigit()) '#' else it }.joinToString("")

    /** 整行掩码（每个单元格各掩一次）。 */
    fun maskRow(cells: List<String>): List<String> = cells.map { maskNumbers(it) }
}
