package dev.penned.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.db.dao.toProgress
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.data.db.entity.WishlistItemEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.TxType
import dev.penned.app.domain.WishlistStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 愿望清单 DAO（M3-15）：排序、软删/撤销、手动存入、跟随账户的余额投影（重算口径）、不碰流水。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WishlistDaoTest {

    private lateinit var db: PennedDatabase
    private val bookId = "book-wl"

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

    private suspend fun seedItem(
        id: String,
        name: String,
        target: Long,
        saved: Long = 0,
        sortOrder: Int = 0,
        accountId: String? = null,
        deleted: Boolean = false,
    ) {
        db.wishlistDao().upsert(
            WishlistItemEntity(
                id = id, bookId = bookId, name = name, targetCents = target, savedCents = saved,
                accountId = accountId, sortOrder = sortOrder, createdAt = sortOrder.toLong(),
                updatedAt = 1L, deleted = deleted, deviceId = "t",
            )
        )
    }

    @Test
    fun `列表：按 sortOrder 排、软删的不出现、别的账本不算`() = runTest {
        seedItem("w2", "旅行基金", 500_000L, sortOrder = 1)
        seedItem("w1", "换电脑", 800_000L, sortOrder = 0)
        seedItem("w3", "删掉的", 100_000L, sortOrder = 2, deleted = true)
        db.wishlistDao().upsert(
            WishlistItemEntity(
                id = "w9", bookId = "other", name = "别的账本", targetCents = 1L,
                sortOrder = 0, createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        val rows = db.wishlistDao().observeByBook(bookId).first()
        assertEquals(listOf("换电脑", "旅行基金"), rows.map { it.item.name })
        assertEquals(2, db.wishlistDao().countInBook(bookId))
        assertEquals(3, db.wishlistDao().nextSortOrder(bookId))
    }

    @Test
    fun `手动存入与取出：只动 savedCents，不写流水`() = runTest {
        seedItem("w1", "换电脑", 800_000L)
        db.wishlistDao().addSaved("w1", 300_000L, now = 2L)
        assertEquals(300_000L, db.wishlistDao().get("w1")!!.savedCents)
        db.wishlistDao().addSaved("w1", -100_000L, now = 3L)
        assertEquals(200_000L, db.wishlistDao().get("w1")!!.savedCents)
        // 一条流水都没生成（攒钱目标是规划工具）
        assertEquals(0, db.transactionDao().all().size)
    }

    @Test
    fun `跟随账户：已存 = 该账户的重算余额（初始 + 收支 ± 转账手续费）`() = runTest {
        db.accountDao().upsert(
            AccountEntity(
                id = "a1", bookId = bookId, name = "微信", type = AccountType.DEBIT,
                initialBalanceCents = 100_000L, createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        db.transactionDao().upsert(
            TransactionEntity(
                id = "t1", bookId = bookId, type = TxType.INCOME, amountCents = 30_000L,
                accountId = "a1", dateEpochMinute = 100L, creatorDeviceId = "t",
                createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        db.transactionDao().upsert(
            TransactionEntity(
                id = "t2", bookId = bookId, type = TxType.EXPENSE, amountCents = 12_000L,
                accountId = "a1", dateEpochMinute = 200L, creatorDeviceId = "t",
                createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        seedItem("w1", "旅行基金", 500_000L, saved = 999L, accountId = "a1")

        val row = db.wishlistDao().observeByBook(bookId).first().single()
        assertEquals("微信", row.accountName)
        assertEquals(118_000L, row.accountBalanceCents)
        // 跟随模式：手动存的那 9.99 不参与显示，进度按账户余额算
        val progress = row.toProgress()
        assertEquals(118_000L, progress.savedCents)
        assertEquals("微信", progress.accountName)
        assertEquals(0.236f, progress.progress, 0.001f)
    }

    @Test
    fun `跟随的账户被软删：退回手动值（投影为 null）`() = runTest {
        db.accountDao().upsert(
            AccountEntity(
                id = "a1", bookId = bookId, name = "微信", type = AccountType.DEBIT,
                initialBalanceCents = 100_000L, createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        seedItem("w1", "旅行基金", 500_000L, saved = 9_900L, accountId = "a1")
        db.accountDao().softDelete("a1", now = 2L)

        val row = db.wishlistDao().observeByBook(bookId).first().single()
        assertNull(row.accountBalanceCents)
        assertNull(row.accountName)
        assertEquals(9_900L, row.toProgress().savedCents)
    }

    @Test
    fun `标记达成与撤销删除`() = runTest {
        seedItem("w1", "换电脑", 800_000L)
        db.wishlistDao().setStatus("w1", WishlistStatus.DONE, now = 2L)
        assertEquals(WishlistStatus.DONE, db.wishlistDao().get("w1")!!.status)
        db.wishlistDao().softDelete("w1", now = 3L)
        assertTrue(db.wishlistDao().observeByBook(bookId).first().isEmpty())
        db.wishlistDao().restoreAll(listOf("w1"), now = 4L)
        assertEquals(1, db.wishlistDao().observeByBook(bookId).first().size)
    }
}
