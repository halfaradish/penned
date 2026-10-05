package dev.penned.app.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.SettingsRepository
import dev.penned.app.data.db.PennedDatabase
import dev.penned.app.data.seed.DefaultSeed
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class ImportRepositoryTest {

    private lateinit var db: PennedDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repository: ImportRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PennedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        repository = ImportRepository(db, settings, deviceId = "device-test")
    }

    /** 种子 + 把当前账本固定为种子账本（DataStore 单例在同类测试间共享，必须显式钉住）。 */
    private suspend fun seedBook() = dev.penned.app.data.seed.DefaultSeed.seedIfEmpty(db, "device-test", 1L)!!.also {
        settings.setCurrentBook(it.id)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun fixture(name: String): ByteArray =
        javaClass.classLoader.getResourceAsStream(name)!!.readBytes()

    @Test
    fun `import yimu xls creates batch transactions and missing categories`() = runTest {
        val book = seedBook()
        val beforeCategories = db.categoryDao().allInBook(book.id).size

        val result = repository.importYimuXls("yimu_sample_mini.xls", fixture("yimu_sample_mini.xls"))

        assertEquals(8, result.imported)
        assertEquals(0, result.skipped)

        val txs = db.transactionDao().allInBook(book.id)
        assertEquals(8, txs.size)
        assertTrue(txs.all { it.source == TxSource.IMPORT && it.importBatchId == result.batchId })

        // 类型与金额（支出恒正，收入 3500.00）
        assertEquals(7, txs.count { it.type == TxType.EXPENSE })
        val income = txs.single { it.type == TxType.INCOME }
        assertEquals(350000L, income.amountCents)

        // 分类：种子已有"食品餐饮/晚餐"直接匹配；自定义"班费"挂到"其他"下新建
        val categories = db.categoryDao().allInBook(book.id)
        assertTrue(categories.size > beforeCategories)
        val banfei = categories.first { it.name == "班费" }
        val qita = categories.first { it.name == "其他" }
        assertEquals(qita.id, banfei.parentId)

        // 全额退款行金额为 0、退款列独立
        val fullRefund = txs.single { it.note == "耳机" }
        assertEquals(0L, fullRefund.amountCents)
        assertEquals(1454L, fullRefund.refundCents)

        // 账户：唯一非空账户名"现金"按名创建
        val accounts = db.accountDao().allInBook(book.id)
        assertEquals(listOf("现金"), accounts.map { it.name })
        val withAccount = txs.single { it.note == "水果捞" }
        assertEquals(accounts[0].id, withAccount.accountId)

        // 批次记录
        val batch = db.importBatchDao().get(result.batchId)
        assertNotNull(batch)
        assertEquals(8, batch!!.count)
        assertEquals("yimu_sample_mini.xls", batch.fileName)
    }

    @Test
    fun `import into named book matches seed book`() = runTest {
        val book = seedBook()
        repository.importYimuXls("yimu_sample_mini.xls", fixture("yimu_sample_mini.xls"))
        // fixture 账本列全为"日常账本"，与种子账本同名 → 不新建账本
        assertEquals(1, db.bookDao().observeAll().first().size)
        assertEquals(book.id, db.transactionDao().allInBook(book.id).first().bookId)
    }

    @Test
    fun `delete batch soft deletes its transactions only`() = runTest {
        val book = seedBook()
        val result = repository.importYimuXls("yimu_sample_mini.xls", fixture("yimu_sample_mini.xls"))
        // 手工再记一笔，不应被批次删除波及
        db.transactionDao().upsert(
            dev.penned.app.data.db.entity.TransactionEntity(
                id = "manual-1", bookId = book.id, type = TxType.EXPENSE, amountCents = 100,
                dateEpochMinute = 10, creatorDeviceId = "d", createdAt = 1, updatedAt = 1, deviceId = "d",
            )
        )

        repository.deleteBatch(result.batchId)

        // 批次流水全部软删；手工一笔保留
        val remaining = db.transactionDao().allInBook(book.id)
        assertEquals(listOf("manual-1"), remaining.map { it.id })
        assertTrue(db.importBatchDao().observeAll().first().isEmpty()) // 批次列表不再显示
    }

    @Test
    fun `csv export import round trip through repository`() = runTest {
        val book = seedBook()
        repository.importYimuXls("yimu_sample_mini.xls", fixture("yimu_sample_mini.xls"))

        val csv = repository.exportCurrentBookCsv()
        assertTrue(csv.contains("dateEpochMinute"))
        assertTrue(csv.contains("dateEpochMinute"))

        // 清空后重灌
        db.clearAllTables()
        val book2 = DefaultSeed.seedIfEmpty(db, "device-test", 10L)!!.also { settings.setCurrentBook(it.id) }
        val result = repository.importCsv("backup.csv", csv)
        assertEquals(8, result.imported)
        val txs = db.transactionDao().allInBook(book2.id)
        assertEquals(8, txs.size)
        // 金额与备注保真
        assertTrue(txs.any { it.amountCents == 1600L && it.note == "超市晚餐" })
    }
}
