package dev.penned.app.data.importer

import dev.penned.app.domain.BillBucket
import dev.penned.app.domain.BillDirection
import dev.penned.app.domain.BillRow
import dev.penned.app.domain.BillRows
import dev.penned.app.domain.BillRowCounts
import dev.penned.app.domain.BillStatus
import dev.penned.app.domain.BillDedupe

/** 解析结果：识别到的来源/表头位置 + 逐行（含被跳过的行，**每行只归一类**）。 */
data class ParsedBill(
    val source: BillSource,
    val headerRowIndex: Int,
    /** 表头之后被当成"数据行"的原始行数（含坏行；空行不计）。 */
    val dataLineCount: Int,
    /** 逐行解析：坏行给 null；[buckets] 与它一一对应。 */
    val rows: List<BillRow?>,
    val buckets: List<BillBucket>,
) {
    /** 分列计数（预览页那块；重复要等去重查库后才能补上，见 `withDuplicateCount`）。 */
    val counts: BillRowCounts get() = BillDedupe.counts(buckets)

    /** 把"重复"那一列补上（去重在 M4-5 查库后做）。 */
    fun withDuplicateCount(duplicate: Int): BillRowCounts =
        counts.copy(duplicate = duplicate)
}

/**
 * 表头之后的逐行解析（M4-4）：把二维文本按列映射变成 [BillRow]，同时按口径判掉坏行 / 状态跳过 /
 * 不计收支——**每行只归一类**（优先级见 `BillDedupe.bucket`）。
 *
 * 只管"这一行长什么样"；去重（查库/文件内）与账户、分类映射在 M4-5。
 */
object BillRowParser {

    fun parse(file: BillFile): ParsedBill {
        val headerIndex = BillFormats.headerRowIndex(file.rows)
        val header = if (headerIndex >= 0) file.rows[headerIndex] else emptyList()
        val format = BillFormats.detect(header)
            ?: throw BillFormatException("认不出这份账单的表头（通用模式要用手动列映射）")
        val map = BillFormats.mapping(header, format)
        return parse(file, format, map)
    }

    /**
     * 通用模式：按"我们的字段 → 表头列名"的映射解析（手改或 AI 建议都用这一条路）。
     *
     * [mapping] 里没有的字段就是"这一列没有"（-1）；时间/金额缺一 = 没法解析（调用方要拦住）。
     */
    fun parseGeneric(file: BillFile, header: List<String>, mapping: Map<String, String>): ParsedBill {
        val cells = header.map { it.trim() }
        fun index(field: String): Int = mapping[field]?.let { cells.indexOf(it) } ?: -1
        val map = BillColumnMap(
            time = index("time"),
            amount = index("amount"),
            direction = index("direction"),
            merchant = index("merchant"),
            item = index("item"),
            status = index("status"),
            externalId = index("externalId"),
            payment = index("payment"),
            category = index("category"),
            note = index("note"),
        )
        if (map.time < 0 || map.amount < 0) throw BillFormatException("时间和金额两列必须指定")
        return parseCells(file, BillFormat(BillSource.GENERIC, header), map, headerRowIndex = -1)
    }

    fun parse(file: BillFile, format: BillFormat, map: BillColumnMap): ParsedBill =
        parseCells(file, format, map, headerRowIndex = BillFormats.headerRowIndex(file.rows))

    private fun parseCells(
        file: BillFile,
        format: BillFormat,
        map: BillColumnMap,
        headerRowIndex: Int,
    ): ParsedBill {
        val headerIndex = headerRowIndex
        // 通用模式（headerRowIndex = -1）：数据从第 2 行起（第 1 行当表头）
        val from = when {
            headerIndex >= 0 -> headerIndex + 1
            file.rows.isNotEmpty() -> 1
            else -> 0
        }
        val rows = ArrayList<BillRow?>(file.rows.size)
        val buckets = ArrayList<BillBucket>(file.rows.size)
        var dataLines = 0

        for (index in from until file.rows.size) {
            val line = file.rows[index]
            if (line.all { it.isBlank() }) continue // 空行不算数据行
            dataLines++

            val direction = BillRows.direction(cell(line, map.direction))
            val minute = parseMinute(cell(line, map.time))
            val amount = BillRows.amountCents(cell(line, map.amount))
            // 每行只归一类，优先级：坏行 → （重复在 M4-5 补）→ 状态跳过 → 不计收支 → 将导入
            val bucket = when {
                direction == null || minute == null -> BillBucket.BAD
                BillRows.status(cell(line, map.status)) == BillStatus.SKIP -> BillBucket.STATUS_SKIP
                direction == BillDirection.SKIP -> BillBucket.NON_CASHFLOW_SKIP
                amount == null || amount <= 0L -> BillBucket.BAD
                else -> BillBucket.IMPORT
            }
            buckets += bucket
            rows += if (bucket != BillBucket.IMPORT) {
                null
            } else {
                BillRow(
                    dateEpochMinute = minute!!,
                    amountCents = amount!!,
                    expense = direction == BillDirection.EXPENSE,
                    note = BillRows.note(
                        cell(line, map.merchant),
                        cell(line, map.item),
                        cell(line, map.note),
                    ),
                    externalId = BillRows.externalId(cell(line, map.externalId), cell(line, map.subExternalId)),
                    accountClue = BillRows.accountClue(cell(line, map.payment)),
                    categoryHint = BillRows.categoryHint(cell(line, map.category)),
                    raw = line,
                )
            }
        }

        return ParsedBill(
            source = format.source,
            headerRowIndex = headerIndex,
            dataLineCount = dataLines,
            rows = rows,
            buckets = buckets,
        )
    }

    private fun cell(line: List<String>, index: Int): String? =
        if (index in line.indices) line[index] else null

    /** 时间列：文本形态先试；不认且是纯数值 → 当 Excel 序列日期（xlsx 的形态）。 */
    private fun parseMinute(raw: String?): Long? {
        BillRows.minuteFromText(raw)?.let { return it }
        val value = BillRows.cell(raw)
        val number = value.toDoubleOrNull() ?: return null
        return BillRows.minuteFromSerial(number)
    }
}
