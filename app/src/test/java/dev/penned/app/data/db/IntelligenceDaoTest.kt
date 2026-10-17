package dev.penned.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.db.entity.KeywordRuleEntity
import dev.penned.app.data.db.entity.PendingEntryEntity
import dev.penned.app.domain.PendingKind
import dev.penned.app.domain.PendingStatus
import dev.penned.app.domain.PendingType
import dev.penned.app.domain.RuleTargetType
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
 * M4-0 的两张新表：关键词规则（排序 / 启停 / 软删 / 上下移）与候选池
 * （标记入账 / 丢弃 / **清空已处理 = 硬删**）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class IntelligenceDaoTest {

    private lateinit var db: PennedDatabase
    private val bookId = "book-m4"
    private val deviceId = "d-test"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PennedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun rule(id: String, keyword: String, priority: Int, createdAt: Long = priority.toLong()) =
        KeywordRuleEntity(
            id = id, bookId = bookId, keyword = keyword, targetType = RuleTargetType.CATEGORY,
            targetId = "cat-$id", priority = priority, enabled = true,
            createdAt = createdAt, updatedAt = createdAt, deviceId = deviceId,
        )

    private fun entry(
        id: String,
        kind: PendingKind = PendingKind.NOTIFICATION,
        status: PendingStatus = PendingStatus.PENDING,
        amountCents: Long? = 1250L,
        occurredAtEpochMinute: Long? = 100L,
        createdAt: Long = 1L,
    ) = PendingEntryEntity(
        id = id, kind = kind, sourceApp = "com.tencent.mm", rawText = "微信支付 12.50",
        type = PendingType.EXPENSE, amountCents = amountCents, occurredAtEpochMinute = occurredAtEpochMinute,
        merchant = "某商家", status = status, createdAt = createdAt, updatedAt = createdAt, deviceId = deviceId,
    )

    // ---- keyword_rules ----

    @Test
    fun `关键词规则按优先级排序并可启停与软删`() = runTest {
        val dao = db.keywordRuleDao()
        dao.upsert(rule("b", "肯打鸡", priority = 1))
        dao.upsert(rule("a", "午饭", priority = 0))

        assertEquals(listOf("a", "b"), dao.observeByBook(bookId).first().map { it.id })

        dao.setEnabled("a", false, now = 2L)
        assertTrue(dao.get("a")!!.enabled == false)
        assertTrue(dao.allInBook(bookId).first { it.id == "a" }.enabled == false)

        dao.softDelete("b", now = 3L)
        assertEquals(listOf("a"), dao.allInBook(bookId).map { it.id })
        assertEquals(1, dao.countInBook(bookId))
    }

    @Test
    fun `上下移交换优先级`() = runTest {
        val dao = db.keywordRuleDao()
        dao.upsert(rule("a", "午饭", priority = 0))
        dao.upsert(rule("b", "晚饭", priority = 1))
        dao.upsert(rule("c", "夜宵", priority = 2))

        dao.swapPriority("b", "c", now = 9L)
        assertEquals(listOf("a", "c", "b"), dao.allInBook(bookId).map { it.id })

        dao.swapPriority("a", "c", now = 10L)
        assertEquals(listOf("c", "a", "b"), dao.allInBook(bookId).map { it.id })
    }

    @Test
    fun `新规则排在最后`() = runTest {
        val dao = db.keywordRuleDao()
        assertEquals(0, dao.nextPriority(bookId))
        dao.upsert(rule("a", "午饭", priority = 0))
        dao.upsert(rule("b", "晚饭", priority = 1))
        assertEquals(2, dao.nextPriority(bookId))
    }

    @Test
    fun `insertAtEnd 在事务里定优先级_连续新增不会撞车`() = runTest {
        val dao = db.keywordRuleDao()
        // 三条连着插（不等前一条落库）：优先级必须依次是 0/1/2 —— 两步写法会全拿 0（本片实踩）
        dao.insertAtEnd(rule("a", "肯打鸡", priority = 0))
        dao.insertAtEnd(rule("b", "午饭", priority = 0))
        dao.insertAtEnd(rule("c", "晚饭", priority = 0))
        assertEquals(listOf(0, 1, 2), dao.allInBook(bookId).map { it.priority })
        assertEquals(listOf("a", "b", "c"), dao.allInBook(bookId).map { it.id })
    }

    @Test
    fun `关键词按名匹配到账本内目标`() = runTest {
        val dao = db.keywordRuleDao()
        dao.upsert(
            rule("a", "肯打鸡", priority = 0).copy(
                targetType = RuleTargetType.TAG, targetId = "tag-fast-food",
            )
        )
        val stored = dao.get("a")!!
        assertEquals(RuleTargetType.TAG, stored.targetType)
        assertEquals("tag-fast-food", stored.targetId)
    }

    // ---- pending_entries ----

    @Test
    fun `候选池按状态观察与计数`() = runTest {
        val dao = db.pendingEntryDao()
        dao.upsert(entry("p1", createdAt = 1L))
        dao.upsert(entry("p2", createdAt = 2L))
        dao.upsert(entry("p3", status = PendingStatus.DISCARDED, createdAt = 3L))

        assertEquals(listOf("p2", "p1"), dao.observeByStatus().first().map { it.id })
        assertEquals(2, dao.observeCount().first())
        assertEquals(2, dao.countPending())
        assertEquals(1, dao.listByKind(PendingKind.NOTIFICATION, PendingStatus.DISCARDED).size)
    }

    @Test
    fun `入账回填 txId 并标 ACCEPTED`() = runTest {
        val dao = db.pendingEntryDao()
        dao.upsert(entry("p1"))
        dao.markAccepted("p1", txId = "tx-9", now = 50L)
        val row = dao.getActive("p1")!!
        assertEquals(PendingStatus.ACCEPTED, row.status)
        assertEquals("tx-9", row.txId)
        // 原文保留（D104：供回看"机器当时读到了什么"，但不做改痕）
        assertEquals("微信支付 12.50", row.rawText)
        assertEquals(0, dao.countPending())
    }

    @Test
    fun `丢弃与全部丢弃`() = runTest {
        val dao = db.pendingEntryDao()
        dao.upsert(entry("p1"))
        dao.upsert(entry("p2"))
        dao.discard("p1", now = 5L)
        assertEquals(PendingStatus.DISCARDED, dao.getActive("p1")!!.status)
        dao.discardAllPending(now = 6L)
        assertEquals(0, dao.countPending())
        // 丢掉的行**不是删掉**（还等着「清空已处理」），只是不再出现在待确认里
        assertEquals(2, dao.observeByStatus(PendingStatus.DISCARDED).first().size)
        assertEquals(2, dao.listByKind(PendingKind.NOTIFICATION, PendingStatus.DISCARDED).size)
    }

    @Test
    fun `清空已处理是硬删 不是软删`() = runTest {
        val dao = db.pendingEntryDao()
        dao.upsert(entry("keep", status = PendingStatus.PENDING))
        dao.upsert(entry("done", status = PendingStatus.ACCEPTED))
        dao.upsert(entry("dropped", status = PendingStatus.DISCARDED))

        assertEquals(2, dao.clearProcessed())

        // 待确认那条还在
        assertTrue(dao.getActive("keep") != null)
        // 已处理的两条**真的没了**（不是 deleted = 1）——全项目唯一一处硬删例外（D104）
        assertNull(dao.getActive("done"))
        assertNull(dao.getActive("dropped"))
        assertEquals(listOf(1), db.query("SELECT COUNT(*) FROM pending_entries", null).use { c ->
            c.moveToFirst(); listOf(c.getInt(0))
        })
    }

    @Test
    fun `候选池金额与时间可空`() = runTest {
        val dao = db.pendingEntryDao()
        dao.upsert(entry("p1", amountCents = null, occurredAtEpochMinute = null))
        val row = dao.getActive("p1")!!
        assertNull(row.amountCents)
        assertNull(row.occurredAtEpochMinute)
        assertEquals(PendingType.EXPENSE, row.type)
    }

    @Test
    fun `截图候选与通知候选可分`() = runTest {
        val dao = db.pendingEntryDao()
        dao.upsert(entry("shot", kind = PendingKind.SCREENSHOT))
        dao.upsert(entry("note", kind = PendingKind.NOTIFICATION))
        assertEquals(listOf("shot"), dao.listByKind(PendingKind.SCREENSHOT).map { it.id })
        assertEquals(listOf("note"), dao.listByKind(PendingKind.NOTIFICATION).map { it.id })
    }

    // ---- 导入查重（M4-5 用，M4-0 先钉住口径）----

    @Test
    fun `externalIdsInBook 只回本账本未软删的命中`() = runTest {
        val txDao = db.transactionDao()
        txDao.upsert(
            dev.penned.app.data.db.entity.TransactionEntity(
                id = "t1", bookId = bookId, type = dev.penned.app.domain.TxType.EXPENSE, amountCents = 1200,
                dateEpochMinute = 100L, externalId = "WX-1", creatorDeviceId = deviceId,
                createdAt = 1L, updatedAt = 1L, deviceId = deviceId,
            )
        )
        txDao.upsert(
            dev.penned.app.data.db.entity.TransactionEntity(
                id = "t2", bookId = bookId, type = dev.penned.app.domain.TxType.EXPENSE, amountCents = 800,
                dateEpochMinute = 200L, externalId = "WX-2", deleted = true, creatorDeviceId = deviceId,
                createdAt = 1L, updatedAt = 1L, deviceId = deviceId,
            )
        )
        txDao.upsert(
            dev.penned.app.data.db.entity.TransactionEntity(
                id = "t3", bookId = "other-book", type = dev.penned.app.domain.TxType.EXPENSE, amountCents = 500,
                dateEpochMinute = 300L, externalId = "WX-3", creatorDeviceId = deviceId,
                createdAt = 1L, updatedAt = 1L, deviceId = deviceId,
            )
        )

        val hits = txDao.externalIdsInBook(bookId, listOf("WX-1", "WX-2", "WX-3", "WX-4"))
        assertEquals(listOf("WX-1"), hits)
    }
}
