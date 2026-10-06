package dev.penned.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.seed.DefaultSeed
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.TxType
import androidx.room.withTransaction
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
    fun `exclude from stats keeps the money in the account balance`() = runTest {
        // D020：不计入收支只影响收支统计，钱实际动了，余额必须照算
        val book = DefaultSeed.seedIfEmpty(db, "device-a", now = 1000L)!!
        val lunch = db.categoryDao().observeByBook(book.id).first().first { it.name == "午餐" }
        db.accountDao().upsert(
            dev.penned.app.data.db.entity.AccountEntity(
                id = "acc-1", bookId = book.id, name = "现金", type = dev.penned.app.domain.AccountType.CASH,
                createdAt = 1000L, updatedAt = 1000L, deviceId = "device-a",
            )
        )
        fun tx(id: String, cents: Long, excluded: Boolean) = TransactionEntity(
            id = id, bookId = book.id, type = TxType.EXPENSE, amountCents = cents,
            accountId = "acc-1", categoryId = lunch.id, dateEpochMinute = 10_000,
            excludeFromStats = excluded,
            creatorDeviceId = "d", createdAt = 1L, updatedAt = 1L, deviceId = "d",
        )
        db.transactionDao().upsert(tx("counted", 1600, excluded = false))
        db.transactionDao().upsert(tx("excluded", 5000, excluded = true))

        val account = db.accountDao().observeWithBalance(book.id).first().single()
        assertEquals(-6600L, account.balanceCents)

        // 投影里带出了标记（首页/统计靠它排除合计）
        val rows = db.transactionDao().observeRecentWithCategory(book.id, 100).first()
        assertEquals(setOf("counted", "excluded"), rows.map { it.id }.toSet())
        assertEquals(listOf("excluded"), rows.filter { it.excludeFromStats }.map { it.id })
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

    @Test
    fun `search matches note category parent and amount within the book`() = runTest {
        val book = DefaultSeed.seedIfEmpty(db, "device-a", now = 1000L)!!
        val other = dev.penned.app.data.db.entity.BookEntity(
            id = "book-b", name = "别的账本", createdAt = 1L, updatedAt = 1L, deviceId = "d",
        )
        db.bookDao().upsert(other)
        val dinner = db.categoryDao().observeByBook(book.id).first().first { it.name == "晚餐" }
        fun tx(id: String, bookId: String, cents: Long, note: String?, categoryId: String?) = TransactionEntity(
            id = id, bookId = bookId, type = TxType.EXPENSE, amountCents = cents,
            categoryId = categoryId, dateEpochMinute = 10_000, note = note,
            creatorDeviceId = "d", createdAt = 1L, updatedAt = 1L, deviceId = "d",
        )
        db.transactionDao().upsert(tx("a", book.id, 1600, "北苑餐厅", dinner.id))
        db.transactionDao().upsert(tx("b", book.id, 1454, "遮光布", null))
        db.transactionDao().upsert(tx("c", other.id, 9999, "北苑餐厅", null))

        fun search(q: String) = kotlinx.coroutines.runBlocking {
            db.transactionDao().observeSearchWithCategory(
                book.id, dev.penned.app.domain.SearchQuery.likePattern(q),
                dev.penned.app.domain.SearchQuery.amountCentsOrNull(q),
            ).first()
        }

        assertEquals(listOf("a"), search("北苑").map { it.id })            // 备注
        assertEquals(listOf("a"), search("晚餐").map { it.id })            // 二级分类名
        assertEquals(listOf("a"), search("食品餐饮").map { it.id })        // 一级分类名
        assertEquals(listOf("b"), search("14.54").map { it.id })           // 金额
        assertTrue(search("不存在的关键词").isEmpty())
        // 账本隔离：别的账本的同名备注搜不到
        assertEquals(1, search("北苑").size)
        // 通配符按字面：单个 % 不应命中全部
        assertTrue(search("%").isEmpty())
    }

    @Test
    fun `recent query resolves transfer target account name`() = runTest {
        // 首页转账行显示"转账 · 转入 X"：投影要带出 targetAccountName
        val book = DefaultSeed.seedIfEmpty(db, "device-a", now = 1000L)!!
        db.accountDao().upsert(
            dev.penned.app.data.db.entity.AccountEntity(
                id = "acc-out", bookId = book.id, name = "支付宝", type = dev.penned.app.domain.AccountType.DEBIT,
                createdAt = 1L, updatedAt = 1L, deviceId = "d",
            )
        )
        db.accountDao().upsert(
            dev.penned.app.data.db.entity.AccountEntity(
                id = "acc-in", bookId = book.id, name = "储蓄卡", type = dev.penned.app.domain.AccountType.DEBIT,
                createdAt = 1L, updatedAt = 1L, deviceId = "d",
            )
        )
        db.transactionDao().upsert(
            TransactionEntity(
                id = "tf", bookId = book.id, type = TxType.TRANSFER, amountCents = 8200,
                accountId = "acc-out", targetAccountId = "acc-in", dateEpochMinute = 1000,
                creatorDeviceId = "d", createdAt = 1L, updatedAt = 1L, deviceId = "d",
            )
        )
        val row = db.transactionDao().observeRecentWithCategory(book.id, 10).first().single()
        assertEquals("储蓄卡", row.targetAccountName)
        assertEquals(null, row.categoryName)
    }

    @Test
    fun `book soft delete cascades to its data and keeps other books`() = runTest {
        // M1 S3：删除=软删（账本 + 其流水/账户/分类/预算/导入批次）
        val bookA = DefaultSeed.seedIfEmpty(db, "device-a", now = 1000L)!!
        val bookB = dev.penned.app.data.db.entity.BookEntity(
            id = "book-b", name = "旅行账本", createdAt = 2000L, updatedAt = 2000L, deviceId = "device-a",
        )
        db.bookDao().upsert(bookB)
        val categoryA = db.categoryDao().observeByBook(bookA.id).first().first { it.name == "午餐" }
        val categoryB = dev.penned.app.data.db.entity.CategoryEntity(
            id = "cat-b", bookId = bookB.id, parentId = null, name = "旅行支出",
            type = CategoryType.EXPENSE, createdAt = 2000L, updatedAt = 2000L, deviceId = "device-a",
        )
        db.categoryDao().upsert(categoryB)
        db.accountDao().upsert(
            dev.penned.app.data.db.entity.AccountEntity(
                id = "acc-a", bookId = bookA.id, name = "现金", type = dev.penned.app.domain.AccountType.CASH,
                createdAt = 2000L, updatedAt = 2000L, deviceId = "device-a",
            )
        )
        db.accountDao().upsert(
            dev.penned.app.data.db.entity.AccountEntity(
                id = "acc-b", bookId = bookB.id, name = "旅费卡", type = dev.penned.app.domain.AccountType.DEBIT,
                createdAt = 2000L, updatedAt = 2000L, deviceId = "device-a",
            )
        )
        fun tx(id: String, bookId: String, categoryId: String) = TransactionEntity(
            id = id, bookId = bookId, type = TxType.EXPENSE, amountCents = 1600,
            categoryId = categoryId, dateEpochMinute = 10_000,
            creatorDeviceId = "d", createdAt = 1L, updatedAt = 1L, deviceId = "d",
        )
        db.transactionDao().upsert(tx("tx-a", bookA.id, categoryA.id))
        db.transactionDao().upsert(tx("tx-b", bookB.id, categoryB.id))
        db.budgetDao().upsert(
            dev.penned.app.data.db.entity.BudgetEntity(
                id = "budget-a", bookId = bookA.id, categoryId = null, amountCents = 100000,
                startEpochDay = 0, createdAt = 1L, updatedAt = 1L, deviceId = "d",
            )
        )
        db.importBatchDao().upsert(
            dev.penned.app.data.db.entity.ImportBatchEntity(
                id = "batch-a", bookId = bookA.id, fileName = "a.xls", count = 1, importedAt = 1L,
                createdAt = 1L, updatedAt = 1L, deviceId = "d",
            )
        )
        assertEquals(1, db.transactionDao().countInBook(bookA.id))

        val now = 5000L
        db.withTransaction {
            db.bookDao().softDelete(bookA.id, now)
            db.transactionDao().softDeleteByBook(bookA.id, now)
            db.accountDao().softDeleteByBook(bookA.id, now)
            db.categoryDao().softDeleteByBook(bookA.id, now)
            db.budgetDao().softDeleteByBook(bookA.id, now)
            db.importBatchDao().softDeleteByBook(bookA.id, now)
        }

        // A 全部隐藏
        assertEquals(listOf("book-b"), db.bookDao().observeAll().first().map { it.id })
        assertTrue(db.transactionDao().allInBook(bookA.id).isEmpty())
        assertTrue(db.accountDao().allInBook(bookA.id).isEmpty())
        assertTrue(db.categoryDao().allInBook(bookA.id).isEmpty())
        assertTrue(db.budgetDao().observeByBook(bookA.id).first().isEmpty())
        assertTrue(db.importBatchDao().observeAll().first().isEmpty())
        // B 不受影响
        assertEquals(1, db.transactionDao().allInBook(bookB.id).size)
        assertEquals(1, db.accountDao().allInBook(bookB.id).size)
        assertEquals(1, db.categoryDao().allInBook(bookB.id).size)
        // 软删不是物理删除（同步需要）
        assertEquals(2, db.bookDao().count())
    }

    @Test
    fun `budget dao enforces one-per-category lookup with nullable categoryId`() = runTest {
        val book = DefaultSeed.seedIfEmpty(db, "device-a", now = 1000L)!!

        fun budget(id: String, categoryId: String?, amount: Long) = dev.penned.app.data.db.entity.BudgetEntity(
            id = id, bookId = book.id, categoryId = categoryId, amountCents = amount,
            startEpochDay = 0, createdAt = 1, updatedAt = 1, deviceId = "d",
        )
        db.budgetDao().upsert(budget("b-total", null, 100000))
        db.budgetDao().upsert(budget("b-food", "food", 50000))

        // IS :categoryId 语义：null 精确匹配（不落到 food 行）
        assertEquals(100000L, db.budgetDao().getByCategory(book.id, null)?.amountCents)
        assertEquals(50000L, db.budgetDao().getByCategory(book.id, "food")?.amountCents)
        assertEquals(null, db.budgetDao().getByCategory(book.id, "other"))

        // 更新走 upsert 复用行
        db.budgetDao().upsert(budget("b-food", "food", 60000))
        assertEquals(listOf("b-total", "b-food"), db.budgetDao().observeByBook(book.id).first().map { it.id })

        // 软删后不出现
        db.budgetDao().softDelete("b-food", 2)
        assertEquals(listOf("b-total"), db.budgetDao().observeByBook(book.id).first().map { it.id })
    }
}
