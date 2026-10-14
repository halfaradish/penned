package dev.penned.app.data.importer

import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 一木记账导出 xls 的语义层（docs/local/一木导出反分析.md）：
 * - 列语义按导出模板 23 列，头部按列名定位（宽容列序）；
 * - 金额：支出为负、收入为正（导出层做了 ×(-1)），入库恒为正、符号由 type 表达（D001）；
 * - 退款/优惠是流水属性（D007），退款列在真实导出里是文本单元格；
 * - 报销列（模板 11-13：报销账户/报销金额/报销明细，M3-3）只取**账户名与金额**——
 *   导出里**没有报销状态列**，状态一律留 NONE（不臆断"已报/待报"，否则会凭空进资产页的可报/已报）；
 *   报销明细不做（D008 明确不做单据明细）；
 * - 分类/账本/账户按显示名引用（导出无 ID），匹配交给 ImportRepository；
 * - 日期 "yyyy-MM-dd HH:mm" 分钟精度文本 → epochMinute（D002）。
 */
object YimuXlsParser {

    /** 模板必填列（其余列忽略）。 */
    private val REQUIRED_COLUMNS = listOf("日期", "收支类型", "金额", "类别")

    data class YimuTx(
        val dateEpochMinute: Long,
        val isIncome: Boolean,
        val amountCents: Long,
        val categoryL1: String,
        val categoryL2: String?,
        val accountName: String?,
        val bookName: String?,
        val refundCents: Long = 0,
        val discountCents: Long = 0,
        val note: String? = null,
        /** 报销账户名（M3-3，导出「报销账户」列）。 */
        val reimburseAccountName: String? = null,
        /** 报销金额（M3-3，导出「报销金额」列）；0 = 按流水净额全额（`ReimburseMath`）。 */
        val reimburseCents: Long = 0,
    )

    data class Problem(val rowIndex: Int, val message: String)

    data class Parsed(val rows: List<YimuTx>, val problems: List<Problem>)

    class YimuFormatException(message: String) : IOException(message)

    private val DATE_FORMATS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/M/d H:m"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    )

    fun parse(bytes: ByteArray): Parsed {
        val doc = Biff8Reader.parse(Ole2Reader.readStream(bytes, "Workbook"))
        val sheetName = doc.sheetNames().firstOrNull { it == "账单" } ?: doc.sheetNames().firstOrNull()
            ?: throw YimuFormatException("工作簿为空")
        val cells = doc.sheet(sheetName)
        if (cells.isEmpty()) throw YimuFormatException("工作表「$sheetName」为空")

        // 头部按列名定位
        val header = cells.filter { it.row == 0 }
            .mapNotNull { c -> (c.value as? Biff8Reader.CellValue.Text)?.value?.trim()?.let { c.col to it } }
            .toMap()
        val colOf: (String) -> Int? = { name -> header.entries.firstOrNull { it.value == name }?.key }
        val missing = REQUIRED_COLUMNS.filter { colOf(it) == null }
        if (missing.isNotEmpty()) throw YimuFormatException("缺少必备列：${missing.joinToString("、")}——请确认是一木记账导出的 xls 账单")

        val cDate = colOf("日期")!!
        val cType = colOf("收支类型")!!
        val cAmount = colOf("金额")!!
        val cL1 = colOf("类别")!!
        val cL2 = colOf("二级分类")
        val cAccount = colOf("账户")
        val cBook = colOf("账本")
        val cRefund = colOf("退款")
        val cDiscount = colOf("优惠")
        val cNote = colOf("备注")
        val cReimburseAccount = colOf("报销账户")
        val cReimburseAmount = colOf("报销金额")

        val byRow = cells.filter { it.row > 0 }.groupBy { it.row }
        val rows = ArrayList<YimuTx>()
        val problems = ArrayList<Problem>()

        for ((rowIndex, rowCells) in byRow) {
            try {
                val cellAt = { col: Int? ->
                    col?.let { c -> rowCells.firstOrNull { it.col == c }?.value }
                }
                fun textOf(col: Int?): String? = (cellAt(col) as? Biff8Reader.CellValue.Text)?.value?.trim()?.ifEmpty { null }
                fun numOf(col: Int?): Double? = when (val v = cellAt(col)) {
                    is Biff8Reader.CellValue.Num -> v.value
                    is Biff8Reader.CellValue.Text -> v.value.trim().replace(",", "").toDoubleOrNull()
                    else -> null
                }

                val typeText = textOf(cType) ?: throw YimuFormatException("收支类型为空")
                val isIncome = when (typeText) {
                    "支出" -> false
                    "收入" -> true
                    else -> throw YimuFormatException("未知收支类型「$typeText」（转账类型导出格式未知，暂不支持）")
                }
                val amount = numOf(cAmount) ?: throw YimuFormatException("金额无法解析")
                val amountCents = Math.round(Math.abs(amount) * 100.0).toLong()
                val dateText = textOf(cDate) ?: throw YimuFormatException("日期为空")
                val dateEpochMinute = parseDate(dateText) ?: throw YimuFormatException("日期格式无法解析：$dateText")
                val l1 = textOf(cL1) ?: throw YimuFormatException("一级分类为空")

                rows.add(
                    YimuTx(
                        dateEpochMinute = dateEpochMinute,
                        isIncome = isIncome,
                        amountCents = amountCents,
                        categoryL1 = l1,
                        categoryL2 = textOf(cL2),
                        accountName = textOf(cAccount),
                        bookName = textOf(cBook),
                        refundCents = numOf(cRefund)?.let { Math.round(Math.abs(it) * 100.0) } ?: 0L,
                        discountCents = numOf(cDiscount)?.let { Math.round(Math.abs(it) * 100.0) } ?: 0L,
                        note = textOf(cNote),
                        reimburseAccountName = textOf(cReimburseAccount),
                        reimburseCents = numOf(cReimburseAmount)?.let { Math.round(Math.abs(it) * 100.0) } ?: 0L,
                    )
                )
            } catch (e: YimuFormatException) {
                problems.add(Problem(rowIndex, e.message ?: "解析失败"))
            } catch (e: Exception) {
                problems.add(Problem(rowIndex, "解析失败：${e.message}"))
            }
        }
        return Parsed(rows, problems)
    }

    private fun parseDate(text: String): Long? {
        val t = text.trim().replace('T', ' ')
        for (f in DATE_FORMATS) {
            try {
                val dt = LocalDateTime.parse(t, f)
                return dt.atZone(java.time.ZoneId.systemDefault()).toEpochSecond() / 60
            } catch (_: Exception) {
            }
        }
        return null
    }
}
