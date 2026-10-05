package dev.penned.app.data.importer

import java.io.IOException

/**
 * 最小 OLE2/CFB（复合文件）只读解析器——一木导出的 .xls 是 BIFF8 包在 OLE2 里。
 * 不引 Apache POI（依赖冻结 D010），只实现本导入器需要的读取面：
 * 头部 → DIFAT → FAT → 目录 → 按名读流（常规扇区 + 迷你扇区双路径）。
 */
object Ole2Reader {

    private val MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
    )

    // 0xFFFFFFFE / 0xFFFFFFFF 的 Int 位型
    private const val ENDOFCHAIN = -2
    private const val FREESECT = -1
    private const val MINI_CUTOFF_DEFAULT = 4096

    class Ole2Exception(message: String) : IOException(message)

    /** 读取 OLE2 容器里名为 [name] 的流（如 "Workbook"）。 */
    fun readStream(data: ByteArray, name: String): ByteArray {
        if (data.size < 512 || !data.startsWith(MAGIC)) throw Ole2Exception("不是有效的 Excel 文件（OLE2 头缺失）")
        val sectorSize = 1 shl u16(data, 30)
        val miniSize = 1 shl u16(data, 32)
        val dirStart = u32(data, 48)
        val miniCutoff = u32(data, 56).toInt()
        val difStart = u32(data, 68).toInt()
        val difCount = u32(data, 72).toInt()

        // DIFAT：头部 109 项 + 扩展扇区
        val fatSectors = ArrayList<Int>(16)
        for (i in 0 until 109) {
            val s = u32(data, 76 + i * 4).toInt()
            if (s >= 0 && s != ENDOFCHAIN) fatSectors.add(s)
        }
        var dif = difStart
        var guard = 0
        while (dif >= 0 && dif != ENDOFCHAIN && difCount > 0 && guard++ < 100_000) {
            val base = (dif + 1) * sectorSize
            for (i in 0 until sectorSize / 4 - 1) {
                val s = u32(data, base + i * 4).toInt()
                if (s >= 0) fatSectors.add(s)
            }
            dif = u32(data, base + (sectorSize / 4 - 1) * 4).toInt()
        }

        val fat = IntArray(fatSectors.size * (sectorSize / 4))
        var w = 0
        for (s in fatSectors) {
            val base = (s + 1) * sectorSize
            for (i in 0 until sectorSize / 4) fat[w++] = u32(data, base + i * 4).toInt()
        }

        fun chain(start: Int): List<Int> {
            val out = ArrayList<Int>()
            var s = start
            var hops = 0
            while (s != ENDOFCHAIN && s != FREESECT && hops++ <= fat.size) {
                out.add(s)
                if (s < 0 || s >= fat.size) throw Ole2Exception("文件 FAT 链损坏")
                s = fat[s]
            }
            return out
        }

        // 目录
        val dirBytes = chain(dirStart).joinToBytes(data, sectorSize)
        var rootStart = ENDOFCHAIN
        val entries = ArrayList<DirEntry>()
        var off = 0
        while (off + 128 <= dirBytes.size) {
            val type = dirBytes[off + 66].toInt() and 0xFF
            // 0 = 空项，255 = 未用项（常以 0xFF 填充）；1 = 存储，2 = 流，5 = 根
            if (type == 1 || type == 2 || type == 5) {
                val nameLen = u16(dirBytes, off + 64)
                // nameLen 含 2 字节 UTF-16 终止符
                val entryName = String(dirBytes, off, (nameLen - 2).coerceAtLeast(0).coerceAtMost(64), Charsets.UTF_16LE)
                val start = u32(dirBytes, off + 116).toInt()
                val size = u32(dirBytes, off + 120).toInt()
                if (type == 5) rootStart = start
                entries.add(DirEntry(entryName, type, start, size))
            }
            off += 128
        }

        val target = entries.firstOrNull { it.type == 2 && it.name == name }
            ?: throw Ole2Exception("Excel 文件缺少 $name 数据流")

        val cutoff = if (miniCutoff in 1..(1 shl 20)) miniCutoff else MINI_CUTOFF_DEFAULT
        if (target.size >= cutoff) {
            val bytes = chain(target.start).joinToBytes(data, sectorSize)
            return bytes.copyOf(target.size.coerceAtMost(bytes.size))
        }

        // 小流：从 Root Entry 的迷你流容器里按 MiniFAT 拼接
        val miniContainer = chain(rootStart).joinToBytes(data, sectorSize)
        val miniFat = ArrayList<Int>()
        entries.firstOrNull { it.type == 2 && it.name == "MiniFAT" }?.let { miniFatEntry ->
            chain(miniFatEntry.start).forEach { s ->
                val base = (s + 1) * sectorSize
                for (i in 0 until sectorSize / 4) miniFat.add(u32(data, base + i * 4).toInt())
            }
        }
        val out = java.io.ByteArrayOutputStream()
        var s = target.start
        var hops = 0
        while (s != ENDOFCHAIN && s != FREESECT && hops++ <= miniFat.size) {
            val base = s * miniSize
            if (base + miniSize > miniContainer.size) break
            out.write(miniContainer, base, miniSize)
            if (s < 0 || s >= miniFat.size) throw Ole2Exception("文件 MiniFAT 链损坏")
            s = miniFat[s]
        }
        val all = out.toByteArray()
        return if (target.size <= all.size) all.copyOf(target.size) else all
    }

    /** 扇区 s 的文件偏移：文件头部本身占一个扇区大小。 */
    private fun List<Int>.joinToBytes(data: ByteArray, sectorSize: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (s in this) {
            val base = (s + 1) * sectorSize
            out.write(data, base, minOf(sectorSize, data.size - base).coerceAtLeast(0))
        }
        return out.toByteArray()
    }

    private data class DirEntry(val name: String, val type: Int, val start: Int, val size: Int)

    private fun u16(b: ByteArray, off: Int): Int = (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }
}
