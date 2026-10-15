package dev.penned.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.data.db.entity.TransactionTagRefEntity
import dev.penned.app.domain.AccountType
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
 * 统计模块的两条新查询（M3-14）：
 * ① 标签聚合（一笔挂多个标签各算一次、软删标签不进榜、不计收支剔除、只看指定类型）；
 * ② 资产走势期初（可见账户初始余额 + 窗口前净变化，转账只减手续费）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsModuleDaoTest {

    private lateinit var db: PennedDatabase
    private val bookId = "book-m314"

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

    private suspend fun seedTag(id: String, name: String, deleted: Boolean = false) {
        db.tagDao().upsert(
            TagEntity(
                id = id, bookId = bookId, name = name, createdAt = 1L, updatedAt = 1L,
                deleted = deleted, deviceId = "t",
            )
        )
    }

    private suspend fun seedTx(
        id: String,
        type: TxType,
        cents: Long,
        minute: Long,
        fee: Long = 0,
        excludeFromStats: Boolean = false,
        accountId: String? = "a1",
    ) {
        db.transactionDao().upsert(
            TransactionEntity(
                id = id, bookId = bookId, type = type, amountCents = cents, accountId = accountId,
                dateEpochMinute = minute, feeCents = fee, excludeFromStats = excludeFromStats,
                creatorDeviceId = "t", createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
    }

    /** `setTagsForTx` 是**整批替换**（M3-5 的口径）：一笔要挂多个标签就一次传进去。 */
    private suspend fun link(txId: String, vararg tagIds: String) {
        db.tagDao().setTagsForTx(txId, tagIds.toList())
    }

    @Test
    fun `标签聚合：多标签各算一次、软删标签不进榜、不计收支剔除、按类型过滤`() = runTest {
        seedTag("g1", "出差")
        seedTag("g2", "报销")
        seedTag("g3", "旧的", deleted = true)
        seedTx("t1", TxType.EXPENSE, 10_000L, minute = 100L)
        seedTx("t2", TxType.EXPENSE, 5_000L, minute = 200L)
        seedTx("t3", TxType.INCOME, 30_000L, minute = 300L)
        seedTx("t4", TxType.EXPENSE, 9_999L, minute = 400L, excludeFromStats = true)
        link("t1", "g1", "g2")   // 同一笔挂两个标签
        link("t2", "g1", "g3")   // g3 是软删标签：不进榜
        link("t3", "g1")
        link("t4", "g1")

        val expense = db.transactionDao().observeTagTotals(bookId, 0L, 999L, TxType.EXPENSE).first()
        // 支出侧：出差 150.00（t1+t2）、报销 100.00（t1）；软删标签与不计收支的都不算
        assertEquals(listOf("出差" to 15_000L, "报销" to 10_000L), expense.map { it.name to it.cents })
        assertEquals(2, expense.first().count)

        // 收入侧只看收入：出差 300.00
        val income = db.transactionDao().observeTagTotals(bookId, 0L, 999L, TxType.INCOME).first()
        assertEquals(listOf("出差" to 30_000L), income.map { it.name to it.cents })

        // 窗口外的不算
        assertEquals(emptyList<String>(), db.transactionDao().observeTagTotals(bookId, 500L, 999L, TxType.EXPENSE).first().map { it.name })
    }

    @Test
    fun `资产走势期初：可见账户初始余额 + 窗口前净变化（转账只减手续费）`() = runTest {
        db.accountDao().upsert(
            AccountEntity(
                id = "a1", bookId = bookId, name = "现金", type = AccountType.CASH,
                initialBalanceCents = 100_000L, createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        db.accountDao().upsert(
            AccountEntity(
                id = "a2", bookId = "other", name = "别的账本的卡", type = AccountType.DEBIT,
                initialBalanceCents = 999_999L, createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        // 窗口前：收入 300、支出 120、转账手续费 2
        seedTx("p1", TxType.INCOME, 30_000L, minute = 10L)
        seedTx("p2", TxType.EXPENSE, 12_000L, minute = 20L)
        seedTx("p3", TxType.TRANSFER, 5_000L, minute = 30L, fee = 200L)
        // 窗口内：不算进期初
        seedTx("w1", TxType.EXPENSE, 7_777L, minute = 100L)
        // 无账户的流水（D005）：不改变任何账户余额，进不了账户合计
        seedTx("p4", TxType.EXPENSE, 50_000L, minute = 40L, accountId = null)

        val opening = db.transactionDao().observeOpeningAssetCents(bookId, beforeMinute = 50L).first()
        // 100_000（本账本账户初始）+ 30_000 − 12_000 − 200 = 117_800；别的账本的账户不算
        assertEquals(117_800L, opening)
    }
}
