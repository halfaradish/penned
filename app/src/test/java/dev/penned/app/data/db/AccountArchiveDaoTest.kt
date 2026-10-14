package dev.penned.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.AccountType
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

/**
 * 资产模块三件的数据侧（M3-9）：停用（不影响余额与净资产）、流水迁移（两端都搬、迁完可删）、自定义分组。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountArchiveDaoTest {

    private lateinit var db: PennedDatabase
    private val bookId = "book-acc"

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, PennedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val now = 1L
        db.accountDao().upsert(
            AccountEntity(id = "a1", bookId = bookId, name = "微信", type = AccountType.DEBIT, initialBalanceCents = 10_000, createdAt = now, updatedAt = now, deviceId = "d")
        )
        db.accountDao().upsert(
            AccountEntity(id = "a2", bookId = bookId, name = "现金", type = AccountType.CASH, createdAt = now, updatedAt = now, deviceId = "d")
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun tx(id: String, type: TxType, cents: Long, accountId: String?, targetAccountId: String? = null) =
        TransactionEntity(
            id = id, bookId = bookId, type = type, amountCents = cents,
            accountId = accountId, targetAccountId = targetAccountId, dateEpochMinute = 1,
            creatorDeviceId = "d", createdAt = 1L, updatedAt = 1L, deviceId = "d",
        )

    @Test
    fun `停用：余额与净资产照旧，只是标记变了`() = runTest {
        db.transactionDao().upsert(tx("e1", TxType.EXPENSE, 2_000, "a1"))
        db.accountDao().setArchived("a1", archived = true, now = 2L)

        val rows = db.accountDao().observeWithBalance(bookId).first()
        val wechat = rows.first { it.id == "a1" }
        assertTrue("标记为已停用", wechat.archived)
        assertEquals("余额照算（100 - 20）", 8_000L, wechat.balanceCents)
        assertEquals("净资产照旧把停用账户算进去", 8_000L, rows.sumOf { it.balanceCents })

        db.accountDao().setArchived("a1", archived = false, now = 3L)
        assertEquals(false, db.accountDao().observeWithBalance(bookId).first().first { it.id == "a1" }.archived)
    }

    @Test
    fun `迁移：accountId 与 targetAccountId 两端都搬，原账户流水数归零`() = runTest {
        db.transactionDao().upsert(tx("e1", TxType.EXPENSE, 2_000, "a1"))
        db.transactionDao().upsert(tx("i1", TxType.INCOME, 5_000, "a1"))
        db.transactionDao().upsert(tx("t1", TxType.TRANSFER, 3_000, "a1", targetAccountId = "a2"))
        db.transactionDao().upsert(tx("t2", TxType.TRANSFER, 1_000, "a2", targetAccountId = "a1"))
        // 迁移前：微信名下 4 笔（3 出 1 入）
        assertEquals(4, db.transactionDao().countByAccount("a1"))

        val now = 9L
        db.accountDao().migrateOutgoing("a1", "a2", now)
        db.accountDao().migrateIncoming("a1", "a2", now)

        assertEquals("迁完原账户可删", 0, db.transactionDao().countByAccount("a1"))
        val txs = db.transactionDao().allInBook(bookId).associateBy { it.id }
        assertEquals("a2", txs.getValue("e1").accountId)
        assertEquals("a2", txs.getValue("i1").accountId)
        assertEquals("a2", txs.getValue("t1").accountId)
        assertEquals("a2", txs.getValue("t2").targetAccountId)
        assertEquals("迁移也刷新 updatedAt", 9L, txs.getValue("e1").updatedAt)

        // 余额联动：微信 100 - 20 + 50 = 130 → 全搬走后 0；现金接住（30 + 50 - 30 - 10 + 10 = 50）
        val rows = db.accountDao().observeWithBalance(bookId).first().associateBy { it.id }
        // 初始余额由 VM 的迁移一并并过去（见 migrateTo）：DAO 测试里手工模拟这一步
        db.accountDao().upsert(db.accountDao().get("a2")!!.copy(initialBalanceCents = 10_000L))
        db.accountDao().upsert(db.accountDao().get("a1")!!.copy(initialBalanceCents = 0L))
        val after = db.accountDao().observeWithBalance(bookId).first().associateBy { it.id }
        assertEquals("原账户余额归零", 0L, after.getValue("a1").balanceCents)
        // a2 接住初始余额 100 + 全部流水（两笔转账在 a2 内部自成闭环、净额为 0）
        assertEquals("接收账户接住全部", 13_000L, after.getValue("a2").balanceCents)
        assertEquals("总额不变", 13_000L, after.values.sumOf { it.balanceCents })
        assertEquals("迁移不改变净资产（只换账户）", rows.values.sumOf { it.balanceCents }, after.values.sumOf { it.balanceCents })
    }

    @Test
    fun `迁移不碰软删流水`() = runTest {
        db.transactionDao().upsert(tx("gone", TxType.EXPENSE, 2_000, "a1").copy(deleted = true))
        db.transactionDao().upsert(tx("live", TxType.EXPENSE, 1_000, "a1"))
        db.accountDao().migrateOutgoing("a1", "a2", now = 2L)
        val txs = db.transactionDao().allInBook(bookId)
        assertEquals(listOf("live"), txs.map { it.id })
        assertEquals("a2", txs.first().accountId)
        // 软删那条留在原地（撤销删除时还挂在原账户上——它的账本来就是那么记的）；
        // countByAccount 只看未删的，所以这里直接读那一行
        assertEquals("a1", db.transactionDao().get("gone")!!.accountId)
        assertEquals("未删的流水数为 0（原账户可删）", 0, db.transactionDao().countByAccount("a1"))
    }

    @Test
    fun `自定义分组：groupName 落库与清除`() = runTest {
        db.accountDao().setGroupName("a1", "旅行基金", now = 2L)
        assertEquals("旅行基金", db.accountDao().get("a1")!!.groupName)
        db.accountDao().setGroupName("a1", null, now = 3L)
        assertEquals(null, db.accountDao().get("a1")!!.groupName)
    }
}
