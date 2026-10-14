package dev.penned.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 报销汇总 SQL（M3-3）：可报 = PENDING 的报销金额和、已报 = DONE 的报销金额和——
 * **缺省 0 按流水净额全额**（`ReimburseMath.claimCents` 的 SQL 版），超额照填的算，软删与 NONE 不计。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionReimburseDaoTest {

    private lateinit var db: PennedDatabase
    private val bookId = "book-reimburse"

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

    private fun tx(
        id: String,
        amountCents: Long,
        state: ReimburseState = ReimburseState.NONE,
        reimburseCents: Long = 0L,
        book: String = bookId,
        deleted: Boolean = false,
    ) = TransactionEntity(
        id = id,
        bookId = book,
        type = TxType.EXPENSE,
        amountCents = amountCents,
        dateEpochMinute = 1000,
        reimburseState = state,
        reimburseCents = reimburseCents,
        creatorDeviceId = "test",
        createdAt = 1L,
        updatedAt = 1L,
        deleted = deleted,
        deviceId = "test",
    )

    @Test
    fun `缺省 0 = 按净额全额：可报与已报各自求和`() = runTest {
        db.transactionDao().upsert(tx("p1", 10_000L, ReimburseState.PENDING))
        db.transactionDao().upsert(tx("p2", 2_500L, ReimburseState.PENDING, reimburseCents = 1_000L))
        db.transactionDao().upsert(tx("d1", 8_000L, ReimburseState.DONE))

        val sums = db.transactionDao().observeReimburseSums(bookId).first()
        assertEquals("可报 = 10,000（全额）+ 1,000（部分）", 11_000L, sums.pendingCents)
        assertEquals("已报 = 8,000（全额）", 8_000L, sums.doneCents)
    }

    @Test
    fun `边界：报销金额等于净额、超额、未填状态、软删、别的账本`() = runTest {
        db.transactionDao().upsert(tx("eq", 5_000L, ReimburseState.PENDING, reimburseCents = 5_000L))
        db.transactionDao().upsert(tx("over", 5_000L, ReimburseState.PENDING, reimburseCents = 7_000L))
        // 是"不报销"或"没状态"的一律不进两张数字
        db.transactionDao().upsert(tx("none", 9_999L, ReimburseState.NONE, reimburseCents = 9_999L))
        // 软删的也不算
        db.transactionDao().upsert(tx("gone", 3_000L, ReimburseState.PENDING, deleted = true))
        // 别的账本不算
        db.transactionDao().upsert(tx("other", 4_000L, ReimburseState.PENDING, book = "book-other"))

        val sums = db.transactionDao().observeReimburseSums(bookId).first()
        assertEquals(5_000L + 7_000L, sums.pendingCents)
        assertEquals(0L, sums.doneCents)
    }

    @Test
    fun `空库返回两个 0（卡上不显示空白）`() = runTest {
        val sums = db.transactionDao().observeReimburseSums(bookId).first()
        assertEquals(0L, sums.pendingCents)
        assertEquals(0L, sums.doneCents)
    }

    @Test
    fun `改状态与金额能改数：同一笔从待报销改已报、再改金额`() = runTest {
        db.transactionDao().upsert(tx("t1", 10_000L, ReimburseState.PENDING))
        assertEquals(10_000L, db.transactionDao().observeReimburseSums(bookId).first().pendingCents)

        // 只把报销金额填成 4,000（仍是待报销）
        db.transactionDao().upsert(tx("t1", 10_000L, ReimburseState.PENDING, reimburseCents = 4_000L))
        assertEquals(4_000L, db.transactionDao().observeReimburseSums(bookId).first().pendingCents)

        // 收到钱了 → 已报销（金额保留）
        db.transactionDao().upsert(tx("t1", 10_000L, ReimburseState.DONE, reimburseCents = 4_000L))
        val after = db.transactionDao().observeReimburseSums(bookId).first()
        assertEquals(0L, after.pendingCents)
        assertEquals(4_000L, after.doneCents)

        // 改回不报销：两张数字都清空（金额留着也无妨，但不再计入）
        db.transactionDao().upsert(tx("t1", 10_000L, ReimburseState.NONE, reimburseCents = 4_000L))
        val none = db.transactionDao().observeReimburseSums(bookId).first()
        assertEquals(0L, none.pendingCents)
        assertEquals(0L, none.doneCents)
    }
}
