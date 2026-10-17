package dev.penned.app.data.importer

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/** 账单文件是怎么装进来的（预览页要显示"来源 / 容器 / 编码"）。 */
enum class BillContainer { CSV, XLSX, XLS, ZIP_CSV }

/**
 * 读出来的原始表格 + 它是怎么读的。
 *
 * [rows] 是**归一前**的二维文本（含前置说明行与表头行，M4-5 要按它定位表头/做列映射）；
 * [charsetLabel] 只在 CSV 有意义（`UTF-8` / `GB18030`），预览页显示、也可据此手动切换重解析。
 */
data class BillFile(
    val rows: List<List<String>>,
    val container: BillContainer,
    val charsetLabel: String? = null,
)

class BillFormatException(message: String) : java.io.IOException(message)

/**
 * 账单文件读取层（M4-4，规格 §三 M4-4）：字节 → 二维文本。**零新增依赖**——
 * `.xlsx` 用平台 `java.util.zip` + `javax.xml.parsers`(DOM)，`.xls` 复用既有 `Biff8Reader`，
 * CSV 复用既有 `CsvCodec`（RFC 4180）。
 *
 * 口径（逐条都有测试）：
 * - **CSV 编码**：剥 BOM → **严格 UTF-8 解码**（`CharsetDecoder` + `CodingErrorAction.REPORT`；
 *   不是 `String(bytes, UTF_8)` 的静默替换——那会把 GBK 的字节变成一堆 `�` 而不是报错）
 *   失败退 **GB18030**（两家历史导出都是 GBK 系）
 * - **`.xlsx`**：字符串走 `sharedStrings`（`t="s"` + 下标）；同时认 `inlineStr` / `str` / 数值；
 *   单元格按 `r` 属性（列字母）定位，**稀疏行**不会错位
 * - **`.zip`**：取其中唯一的 `.csv`（支付宝网页版偶发这种打包）
 * - 读不出内容（空文件 / 认不出的容器）= [BillFormatException]
 */
object BillFileReader {

    /** 按文件名后缀选容器；后缀认不出时按"是不是 zip / 是不是 xls OLE"兜一下。 */
    fun read(bytes: ByteArray, fileName: String, charsetOverride: Charset? = null): BillFile {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".xlsx") -> BillFile(readXlsx(bytes), BillContainer.XLSX)
            lower.endsWith(".xls") -> BillFile(readXls(bytes), BillContainer.XLS)
            lower.endsWith(".zip") -> readZip(bytes, charsetOverride)
            lower.endsWith(".csv") || lower.endsWith(".txt") -> readCsv(bytes, charsetOverride)
            // 后缀不可信时按魔数认：zip（PK）→ xlsx；OLE（D0CF11E0）→ xls；其余当 CSV
            isZip(bytes) -> BillFile(readXlsx(bytes), BillContainer.XLSX)
            isOle(bytes) -> BillFile(readXls(bytes), BillContainer.XLS)
            else -> readCsv(bytes, charsetOverride)
        }
    }

    /** 手动切编码（预览页"切换编码重解析"用）：只对 CSV/ZIP 有意义。 */
    fun decodeText(bytes: ByteArray, charsetOverride: Charset? = null): Pair<String, String> {
        // 剥 UTF-8 BOM（CsvCodec 也会剥一次，这里剥是为了让编码判定更干净）
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
        if (charsetOverride != null) {
            return String(body, charsetOverride) to charsetOverride.name()
        }
        // 严格 UTF-8：能过就 UTF-8（含纯 ASCII）；过不了说明是 GBK 系
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(body)).toString() to "UTF-8"
        } catch (_: CharacterCodingException) {
            String(body, Charset.forName("GB18030")) to "GB18030"
        }
    }

    private fun readCsv(bytes: ByteArray, charsetOverride: Charset?): BillFile {
        val (text, label) = decodeText(bytes, charsetOverride)
        val rows = CsvCodec.parse(text)
        if (rows.isEmpty()) throw BillFormatException("文件是空的")
        return BillFile(rows, BillContainer.CSV, label)
    }

    private fun readZip(bytes: ByteArray, charsetOverride: Charset?): BillFile {
        val entries = unzip(bytes)
        val csvEntry = entries.entries.firstOrNull { it.key.lowercase().endsWith(".csv") }
            ?: throw BillFormatException("这个 zip 里没有 csv")
        val (text, label) = decodeText(csvEntry.value, charsetOverride)
        val rows = CsvCodec.parse(text)
        if (rows.isEmpty()) throw BillFormatException("csv 是空的")
        return BillFile(rows, BillContainer.ZIP_CSV, label)
    }

    private fun readXls(bytes: ByteArray): List<List<String>> {
        // 与一木导入同一条管线：OLE2 容器 → 取 Workbook 流 → BIFF8 记录（`Biff8Reader` 只吃流的字节）
        val doc = Biff8Reader.parse(Ole2Reader.readStream(bytes, "Workbook"))
        val sheetName = doc.sheetNames().firstOrNull() ?: throw BillFormatException("xls 里没有工作表")
        val cells = doc.sheet(sheetName)
        if (cells.isEmpty()) throw BillFormatException("第一张工作表是空的")
        val maxRow = cells.maxOf { it.row }
        val maxCol = cells.maxOf { it.col }
        val rows = MutableList(maxRow + 1) { MutableList(maxCol + 1) { "" } }
        for (cell in cells) {
            rows[cell.row][cell.col] = when (val v = cell.value) {
                is Biff8Reader.CellValue.Text -> v.value
                is Biff8Reader.CellValue.Num -> formatNumber(v.value)
            }
        }
        return rows
    }

    /** 数值原样给（整数不带 `.0`）；金额/时间的"数值口径"在 `BillRows` 里判，不在这里猜。 */
    private fun formatNumber(value: Double): String =
        if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()

    /** `.xlsx`：zip 里取 sharedStrings + 第一张表，DOM 解析。 */
    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        val entries = unzip(bytes)
        val shared = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetXml = entries["xl/worksheets/sheet1.xml"]
            ?: entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/") && it.key.endsWith(".xml") }?.value
            ?: throw BillFormatException("xlsx 里没有工作表")
        return parseSheet(sheetXml, shared)
    }

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    out[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            throw BillFormatException("压缩包读不动：${e.message}")
        }
        if (out.isEmpty()) throw BillFormatException("压缩包是空的")
        return out
    }

    private fun isZip(bytes: ByteArray) =
        bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private fun isOle(bytes: ByteArray) =
        bytes.size > 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()

    // ---- DOM 解析 ----

    private fun document(bytes: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        // 不拉外部实体（账单文件里不该有 DTD；防 XXE）
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    }

    /** `<si><t>…</t></si>` 顺序即索引（`<si>` 里可能有多个 `<t>`——富文本片段拼起来）。 */
    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val root = document(xml)
        val out = ArrayList<String>()
        val siNodes = root.getElementsByTagName("si")
        for (i in 0 until siNodes.length) {
            val si = siNodes.item(i) as? Element ?: continue
            val texts = si.getElementsByTagName("t")
            val sb = StringBuilder()
            for (j in 0 until texts.length) sb.append(texts.item(j).textContent ?: "")
            out += sb.toString()
        }
        return out
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<List<String>> {
        val root = document(xml)
        val rowNodes = root.getElementsByTagName("row")
        val rows = ArrayList<List<String>>()
        for (i in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(i) as? Element ?: continue
            // 稀疏行：按 r 属性定位到"第几行"，中间空行补上（账单文件里通常没有）
            val rowIndex = rowEl.getAttribute("r").toIntOrNull()?.minus(1) ?: rows.size
            while (rows.size < rowIndex) rows.add(emptyList())
            val cells = LinkedHashMap<Int, String>()
            var maxCol = -1
            var node: Node? = rowEl.firstChild
            while (node != null) {
                val cell = node as? Element
                if (cell != null && cell.tagName == "c") {
                    val colIndex = columnIndex(cell.getAttribute("r"))
                    if (colIndex >= 0) {
                        cells[colIndex] = cellText(cell, shared)
                        if (colIndex > maxCol) maxCol = colIndex
                    }
                }
                node = node.nextSibling
            }
            val row = MutableList(maxCol + 1) { "" }
            cells.forEach { (col, value) -> if (col < row.size) row[col] = value }
            rows.add(row)
        }
        if (rows.isEmpty()) throw BillFormatException("工作表里没有行")
        return rows
    }

    /** 单元格值：`t="s"` 查 sharedStrings、`inlineStr`/`str` 取文本、无 `t` 取数值。 */
    private fun cellText(cell: Element, shared: List<String>): String {
        val type = cell.getAttribute("t")
        if (type == "inlineStr") {
            val texts = cell.getElementsByTagName("t")
            val sb = StringBuilder()
            for (i in 0 until texts.length) sb.append(texts.item(i).textContent ?: "")
            return sb.toString()
        }
        val v = cell.getElementsByTagName("v").item(0)?.textContent ?: return ""
        return when (type) {
            "s" -> v.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
            "str" -> v
            else -> v // 数值（金额/序列日期）原样给，口径在 BillRows
        }
    }

    /** `"A19"` → 0 / `"AA3"` → 26（列号 0 起；认不出返回 -1）。 */
    private fun columnIndex(ref: String): Int {
        var col = 0
        var seen = false
        for (ch in ref) {
            when {
                ch in 'A'..'Z' -> {
                    col = col * 26 + (ch - 'A' + 1)
                    seen = true
                }
                ch in 'a'..'z' -> {
                    col = col * 26 + (ch - 'a' + 1)
                    seen = true
                }
                else -> break
            }
        }
        return if (seen) col - 1 else -1
    }
}
