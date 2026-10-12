package dev.penned.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 标签 DAO（M3-1）：两级 / 软删连带 / 关系表整批替换的幂等 / 隐藏 / 用量计数 / 按标签查流水。
 *
 * 关系表 `transaction_tags` 没有软删字段（v1 建表先例），所以"换标签"是整批删+插：
 * 重复保存同一组标签不能把关系行越攒越多（幂等），这一条单独钉一个用例。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TagDaoTest {

    private lateinit var db: PennedDatabase
    private val bookId = "book-tags"

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

    private fun tag(id: String, name: String, parentId: String? = null, sortOrder: Int = 0, hidden: Boolean = false) =
        TagEntity(
            id = id,
            bookId = bookId,
            parentId = parentId,
            name = name,
            sortOrder = sortOrder,
            hidden = hidden,
            createdAt = 1L,
            updatedAt = 1L,
            deviceId = "test",
        )

    private fun tx(id: String, minute: Long) = TransactionEntity(
        id = id,
        bookId = bookId,
        type = TxType.EXPENSE,
        amountCents = 100,
        dateEpochMinute = minute,
        creatorDeviceId = "test",
        createdAt = 1L,
        updatedAt = 1L,
        deviceId = "test",
    )

    private fun refCount(): Int {
        val c = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transaction_tags")
        c.moveToFirst()
        val n = c.getInt(0)
        c.close()
        return n
    }

    @Test
    fun `observeByBook returns two levels and drops soft deleted`() = runTest {
        val dao = db.tagDao()
        dao.upsert(tag("p1", "出差", sortOrder = 0))
        dao.upsert(tag("c1", "交通", parentId = "p1", sortOrder = 1))
        dao.upsert(tag("c2", "住宿", parentId = "p1", sortOrder = 2))
        dao.upsert(tag("p2", "报销中", sortOrder = 3))

        val all = dao.observeByBook(bookId).first()
        assertEquals(4, all.size)
        assertEquals(listOf("交通", "住宿"), all.filter { it.parentId == "p1" }.map { it.name })

        // 软删一个二级：列表里消失，其它不动
        dao.softDelete("c1", now = 2L)
        assertEquals(listOf("出差", "住宿", "报销中"), dao.observeByBook(bookId).first().map { it.name })
    }

    @Test
    fun `hidden tag stays in list and keeps refs`() = runTest {
        val dao = db.tagDao()
        dao.upsert(tag("p1", "出差"))
        db.transactionDao().upsert(tx("t1", 100))
        dao.setTagsForTx("t1", listOf("p1"))

        dao.setHidden("p1", hidden = true, now = 2L)

        val stored = dao.get("p1")!!
        assertTrue(stored.hidden)
        // 隐藏只影响选择器（UI 层过滤），数据层照旧：仍能读到、流水关联还在
        assertEquals(1, dao.observeByBook(bookId).first().size)
        assertEquals(listOf("出差"), dao.tagsForTx("t1").map { it.name })
        assertEquals(1, dao.usageCount("p1"))
    }

    @Test
    fun `setTagsForTx replaces refs and is idempotent`() = runTest {
        val dao = db.tagDao()
        dao.upsert(tag("p1", "出差"))
        dao.upsert(tag("c1", "交通", parentId = "p1"))
        dao.upsert(tag("p2", "报销中"))
        db.transactionDao().upsert(tx("t1", 100))

        dao.setTagsForTx("t1", listOf("p1", "c1"))
        assertEquals(2, refCount())

        // 同一组再存一次：不该攒出重复关系行（幂等）
        dao.setTagsForTx("t1", listOf("p1", "c1"))
        assertEquals(2, refCount())

        // 重复 id 去重
        dao.setTagsForTx("t1", listOf("p1", "p1"))
        assertEquals(1, refCount())

        // 换一批：只留新的
        dao.setTagsForTx("t1", listOf("p2"))
        assertEquals(listOf("报销中"), dao.tagsForTx("t1").map { it.name })
        assertEquals(1, refCount())

        // 清空
        dao.setTagsForTx("t1", emptyList())
        assertEquals(0, refCount())
    }

    @Test
    fun `deleteTag clears refs of the tag and its children`() = runTest {
        val dao = db.tagDao()
        dao.upsert(tag("p1", "出差"))
        dao.upsert(tag("c1", "交通", parentId = "p1"))
        db.transactionDao().upsert(tx("t1", 100))
        db.transactionDao().upsert(tx("t2", 200))
        dao.setTagsForTx("t1", listOf("p1", "c1"))
        dao.setTagsForTx("t2", listOf("c1"))
        assertEquals(3, refCount())

        dao.deleteTag("p1", now = 2L)

        // 一级 + 二级一起软删，关系行全部摘掉；流水本身不动
        assertTrue(dao.get("p1")!!.deleted)
        assertTrue(dao.get("c1")!!.deleted)
        assertEquals(0, refCount())
        assertEquals(2, db.transactionDao().all().size)
        assertTrue(dao.observeByBook(bookId).first().isEmpty())
    }

    @Test
    fun `usageCount skips soft deleted transactions`() = runTest {
        val dao = db.tagDao()
        dao.upsert(tag("p1", "出差"))
        db.transactionDao().upsert(tx("t1", 100))
        db.transactionDao().upsert(tx("t2", 200))
        dao.setTagsForTx("t1", listOf("p1"))
        dao.setTagsForTx("t2", listOf("p1"))
        assertEquals(2, dao.usageCount("p1"))

        db.transactionDao().softDelete("t1", now = 3L)
        assertEquals(1, dao.usageCount("p1"))
    }

    @Test
    fun `observeByTags returns each transaction once ordered by time`() = runTest {
        val dao = db.tagDao()
        dao.upsert(tag("p1", "出差"))
        dao.upsert(tag("c1", "交通", parentId = "p1"))
        db.transactionDao().upsert(tx("t1", 100))
        db.transactionDao().upsert(tx("t2", 200))
        dao.setTagsForTx("t1", listOf("p1", "c1"))   // 两个标签命中同一条
        dao.setTagsForTx("t2", listOf("c1"))

        // 同时按一级与二级查：t1 只出现一次（GROUP BY），且按时间倒序
        val rows = dao.observeByTags(listOf("p1", "c1")).first()
        assertEquals(listOf("t2", "t1"), rows.map { it.id })

        // 软删的流水不出现
        db.transactionDao().softDelete("t2", now = 3L)
        assertEquals(listOf("t1"), dao.observeByTags(listOf("c1")).first().map { it.id })
        assertFalse(dao.observeByTags(listOf("p1")).first().isEmpty())
    }
}
