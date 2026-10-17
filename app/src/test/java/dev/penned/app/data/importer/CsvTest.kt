package dev.penned.app.data.importer

import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
            tx("t4") {
                it.copy(
                    currencyCode = "USD", rate = 7.12, address = "上海市,静安区",
                    reimburseState = dev.penned.app.domain.ReimburseState.PENDING, reimburseAccountId = "a9",
                )
            },
        )
        val csv = PennedCsv.export(original)
        val restored = PennedCsv.import(csv, bookId = "b2")

        assertEquals(4, restored.size)
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

        // 无损备份：币种/汇率/地址/报销也在 CSV 里往返
        val r4 = restored.first { it.id == "t4" }
        assertEquals("USD", r4.currencyCode)
        assertEquals(7.12, r4.rate!!, 1e-9)
        assertEquals("上海市,静安区", r4.address)
        assertEquals(dev.penned.app.domain.ReimburseState.PENDING, r4.reimburseState)
        assertEquals("a9", r4.reimburseAccountId)
    }

    @Test
    fun `notification source round trips and old values stay`() {
        val csv = PennedCsv.export(
            listOf(
                tx("n1") { it.copy(source = TxSource.NOTIFICATION) },
                tx("n2") { it.copy(source = TxSource.AUTO) },
                tx("n3") { it.copy(source = TxSource.MANUAL) },
            )
        )
        assertTrue("导出列里带 NOTIFICATION", csv.contains("NOTIFICATION"))

        val restored = PennedCsv.import(csv, bookId = "b1").associateBy { it.id }
        assertEquals(TxSource.NOTIFICATION, restored.getValue("n1").source)
        assertEquals(TxSource.AUTO, restored.getValue("n2").source)
        assertEquals(TxSource.MANUAL, restored.getValue("n3").source)
    }

    @Test
    fun `bad header rejected`() {
        assertThrows(PennedCsv.CsvFormatException::class.java) {
            PennedCsv.import("foo,bar\n1,2", bookId = "b1")
        }
    }

    @Test
    fun `externalId 往返并写在末尾列`() {
        val csv = PennedCsv.export(listOf(tx("t1") { it.copy(externalId = "4200003225202610171528367827") }))
        assertEquals("externalId", PennedCsv.COLUMNS.last())
        assertTrue(csv.lines().first().endsWith(",externalId"))
        val restored = PennedCsv.import(csv, bookId = "b1").single()
        assertEquals("4200003225202610171528367827", restored.externalId)
    }

    @Test
    fun `旧 24 列备份（没有 externalId）仍能导入`() {
        // 模拟 M4 之前导出的文件：把表头与每行的最后一列去掉
        val old = PennedCsv.export(listOf(tx("t1") { it.copy(note = "旧备份") }))
            .lines()
            .filter { it.isNotEmpty() }
            .joinToString("\r\n") { line -> line.substringBeforeLast(",") } + "\r\n"
        assertFalse(old.contains("externalId"))
        val restored = PennedCsv.import(old, bookId = "b1").single()
        assertEquals("旧备份", restored.note)
        assertEquals(null, restored.externalId)
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
