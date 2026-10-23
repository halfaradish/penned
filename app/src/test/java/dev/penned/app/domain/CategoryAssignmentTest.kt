package dev.penned.app.domain

import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.KeywordRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 导入分类判定（D117）：**平台分类列 → 关键词规则 → AI 建议 → 未分类**。
 *
 * 只在本账本现有、未删、未隐藏、收支类型相符的分类里找（**绝不新建**）；
 * 标签规则与坏目标不能挡住后面的分类规则。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class CategoryAssignmentTest {

    private fun category(
        id: String,
        name: String,
        parentId: String? = null,
        type: CategoryType = CategoryType.EXPENSE,
        hidden: Boolean = false,
        deleted: Boolean = false,
    ) = CategoryEntity(
        id = id, bookId = "b1", parentId = parentId, name = name, type = type,
        hidden = hidden, createdAt = 1L, updatedAt = 1L, deleted = deleted, deviceId = "d",
    )

    private fun rule(
        id: String,
        keyword: String,
        targetId: String,
        targetType: RuleTargetType = RuleTargetType.CATEGORY,
        priority: Int = 0,
        createdAt: Long = 1L,
    ) = KeywordRuleEntity(
        id = id, bookId = "b1", keyword = keyword, targetType = targetType, targetId = targetId,
        priority = priority, enabled = true, createdAt = createdAt, updatedAt = createdAt, deviceId = "d",
    )

    private fun row(
        note: String? = null,
        hint: String? = null,
        expense: Boolean = true,
        aiCategoryId: String? = null,
        merchant: String? = null,
    ) = BillRow(
        dateEpochMinute = 1000L, amountCents = 1200, expense = expense, note = note,
        externalId = null, accountClue = null, categoryHint = hint,
        suggestedCategoryId = aiCategoryId, merchant = merchant,
    )

    private val categories = listOf(
        category("c-food", "食品餐饮"),
        category("c-breakfast", "早餐", parentId = "c-food"),
        category("c-lunch", "午餐", parentId = "c-food"),
        category("c-shopping", "购物消费"),
        category("c-salary", "工资", type = CategoryType.INCOME),
        category("c-redpacket", "红包", type = CategoryType.INCOME),
        category("c-hidden", "隐藏分类", hidden = true),
        category("c-deleted", "已删分类", deleted = true),
    )

    @Test
    fun `商户名只取交易对方列 不从备注猜`() {
        assertEquals(
            "肯打鸡",
            CategoryAssignment.merchantOf(row(note = "肯打鸡 · 全家桶", merchant = "肯打鸡")),
        )
        // 通用模式没映射商户列：备注里可能是商品/单号 → 不外发，保持未分类
        assertNull(CategoryAssignment.merchantOf(row(note = "某商品 123456")))
        assertNull(CategoryAssignment.merchantOf(row(note = "   ", merchant = "   ")))
    }

    @Test
    fun `平台分类列：一级 二级 路径都认 但必须同域`() {
        assertEquals(
            "c-food",
            CategoryAssignment.resolve(row(hint = "食品餐饮"), categories, emptyList()).categoryId,
        )
        assertEquals(
            "c-lunch",
            CategoryAssignment.resolve(row(hint = "午餐"), categories, emptyList()).categoryId,
        )
        assertEquals(
            "c-lunch",
            CategoryAssignment.resolve(row(hint = "食品餐饮 / 午餐"), categories, emptyList()).categoryId,
        )
        // 收入行不能命中支出分类
        assertNull(
            CategoryAssignment.resolve(row(hint = "食品餐饮", expense = false), categories, emptyList()).categoryId,
        )
        // 平台词汇不重合 → 留空，绝不新建
        assertNull(
            CategoryAssignment.resolve(row(hint = "餐饮美食"), categories, emptyList()).categoryId,
        )
    }

    @Test
    fun `隐藏与已删的分类不参与自动贴标`() {
        assertNull(
            CategoryAssignment.resolve(row(hint = "隐藏分类"), categories, emptyList()).categoryId,
        )
        assertNull(
            CategoryAssignment.resolve(row(hint = "已删分类"), categories, emptyList()).categoryId,
        )
        assertNull(
            CategoryAssignment.resolve(row(aiCategoryId = "c-hidden"), categories, emptyList()).categoryId,
        )
    }

    @Test
    fun `关键词规则优先于 AI 建议`() {
        val rules = listOf(rule("r1", "肯打鸡", "c-food"))
        val result = CategoryAssignment.resolve(
            row(note = "肯打鸡 · 全家桶", aiCategoryId = "c-shopping"), categories, rules,
        )
        assertEquals("c-food", result.categoryId)
        assertEquals(CategorySource.RULE, result.source)
    }

    @Test
    fun `标签规则或坏目标不能挡住后面的分类规则`() {
        val rules = listOf(
            rule("tag-rule", "肯打鸡", "tag-1", targetType = RuleTargetType.TAG, priority = 0),
            rule("missing-target", "肯打鸡", "missing", priority = 1),
            rule("food-rule", "肯打鸡", "c-food", priority = 2),
        )
        val result = CategoryAssignment.resolve(row(note = "肯打鸡"), categories, rules)
        assertEquals("c-food", result.categoryId)
        assertEquals(CategorySource.RULE, result.source)
    }

    @Test
    fun `规则目标类型不符时让位给 AI`() {
        // 规则指向收入分类，支出行不能用
        val rules = listOf(rule("r1", "某商户", "c-salary"))
        val result = CategoryAssignment.resolve(
            row(note = "某商户", aiCategoryId = "c-shopping"), categories, rules,
        )
        assertEquals("c-shopping", result.categoryId)
        assertEquals(CategorySource.AI, result.source)
    }

    @Test
    fun `AI 建议兜底：没有线索时才用 且必须同域`() {
        val result = CategoryAssignment.resolve(
            row(note = "陌生商户", aiCategoryId = "c-shopping"), categories, emptyList(),
        )
        assertEquals("c-shopping", result.categoryId)
        assertEquals(CategorySource.AI, result.source)

        // 收入行拿到支出分类 → 不采用
        assertNull(
            CategoryAssignment.resolve(
                row(note = "陌生商户", expense = false, aiCategoryId = "c-shopping"), categories, emptyList(),
            ).categoryId,
        )
        // 收入行自己的分类能用
        assertEquals(
            "c-redpacket",
            CategoryAssignment.resolve(
                row(note = "亲戚转账", expense = false, aiCategoryId = "c-redpacket"), categories, emptyList(),
            ).categoryId,
        )
    }

    @Test
    fun `三条线索都没有就是未分类`() {
        val result = CategoryAssignment.resolve(row(note = "陌生商户"), categories, emptyList())
        assertNull(result.categoryId)
        assertEquals(CategorySource.NONE, result.source)
    }
}
