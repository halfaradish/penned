package dev.penned.app.data.importer

import java.io.IOException

/**
 * 最小 BIFF8（xls 工作簿）只读解析器。配合 [Ole2Reader] 使用，不引 Apache POI（依赖冻结 D010）。
 * 只支持一木导出需要的记录面：BOUNDSHEET / SST(+CONTINUE) / LABELSST / LABEL / NUMBER / RK / MULRK / FORMULA(数值缓存)。
 *
 * SST 跨 CONTINUE 的两种真实形态都支持（MS-XLS CONTINUE 规则）：
 * A. 字符数据中间断开 —— 新片首字节是该段字符数据的编码标志（Excel / POI 拆长字符串都这样）；
 * B. 字符串整体放进新片 —— 新片直接以字符串头开始。
 */
object Biff8Reader {

    class BiffException(message: String) : IOException(message)

    sealed interface CellValue {
        data class Text(val value: String) : CellValue
        data class Num(val value: Double) : CellValue
    }

    data class Cell(val row: Int, val col: Int, val value: CellValue)

    class XlsDoc(private val sheetCells: Map<String, List<Cell>>) {
        fun sheet(name: String): List<Cell> = sheetCells[name] ?: emptyList()
        fun sheetNames(): Set<String> = sheetCells.keys
    }

    fun parse(workbookStream: ByteArray): XlsDoc {
        val sst = ArrayList<String>(256)
        val sheetOffsets = LinkedHashMap<String, Int>()

        // 第一遍：收集 SST(+CONTINUE 链) 与 BOUNDSHEET
        var pos = 0
        while (pos + 4 <= workbookStream.size) {
            val rec = u16(workbookStream, pos)
            val len = u16(workbookStream, pos + 2)
            val body = pos + 4
            when (rec) {
                0x00FC -> {
                    val pieces = ArrayList<ByteArray>()
                    pieces.add(workbookStream.copyOfRange(body, body + len))
                    var p = body + len
                    while (p + 4 <= workbookStream.size && u16(workbookStream, p) == 0x003C) {
                        val l2 = u16(workbookStream, p + 2)
                        pieces.add(workbookStream.copyOfRange(p + 4, p + 4 + l2))
                        p += 4 + l2
                    }
                    readSst(pieces, sst)
                    pos = p
                    continue
                }
                0x0085 -> {
                    val name = readBoundSheetName(workbookStream, body, len)
                    sheetOffsets[name] = u32(workbookStream, body)
                }
            }
            pos = body + len
        }

        if (sheetOffsets.isEmpty()) throw BiffException("工作簿缺少工作表定义（BOUNDSHEET）")

        val sheets = LinkedHashMap<String, List<Cell>>()
        for ((name, offset) in sheetOffsets) {
            sheets[name] = readSheetCells(workbookStream, offset, sst)
        }
        return XlsDoc(sheets)
    }

    private fun readBoundSheetName(data: ByteArray, off: Int, len: Int): String {
        if (len < 6) throw BiffException("BOUNDSHEET 记录过短")
        val cch = data[off + 4].toInt() and 0xFF
        val high = data[off + 5].toInt() and 0x01 != 0
        val start = off + 6
        val byteLen = cch * (if (high) 2 else 1)
        if (start + byteLen > data.size) throw BiffException("工作表名越界")
        return if (high) String(data, start, byteLen, Charsets.UTF_16LE)
        else String(data, start, byteLen, Charsets.ISO_8859_1)
    }

    private fun readSst(pieces: List<ByteArray>, out: MutableList<String>) {
        val first = pieces.first()
        if (first.size < 8) throw BiffException("SST 记录过短")
        var piece = 0
        var off = 8

        /** 不做任何标志字节处理的原始推进（用于字符串头）。 */
        fun advancePiece() {
            if (piece >= pieces.size - 1) throw BiffException("SST 意外结束")
            piece++
            off = 0
        }

        fun readByteRaw(): Int {
            if (off >= pieces[piece].size) advancePiece()
            return pieces[piece][off++].toInt() and 0xFF
        }

        /** 读 n 个字符；跨片时消耗新片首的编码标志字节（形态 A）。 */
        fun readChars(count: Int, startHigh: Boolean): String {
            val sb = StringBuilder(count)
            var remaining = count
            var high = startHigh
            while (remaining > 0) {
                if (off >= pieces[piece].size) {
                    if (piece >= pieces.size - 1) break
                    piece++
                    off = 1 // 片首 = 编码标志字节
                    high = pieces[piece][0].toInt() and 0x01 != 0
                }
                val width = if (high) 2 else 1
                val take = minOf(remaining, (pieces[piece].size - off) / width)
                if (take <= 0) throw BiffException("SST 字符数据越界")
                val byteLen = take * width
                sb.append(String(pieces[piece], off, byteLen, if (high) Charsets.UTF_16LE else Charsets.ISO_8859_1))
                off += byteLen
                remaining -= take
            }
            return sb.toString()
        }

        fun readU16Raw(): Int = readByteRaw() or (readByteRaw() shl 8)

        var guard = 0
        while (true) {
            if (guard++ > 1_000_000) throw BiffException("SST 解析超限")
            if (off >= pieces[piece].size) {
                if (piece >= pieces.size - 1) break
                advancePiece() // 形态 B：字符串整体在新片，片首无标志字节
            }
            if (piece == pieces.size - 1 && off + 3 > pieces[piece].size) break
            val cch = readU16Raw()
            val flags = readByteRaw()
            val text = readChars(cch, flags and 0x01 != 0)
            out.add(text)
            if (flags and 0x08 != 0) repeat(2 + 4 * readU16Raw()) { readByteRaw() } // 富文本 run
            if (flags and 0x04 != 0) {
                var skip = 0
                for (i in 0 until 4) skip = skip or (readByteRaw() shl (8 * i))
                repeat(skip) { readByteRaw() } // 扩展字段
            }
        }
    }

    private fun readSheetCells(data: ByteArray, start: Int, sst: List<String>): List<Cell> {
        val cells = ArrayList<Cell>()
        var pos = start
        var bofSeen = false
        while (pos + 4 <= data.size) {
            val rec = u16(data, pos)
            val len = u16(data, pos + 2)
            val body = pos + 4
            when (rec) {
                0x0809 -> bofSeen = true
                0x000A -> return cells
                0x0203 -> if (bofSeen && len >= 14) {
                    cells.add(Cell(u16(data, body), u16(data, body + 2), CellValue.Num(doubleAt(data, body + 6))))
                }
                0x00FD -> if (bofSeen && len >= 10) {
                    val text = sst.getOrNull(u32(data, body + 6)) ?: ""
                    cells.add(Cell(u16(data, body), u16(data, body + 2), CellValue.Text(text)))
                }
                0x0204 -> if (bofSeen && len >= 9) {
                    val cch = u16(data, body + 6)
                    val high = data[body + 8].toInt() and 0x01 != 0
                    val byteLen = cch * (if (high) 2 else 1)
                    if (body + 9 + byteLen <= data.size) {
                        val text = String(data, body + 9, byteLen, if (high) Charsets.UTF_16LE else Charsets.ISO_8859_1)
                        cells.add(Cell(u16(data, body), u16(data, body + 2), CellValue.Text(text)))
                    }
                }
                0x027E -> if (bofSeen && len >= 10) {
                    cells.add(Cell(u16(data, body), u16(data, body + 2), CellValue.Num(decodeRk(i32(data, body + 6)))))
                }
                0x00BD -> if (bofSeen && len >= 6) {
                    val row = u16(data, body)
                    val colFirst = u16(data, body + 2)
                    val n = (len - 6) / 6
                    for (i in 0 until n) {
                        val base = body + 4 + i * 6
                        cells.add(Cell(row, colFirst + i, CellValue.Num(decodeRk(i32(data, base + 2)))))
                    }
                }
                0x0006 -> if (bofSeen && len >= 14) {
                    // FORMULA：仅数值缓存（字节 6~13）；0xFF 0xFF 开头 = 字符串/布尔/错误缓存，跳过
                    val special = data[body + 6] == 0xFF.toByte() && data[body + 7] == 0xFF.toByte()
                    if (!special) {
                        cells.add(Cell(u16(data, body), u16(data, body + 2), CellValue.Num(doubleAt(data, body + 6))))
                    }
                }
            }
            pos = body + len
        }
        return cells
    }

    private fun decodeRk(rk: Int): Double {
        val div100 = rk and 0x02 != 0
        val value = if (rk and 0x01 != 0) {
            (rk shr 2).toDouble()
        } else {
            Double.fromBits(rk.toLong() and 0xFFFFFFFC)
        }
        return if (div100) value / 100.0 else value
    }

    private fun u16(b: ByteArray, off: Int): Int = (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)
    private fun i32(b: ByteArray, off: Int): Int = u32(b, off)
    private fun doubleAt(b: ByteArray, off: Int): Double =
        Double.fromBits((b[off].toLong() and 0xFF) or ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or ((b[off + 3].toLong() and 0xFF) shl 24) or
            ((b[off + 4].toLong() and 0xFF) shl 32) or ((b[off + 5].toLong() and 0xFF) shl 40) or
            ((b[off + 6].toLong() and 0xFF) shl 48) or ((b[off + 7].toLong() and 0xFF) shl 56))
}
