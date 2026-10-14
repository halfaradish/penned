package dev.penned.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.db.PennedDatabase
import dev.penned.app.data.db.entity.RecurringRuleEntity
import dev.penned.app.domain.RecurringPeriod
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * 打开即补（M3-6，D070）：到期才补、未来不产、同规则同期幂等（重复启动不重复）、
 * 停用/软删/结束日都不补；删规则不连带删已生成的流水。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecurringGeneratorTest {

    private lateinit var db: PennedDatabase
    private val bookId = "book-rec"
    private val zone: ZoneId = ZoneId.systemDefault()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, PennedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun rule(
        id: String,
        period: RecurringPeriod = RecurringPeriod.DAILY,
        interval: Int = 1,
        start: Long,
        next: Long = start,
        end: Long? = null,
        enabled: Boolean = true,
        deleted: Boolean = false,
    ) = RecurringRuleEntity(
        id = id, bookId = bookId, name = "房租", type = TxType.EXPENSE, amountCents = 200_000,
        categoryId = "c1", accountId = "a1", periodType = period, interval = interval,
        startEpochDay = start, nextEpochDay = next, endEpochDay = end, enabled = enabled,
        createdAt = 1L, updatedAt = 1L, deleted = deleted, deviceId = "d",
    )

    private fun generator() = RecurringGenerator(db, "device-1", zone)

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun `补到期期次：未来不产、重复启动不重复`() = runTest {
        val start = day(2026, 10, 10)
        db.recurringDao().upsert(rule("r1", start = start))

        // 今天 10/14：补 10/10 ~ 10/14 共 5 期
        val first = generator().catchUp(todayEpochDay = day(2026, 10, 14), now = 100L)
        assertEquals(5, first)
        val txs = db.transactionDao().allInBook(bookId)
        assertEquals(5, txs.size)
        assertTrue("都挂在规则上", txs.all { it.recurringRuleId == "r1" })
        assertTrue("记录方式 = 自动记账", txs.all { it.source == TxSource.AUTO })
        assertTrue("金额/分类/账户照规则", txs.all { it.amountCents == 200_000L && it.categoryId == "c1" && it.accountId == "a1" })
        assertTrue("没有未来日期", txs.all { dev.penned.app.domain.AddDate.dayFromMinute(it.dateEpochMinute, zone) <= day(2026, 10, 14) })
        // 期次是 10/10..10/14 各一条（按天）
        assertEquals(
            (10..14).map { day(2026, 10, it) },
            txs.map { dev.penned.app.domain.AddDate.dayFromMinute(it.dateEpochMinute, zone) }.sorted(),
        )

        // 再启动一次：同一天不再补
        assertEquals(0, generator().catchUp(todayEpochDay = day(2026, 10, 14), now = 200L))
        assertEquals(5, db.transactionDao().allInBook(bookId).size)
        // 指针已推到 10/15
        assertEquals(day(2026, 10, 15), db.recurringDao().get("r1")!!.nextEpochDay)

        // 又过一天：只补 10/15 这一期
        assertEquals(1, generator().catchUp(todayEpochDay = day(2026, 10, 15), now = 300L))
        assertEquals(6, db.transactionDao().allInBook(bookId).size)
    }

    @Test
    fun `按月对齐：1 月 31 日的规则在 2 月补出 2 月 28 日`() = runTest {
        db.recurringDao().upsert(
            rule("r1", period = RecurringPeriod.MONTHLY, start = day(2026, 1, 31), next = day(2026, 1, 31))
        )
        // 到 3 月 5 日：补 1/31、2/28 两期
        assertEquals(2, generator().catchUp(todayEpochDay = day(2026, 3, 5), now = 1L))
        val days = db.transactionDao().allInBook(bookId)
            .map { dev.penned.app.domain.AddDate.dayFromMinute(it.dateEpochMinute, zone) }.sorted()
        assertEquals(listOf(day(2026, 1, 31), day(2026, 2, 28)), days)
        // 下一期是 3/31（回到锚定日）
        assertEquals(day(2026, 3, 31), db.recurringDao().get("r1")!!.nextEpochDay)
    }

    @Test
    fun `停用、软删、结束日：都不补`() = runTest {
        val start = day(2026, 10, 1)
        db.recurringDao().upsert(rule("off", start = start, enabled = false))
        db.recurringDao().upsert(rule("gone", start = start, deleted = true))
        // 结束日 10/3：只补 10/1~10/3
        db.recurringDao().upsert(rule("ended", start = start, end = day(2026, 10, 3)))

        assertEquals(3, generator().catchUp(todayEpochDay = day(2026, 10, 14), now = 1L))
        assertEquals(listOf("ended"), db.transactionDao().allInBook(bookId).map { it.recurringRuleId }.distinct())
    }

    @Test
    fun `删规则不连带删已生成的流水`() = runTest {
        db.recurringDao().upsert(rule("r1", start = day(2026, 10, 12)))
        assertEquals(3, generator().catchUp(todayEpochDay = day(2026, 10, 14), now = 1L))

        db.recurringDao().softDelete("r1", now = 2L)
        assertEquals(0, db.recurringDao().observeByBook(bookId).first().size)
        assertEquals("流水保留", 3, db.transactionDao().allInBook(bookId).size)
        // 再启动也不会补（规则已软删）
        assertEquals(0, generator().catchUp(todayEpochDay = day(2026, 10, 20), now = 3L))
    }

    @Test
    fun `单次上限：一次最多补 MAX_CATCH_UP 期，剩下的下次继续`() = runTest {
        db.recurringDao().upsert(rule("r1", start = day(2024, 1, 1)))
        val n = generator().catchUp(todayEpochDay = day(2026, 12, 31), now = 1L)
        assertEquals(dev.penned.app.domain.RecurringMath.MAX_CATCH_UP, n)
        val next = db.recurringDao().get("r1")!!.nextEpochDay
        assertEquals("指针停在已生成的下一期", day(2024, 1, 1) + n, next)
        // 再启动一次继续补（不是重复）
        val more = generator().catchUp(todayEpochDay = day(2026, 12, 31), now = 2L)
        assertTrue("还有剩的", more > 0)
        assertEquals(n + more, db.transactionDao().allInBook(bookId).size)
    }
}
