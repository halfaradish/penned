package dev.penned.app.domain

import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.KeywordRuleEntity
import dev.penned.app.data.db.entity.TagEntity

/** 一条关键词规则的命中结果（M4-0）：命中的规则 + 它要填的目标。 */
data class RuleHit(
    val ruleId: String,
    val keyword: String,
    val targetType: RuleTargetType,
    val targetId: String,
)

/**
 * 关键词规则匹配（M4-0 落地，M4-1/M4-3/M4-4/M4-7/M4-8 四处接入）。
 *
 * 口径（规格 M4 §三 M4-0）：
 * - 按 `priority` 升序取**第一条命中**，同优先级按 `createdAt`（列表第一行就是最先命中的那条）
 * - `enabled = false` 整条跳过；软删（`deleted`）的跳过
 * - **空关键词跳过**（"包含空串"会让每条规则都命中）
 * - 去首尾空白、**大小写不敏感**（中文没有大小写，英文商户名用得上）
 *
 * 纯函数：不碰数据库、不知道账本——规则列表由调用方按账本取好（`KeywordRuleDao.allInBook`）。
 * 命中只代表**建议值**，UI 上永远可改（规格 §二·6）。
 */
object KeywordRules {

    fun match(text: String?, rules: List<KeywordRuleEntity>): RuleHit? {
        if (text.isNullOrBlank()) return null
        val haystack = text.lowercase()
        val ordered = rules
            .filter { it.enabled && !it.deleted && it.keyword.isNotBlank() }
            .sortedWith(compareBy({ it.priority }, { it.createdAt }))
        for (rule in ordered) {
            val needle = rule.keyword.trim().lowercase()
            if (needle.isNotEmpty() && haystack.contains(needle)) {
                return RuleHit(rule.id, rule.keyword, rule.targetType, rule.targetId)
            }
        }
        return null
    }

    /**
     * 规则目标的显示名（规则页那行「→ 分类：食品餐饮 / 午餐」用）。
     *
     * 分类给**含父级的路径**——二级分类只写"午餐"看不出是哪一支；标签直接给名字。
     * 目标被软删/找不到时返回 null：页面显示「目标已删除」并让用户自己重选，
     * **不静默清空规则**（清空的规则看着像没建过，用户会以为规则丢了）。
     */
    fun targetName(
        targetType: RuleTargetType,
        targetId: String,
        categories: List<CategoryEntity>,
        tags: List<TagEntity>,
    ): String? = when (targetType) {
        RuleTargetType.CATEGORY -> categories.firstOrNull { it.id == targetId }?.let { category ->
            categories.firstOrNull { it.id == category.parentId }
                ?.let { parent -> "${parent.name} / ${category.name}" }
                ?: category.name
        }
        RuleTargetType.TAG -> tags.firstOrNull { it.id == targetId }?.name
    }
}
