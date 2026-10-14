package dev.penned.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.data.db.entity.TxTemplateEntity
import dev.penned.app.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 模板 DAO（M3-5）：列表带分类/账户/标签名、标签全量替换幂等、软删模板连关系、不碰流水。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TemplateDaoTest {

    private lateinit var db: PennedDatabase
    private val bookId = "book-tpl"

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

    private fun template(
        id: String,
        name: String = "午饭",
        sortOrder: Int = 0,
        deleted: Boolean = false,
        book: String = bookId,
    ) = TxTemplateEntity(
        id = id, bookId = book, name = name, type = TxType.EXPENSE, amountCents = 1650,
        categoryId = "c1", accountId = "a1", note = "午饭", sortOrder = sortOrder,
        createdAt = 1L, updatedAt = 1L, deleted = deleted, deviceId = "d",
    )

    private fun tag(id: String, name: String) = TagEntity(
        id = id, bookId = bookId, parentId = null, name = name, sortOrder = 0,
        createdAt = 1L, updatedAt = 1L, deviceId = "d",
    )

    @Test
    fun `列表行带分类名账户名与标签名，按 sortOrder 排`() = runTest {
        db.categoryDao().upsert(
            dev.penned.app.data.db.entity.CategoryEntity(
                id = "c1", bookId = bookId, name = "食品餐饮", type = dev.penned.app.domain.CategoryType.EXPENSE,
                createdAt = 1L, updatedAt = 1L, deviceId = "d",
            )
        )
        db.accountDao().upsert(
            dev.penned.app.data.db.entity.AccountEntity(
                id = "a1", bookId = bookId, name = "微信", type = dev.penned.app.domain.AccountType.DEBIT,
                createdAt = 1L, updatedAt = 1L, deviceId = "d",
            )
        )
        db.templateDao().upsert(template("t1", sortOrder = 1))
        db.templateDao().upsert(template("t2", name = "早饭", sortOrder = 0))
        db.tagDao().upsert(tag("g1", "报销中"))
        db.tagDao().upsert(tag("g2", "出差"))
        db.templateDao().setTagsForTemplate("t1", listOf("g1", "g2"))

        val rows = db.templateDao().observeByBook(bookId).first()
        assertEquals(listOf("t2", "t1"), rows.map { it.id })
        val first = rows.first { it.id == "t1" }
        assertEquals("食品餐饮", first.categoryName)
        assertEquals("微信", first.accountName)
        assertEquals("报销中、出差", first.tagNames)
        assertNull(rows.first { it.id == "t2" }.tagNames)
    }

    @Test
    fun `标签全量替换幂等；删模板摘关系且不碰流水`() = runTest {
        db.templateDao().upsert(template("t1"))
        db.tagDao().upsert(tag("g1", "报销中"))
        db.tagDao().upsert(tag("g2", "出差"))

        db.templateDao().setTagsForTemplate("t1", listOf("g1", "g2", "g1"))
        // 排序按 sortOrder 再按名字（两个标签同序 → 名字序），只断言集合与数量
        assertEquals(listOf("g1", "g2"), db.templateDao().tagsForTemplate("t1").map { it.id }.sorted())
        // 再设一遍同样的（幂等）
        db.templateDao().setTagsForTemplate("t1", listOf("g2"))
        assertEquals(listOf("g2"), db.templateDao().tagsForTemplate("t1").map { it.id })

        // 手工记一笔（与模板无关）：删模板不能碰它
        db.transactionDao().upsert(
            dev.penned.app.data.db.entity.TransactionEntity(
                id = "tx1", bookId = bookId, type = TxType.EXPENSE, amountCents = 100,
                dateEpochMinute = 1, creatorDeviceId = "d", createdAt = 1L, updatedAt = 1L, deviceId = "d",
            )
        )
        db.templateDao().deleteTemplate("t1", now = 2L)
        assertEquals(0, db.templateDao().observeByBook(bookId).first().size)
        // get 不滤 deleted（编辑页口径）：行还在，只是列表里不出现
        assertEquals(true, db.templateDao().get("t1")?.deleted)
        assertEquals(1, db.transactionDao().allInBook(bookId).size)
        assertEquals(0, db.templateDao().countInBook(bookId))
        // 标签关系**留着**（撤销删除时标签跟着回来；标签自己软删后照样读不到）
        assertEquals(listOf("g2"), db.templateDao().tagsForTemplate("t1").map { it.id })
    }

    @Test
    fun `软删的模板不进列表，别的账本不进列表`() = runTest {
        db.templateDao().upsert(template("t1"))
        db.templateDao().upsert(template("t2", deleted = true))
        db.templateDao().upsert(template("t3", book = "book-other"))
        assertEquals(listOf("t1"), db.templateDao().observeByBook(bookId).first().map { it.id })
    }
}
