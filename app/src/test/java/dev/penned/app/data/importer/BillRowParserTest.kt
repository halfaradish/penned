package dev.penned.app.data.importer

import dev.penned.app.domain.BillBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.ZoneId

/**
 * 两家样本形状的**全链路解析**（M4-4，脱敏 fixture 现场生成）：
 * 编码 → 表头定位 → 来源识别 → 逐行口径 → 分列计数。
 *
 * 计数里特意放了一行**既"不计收支"又"退款成功"**（真实样本里 8 行这样重叠）——
 * 它只能计一次、落在状态桶（优先级：坏行 → 重复 → 状态 → 不计收支 → 导入）。
 */
class BillRowParserTest {

    private val gb18030: Charset = Charset.forName("GB18030")

    private fun minute(y: Int, m: Int, d: Int, h: Int, mi: Int): Long =
        LocalDate.of(y, m, d).atTime(h, mi).atZone(ZoneId.systemDefault()).toEpochSecond() / 60

    /** 微信 CSV 形状（脱敏）：前置说明 + 分隔线 + 表头 + 四行（正常 / 收入 / 不计收支+退款 / 坏行）。 */
    private val wechatCsv: String = listOf(
        "微信支付账单明细",
        "微信昵称：[测试]",
        "起始时间：[2026-09-17 00:00:00] 终止时间：[2026-10-17 20:02:42]",
        "共4笔记录",
        "----------------------微信支付账单明细列表--------------------",
        "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注",
        "2026-10-17 13:19:14,商户消费,某食堂,炒饭,支出,12,零钱,支付成功,WX-1,M-1,/",
        "2026-10-14 13:18:39,微信红包,某人,/,收入,88,/,已存入零钱,WX-2,/,/",
        "2026-10-13 10:00:00,零钱提现,/,/,不计收支,50,零钱,已退款,WX-3,M-3,/",
        "2026-10-12 10:00:00,商户消费,某店,东西,支出,-,零钱,支付成功,WX-4,M-4,/",
    ).joinToString("\r\n")

    /** 支付宝 CSV 形状（脱敏）：表头 12 列 + 末尾无名列，单号带尾随制表符。 */
    private val alipayCsv: String = listOf(
        "导出信息：",
        "起始时间：[2025-01-01 00:00:00]    终止时间：[2025-12-31 23:59:59]",
        "共3笔记录",
        "------------------------支付宝支付科技有限公司  电子客户回单------------------------",
        "交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注,",
        "2025-12-31 18:33:30,餐饮美食,某美食店,/,某美食店,支出,12.00,账户余额,交易成功,AL-1\t,M-AL-1\t,,",
        "2025-12-30 14:17:36,餐饮美食,某烤肉饭,/,收钱码收款,支出,13.80,XX银行储蓄卡(4936)&红包,交易成功,AL-2\t,M-AL-2\t,,",
        "2025-12-29 10:00:00,退款,某店,/,东西,不计收支,1.07,账户余额,退款成功,AL-3\t,M-AL-3\t,,",
    ).joinToString("\r\n")

    @Test
    fun `微信 CSV 全链路：编码 表头 来源 行口径 计数`() {
        val file = BillFileReader.read(wechatCsv.toByteArray(gb18030), "wechat.csv")
        assertEquals("GB18030", file.charsetLabel)
        assertEquals(5, BillFormats.headerRowIndex(file.rows)) // 表头在第 6 行（0 起 = 5）

        val parsed = BillRowParser.parse(file)
        assertEquals(BillSource.WECHAT, parsed.source)
        assertEquals(4, parsed.dataLineCount)

        // 计数：2 导入（支出 + 收入）/ 1 状态跳过（退款，同时是不计收支 → 只算一次）/ 1 坏行
        assertEquals(2, parsed.counts.importable)
        assertEquals(1, parsed.counts.statusSkipped)
        assertEquals(0, parsed.counts.nonCashflowSkipped)
        assertEquals(1, parsed.counts.bad)

        val normal = parsed.rows[0]!!
        assertEquals(minute(2026, 10, 17, 13, 19), normal.dateEpochMinute)
        assertEquals(1200L, normal.amountCents)
        assertTrue(normal.expense)
        assertEquals("某食堂 · 炒饭", normal.note) // 备注列是 `/`（空值占位）→ 不加
        assertEquals("某食堂", normal.merchant) // AI 归类只发交易对方，不发整段备注
        assertEquals("WX-1", normal.externalId)
        assertEquals("零钱", normal.accountClue)

        val income = parsed.rows[1]!!
        assertEquals(8800L, income.amountCents)
        assertEquals(false, income.expense)
        assertEquals("某人", income.note) // 商品与备注都是 `/`（占位）→ 备注只剩交易对方（交易类型不进备注）
        assertEquals("某人", income.merchant)
        assertNull(income.accountClue) // 支付方式是 `/`

        assertEquals(BillBucket.STATUS_SKIP, parsed.buckets[2])
        assertEquals(BillBucket.BAD, parsed.buckets[3]) // 金额 `-`
    }

    @Test
    fun `支付宝 CSV 全链路：尾随制表符 组合支付方式 重叠计数`() {
        val file = BillFileReader.read(alipayCsv.toByteArray(gb18030), "alipay.csv")
        assertEquals("GB18030", file.charsetLabel)

        val parsed = BillRowParser.parse(file)
        assertEquals(BillSource.ALIPAY, parsed.source)
        assertEquals(3, parsed.dataLineCount)

        // 2 导入 + 1 状态跳过（退款成功，且不计收支 → 只落状态桶）
        assertEquals(2, parsed.counts.importable)
        assertEquals(1, parsed.counts.statusSkipped)
        assertEquals(0, parsed.counts.nonCashflowSkipped)

        val first = parsed.rows[0]!!
        assertEquals("AL-1", first.externalId) // 尾随 \t 被 trim 掉（不 trim 去重永远不命中）
        assertEquals(1200L, first.amountCents)
        assertEquals("餐饮美食", first.categoryHint)
        assertEquals("某美食店", first.merchant)

        val second = parsed.rows[1]!!
        assertEquals("XX银行储蓄卡(4936)", second.accountClue) // `&红包` 只取第一段
        assertEquals(1380L, second.amountCents)
        assertEquals("某烤肉饭", second.merchant)
        assertEquals(minute(2025, 12, 30, 14, 17), second.dateEpochMinute)
    }

    @Test
    fun `通用模式认不出会抛可读错误`() {
        val csv = "时间,钱,方向\n2026-10-17 12:00,12,支出\n".toByteArray(Charsets.UTF_8)
        val file = BillFileReader.read(csv, "other.csv")
        val result = runCatching { BillRowParser.parse(file) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("认不出"))
    }

    @Test
    fun `去重与计数接得上：将导入的行拿去查库`() {
        val file = BillFileReader.read(wechatCsv.toByteArray(gb18030), "wechat.csv")
        val parsed = BillRowParser.parse(file)
        val importable = parsed.rows.filterNotNull()
        assertEquals(2, importable.size)

        val (keep, dup) = BillDedupeFun(importable, existing = setOf("WX-1"))
        assertEquals(listOf("WX-2"), keep.map { it.externalId })
        assertEquals(listOf("WX-1"), dup.map { it.externalId })
        assertEquals(2, parsed.withDuplicateCount(dup.size).importable - dup.size + 1)
    }

    /** 小工具：把 `BillDedupe.partition` 的调用写短一点（测试里只关心这一件事）。 */
    private fun BillDedupeFun(
        rows: List<dev.penned.app.domain.BillRow>,
        existing: Set<String>,
    ) = dev.penned.app.domain.BillDedupe.partition(rows, existingIds = existing)
}
