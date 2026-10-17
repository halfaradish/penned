package dev.penned.app.domain

import dev.penned.app.data.db.entity.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LLM 建议的本地校验与分批（M4-6）：列名必须在表头里真实存在、时间/金额缺一即作废、
 * 分类只按名匹配、商户去重+分批（≤100）、以及外发边界的**数字掩码**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class LlmSuggestionsTest {

    private val header = listOf("交易日期", "交易对方", "金额(元)", "收/支", "订单号\t", "备注")

    private fun category(id: String, name: String, parentId: String? = null) = CategoryEntity(
        id = id, bookId = "b1", parentId = parentId, name = name,
        type = CategoryType.EXPENSE, createdAt = 1L, updatedAt = 1L, deviceId = "d",
    )

    @Test
    fun `列映射：列名必须在表头里真实存在 编的列丢弃`() {
        val suggestion = LlmSuggestions.parseColumnMapping(
            """{"time":"交易日期","amount":"金额(元)","direction":"收/支","merchant":"不存在的列","externalId":"订单号"}""",
            header,
        )!!
        assertEquals("交易日期", suggestion.timeColumn)
        assertEquals("金额(元)", suggestion.amountColumn)
        assertEquals("收/支", suggestion.directionColumn)
        assertNull(suggestion.merchantColumn) // 表头里没有 → 丢弃
        assertEquals("订单号", suggestion.externalIdColumn) // 表头带尾随 \t，trim 后能对上
    }

    @Test
    fun `列映射：缺时间或金额整份作废`() {
        assertNull(LlmSuggestions.parseColumnMapping("""{"time":"交易日期"}""", header))
        assertNull(LlmSuggestions.parseColumnMapping("""{"amount":"金额(元)"}""", header))
        assertNull(LlmSuggestions.parseColumnMapping("""{"time":"编的","amount":"金额(元)"}""", header))
        assertNull(LlmSuggestions.parseColumnMapping("", header))
        assertNull(LlmSuggestions.parseColumnMapping("这不是 JSON", header))
    }

    @Test
    fun `列映射：围栏与闲聊都能剥掉`() {
        val suggestion = LlmSuggestions.parseColumnMapping(
            "好的，这是结果：```json\n{\"time\":\"交易日期\",\"amount\":\"金额(元)\"}\n```",
            header,
        )!!
        assertEquals("交易日期", suggestion.timeColumn)
    }

    @Test
    fun `商户分类：只按名字在本地匹配 一级与二级路径都认`() {
        val categories = listOf(
            category("c1", "食品餐饮"),
            category("c2", "早餐", parentId = "c1"),
            category("c3", "出行交通"),
        )
        val suggestions = LlmSuggestions.parseMerchantCategories(
            """{"肯打鸡":"食品餐饮","某咖啡":"食品餐饮 / 早餐","某打车":"出行交通","编的商户":"不存在的分类"}""",
            categories,
        ).associateBy { it.merchant }

        assertEquals("c1", suggestions.getValue("肯打鸡").categoryId)
        assertEquals("c2", suggestions.getValue("某咖啡").categoryId)
        assertEquals("c3", suggestions.getValue("某打车").categoryId)
        // 匹配不上留空（不猜），分类名原样留着给 UI 显示
        assertNull(suggestions.getValue("编的商户").categoryId)
        assertEquals("不存在的分类", suggestions.getValue("编的商户").categoryName)
    }

    @Test
    fun `商户分类：坏输入给空列表 不炸`() {
        assertTrue(LlmSuggestions.parseMerchantCategories(null, emptyList()).isEmpty())
        assertTrue(LlmSuggestions.parseMerchantCategories("[]", emptyList()).isEmpty())
        assertTrue(LlmSuggestions.parseMerchantCategories("闲聊", emptyList()).isEmpty())
    }

    @Test
    fun `商户去重与分批：一批不超过 100 个`() {
        val merchants = (1..250).map { "商户$it" } + listOf("商户1", "商户2", "  ", "")
        val batches = LlmSuggestions.batchMerchants(merchants)
        assertEquals(3, batches.size)
        assertEquals(100, batches[0].size)
        assertEquals(100, batches[1].size)
        assertEquals(50, batches[2].size)
        assertEquals(250, batches.flatten().toSet().size) // 去重 + 去空白
    }

    @Test
    fun `外发边界：数字一律掩码 形状留着`() {
        assertEquals("####-##-## ##:##:##", LlmSuggestions.maskNumbers("2026-10-17 13:19:14"))
        assertEquals("¥###.##", LlmSuggestions.maskNumbers("¥123.45"))
        assertEquals("#### 号", LlmSuggestions.maskNumbers("1234 号"))
        assertEquals(
            listOf("####-##-## ##:##:##", "某商户", "####.##", "支出"),
            LlmSuggestions.maskRow(listOf("2026-10-17 13:19:14", "某商户", "1234.56", "支出")),
        )
        // 掩码后原始数字不再出现（这是"金额/时间/单号不出设备"那层保险）
        val sample = "4200003225202610171528367827"
        assertTrue(!LlmSuggestions.maskNumbers(sample).contains("4200"))
    }
}
