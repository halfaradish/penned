package dev.penned.app.data.importer

/**
 * RFC 4180 CSV：引号转义、单元格内逗号/引号/换行、CRLF/LF。
 * 纯函数，无 Android 依赖。
 */
object CsvCodec {

    fun parse(content: String): List<List<String>> {
        var text = content
        if (text.startsWith("\uFEFF")) text = text.substring(1) // Excel 友好的 BOM
        val rows = ArrayList<List<String>>()
        val row = ArrayList<String>()
        val cell = StringBuilder()
        var i = 0
        var inQuotes = false
        var cellStarted = false
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                inQuotes -> when (c) {
                    '"' -> if (i + 1 < n && text[i + 1] == '"') { cell.append('"'); i++ } else inQuotes = false
                    else -> cell.append(c)
                }
                c == '"' -> {
                    inQuotes = true
                    cellStarted = true
                }
                c == ',' -> {
                    row.add(cell.toString()); cell.clear(); cellStarted = true
                }
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && i + 1 < n && text[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.clear()
                    rows.add(row.toList()); row.clear()
                    cellStarted = false
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty() || cellStarted) {
            row.add(cell.toString())
            rows.add(row.toList())
        }
        return rows
    }

    fun serialize(rows: List<List<String>>): String {
        return rows.joinToString("\r\n") { row -> row.joinToString(",") { cell -> quote(cell) } } + "\r\n"
    }

    private fun quote(cell: String): String {
        return if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else cell
    }
}
