package dev.penned.app.domain

import dev.penned.app.data.db.entity.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 组合筛选口径（M3-7）表驱动：日期窗口、分类含二级展开、条件摘要、导出文件名、
 * 空值/空列表语义（空列表 = 不限，不是"筛出 0 条"）。
 */
class TxFilterTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 15)

    private fun category(id: String, name: String, parentId: String? = null, deleted: Boolean = false) =
        CategoryEntity(
            id = id, bookId = "b1", parentId = parentId, name = name,
            type = CategoryType.EXPENSE, createdAt = 0, updatedAt = 0, deleted = deleted, deviceId = "d",
        )

    @Test
    fun `日期区间转分钟窗口：两端都含当天、按本地墙钟`() {
        val (from, to) = TxFilters.minuteRange(
            LocalDate.of(2026, 10, 1).toEpochDay(),
            LocalDate.of(2026, 10, 15).toEpochDay(),
            zone,
        )
        // 10/1 00:00 本地（UTC+8）= 9/30 16:00 UTC
        assertEquals(LocalDate.of(2026, 10, 1).atStartOfDay(zone).toEpochSecond() / 60, from)
        // 10/15 23:59 本地
        assertEquals(LocalDate.of(2026, 10, 15).atStartOfDay(zone).toEpochSecond() / 60 + 1439, to)
        // 只设一头：另一头不加边界
        assertEquals(null, TxFilters.minuteRange(null, today.toEpochDay(), zone).first)
        assertEquals(null, TxFilters.minuteRange(today.toEpochDay(), null, zone).second)
        assertEquals(null to null, TxFilters.minuteRange(null, null, zone))
    }

    @Test
    fun `分类含二级：选一级连子树一起算，软删的不进`() {
        val categories = listOf(
            category("p1", "食品餐饮"),
            category("s1", "午餐", parentId = "p1"),
            category("s2", "晚餐", parentId = "p1"),
            category("p2", "购物消费"),
            category("s3", "数码", parentId = "p2", deleted = true),
        )
        assertEquals(
            listOf("p1", "s1", "s2"),
            TxFilters.expandCategoryIds(categories, listOf("p1")),
        )
        // 选二级只算它自己
        assertEquals(listOf("s1"), TxFilters.expandCategoryIds(categories, listOf("s1")))
        // 软删的子分类不算
        assertEquals(listOf("p2"), TxFilters.expandCategoryIds(categories, listOf("p2")))
        // 空选 = 不限
        assertEquals(emptyList<String>(), TxFilters.expandCategoryIds(categories, emptyList()))
    }

    @Test
    fun `条件摘要：只列在用的条件，顺序固定`() {
        assertEquals("", TxFilters.summary(TxFilter(), today = today))
        assertEquals(
            "支出 · 食品餐饮 · 近 7 天 · 2 个标签 · ≥ 10.00 · 不计收支",
            TxFilters.summary(
                TxFilter(
                    type = TxType.EXPENSE,
                    fromEpochDay = today.minusDays(6).toEpochDay(),
                    toEpochDay = today.toEpochDay(),
                    categoryIds = listOf("c1"),
                    tagIds = listOf("g1", "g2"),
                    amountMinCents = 1000,
                    excludeFromStats = true,
                ),
                categoryNames = mapOf("c1" to "食品餐饮"),
                today = today,
            ),
        )
        // 三个以上分类收成「前两个 等 N 个分类」；关键词垫底
        assertEquals(
            "食品餐饮、购物消费 等 3 个分类 · 「奶茶」",
            TxFilters.summary(
                TxFilter(categoryIds = listOf("c1", "c2", "c3"), keyword = "奶茶"),
                categoryNames = mapOf("c1" to "食品餐饮", "c2" to "购物消费", "c3" to "出行交通"),
                today = today,
            ),
        )
        // 类型不限但要求排除转账
        assertEquals("不含转账", TxFilters.summary(TxFilter(includeTransfer = false), today = today))
    }

    @Test
    fun `日期段文案：同一天、近 N 天、区间、单头`() {
        val d = { day: Int -> LocalDate.of(2026, 10, day).toEpochDay() }
        assertEquals("10月15日", TxFilters.dateLabel(d(15), d(15), today))
        assertEquals("近 7 天", TxFilters.dateLabel(d(9), d(15), today))
        assertEquals("10月1日 - 10月5日", TxFilters.dateLabel(d(1), d(5), today))
        assertEquals("10月1日起", TxFilters.dateLabel(d(1), null, today))
        assertEquals("到 10月5日", TxFilters.dateLabel(null, d(5), today))
    }

    @Test
    fun `金额段文案`() {
        assertEquals("≥ 10.00", TxFilters.amountLabel(1000, null))
        assertEquals("≤ 100.00", TxFilters.amountLabel(null, 10_000))
        assertEquals("10.00 - 100.00", TxFilters.amountLabel(1000, 10_000))
        assertEquals("", TxFilters.amountLabel(null, null))
    }

    @Test
    fun `导出文件名：带摘要与日期，非法字符被去掉`() {
        assertEquals(
            "penned-全部流水-20261015.csv",
            TxFilters.exportFileName(TxFilter(), today = today),
        )
        assertEquals(
            "penned-支出-食品餐饮-20261015.csv",
            TxFilters.exportFileName(
                TxFilter(type = TxType.EXPENSE, categoryIds = listOf("c1")),
                categoryNames = mapOf("c1" to "食品餐饮"),
                today = today,
            ),
        )
        // 关键词里带引号/斜杠：都要被去掉（文件名安全）
        val name = TxFilters.exportFileName(
            TxFilter(keyword = "a/b:c"),
            today = today,
        )
        assertFalse("不能有斜杠", name.contains("/"))
        assertFalse("不能有冒号", name.contains(":"))
        assertTrue(name.endsWith("-20261015.csv"))
    }

    @Test
    fun `是否在用与条件计数：空列表不算条件`() {
        assertFalse(TxFilter().isActive)
        assertEquals(0, TxFilter().activeCount)
        // 空列表 = 不限（不是"筛出 0 条"）
        assertFalse(TxFilter(categoryIds = emptyList(), accountIds = emptyList(), tagIds = emptyList()).isActive)
        assertEquals(1, TxFilter(type = TxType.INCOME).activeCount)
        // 日期两端算一个条件；金额两端算一个条件
        assertEquals(1, TxFilter(fromEpochDay = 1L, toEpochDay = 2L).activeCount)
        assertEquals(1, TxFilter(amountMinCents = 1L, amountMaxCents = 2L).activeCount)
        assertEquals(3, TxFilter(type = TxType.EXPENSE, tagIds = listOf("g"), includeTransfer = true).activeCount)
    }
}
