package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * 账单列表出参解析（M4-7 改批量，D111）——表驱动。
 *
 * 覆盖：三种数组形态（`rows` / `items` / 裸数组）/ 模型直接回单个对象 / 围栏与前后闲聊 /
 * **坏行（缺金额）丢掉但计数** / 空数组 = 不是账单 / 非 JSON 判失败 / 超上限截断 / 备注拼法。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class LlmRowsParserTest {

    private fun row(
        amount: String,
        time: String = "",
        merchant: String = "",
        type: String = "EXPENSE",
        note: String = "",
    ): String = buildString {
        append("""{"type":"$type","amount":$amount""")
        if (time.isNotEmpty()) append(""","time":"$time"""")
        if (merchant.isNotEmpty()) append(""","merchant":"$merchant"""")
        if (note.isNotEmpty()) append(""","note":"$note"""")
        append("}")
    }

    @Test
    fun `rows 数组两笔`() {
        val content = """{"rows":[${row("67.07", "10月18日 09:23", "拼多多平台商户")},${row("12.00", "10月17日 13:19", "西大朴诚食堂")}]}"""
        val parsed = LlmRowsParser.parse(content)!!
        assertEquals(2, parsed.rows.size)
        assertEquals(6707L, parsed.rows[0].amountCents)
        assertEquals("拼多多平台商户", parsed.rows[0].merchant)
        assertEquals(1200L, parsed.rows[1].amountCents)
        assertEquals(0, parsed.droppedRows)
    }

    @Test
    fun `items 与裸数组都认`() {
        val items = """{"items":[${row("1.04")}]}"""
        assertEquals(1, LlmRowsParser.parse(items)!!.rows.size)

        val bare = """[${row("1.04")},${row("13.60")}]"""
        assertEquals(2, LlmRowsParser.parse(bare)!!.rows.size)

        val wrapped = """{"list":[${row("9.90")}]}"""
        assertEquals(1, LlmRowsParser.parse(wrapped)!!.rows.size)
    }

    @Test
    fun `围栏与前后闲聊都当噪声`() {
        val content = """
            好的，我从图里读到这些：
            ```json
            {"rows":[${row("88.00", "10月14日 13:18", "微信红包-来自姐姐", type = "INCOME")}]}
            ```
            一共一笔。
        """.trimIndent()
        val parsed = LlmRowsParser.parse(content)!!
        assertEquals(1, parsed.rows.size)
        assertEquals(TxType.INCOME, parsed.rows[0].type)
        assertEquals(8800L, parsed.rows[0].amountCents)
    }

    @Test
    fun `裸数组前后有闲聊`() {
        val content = """识别结果如下：[${row("800.00", type = "INCOME")}] 以上就是全部。"""
        val parsed = LlmRowsParser.parse(content)!!
        assertEquals(1, parsed.rows.size)
        assertEquals(TxType.INCOME, parsed.rows[0].type)
    }

    @Test
    fun `模型直接回一个对象也当一笔`() {
        // 模型没听"回数组"的指示（单笔支付截图很常见）
        val parsed = LlmRowsParser.parse(row("12.50", "2026-10-17 12:30", "蜜雪冰城"))!!
        assertEquals(1, parsed.rows.size)
        assertEquals(1250L, parsed.rows[0].amountCents)
    }

    @Test
    fun `缺金额的行丢掉并计数 别的行照收`() {
        val content = """{"rows":[
            ${row("12.00")},
            {"type":"EXPENSE","merchant":"看不清金额的一行"},
            ${row("bogus")},
            ${row("8.00")}
        ]}"""
        val parsed = LlmRowsParser.parse(content)!!
        assertEquals(2, parsed.rows.size)
        assertEquals("坏行要计数（预览页显示「忽略 N 行」）", 2, parsed.droppedRows)
    }

    @Test
    fun `金额形态：字符串带符号千分位都吃`() {
        val content = """{"rows":[${row("\"¥1,234.56\"")},${row("\"12元\"")},${row("12.5")}]}"""
        val rows = LlmRowsParser.parse(content)!!.rows
        assertEquals(listOf(123_456L, 1200L, 1250L), rows.map { it.amountCents })
    }

    @Test
    fun `type 缺省与认不出都算支出`() {
        val content = """{"rows":[{"amount":1},{"amount":2,"type":"WHATEVER"},{"amount":3,"type":"income"}]}"""
        val rows = LlmRowsParser.parse(content)!!.rows
        assertEquals(listOf(TxType.EXPENSE, TxType.EXPENSE, TxType.INCOME), rows.map { it.type })
    }

    @Test
    fun `时间是宽松口径 无年份也能落成真 epoch 分钟`() {
        val content = """{"rows":[${row("12.00", "10月17日 09:41")}]}"""
        val minute = LlmRowsParser.parse(content)!!.rows[0].occurredAtEpochMinute!!
        // 无年份的月日按"最近一次过去"推，年份依跑测试那天而定——所以断言**月日时分**（墙钟）而不是写死年份
        val local = java.time.Instant.ofEpochSecond(minute * 60).atZone(ZoneId.systemDefault())
        assertEquals(10, local.monthValue)
        assertEquals(17, local.dayOfMonth)
        assertEquals(9, local.hour)
        assertEquals(41, local.minute)
    }

    @Test
    fun `空数组不是失败 是没读出账单`() {
        val parsed = LlmRowsParser.parse("""{"rows":[]}""")!!
        assertEquals(0, parsed.rows.size)
        assertEquals(0, parsed.droppedRows)
    }

    @Test
    fun `非 JSON 与空内容判失败`() {
        assertNull(LlmRowsParser.parse("我不知道"))
        assertNull(LlmRowsParser.parse(""))
        assertNull(LlmRowsParser.parse(null))
        assertNull(LlmRowsParser.parse("""{"result":"ok"}""")) // 既没有数组也没有金额
    }

    @Test
    fun `超过上限截断并标记`() {
        val body = (1..(LlmRowsParser.MAX_ROWS + 5)).joinToString(",") { row("1.00") }
        val parsed = LlmRowsParser.parse("""{"rows":[$body]}""")!!
        assertEquals(LlmRowsParser.MAX_ROWS, parsed.rows.size)
        assertEquals(true, parsed.truncated)
    }

    @Test
    fun `备注拼法：商户加状态 一样时只留一份`() {
        val plain = LlmDraft(TxType.EXPENSE, 100, "西大朴诚食堂", null, null)
        assertEquals("西大朴诚食堂", plain.mergedNote())

        val withStatus = LlmDraft(TxType.EXPENSE, 17900, "allkeep酵母蛋白粉", null, "等待确认收货")
        assertEquals("allkeep酵母蛋白粉 等待确认收货", withStatus.mergedNote())

        val same = LlmDraft(TxType.EXPENSE, 100, "南宁玲慧餐饮店", null, "南宁玲慧餐饮店")
        assertEquals("南宁玲慧餐饮店", same.mergedNote())

        val empty = LlmDraft(TxType.EXPENSE, 100, null, null, null)
        assertNull(empty.mergedNote())
    }
}
