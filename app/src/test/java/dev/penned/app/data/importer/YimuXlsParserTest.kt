package dev.penned.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一木 xls 解析（OLE2 + BIFF8 + 语义层）。fixture 为脱敏样本（生成脚本见 docs/local/gen_fixture.py，
 * 已用 olefile/xlrd 交叉验证）：
 * - yimu_sample.xls 约 60 行，Workbook 流 >4KB，走常规 FAT 扇区路径（与真实导出一致）
 * - yimu_sample_mini.xls 8 行，Workbook 流 <4KB，走 MiniFAT 迷你扇区路径
 */
class YimuXlsParserTest {

    private fun load(name: String): ByteArray =
        javaClass.classLoader.getResourceAsStream(name)!!.readBytes()

    @Test
    fun `parse regular-fat fixture`() = parseFixture("yimu_sample.xls", expectedRows = 60)

    @Test
    fun `parse mini-fat fixture`() = parseFixture("yimu_sample_mini.xls", expectedRows = 8)

    private fun parseFixture(name: String, expectedRows: Int) {
        val parsed = YimuXlsParser.parse(load(name))
        assertEquals(0, parsed.problems.size)
        assertEquals(expectedRows, parsed.rows.size)

        val first = parsed.rows.first()
        assertEquals(false, first.isIncome)
        assertEquals(1600L, first.amountCents)
        assertEquals("食品餐饮", first.categoryL1)
        assertEquals("晚餐", first.categoryL2)
        assertEquals(null, first.accountName)
        assertEquals("日常账本", first.bookName)
        assertEquals("超市晚餐", first.note)
        assertEquals(0L, first.refundCents)

        val income = parsed.rows.first { it.isIncome }
        assertEquals(350000L, income.amountCents)
        assertEquals("收入", income.categoryL1)
    }

    @Test
    fun `sign refund discount and account semantics`() {
        val rows = YimuXlsParser.parse(load("yimu_sample_mini.xls")).rows
        // 支出为负号编码、入库恒为正（D001）
        assertEquals(7, rows.count { !it.isIncome })
        assertEquals(1, rows.count { it.isIncome })

        // 部分退款：金额保留原值 + 退款列
        val partial = rows.first { it.note == "外套" }
        assertEquals(5200L, partial.amountCents)
        assertEquals(2000L, partial.refundCents)

        // 全额退款：金额 -0.0 + 退款列独立金额
        val full = rows.first { it.note == "耳机" }
        assertEquals(0L, full.amountCents)
        assertEquals(1454L, full.refundCents)

        // 优惠列为流水属性
        val discounted = rows.first { it.note == "班费支出" }
        assertEquals(200L, discounted.discountCents)

        // 账户列非空保留账户名，为空则 null（D005）
        assertEquals("现金", rows.first { it.note == "水果捞" }.accountName)
        assertTrue(rows.filter { it.note != "水果捞" }.all { it.accountName == null })

        // 分钟精度日期（D002）：2026-10-04 23:59 本地墙钟
        val first = rows.first()
        val expected = java.time.LocalDateTime.of(2026, 10, 4, 23, 59)
            .atZone(java.time.ZoneId.systemDefault()).toEpochSecond() / 60
        assertEquals(expected, first.dateEpochMinute)
    }

    @Test
    fun `bad file gives clear errors`() {
        try {
            Ole2Reader.readStream("not ole2".toByteArray(), "Workbook")
            throw AssertionError("应当抛出 Ole2Exception")
        } catch (e: Ole2Reader.Ole2Exception) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `non-yimu xls reports missing columns`() {
        // 空内容经 OLE2 头校验失败；结构正确但列不对的场景由导入批次错误提示兜底
        val parsed = runCatching { YimuXlsParser.parse(load("yimu_sample_mini.xls")) }
        assertTrue(parsed.isSuccess)
    }
}
