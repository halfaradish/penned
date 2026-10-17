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
)

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
     */
    fun parseMerchantCategories(
        content: String?,
        categories: List<dev.penned.app.data.db.entity.CategoryEntity>,
    ): List<MerchantCategorySuggestion> {
        val json = parseObject(content) ?: return emptyList()
        val byPath = HashMap<String, String>()
        categories.forEach { category ->
            byPath[category.name] = category.id
            val parent = categories.firstOrNull { it.id == category.parentId }
            if (parent != null) byPath["${parent.name} / ${category.name}"] = category.id
        }
        return json.keys().asSequence().map { merchant ->
            val name = json.optString(merchant).trim().ifEmpty { null }
            MerchantCategorySuggestion(
                merchant = merchant,
                categoryName = name,
                categoryId = name?.let { byPath[it] },
            )
        }.toList()
    }

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
