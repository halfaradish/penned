package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * LLM 出参校验与归一（M4-2，规格 §三 M4-2 / R4）。挂 Robolectric：解析走平台 `org.json`（D058 口径）。
 *
 * 覆盖：正常 / 金额是数字或字符串（带 ¥、千分位、元）/ **缺金额即失败** / 时间缺失与坏格式 /
 * 类型缺省与认不出 / 脏字段（闲聊、围栏、多余字段）/ 非 JSON / 空 content / 时间编码口径。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class LlmDraftParserTest {

    @Test
    fun `正常出参_数字金额`() {
        val draft = LlmDraftParser.parse(
            """{"type":"EXPENSE","amount":12.34,"merchant":"肯德基","time":"2026-10-17 12:30","note":"午饭"}"""
        )!!
        assertEquals(TxType.EXPENSE, draft.type)
        assertEquals(1234L, draft.amountCents)
        assertEquals("肯德基", draft.merchant)
        assertEquals("午饭", draft.note)
        assertEquals(
            ZonedDateTime.of(LocalDate.of(2026, 10, 17), LocalTime.of(12, 30), ZoneId.systemDefault())
                .toEpochSecond() / 60,
            draft.occurredAtEpochMinute,
        )
    }

    @Test
    fun `收入类型`() {
        val draft = LlmDraftParser.parse("""{"type":"INCOME","amount":8000,"merchant":"公司"}""")!!
        assertEquals(TxType.INCOME, draft.type)
        assertEquals(800_000L, draft.amountCents)
    }

    @Test
    fun `类型缺省或认不出都按支出`() {
        assertEquals(TxType.EXPENSE, LlmDraftParser.parse("""{"amount":12}""")!!.type)
        assertEquals(TxType.EXPENSE, LlmDraftParser.parse("""{"type":"PURCHASE","amount":12}""")!!.type)
        assertEquals(TxType.INCOME, LlmDraftParser.parse("""{"type":"income","amount":12}""")!!.type)
    }

    @Test
    fun `金额是字符串也要收（带符号与单位）`() {
        assertEquals(123_456L, LlmDraftParser.parse("""{"amount":"¥1,234.56"}""")!!.amountCents)
        assertEquals(1200L, LlmDraftParser.parse("""{"amount":"12元"}""")!!.amountCents)
        assertEquals(1050L, LlmDraftParser.parse("""{"amount":"10.5"}""")!!.amountCents)
    }

    @Test
    fun `缺金额整条判失败`() {
        assertNull(LlmDraftParser.parse("""{"type":"EXPENSE","merchant":"肯德基"}"""))
        assertNull(LlmDraftParser.parse("""{"amount":null}"""))
        assertNull(LlmDraftParser.parse("""{"amount":""}"""))
        assertNull(LlmDraftParser.parse("""{"amount":"说不清"}"""))
    }

    @Test
    fun `时间缺失或坏格式都不猜`() {
        assertNull(LlmDraftParser.parse("""{"amount":12}""")!!.occurredAtEpochMinute)
        assertNull(LlmDraftParser.parse("""{"amount":12,"time":""}""")!!.occurredAtEpochMinute)
        assertNull(LlmDraftParser.parse("""{"amount":12,"time":"昨天中午"}""")!!.occurredAtEpochMinute)
        assertNull(LlmDraftParser.parse("""{"amount":12,"time":"2026-02-30 10:00"}""")!!.occurredAtEpochMinute)
        assertNull(LlmDraftParser.parse("""{"amount":12,"time":"2026-10-17 25:00"}""")!!.occurredAtEpochMinute)
    }

    @Test
    fun `时间容两种分隔与秒与T`() {
        val expected = ZonedDateTime.of(LocalDate.of(2026, 10, 17), LocalTime.of(9, 5), ZoneId.systemDefault())
            .toEpochSecond() / 60
        assertEquals(expected, LlmDraftParser.parse("""{"amount":1,"time":"2026/10/17 09:05"}""")!!.occurredAtEpochMinute)
        assertEquals(expected, LlmDraftParser.parse("""{"amount":1,"time":"2026-10-17 09:05:31"}""")!!.occurredAtEpochMinute)
        assertEquals(expected, LlmDraftParser.parse("""{"amount":1,"time":"2026-10-17T09:05"}""")!!.occurredAtEpochMinute)
    }

    @Test
    fun `时间是本地墙钟_必须过时区换算（不是 epochDay 编码）`() {
        val draft = LlmDraftParser.parse("""{"amount":1,"time":"2026-10-17 00:30"}""")!!
        val wall = LocalDate.of(2026, 10, 17).toEpochDay() * 1440 + 30
        if (ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds != 0) {
            // UTC+8 这类时区下：墙钟编码会偏整整 8 小时（AddDate 头注释里写着这条坑）
            assertEquals(true, draft.occurredAtEpochMinute != wall)
        }
        assertEquals(
            ZonedDateTime.of(LocalDate.of(2026, 10, 17), LocalTime.of(0, 30), ZoneId.systemDefault())
                .toEpochSecond() / 60,
            draft.occurredAtEpochMinute,
        )
    }

    @Test
    fun `脏输入一律当噪声处理`() {
        // ```json 围栏
        assertEquals(1200L, LlmDraftParser.parse("```json\n{\"amount\":12}\n```")!!.amountCents)
        // 前后各有一句闲聊
        assertEquals(1200L, LlmDraftParser.parse("好的，这是结果：{\"amount\":12} 需要修改告诉我")!!.amountCents)
        // 多余字段忽略
        val draft = LlmDraftParser.parse("""{"amount":12,"category":"餐饮","accountId":"a1","confidence":0.9}""")!!
        assertEquals(1200L, draft.amountCents)
    }

    @Test
    fun `不是 JSON 或空内容 = 失败`() {
        assertNull(LlmDraftParser.parse(null))
        assertNull(LlmDraftParser.parse(""))
        assertNull(LlmDraftParser.parse("   "))
        assertNull(LlmDraftParser.parse("这不是账单"))
        assertNull(LlmDraftParser.parse("[]"))
        assertNull(LlmDraftParser.parse("{}"))
    }

    @Test
    fun `商户与备注空串当没给`() {
        val draft = LlmDraftParser.parse("""{"amount":12,"merchant":"  ","note":""}""")!!
        assertNull(draft.merchant)
        assertNull(draft.note)
        assertEquals("肯德基", LlmDraftParser.parse("""{"amount":12,"merchant":" 肯德基 "}""")!!.merchant)
    }
}
