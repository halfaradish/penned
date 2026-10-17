package dev.penned.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 账单文件读取层（M4-4）：编码三态 / xlsx 四种单元格形态（**fixture 现场用 ZipOutputStream 生成**，
 * 仓库里不落任何账单样本）/ zip 包 / xls 复用既有读取器 / 表头定位与来源识别。
 *
 * 真实账单（微信 xlsx、支付宝 csv）只在 M4-5 的"全链路对账"里用 `docs/local/` 的文件跑，不进仓库。
 */
class BillFileReaderTest {

    private val gb18030: Charset = Charset.forName("GB18030")

    // ---- CSV 编码三态 ----

    @Test
    fun `纯 UTF-8 走 UTF-8`() {
        val bytes = "交易时间,金额\n2026-10-17 12:00,12.00\n".toByteArray(Charsets.UTF_8)
        val file = BillFileReader.read(bytes, "alipay.csv")
        assertEquals(BillContainer.CSV, file.container)
        assertEquals("UTF-8", file.charsetLabel)
        assertEquals("交易时间", file.rows[0][0])
    }

    @Test
    fun `带 BOM 的 UTF-8 也走 UTF-8（BOM 不留在第一格）`() {
        val bytes = "\uFEFF交易时间,金额\n".toByteArray(Charsets.UTF_8)
        val file = BillFileReader.read(bytes, "a.csv")
        assertEquals("UTF-8", file.charsetLabel)
        assertEquals("交易时间", file.rows[0][0])
    }

    @Test
    fun `GBK 系的字节严格 UTF-8 解不开_退 GB18030`() {
        // 真实导出：GB18030 无 BOM；同样的字用 UTF-8 编出来 UTF-8 解不开
        val text = "交易时间,交易对方,金额\n2026-10-17 12:00,西大朴诚食堂,12.00\n"
        val bytes = text.toByteArray(gb18030)
        // 前提：这份内容用 UTF-8 严格解码确实会失败（否则这条测试没有意义）
        var utf8Fails = false
        try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes))
        } catch (_: java.nio.charset.CharacterCodingException) {
            utf8Fails = true
        }
        assertTrue("这份 GBK 内容应当解不成 UTF-8", utf8Fails)

        val file = BillFileReader.read(bytes, "alipay.csv")
        assertEquals("GB18030", file.charsetLabel)
        assertEquals("西大朴诚食堂", file.rows[1][1])
    }

    @Test
    fun `手动指定编码可以覆盖探测结果`() {
        val bytes = "交易时间,金额\n".toByteArray(gb18030)
        val file = BillFileReader.read(bytes, "a.csv", charsetOverride = gb18030)
        assertEquals("GB18030", file.charsetLabel)
        assertEquals("交易时间", file.rows[0][0])
    }

    @Test
    fun `合法的 UTF-8 不许被误判成 GB18030`() {
        val bytes = "时间,金额\n12:00,1.00\n".toByteArray(Charsets.UTF_8)
        assertEquals("UTF-8", BillFileReader.read(bytes, "a.csv").charsetLabel)
    }

    // ---- zip 包（支付宝网页版偶发） ----

    @Test
    fun `zip 里取唯一的 csv`() {
        val csv = "交易时间,金额\n2026-10-17 12:00,12.00\n".toByteArray(gb18030)
        val zip = zipOf("账单/交易明细.csv" to csv)
        val file = BillFileReader.read(zip, "alipay.zip")
        assertEquals(BillContainer.ZIP_CSV, file.container)
        assertEquals("GB18030", file.charsetLabel)
        assertEquals("交易时间", file.rows[0][0])
    }

    // ---- xlsx：四种单元格形态 ----

    @Test
    fun `xlsx sharedStrings 与数值与序列日期与斜杠占位`() {
        val xlsx = minimalXlsx()
        val file = BillFileReader.read(xlsx, "wechat.xlsx")
        assertEquals(BillContainer.XLSX, file.container)

        // 表头在第 3 行（前两行是前置说明/空行）
        val headerIndex = BillFormats.headerRowIndex(file.rows)
        assertEquals(2, headerIndex)
        val header = file.rows[headerIndex]
        assertEquals("交易时间", header[0])
        assertEquals("收/支", header[4])
        assertEquals("金额(元)", header[5])

        // 数据行：时间 = 序列日期数值；金额 = 数值；收/支 = sharedStrings 字符串；备注 = inlineStr
        val row = file.rows[3]
        assertEquals("46312.55502314815", row[0])
        assertEquals("支出", row[4])
        assertEquals("12.1", row[5])
        assertEquals("/", row[6]) // `/` 占位原样读出来（归一在 BillRows）
        assertEquals("订单付款", row[7])
    }

    @Test
    fun `xlsx 稀疏行与跳列不会错位`() {
        val xlsx = sparseXlsx()
        val file = BillFileReader.read(xlsx, "s.xlsx")
        // A1 / C1 两格：中间 B 空着，B 不能跑到 C 的位置
        assertEquals(listOf("A", "", "C"), file.rows[0])
        // 第 1 行与第 3 行有内容，第 2 行整行缺失 → 补成空行
        assertEquals(3, file.rows.size)
        assertEquals(listOf("X"), file.rows[2])
    }

    // ---- xls：复用既有读取器（用仓库里已有的脱敏 fixture） ----

    @Test
    fun `xls 走既有 Biff8Reader`() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("yimu_sample.xls")!!.readBytes()
        val file = BillFileReader.read(bytes, "yimu.xls")
        assertEquals(BillContainer.XLS, file.container)
        assertTrue("至少要有表头与一行数据", file.rows.size >= 2)
        assertEquals("日期", file.rows[0][0])
    }

    // ---- 来源识别与表头定位 ----

    @Test
    fun `表头定位：跳过前置说明与分隔线`() {
        val rows = listOf(
            listOf("微信支付账单明细"),
            listOf("起始时间：[2026-09-17 00:00:00] 终止时间：[2026-10-17 20:02:42]"),
            listOf("共42笔记录"),
            listOf("----------------------微信支付账单明细列表--------------------"),
            listOf("交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)"),
            listOf("2026-10-17 13:19:14", "商户消费", "西大朴诚食堂", "炒饭", "支出", "12"),
        )
        assertEquals(4, BillFormats.headerRowIndex(rows))
    }

    @Test
    fun `表头定位：找不到返回 -1`() {
        assertEquals(-1, BillFormats.headerRowIndex(listOf(listOf("账单"), listOf("没有表头"))))
    }

    @Test
    fun `认得出微信与支付宝 认不出的落通用模式`() {
        val wechat = BillFormats.WECHAT.header
        val alipay = BillFormats.ALIPAY.header + listOf("") // 支付宝表头末尾那个无名列

        assertEquals(BillSource.WECHAT, BillFormats.detect(wechat)?.source)
        assertEquals(BillSource.ALIPAY, BillFormats.detect(alipay)?.source)
        assertNull(BillFormats.detect(listOf("时间", "钱", "方向")))

        val w = BillFormats.mapping(wechat, BillFormats.WECHAT)
        assertTrue(w.valid)
        assertEquals(0, w.time)
        assertEquals(5, w.amount)
        assertEquals(4, w.direction)
        assertEquals(8, w.externalId)
        assertEquals(9, w.subExternalId)
        assertEquals(6, w.payment)
        assertEquals(7, w.status)

        val a = BillFormats.mapping(alipay, BillFormats.ALIPAY)
        assertTrue(a.valid)
        assertEquals(0, a.time)
        assertEquals(6, a.amount)
        assertEquals(5, a.direction)
        assertEquals(9, a.externalId)
        assertEquals(10, a.subExternalId)
        assertEquals(7, a.payment)
        assertEquals(1, a.category)
    }

    @Test
    fun `列名带尾随制表符也能对上（真实导出如此）`() {
        val header = listOf("交易时间", "交易分类", "交易对方", "对方账号", "商品说明", "收/支", "金额", "收/付款方式", "交易状态", "交易订单号\t", "商家订单号\t", "备注")
        val map = BillFormats.mapping(header, BillFormats.ALIPAY)
        assertTrue(map.valid)
        assertEquals(9, map.externalId)
        assertEquals(10, map.subExternalId)
    }

    @Test
    fun `空文件与认不出的容器给可读错误`() {
        val empty = BillFileReader.runCatching { read(ByteArray(0), "a.csv") }
        assertTrue(empty.isFailure)
        assertNotNull(empty.exceptionOrNull()?.message)

        // 后缀是 xlsx 但内容不是 zip → 也该给可读错误而不是崩
        val bad = BillFileReader.runCatching { read("not a zip".toByteArray(), "a.xlsx") }
        assertTrue(bad.isFailure)
    }

    // ---- fixture 现场生成（仓库里不落账单样本） ----

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /**
     * 最小 xlsx：前置说明两行 + 表头 + 一行数据；四种单元格形态各占一格
     * （A 数值序列日期 / E sharedStrings / F 数值 / G inlineStr `/` / H 公式字符串）。仓库里不落文件。
     */
    private fun minimalXlsx(): ByteArray = zipOf(
        "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>""".toByteArray(),
        "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/></sheets></workbook>""".toByteArray(),
        "xl/sharedStrings.xml" to ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="6" uniqueCount="6">""" +
            "<si><t>微信支付账单明细</t></si>" +
            "<si><t>交易时间</t></si><si><t>收/支</t></si><si><t>金额(元)</t></si><si><t>支出</t></si>" +
            "<si><t>商品</t></si>" +
            "</sst>").toByteArray(),
        "xl/worksheets/sheet1.xml" to ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""" +
            """<row r="1"><c r="A1" t="s"><v>0</v></c></row>""" +
            """<row r="2"><c r="A2" t="s"><v>1</v></c></row>""" +
            """<row r="3"><c r="A3" t="s"><v>1</v></c><c r="E3" t="s"><v>2</v></c><c r="F3" t="s"><v>3</v></c><c r="H3" t="s"><v>5</v></c></row>""" +
            """<row r="4"><c r="A4"><v>46312.55502314815</v></c><c r="E4" t="s"><v>4</v></c><c r="F4"><v>12.1</v></c><c r="G4" t="inlineStr"><is><t>/</t></is></c><c r="H4" t="str"><v>订单付款</v></c></row>""" +
            "</sheetData></worksheet>").toByteArray(),
    )

    /** 稀疏行 fixture：A1/C1 两格（B 空）、第 2 行整行缺失、第 3 行只有 A。 */
    private fun sparseXlsx(): ByteArray = zipOf(
        "xl/worksheets/sheet1.xml" to ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""" +
            """<row r="1"><c r="A1" t="inlineStr"><is><t>A</t></is></c><c r="C1" t="inlineStr"><is><t>C</t></is></c></row>""" +
            """<row r="3"><c r="A3" t="inlineStr"><is><t>X</t></is></c></row>""" +
            "</sheetData></worksheet>").toByteArray(),
    )
}
