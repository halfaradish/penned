package dev.penned.app.domain

import dev.penned.app.data.db.dao.TemplateWithTags
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.data.db.entity.TxTemplateEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 模板预填口径（M3-5）表驱动：模板/流水 → 记一笔预填的字段映射（含转账两端、标签、金额文本），
 * 以及列表行副标题（转账写两端）。
 */
class TemplatePrefillTest {

    private fun template(
        type: TxType = TxType.EXPENSE,
        amountCents: Long = 1650,
        categoryId: String? = "c1",
        accountId: String? = "a1",
        targetAccountId: String? = null,
        note: String? = "午饭",
        feeCents: Long = 0,
    ) = TxTemplateEntity(
        id = "t1", bookId = "b1", name = "午饭", type = type, amountCents = amountCents,
        categoryId = categoryId, accountId = accountId, targetAccountId = targetAccountId,
        note = note, feeCents = feeCents, sortOrder = 0,
        createdAt = 1L, updatedAt = 1L, deviceId = "d",
    )

    @Test
    fun `模板到预填：字段逐个照搬，标签引用带上`() {
        val prefill = Templates.of(template(), listOf("g1", "g2"))
        assertEquals(TxType.EXPENSE, prefill.type)
        assertEquals(1650L, prefill.amountCents)
        assertEquals("c1", prefill.categoryId)
        assertEquals("a1", prefill.accountId)
        assertEquals(null, prefill.targetAccountId)
        assertEquals("午饭", prefill.note)
        assertEquals(0L, prefill.feeCents)
        assertEquals(listOf("g1", "g2"), prefill.tagIds)
        // 金额文本不带千分位（与编辑回填同口径），可直接落进金额框
        assertEquals("16.50", prefill.amountText)
    }

    @Test
    fun `转账模板：两端都带、没有分类，手续费照搬`() {
        val prefill = Templates.of(
            template(type = TxType.TRANSFER, amountCents = 100_000, categoryId = null, accountId = "a1", targetAccountId = "a2", feeCents = 200),
            emptyList(),
        )
        assertEquals(TxType.TRANSFER, prefill.type)
        assertEquals("a1", prefill.accountId)
        assertEquals("a2", prefill.targetAccountId)
        assertEquals(null, prefill.categoryId)
        assertEquals(200L, prefill.feeCents)
        assertEquals("1000.00", prefill.amountText)
    }

    @Test
    fun `金额为 0 时文本为空（不是 0 元）`() {
        assertEquals("", Templates.of(template(amountCents = 0), emptyList()).amountText)
    }

    @Test
    fun `流水到预填：存为模板的草稿来源取净额`() {
        val tx = TransactionEntity(
            id = "x1", bookId = "b1", type = TxType.EXPENSE, amountCents = 7000,
            accountId = "a1", categoryId = "c1", dateEpochMinute = 1000, note = "打车",
            refundCents = 3000, discountCents = 0, feeCents = 0,
            creatorDeviceId = "d", createdAt = 1L, updatedAt = 1L, deviceId = "d",
        )
        val prefill = Templates.of(tx, listOf("g1"))
        assertEquals(TxType.EXPENSE, prefill.type)
        // 金额取落库净额（退款/优惠不是模板的一部分）
        assertEquals(7000L, prefill.amountCents)
        assertEquals("c1", prefill.categoryId)
        assertEquals("a1", prefill.accountId)
        assertEquals("打车", prefill.note)
        assertEquals(listOf("g1"), prefill.tagIds)
    }

    @Test
    fun `列表副标题：支出写分类与账户，转账写两端，缺的段省掉`() {
        val cases = listOf(
            Triple("支出 · 16.50 · 食品餐饮 · 微信", TxType.EXPENSE, "食品餐饮"),
            Triple("收入 · 16.50 · 工资 · 微信", TxType.INCOME, "工资"),
            Triple("支出 · 16.50 · 未分类 · 微信", TxType.EXPENSE, null),
        )
        cases.forEach { (expected, type, categoryName) ->
            val row = TemplateWithTags(
                id = "t1", name = "午饭", type = type, amountCents = 1650,
                categoryId = null, accountId = null, targetAccountId = null, note = null,
                feeCents = 0, sortOrder = 0,
                categoryName = categoryName, accountName = "微信", targetAccountName = null,
                tagNames = null,
            )
            assertEquals(expected, Templates.summaryText(row))
        }

        val transfer = TemplateWithTags(
            id = "t2", name = "还信用卡", type = TxType.TRANSFER, amountCents = 100_000,
            categoryId = null, accountId = "a1", targetAccountId = "a2", note = null,
            feeCents = 0, sortOrder = 1,
            categoryName = null, accountName = "招行", targetAccountName = "现金", tagNames = "报销中",
        )
        assertEquals("转账 · 1,000.00 · 招行 → 现金", Templates.summaryText(transfer))
    }
}
