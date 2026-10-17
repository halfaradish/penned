package dev.penned.app.domain

import dev.penned.app.data.db.entity.KeywordRuleEntity

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
}
