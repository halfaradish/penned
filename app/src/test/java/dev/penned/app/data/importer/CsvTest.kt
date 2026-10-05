package dev.penned.app.data.importer

import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CsvCodecTest {

    @Test
    fun `round trip with quotes commas newlines`() {
        val rows = listOf(
            listOf("id", "note", "num"),
            listOf("a1", "普通", "1"),
            listOf("a2", "带,逗号", "2"),
            listOf("a3", "带\"引号\"", "3"),
            listOf("a4", "带\n换行", "4"),
            listOf("a5", "", "5"),
        )
        val text = CsvCodec.serialize(rows)
        assertEquals(rows, CsvCodec.parse(text))
    }

    @Test
    fun `parse crlf lf and bom`() {
        val text = "\uFEFFa,b\r\nc,d\ne,f"
        assertEquals(
            listOf(listOf("a", "b"), listOf("c", "d"), listOf("e", "f")),
            CsvCodec.parse(text),
        )
    }

    @Test
    fun `trailing empty cells kept`() {
        assertEquals(listOf(listOf("a", "", "")), CsvCodec.parse("a,,"))
    }
}

class PennedCsvTest {

    private fun tx(id: String, overrides: (TransactionEntity) -> TransactionEntity = { it }) = overrides(
        TransactionEntity(
            id = id, bookId = "b1", type = TxType.EXPENSE, amountCents = 1600,
            categoryId = "c1", dateEpochMinute = 1000,
            creatorDeviceId = "d", createdAt = 1, updatedAt = 2, deviceId = "d",
        )
    )

    @Test
    fun `export import round trip preserves fields`() {
        val original = listOf(
            tx("t1") { it.copy(note = "备注,带逗号", refundCents = 500, source = TxSource.IMPORT, importBatchId = "batch-1") },
            tx("t2") { it.copy(type = TxType.TRANSFER, accountId = "a1", targetAccountId = "a2", feeCents = 100, excludeFromStats = true) },
            tx("t3") { it.copy(type = TxType.INCOME, amountCents = 350000, categoryId = null) },
        )
        val csv = PennedCsv.export(original)
        val restored = PennedCsv.import(csv, bookId = "b2")

        assertEquals(3, restored.size)
        val r1 = restored.first { it.id == "t1" }
        assertEquals("备注,带逗号", r1.note)
        assertEquals(500L, r1.refundCents)
        assertEquals(TxSource.IMPORT, r1.source)
        assertEquals("batch-1", r1.importBatchId)
        assertEquals("b2", r1.bookId) // bookId 重映射到导入目标账本

        val r2 = restored.first { it.id == "t2" }
        assertEquals(TxType.TRANSFER, r2.type)
        assertEquals("a1", r2.accountId)
        assertEquals("a2", r2.targetAccountId)
        assertEquals(100L, r2.feeCents)
        assertEquals(true, r2.excludeFromStats)

        val r3 = restored.first { it.id == "t3" }
        assertEquals(TxType.INCOME, r3.type)
        assertEquals(350000L, r3.amountCents)
        assertEquals(null, r3.categoryId)
    }

    @Test
    fun `bad header rejected`() {
        assertThrows(PennedCsv.CsvFormatException::class.java) {
            PennedCsv.import("foo,bar\n1,2", bookId = "b1")
        }
    }

    @Test
    fun `bad number rejected with line number`() {
        val csv = PennedCsv.export(listOf(tx("t1")))
        val broken = csv.replace("1600", "abc")
        assertThrows(PennedCsv.CsvFormatException::class.java) {
            PennedCsv.import(broken, bookId = "b1")
        }
    }
}
