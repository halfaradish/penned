package dev.penned.app.domain

import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.KeywordRuleEntity
import dev.penned.app.data.db.entity.TagEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 关键词规则匹配（M4-0）：优先级 / 多命中取先 / 禁用跳过 / 软删跳过 / 大小写 / 空关键词 /
 * 标签目标 / 二级分类目标 / 空文本 / 不猜（无命中就是 null）。
 */
class KeywordRulesTest {

    private var seq = 0L

    private fun rule(
        keyword: String,
        targetId: String = "cat-1",
        priority: Int = 0,
        enabled: Boolean = true,
        deleted: Boolean = false,
        targetType: RuleTargetType = RuleTargetType.CATEGORY,
        createdAt: Long = ++seq,
    ) = KeywordRuleEntity(
        id = "r-$keyword-$createdAt",
        bookId = "b1",
        keyword = keyword,
        targetType = targetType,
        targetId = targetId,
        priority = priority,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = createdAt,
        deleted = deleted,
        deviceId = "d",
    )

    @Test
    fun `命中返回规则的目标`() {
        val hit = KeywordRules.match("肯打鸡花了15", listOf(rule("肯打鸡", targetId = "cat-food")))
        assertEquals("cat-food", hit?.targetId)
        assertEquals(RuleTargetType.CATEGORY, hit?.targetType)
        assertEquals("肯打鸡", hit?.keyword)
    }

    @Test
    fun `没命中就是 null 不猜`() {
        assertNull(KeywordRules.match("午饭 12", listOf(rule("肯打鸡"))))
    }

    @Test
    fun `空文本不匹配`() {
        assertNull(KeywordRules.match("", listOf(rule("肯打鸡"))))
        assertNull(KeywordRules.match("   ", listOf(rule("肯打鸡"))))
        assertNull(KeywordRules.match(null, listOf(rule("肯打鸡"))))
    }

    @Test
    fun `多条命中取优先级最小的`() {
        val rules = listOf(
            rule("肯打鸡", targetId = "low", priority = 9),
            rule("打", targetId = "high", priority = 1),
        )
        assertEquals("high", KeywordRules.match("肯打鸡花了15", rules)?.targetId)
    }

    @Test
    fun `同优先级按创建时间取先建的`() {
        val first = rule("鸡", targetId = "first", priority = 0, createdAt = 10)
        val second = rule("肯", targetId = "second", priority = 0, createdAt = 20)
        assertEquals("first", KeywordRules.match("肯打鸡", listOf(second, first))?.targetId)
    }

    @Test
    fun `禁用的规则不参与匹配`() {
        val rules = listOf(rule("肯打鸡", targetId = "off", enabled = false), rule("鸡", targetId = "on", priority = 5))
        assertEquals("on", KeywordRules.match("肯打鸡", rules)?.targetId)
    }

    @Test
    fun `软删的规则不参与匹配`() {
        val rules = listOf(rule("肯打鸡", targetId = "gone", deleted = true))
        assertNull(KeywordRules.match("肯打鸡", rules))
    }

    @Test
    fun `空关键词与纯空白关键词跳过`() {
        val rules = listOf(rule("", targetId = "empty"), rule("   ", targetId = "blank"), rule("鸡", targetId = "ok"))
        assertEquals("ok", KeywordRules.match("肯打鸡", rules)?.targetId)
        assertNull(KeywordRules.match("随便什么文本", listOf(rule("", targetId = "empty"))))
    }

    @Test
    fun `大小写不敏感（英文商户名）`() {
        val rules = listOf(rule("luckin coffee", targetId = "coffee"))
        assertEquals("coffee", KeywordRules.match("Luckin Coffee 订单付款", rules)?.targetId)
        assertEquals("coffee", KeywordRules.match("LUCKIN COFFEE", rules)?.targetId)
    }

    @Test
    fun `关键词自身带空白按去空白后匹配`() {
        val rules = listOf(rule("  肯打鸡  ", targetId = "food"))
        assertEquals("food", KeywordRules.match("今天在肯打鸡吃了", rules)?.targetId)
    }

    @Test
    fun `标签目标`() {
        val rules = listOf(rule("租房", targetType = RuleTargetType.TAG, targetId = "tag-rent"))
        val hit = KeywordRules.match("10月租房 2500", rules)
        assertEquals(RuleTargetType.TAG, hit?.targetType)
        assertEquals("tag-rent", hit?.targetId)
    }

    @Test
    fun `二级分类目标与规则来源可追溯`() {
        val rules = listOf(rule("午饭", targetId = "cat-lunch-l2"))
        val hit = KeywordRules.match("食堂午饭 15", rules)
        assertEquals("cat-lunch-l2", hit?.targetId)
        // 命中结果带着规则 id，UI 上才能给"来自关键词规则"的小标记（M4-3）
        assertEquals(rules[0].id, hit?.ruleId)
    }

    @Test
    fun `规则列表为空返回 null`() {
        assertNull(KeywordRules.match("肯打鸡花了15", emptyList()))
    }

    // ---- 目标名解析（M4-1 规则页那行「→ 分类：食品餐饮 / 午餐」）----

    private fun category(id: String, name: String, parentId: String? = null) = CategoryEntity(
        id = id, bookId = "b1", parentId = parentId, name = name,
        type = CategoryType.EXPENSE, sortOrder = 0, createdAt = 1L, updatedAt = 1L, deviceId = "d",
    )

    private fun tag(id: String, name: String) = TagEntity(
        id = id, bookId = "b1", parentId = null, name = name, sortOrder = 0,
        createdAt = 1L, updatedAt = 1L, deviceId = "d",
    )

    @Test
    fun `目标名解析 - 一级分类给名字 二级给父级路径`() {
        val categories = listOf(category("c1", "食品餐饮"), category("c2", "午餐", parentId = "c1"))
        assertEquals(
            "食品餐饮",
            KeywordRules.targetName(RuleTargetType.CATEGORY, "c1", categories, emptyList()),
        )
        assertEquals(
            "食品餐饮 / 午餐",
            KeywordRules.targetName(RuleTargetType.CATEGORY, "c2", categories, emptyList()),
        )
    }

    @Test
    fun `目标名解析 - 标签给名字`() {
        assertEquals(
            "出差",
            KeywordRules.targetName(RuleTargetType.TAG, "tag-1", emptyList(), listOf(tag("tag-1", "出差"))),
        )
    }

    @Test
    fun `目标名解析 - 目标被删返回 null 而不是空串`() {
        assertNull(KeywordRules.targetName(RuleTargetType.CATEGORY, "gone", emptyList(), emptyList()))
        assertNull(KeywordRules.targetName(RuleTargetType.TAG, "gone", emptyList(), emptyList()))
    }
}
