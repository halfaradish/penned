package dev.penned.app.ui.add

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.db.PennedDatabase
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.data.seed.DefaultSeed
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 复查 P0 回归（落库）：经 TxEdit 合并后的实体写回 Room 再读出，
 * 开关/批次等字段必须保真（含 @ColumnInfo(defaultValue) 列）。
 * 退款/优惠自 M3-2 起可编辑：编辑页把它们按记录现值回传（下面这条就是"只改备注"的真实形态）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class TxEditPersistenceTest {

    private lateinit var db: PennedDatabase
    private lateinit var bookId: String

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PennedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookId = DefaultSeed.seedIfEmpty(db, "device-test", 1L)!!.id
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun importedRow(dateMinute: Long) = TransactionEntity(
        id = "tx-1",
        bookId = bookId,
        type = TxType.EXPENSE,
        amountCents = 5000,
        dateEpochMinute = dateMinute,
        note = "原备注",
        refundCents = 2000,
        discountCents = 100,
        reimburseState = ReimburseState.PENDING,
        reimburseAccountId = "acc-2",
        currencyCode = "USD",
        rate = 7.12,
        address = "上海市",
        excludeFromStats = true,
        excludeFromBudget = true,
        source = TxSource.IMPORT,
        importBatchId = "batch-x",
        creatorDeviceId = "creator-other",
        createdAt = 111L,
        updatedAt = 222L,
        deviceId = "device-other",
    )

    @Test
    fun `editing only the note round trips every other field`() = runTest {
        db.transactionDao().upsert(importedRow(dateMinute = 1000))

        val original = db.transactionDao().get("tx-1")!!
        val edited = TxEdit.applyEdit(
            original,
            TxEdit.Editable(
                type = original.type,
                amountCents = original.amountCents,
                accountId = original.accountId,
                targetAccountId = original.targetAccountId,
                categoryId = original.categoryId,
                dateEpochMinute = original.dateEpochMinute,
                note = "改个备注",
                feeCents = original.feeCents,
                refundCents = original.refundCents,
                discountCents = original.discountCents,
                // 报销三字段自 M3-3 起也在可编辑白名单里：只改备注时按记录现值回传
                reimburseState = original.reimburseState,
                reimburseAccountId = original.reimburseAccountId,
                reimburseCents = original.reimburseCents,
            ),
            now = 5000L,
            deviceId = "device-1",
        )
        db.transactionDao().upsert(edited)

        val saved = db.transactionDao().get("tx-1")!!
        assertEquals("改个备注", saved.note)
        assertEquals(2000L, saved.refundCents)
        assertEquals(100L, saved.discountCents)
        assertEquals(ReimburseState.PENDING, saved.reimburseState)
        assertEquals("acc-2", saved.reimburseAccountId)
        assertEquals("USD", saved.currencyCode)
        assertEquals(7.12, saved.rate!!, 1e-9)
        assertEquals("上海市", saved.address)
        assertTrue(saved.excludeFromStats)
        assertTrue(saved.excludeFromBudget)
        assertEquals(TxSource.IMPORT, saved.source)
        assertEquals("batch-x", saved.importBatchId)
        assertEquals("creator-other", saved.creatorDeviceId)
        assertEquals(111L, saved.createdAt)
        assertEquals(1000L, saved.dateEpochMinute)
        assertEquals(bookId, saved.bookId)
        assertEquals(5000L, saved.updatedAt)
        // 批次归属没变：按批次删除仍能命中
        assertEquals(1, db.transactionDao().allInBook(bookId).count { it.importBatchId == "batch-x" })
    }
}
