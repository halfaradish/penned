package dev.penned.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.seed.DefaultSeed
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PennedDatabaseTest {

    private lateinit var db: PennedDatabase

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

    @Test
    fun `seed creates one book and category tree once`() = runTest {
        val book1 = DefaultSeed.seedIfEmpty(db, "device-a", now = 1000L)
        val book2 = DefaultSeed.seedIfEmpty(db, "device-a", now = 2000L)

        assertNotNull(book1)
        assertNull(book2)
        assertEquals(1, db.bookDao().count())
        assertEquals(1, db.bookDao().observeAll().first().size)

        val categories = db.categoryDao().observeByBook(book1!!.id).first()
        val parents = categories.filter { it.parentId == null }
        val subs = categories.filter { it.parentId != null }
        assertEquals(DefaultSeed.expenseTree.size + DefaultSeed.incomeList.size, parents.size)
        assertEquals(DefaultSeed.expenseTree.values.sumOf { it.size }, subs.size)
        assertTrue(parents.any { it.type == CategoryType.INCOME && it.name == "工资" })
    }

    @Test
    fun `insert transaction then soft delete hides it`() = runTest {
        val book = DefaultSeed.seedIfEmpty(db, "device-a", now = 1000L)!!
        val dinner = db.categoryDao().observeByBook(book.id).first().first { it.name == "晚餐" }
        val now = 10_000L
        val tx = TransactionEntity(
            id = "tx-1", bookId = book.id, type = TxType.EXPENSE, amountCents = 1600,
            categoryId = dinner.id, dateEpochMinute = now / 60_000,
            creatorDeviceId = "device-a", createdAt = now, updatedAt = now, deviceId = "device-a",
        )
        db.transactionDao().upsert(tx)

        val rows = db.transactionDao().observeRecentWithCategory(book.id, 100).first()
        assertEquals(1, rows.size)
        assertEquals("晚餐", rows[0].categoryName)
        assertEquals("食品餐饮", rows[0].categoryParentName)

        db.transactionDao().softDelete("tx-1", now + 1)
        assertTrue(db.transactionDao().observeRecentWithCategory(book.id, 100).first().isEmpty())
        // 软删不是物理删除，原始行仍在（同步需要）
        assertEquals(1, db.transactionDao().count())
    }

    @Test
    fun `recent query joins category and orders descending`() = runTest {
        val book = DefaultSeed.seedIfEmpty(db, "device-a", now = 1000L)!!
        val lunch = db.categoryDao().observeByBook(book.id).first().first { it.name == "午餐" }
        fun tx(id: String, minute: Long) = TransactionEntity(
            id = id, bookId = book.id, type = TxType.EXPENSE, amountCents = 100,
            categoryId = lunch.id, dateEpochMinute = minute,
            creatorDeviceId = "d", createdAt = minute, updatedAt = minute, deviceId = "d",
        )
        db.transactionDao().upsert(tx("old", 1000))
        db.transactionDao().upsert(tx("new", 2000))

        val rows = db.transactionDao().observeRecentWithCategory(book.id, 10).first()
        assertEquals(listOf("new", "old"), rows.map { it.id })
    }
}
