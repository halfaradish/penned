package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 账单行的字段口径（M4-4）：金额四种文本写法 + 两种数值、时间两种分隔 + 序列日期、方向、状态、
 * `/` 占位、`\t` 尾随、备注拼法、单号缺失、`&` 组合取段。
 *
 * 期望值全部按"本地墙钟 → 真 epoch 分钟"算（`dateEpochMinute` 的口径，别写成墙钟编码）。
 */
class BillRowsTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun minute(y: Int, m: Int, d: Int, h: Int, mi: Int): Long =
        LocalDate.of(y, m, d).atTime(h, mi).atZone(zone).toEpochSecond() / 60

    @Test
    fun `单元格归一：斜杠是空值占位 空白与制表符都剔掉`() {
        assertEquals("", BillRows.cell("/"))
        assertEquals("", BillRows.cell("   "))
        assertEquals("", BillRows.cell(null))
        assertEquals("零钱", BillRows.cell("  零钱  "))
        // 支付宝单号每行带尾随制表符：不 trim 则去重永远不命中
        assertEquals("2025123122001464561428586485", BillRows.cell("2025123122001464561428586485\t"))
    }

    @Test
    fun `金额四种文本写法与两种数值`() {
        assertEquals(123_456L, BillRows.amountCents("¥1,234.56"))
        assertEquals(123_456L, BillRows.amountCents("1,234.56"))
        assertEquals(123_456L, BillRows.amountCents("1234.56"))
        assertEquals(1200L, BillRows.amountCents("12元"))
        assertEquals(1234L, BillRows.amountCents("12.34"))
        assertEquals(1200L, BillRows.amountCents("12"))
        // 数值单元格（xlsx）：¥ 只在显示格式里
        assertEquals(1210L, BillRows.amountCentsFromNumber(12.1))
        assertEquals(1701L, BillRows.amountCentsFromNumber(17.01))
        // 坏值
        assertNull(BillRows.amountCents(""))
        assertNull(BillRows.amountCents("/"))
        assertNull(BillRows.amountCents("-"))
        assertNull(BillRows.amountCents("待定"))
    }

    @Test
    fun `时间文本两种分隔与秒可缺`() {
        assertEquals(minute(2026, 10, 17, 13, 19), BillRows.minuteFromText("2026-10-17 13:19:14"))
        assertEquals(minute(2026, 10, 17, 13, 19), BillRows.minuteFromText("2026-10-17 13:19"))
        assertEquals(minute(2026, 10, 17, 13, 19), BillRows.minuteFromText("2026/10/17 13:19"))
        assertEquals(minute(2026, 10, 17, 9, 5), BillRows.minuteFromText("2026-10-17 09:05:31"))
        assertNull(BillRows.minuteFromText(""))
        assertNull(BillRows.minuteFromText("昨天"))
        assertNull(BillRows.minuteFromText("2026-02-30 10:00"))
    }

    @Test
    fun `时间是 Excel 序列日期数值（基准 1899-12-30）`() {
        // 实测样本值：46312.55502314815 → 2026-10-17 13:19:14（秒被四舍五入，分钟对得上）
        assertEquals(minute(2026, 10, 17, 13, 19), BillRows.minuteFromSerial(46312.55502314815))
        // 整天的序列：46312 → 2026-10-17 00:00
        assertEquals(minute(2026, 10, 17, 0, 0), BillRows.minuteFromSerial(46312.0))
        // 不合理范围不当日期
        assertNull(BillRows.minuteFromSerial(0.0))
        assertNull(BillRows.minuteFromSerial(999_999.0))
    }

    @Test
    fun `方向三值：支出 收入 跳过`() {
        assertEquals(BillDirection.EXPENSE, BillRows.direction("支出"))
        assertEquals(BillDirection.INCOME, BillRows.direction("收入"))
        assertEquals(BillDirection.SKIP, BillRows.direction("不计收支"))
        assertEquals(BillDirection.SKIP, BillRows.direction("其他"))
        assertEquals(BillDirection.SKIP, BillRows.direction("/"))
        assertEquals(BillDirection.SKIP, BillRows.direction(""))
        // 带后缀的变体
        assertEquals(BillDirection.EXPENSE, BillRows.direction("支出（已退款）"))
        assertEquals(BillDirection.SKIP, BillRows.direction("不计收支（转账）"))
        assertNull(BillRows.direction("冻结"))
    }

    @Test
    fun `状态：成功态照导入 退款与关闭跳过`() {
        listOf("支付成功", "交易成功", "已收款", "收款成功", "充值成功", "对方已收钱", "已存入零钱").forEach {
            assertEquals(BillStatus.OK, BillRows.status(it))
        }
        listOf("已退款", "退款成功", "部分退款", "交易关闭", "已关闭", "已全额退款").forEach {
            assertEquals(BillStatus.SKIP, BillRows.status(it))
        }
        // 两家的实测成功态
        assertEquals(BillStatus.OK, BillRows.status(""))
        assertEquals(BillStatus.SKIP, BillRows.status("退款处理中"))
    }

    @Test
    fun `备注拼法：对方 + 商品 + 原备注，同值去重`() {
        assertEquals("西大朴诚食堂 · 裕膳炒饭", BillRows.note("西大朴诚食堂", "裕膳炒饭", null))
        assertEquals("某商户", BillRows.note("某商户", "某商户", null))
        assertEquals("某商户 · 备注", BillRows.note("某商户", null, "备注"))
        assertEquals("某商户 · 商品 · 备注", BillRows.note("某商户", "商品", "备注"))
        assertNull(BillRows.note("/", "/", "/"))
        assertNull(BillRows.note(null, null, null))
    }

    @Test
    fun `账户线索：取地址组合的第一段 掩码照留`() {
        assertEquals("XX银行储蓄卡(1234)", BillRows.accountClue("XX银行储蓄卡(1234)&红包"))
        assertEquals("账户余额", BillRows.accountClue("账户余额&碰一下立减"))
        assertEquals("零钱", BillRows.accountClue("零钱"))
        assertEquals("XX银行储蓄卡(1234)", BillRows.accountClue("XX银行储蓄卡(1234)&账户余额"))
        assertNull(BillRows.accountClue("/"))
        assertNull(BillRows.accountClue(""))
    }

    @Test
    fun `单号：主单号优先 缺失退副单号`() {
        assertEquals("4200", BillRows.externalId("4200", "2610"))
        assertEquals("2610", BillRows.externalId("/", "2610"))
        assertEquals("2610", BillRows.externalId("", "2610"))
        assertNull(BillRows.externalId("/", "/"))
        assertNull(BillRows.externalId(null, null))
    }

    @Test
    fun `疑似重复键：时间 金额 方向 备注`() {
        val row = BillRow(
            dateEpochMinute = minute(2026, 10, 17, 12, 0), amountCents = 1200, expense = true,
            note = "食堂", externalId = null, accountClue = null, categoryHint = null,
        )
        assertEquals("${minute(2026, 10, 17, 12, 0)}|1200|E|食堂", row.fallbackKey)
    }

    @Test
    fun `去重：单号优先 无单号退兜底键 文件内自身也去重`() {
        fun row(id: String?, time: Long = 100L, amount: Long = 1000L, note: String? = "x") = BillRow(
            dateEpochMinute = time, amountCents = amount, expense = true, note = note,
            externalId = id, accountClue = null, categoryHint = null,
        )

        // 库内同单号
        val (keep, dup) = BillDedupe.partition(
            listOf(row("A1"), row("A2")),
            existingIds = setOf("A1"),
        )
        assertEquals(listOf("A2"), keep.map { it.externalId })
        assertEquals(listOf("A1"), dup.map { it.externalId })

        // 文件内自身重复（同单号出现两次 → 第二次算重复）
        val (keep2, dup2) = BillDedupe.partition(listOf(row("B1"), row("B1")))
        assertEquals(1, keep2.size)
        assertEquals(1, dup2.size)

        // 无单号 → 时间+金额+方向+备注 兜底；库内命中算重复
        val (keep3, dup3) = BillDedupe.partition(
            listOf(row(null, time = 500L, amount = 700L, note = "午饭")),
            existingFallbackKeys = setOf("500|700|E|午饭"),
        )
        assertEquals(0, keep3.size)
        assertEquals(1, dup3.size)
    }

    @Test
    fun `逐行标重复：保留行序 只标后面的那一条`() {
        fun row(time: Long = 100L, amount: Long = 1000L, note: String? = "x") = BillRow(
            dateEpochMinute = time, amountCents = amount, expense = true, note = note,
            externalId = null, accountClue = null, categoryHint = null,
        )

        // 同一键出现两次：第 1 条该导入、第 2 条才算重复（截图预览页按屏幕顺序标"重复"要靠这条）
        val flags = BillDedupe.duplicateFlags(listOf(row(), row(), row(amount = 2000L)))
        assertEquals(listOf(false, true, false), flags)

        // 库内已有同键 → 第一条就标重复
        val againstDb = BillDedupe.duplicateFlags(
            listOf(row(time = 500L, amount = 700L, note = "午饭")),
            existingFallbackKeys = setOf("500|700|E|午饭"),
        )
        assertEquals(listOf(true), againstDb)

        // 与 partition 同口径：标出来的条数 = partition 的重复条数
        val rows = listOf(row(), row(), row(amount = 3000L))
        assertEquals(BillDedupe.partition(rows).second.size, BillDedupe.duplicateFlags(rows).count { it })
    }

    @Test
    fun `每行只归一类：优先级 坏行 重复 状态 不计收支 导入`() {
        val ok = BillRow(1L, 100L, true, null, null, null, null)
        assertEquals(BillBucket.BAD, BillDedupe.bucket(null, false, false, false))
        assertEquals(BillBucket.DUPLICATE, BillDedupe.bucket(ok, true, false, false))
        // 既"不计收支"又"退款成功"（实测重叠）：先判状态 → 落在退款桶，不计收支桶不重复计
        assertEquals(BillBucket.STATUS_SKIP, BillDedupe.bucket(ok, false, true, true))
        assertEquals(BillBucket.NON_CASHFLOW_SKIP, BillDedupe.bucket(ok, false, false, true))
        assertEquals(BillBucket.IMPORT, BillDedupe.bucket(ok, false, false, false))
    }

    @Test
    fun `分列计数：跳过与重复各算各的`() {
        val counts = BillDedupe.counts(
            listOf(
                BillBucket.IMPORT, BillBucket.IMPORT, BillBucket.DUPLICATE,
                BillBucket.STATUS_SKIP, BillBucket.NON_CASHFLOW_SKIP, BillBucket.BAD,
            )
        )
        assertEquals(6, counts.total)
        assertEquals(2, counts.importable)
        assertEquals(1, counts.duplicate)
        assertEquals(1, counts.statusSkipped)
        assertEquals(1, counts.nonCashflowSkipped)
        assertEquals(1, counts.bad)
        assertEquals(3, counts.skipped)
    }

    @Test
    fun `时间口径是本地墙钟换算成真 epoch 分钟`() {
        val minuteValue = BillRows.minuteFromText("2026-10-17 00:30")!!
        val wallEncoding = LocalDate.of(2026, 10, 17).toEpochDay() * 1440 + 30
        if (zone.rules.getOffset(java.time.Instant.now()).totalSeconds != 0) {
            // UTC+8 这类时区：墙钟编码会偏整整 8 小时（AddDate 头注释里写着这条）
            assertEquals(true, minuteValue != wallEncoding)
        }
        assertEquals(minute(2026, 10, 17, 0, 30), minuteValue)
        // 序列日期同样过时区（不是"序列 × 1440"）
        assertEquals(minute(2026, 10, 17, 12, 0), BillRows.minuteFromSerial(46312.5))
        assertEquals(LocalTime.of(12, 0), LocalTime.ofSecondOfDay((46312.5 % 1 * 86_400).toLong()))
    }
}
