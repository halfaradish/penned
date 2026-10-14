package dev.penned.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.db.entity.DebtEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.DebtDirection
import dev.penned.app.domain.DebtMath
import dev.penned.app.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 债务「已归还」的 SQL 口径（M3-4）：只认**清偿方向**的流水——
 * BORROW 认支出（我还款）、LEND 认收入（对方还我），借入/借出那一笔不算；软删与别账本不计。
 * 这是 `DebtMath.settleType` 的孪生实现，一处改了另一处必须跟着改（域层表驱动在 `DebtMathTest`）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DebtDaoTest {

    private lateinit var db: PennedDatabase
    private val bookId = "book-debt"

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

    private fun debt(
        id: String,
        direction: DebtDirection = DebtDirection.BORROW,
        principalCents: Long = 100_000L,
        closed: Boolean = false,
        book: String = bookId,
        deleted: Boolean = false,
        counterparty: String = "张三",
    ) = DebtEntity(
        id = id,
        bookId = book,
        direction = direction,
        counterparty = counterparty,
        principalCents = principalCents,
        startEpochDay = 20_000L,
        closed = closed,
        createdAt = 1L,
        updatedAt = 1L,
        deleted = deleted,
        deviceId = "test",
    )

    private fun tx(
        id: String,
        type: TxType,
        amountCents: Long,
        debtId: String?,
        deleted: Boolean = false,
        book: String = bookId,
    ) = TransactionEntity(
        id = id,
        bookId = book,
        type = type,
        amountCents = amountCents,
        dateEpochMinute = 1000,
        debtId = debtId,
        creatorDeviceId = "test",
        createdAt = 1L,
        updatedAt = 1L,
        deleted = deleted,
        deviceId = "test",
    )

    @Test
    fun `我欠人：借入不算已还，还款才算`() = runTest {
        db.debtDao().upsert(debt("d1"))
        db.transactionDao().upsert(tx("in", TxType.INCOME, 100_000L, "d1"))   // 借入：钱进了账户
        db.transactionDao().upsert(tx("r1", TxType.EXPENSE, 40_000L, "d1"))  // 还款
        db.transactionDao().upsert(tx("r2", TxType.EXPENSE, 20_000L, "d1"))  // 再还

        val row = db.debtDao().observeByBook(bookId).first().single()
        assertEquals("已归还只算两笔还款", 60_000L, row.repaidCents)
        assertEquals("剩余 = 本金 − 已还", 40_000L, row.remainingCents)
        assertEquals(40_000L, DebtMath.payableCents(listOf(row.toBalance())))
    }

    @Test
    fun `人欠我：借出不算已还，收到还款才算`() = runTest {
        db.debtDao().upsert(debt("d2", direction = DebtDirection.LEND, principalCents = 30_000L))
        db.transactionDao().upsert(tx("out", TxType.EXPENSE, 30_000L, "d2"))  // 借出：钱出去了
        db.transactionDao().upsert(tx("back", TxType.INCOME, 12_000L, "d2")) // 对方还我

        val row = db.debtDao().observeByBook(bookId).first().single()
        assertEquals(12_000L, row.repaidCents)
        assertEquals(18_000L, row.remainingCents)
        assertEquals(18_000L, DebtMath.receivableCents(listOf(row.toBalance())))
        assertEquals("应收不进应付", 0L, DebtMath.payableCents(listOf(row.toBalance())))
    }

    @Test
    fun `边界：软删流水、无流水、别的债务、别的账本`() = runTest {
        db.debtDao().upsert(debt("empty", principalCents = 50_000L))
        db.debtDao().upsert(debt("d1", principalCents = 10_000L))
        db.debtDao().upsert(debt("elsewhere", book = "book-other"))
        // 软删的还款不算
        db.transactionDao().upsert(tx("gone", TxType.EXPENSE, 9_999L, "d1", deleted = true))
        // 挂在别的债务上的流水不算这个债务的
        db.transactionDao().upsert(tx("other-debt", TxType.EXPENSE, 5_000L, "elsewhere"))
        // 没有债务归属的普通流水不算
        db.transactionDao().upsert(tx("plain", TxType.EXPENSE, 3_000L, null))

        val rows = db.debtDao().observeByBook(bookId).first().associateBy { it.id }
        assertEquals("本账本两笔债务", setOf("empty", "d1"), rows.keys)
        assertEquals(0L, rows.getValue("empty").repaidCents)
        assertEquals(0L, rows.getValue("d1").repaidCents)
        assertEquals(50_000L, rows.getValue("empty").remainingCents)
    }

    @Test
    fun `单笔观察 + 结清标记 + 软删`() = runTest {
        db.debtDao().upsert(debt("d1", principalCents = 100_000L))
        db.transactionDao().upsert(tx("r1", TxType.EXPENSE, 100_000L, "d1"))

        val row = db.debtDao().observeById("d1").first()!!
        assertEquals(100_000L, row.repaidCents)
        assertEquals(0L, row.remainingCents)
        assertEquals("还清后应付归零", 0L, DebtMath.payableCents(listOf(row.toBalance())))
        assertNull(db.debtDao().observeById("nope").first())

        // 手动标结清：哪怕账面还剩零头也不计入应付/应收
        db.debtDao().upsert(debt("d3", principalCents = 60_000L))
        db.debtDao().setClosed("d3", closed = true, now = 2L)
        val closed = db.debtDao().observeByBook(bookId).first().first { it.id == "d3" }
        assertEquals(true, closed.closed)
        assertEquals("结清的不进应付", 0L, DebtMath.payableCents(listOf(closed.toBalance())))
        assertEquals("但剩余数照算（账面事实）", 60_000L, closed.remainingCents)

        // 软删债务：从列表消失（名下流水由 TransactionDao 那侧保留）
        db.debtDao().softDelete("d1", now = 3L)
        assertEquals(listOf("d3"), db.debtDao().observeByBook(bookId).first().map { it.id })
    }
}
