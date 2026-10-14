package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 账单组合的条件编解码（M3-8）表驱动：空列表 ↔ null、转义（反斜杠/逗号）、脏数据（连续逗号/空段）、
 * 以及 `TxFilter` ↔ 实体往返（含三个开关与数值/枚举条件的 null 语义）。
 */
class BillFilterCodecTest {

    @Test
    fun `多值编码：空列表是 null 不是空串`() {
        assertNull(BillFilterCodec.encodeIds(emptyList()))
        assertNull("全是空串也当空", BillFilterCodec.encodeIds(listOf("", "")))
        assertEquals("a", BillFilterCodec.encodeIds(listOf("a")))
        assertEquals("a,b,c", BillFilterCodec.encodeIds(listOf("a", "b", "c")))
    }

    @Test
    fun `多值解码：null 空串 与空段`() {
        assertEquals(emptyList<String>(), BillFilterCodec.decodeIds(null))
        assertEquals(emptyList<String>(), BillFilterCodec.decodeIds(""))
        assertEquals(emptyList<String>(), BillFilterCodec.decodeIds(",,,"))
        assertEquals(listOf("a", "b"), BillFilterCodec.decodeIds("a,,b"))
        assertEquals(listOf("a", "b", "c"), BillFilterCodec.decodeIds("a,b,c"))
    }

    @Test
    fun `转义：值里的反斜杠与逗号往返不丢`() {
        val tricky = listOf("a,b", "c\\d", "e\\,f", "g")
        val encoded = BillFilterCodec.encodeIds(tricky)!!
        assertEquals(tricky, BillFilterCodec.decodeIds(encoded))
        // 值里的分隔符被转义：含逗号的一个值编码后仍是"一个值"
        assertEquals("a\\,b", BillFilterCodec.encodeIds(listOf("a,b")))
        assertEquals(listOf("a,b"), BillFilterCodec.decodeIds(BillFilterCodec.encodeIds(listOf("a,b"))))
    }

    @Test
    fun `往返：TxFilter 到实体再回来，字段一个不丢`() {
        val filter = TxFilter(
            keyword = "奶茶",
            type = TxType.EXPENSE,
            fromEpochDay = 20_000L,
            toEpochDay = 20_010L,
            categoryIds = listOf("c1", "c2"),
            accountIds = listOf("a1"),
            tagIds = listOf("g1", "g2", "g3"),
            amountMinCents = 1000L,
            amountMaxCents = 50_000L,
            excludeFromStats = true,
            excludeFromBudget = false,
            includeTransfer = false,
        )
        val entity = BillFilterCodec.toEntity(
            id = "f1", bookId = "b1", name = "常看的支出", filter = filter,
            sortOrder = 0, now = 1L, deviceId = "d",
        )
        assertEquals("常看的支出", entity.name)
        assertEquals("c1,c2", entity.categoryIds)
        assertEquals("g1,g2,g3", entity.tagIds)
        assertEquals("奶茶", entity.keyword)
        assertEquals(TxFilter(
            keyword = "奶茶", type = TxType.EXPENSE, fromEpochDay = 20_000L, toEpochDay = 20_010L,
            categoryIds = listOf("c1", "c2"), accountIds = listOf("a1"), tagIds = listOf("g1", "g2", "g3"),
            amountMinCents = 1000L, amountMaxCents = 50_000L,
            excludeFromStats = true, excludeFromBudget = false, includeTransfer = false,
        ), BillFilterCodec.toFilter(entity))
    }

    @Test
    fun `往返：全空条件（只存了个名字）`() {
        val entity = BillFilterCodec.toEntity(
            id = "f2", bookId = "b1", name = "空组合", filter = TxFilter(),
            sortOrder = 1, now = 1L, deviceId = "d",
        )
        assertNull(entity.keyword)
        assertNull(entity.categoryIds)
        assertNull(entity.accountIds)
        assertNull(entity.tagIds)
        assertNull(entity.type)
        assertNull(entity.includeTransfer)
        assertEquals(TxFilter(), BillFilterCodec.toFilter(entity))
    }

    @Test
    fun `关键词首尾空白不入库（与搜索口径一致）`() {
        val entity = BillFilterCodec.toEntity(
            id = "f3", bookId = "b1", name = "x", filter = TxFilter(keyword = "  "),
            sortOrder = 0, now = 1L, deviceId = "d",
        )
        assertNull(entity.keyword)
    }
}
