package dev.penned.app.domain

import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.KeywordRuleEntity

/** 一行账的分类是从哪条线索来的（预览页的"分类来源"按它计数）。 */
enum class CategorySource { COLUMN, RULE, AI, NONE }

/** 一行的分类判定结果：分类 id（null = 未分类）+ 线索来源。 */
data class CategoryResolution(
    val categoryId: String?,
    val source: CategorySource,
)

/**
 * 导入时给一行贴分类（D117，纯函数 + 表驱动）。
 *
 * 判定顺序（前一条命中就不再往后走）：
 * 1. **平台分类列**（`categoryHint`，微信/支付宝导出的「交易分类」）——只按名字匹配；
 * 2. **用户的关键词规则**（M4-1）——规则是用户亲手定的，比模型更可信；
 * 3. **AI 建议**（`suggestedCategoryId`，M4-6/D117）——兜底，只认本地校验过的现有分类。
 *
 * 三条都只在本账本**现有、未删、未隐藏、收支类型相符**的分类里找（**绝不新建**，B1）；
 * 找不到就返回 null = 未分类。隐藏分类不参与自动贴标：用户把它藏起来就是"不再用"。
 */
object CategoryAssignment {

    /**
     * 一行的"商户名"：**只取交易对方原始列**（[BillRow.merchant]）。
     *
     * 不从 `note` 里猜第一段：文件导入的备注是「交易对方 · 商品说明 · 备注」，截图导入的备注后半段
     * 是"交易关闭"这类状态；整段发出去就破了"金额/时间/单号不出设备"的口径（D117）。
     * 没有商户列（通用模式没映射）就返回 null——这一行不参与 AI 归类，保持未分类。
     */
    fun merchantOf(row: BillRow): String? =
        row.merchant?.trim()?.take(64)?.ifEmpty { null }

    fun resolve(
        row: BillRow,
        categories: List<CategoryEntity>,
        rules: List<KeywordRuleEntity>,
    ): CategoryResolution {
        val type = if (row.expense) CategoryType.EXPENSE else CategoryType.INCOME
        val usable = categories.filter { !it.deleted && !it.hidden && it.type == type }
        if (usable.isEmpty()) return CategoryResolution(null, CategorySource.NONE)

        // ① 平台分类列：按名 / 按「一级 / 二级」路径
        row.categoryHint?.trim()?.takeIf { it.isNotEmpty() }?.let { hint ->
            matchByHint(hint, usable)?.let {
                return CategoryResolution(it.id, CategorySource.COLUMN)
            }
        }

        // ② 关键词规则：跳过标签规则与"目标不可用"的命中，继续找后面那条分类规则
        val usableIds = usable.mapTo(HashSet()) { it.id }
        KeywordRules.match(row.note, rules) { hit ->
            hit.targetType == RuleTargetType.CATEGORY && hit.targetId in usableIds
        }?.let { return CategoryResolution(it.targetId, CategorySource.RULE) }

        // ③ AI 建议：本地校验过的 id；仍要防一手"换账本后 id 不属于这里"
        row.suggestedCategoryId?.takeIf { it in usableIds }?.let {
            return CategoryResolution(it, CategorySource.AI)
        }

        return CategoryResolution(null, CategorySource.NONE)
    }

    private fun matchByHint(hint: String, usable: List<CategoryEntity>): CategoryEntity? {
        // 一级 / 二级路径（两边都容忍空格）
        val parts = hint.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val parent = usable.firstOrNull { it.parentId == null && it.name == parts[0] }
            val child = usable.firstOrNull { it.parentId == parent?.id && it.name == parts[1] }
            if (child != null) return child
        }
        // 裸名字：一级与二级都认（与既有 M4-5 口径一致）
        return usable.firstOrNull { it.name == hint }
    }
}
