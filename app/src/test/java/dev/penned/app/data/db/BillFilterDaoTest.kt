package dev.penned.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.db.entity.BillFilterEntity
import dev.penned.app.domain.BillFilterCodec
import dev.penned.app.domain.TxFilter
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

/** 账单组合 DAO（M3-8）：排序、重名判定、重命名、软删（不碰流水）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BillFilterDaoTest {

    private lateinit var db: PennedDatabase
    private val bookId = "book-bf"

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

    private fun entity(id: String, name: String, sortOrder: Int = 0, deleted: Boolean = false, book: String = bookId) =
        BillFilterEntity(
            id = id, bookId = book, name = name, type = TxType.EXPENSE, categoryIds = "c1,c2",
            sortOrder = sortOrder, createdAt = 1L, updatedAt = 1L, deleted = deleted, deviceId = "d",
        )

    @Test
    fun `列表按 sortOrder 排，软删与别账本不出现`() = runTest {
        db.billFilterDao().upsert(entity("f2", "待报销", sortOrder = 1))
        db.billFilterDao().upsert(entity("f1", "上月餐饮", sortOrder = 0))
        db.billFilterDao().upsert(entity("f3", "删掉的", deleted = true))
        db.billFilterDao().upsert(entity("f4", "别的账本", book = "other"))

        val rows = db.billFilterDao().observeByBook(bookId).first()
        assertEquals(listOf("f1", "f2"), rows.map { it.id })
        assertEquals(2, db.billFilterDao().countInBook(bookId))
    }

    @Test
    fun `重名判定：自己不算重名`() = runTest {
        db.billFilterDao().upsert(entity("f1", "上月餐饮"))
        assertEquals(1, db.billFilterDao().nameTaken(bookId, "上月餐饮", excludeId = ""))
        assertEquals("重命名时排除自己", 0, db.billFilterDao().nameTaken(bookId, "上月餐饮", excludeId = "f1"))
        assertEquals(0, db.billFilterDao().nameTaken(bookId, "没这个名字", excludeId = ""))
    }

    @Test
    fun `重命名与软删：条件原样保留`() = runTest {
        db.billFilterDao().upsert(entity("f1", "上月餐饮"))
        db.billFilterDao().rename("f1", "上月吃饭", now = 5L)
        val renamed = db.billFilterDao().get("f1")!!
        assertEquals("上月吃饭", renamed.name)
        assertEquals("c1,c2", renamed.categoryIds)
        assertEquals(5L, renamed.updatedAt)

        db.billFilterDao().softDelete("f1", now = 6L)
        assertEquals(0, db.billFilterDao().observeByBook(bookId).first().size)
        // 软删后 get 仍读得到（撤销删除用）
        assertEquals(true, db.billFilterDao().get("f1")?.deleted)
    }

    @Test
    fun `落库-读回：编解码往返（含多值与开关）`() = runTest {
        val filter = TxFilter(
            keyword = "奶茶", type = TxType.EXPENSE, categoryIds = listOf("c1", "c2"),
            tagIds = listOf("g1"), amountMinCents = 1000, excludeFromStats = true,
        )
        db.billFilterDao().upsert(
            BillFilterCodec.toEntity("f1", bookId, "组合", filter, sortOrder = 0, now = 1L, deviceId = "d")
        )
        val back = BillFilterCodec.toFilter(db.billFilterDao().get("f1")!!)
        assertEquals(filter, back)
        assertNull("没填的字段仍是 null", db.billFilterDao().get("f1")!!.accountIds)
    }
}
