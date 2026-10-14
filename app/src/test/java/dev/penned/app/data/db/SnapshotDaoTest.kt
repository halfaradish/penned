package dev.penned.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.AssetSnapshotEntity
import dev.penned.app.domain.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 理财快照 DAO（M3-10）：按账户倒序、软删不进、本账本可见账户的快照才进资产页汇总。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SnapshotDaoTest {

    private lateinit var db: PennedDatabase
    private val bookId = "book-inv"

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, PennedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val now = 1L
        db.accountDao().upsert(
            AccountEntity(id = "inv1", bookId = bookId, name = "理财A", type = AccountType.INVEST, createdAt = now, updatedAt = now, deviceId = "d")
        )
        db.accountDao().upsert(
            AccountEntity(id = "inv2", bookId = "book-other", name = "理财B", type = AccountType.INVEST, createdAt = now, updatedAt = now, deviceId = "d")
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun snapshot(id: String, accountId: String, day: Long, cents: Long, deleted: Boolean = false) =
        AssetSnapshotEntity(
            id = id, accountId = accountId, dateEpochDay = day, valueCents = cents,
            createdAt = day, updatedAt = day, deleted = deleted, deviceId = "d",
        )

    @Test
    fun `按账户取快照：日期倒序、软删不进`() = runTest {
        db.snapshotDao().upsert(snapshot("s1", "inv1", 20_000L, 10_000L))
        db.snapshotDao().upsert(snapshot("s2", "inv1", 20_030L, 13_000L))
        db.snapshotDao().upsert(snapshot("s3", "inv1", 20_010L, 11_000L, deleted = true))
        db.snapshotDao().upsert(snapshot("s4", "inv2", 20_030L, 99_999L))

        val rows = db.snapshotDao().observeByAccount("inv1").first()
        assertEquals(listOf("s2", "s1"), rows.map { it.id })
        assertEquals(2, db.snapshotDao().countForAccount("inv1"))

        db.snapshotDao().softDelete("s2", now = 9L)
        assertEquals(listOf("s1"), db.snapshotDao().observeByAccount("inv1").first().map { it.id })
        assertEquals(true, db.snapshotDao().get("s2")!!.deleted)
    }

    @Test
    fun `本账本汇总：只看本账本可见账户的快照`() = runTest {
        db.snapshotDao().upsert(snapshot("s1", "inv1", 20_000L, 10_000L))
        db.snapshotDao().upsert(snapshot("s2", "inv2", 20_000L, 99_999L))
        assertEquals(listOf("s1"), db.snapshotDao().observeForBook(bookId).first().map { it.id })
        // 账户软删后它的快照也不进汇总
        db.accountDao().softDelete("inv1", now = 2L)
        assertEquals(0, db.snapshotDao().observeForBook(bookId).first().size)
    }

    @Test
    fun `同日再记：upsert 覆盖同一条`() = runTest {
        db.snapshotDao().upsert(snapshot("s1", "inv1", 20_000L, 10_000L))
        db.snapshotDao().upsert(snapshot("s1", "inv1", 20_000L, 12_000L))
        val rows = db.snapshotDao().observeByAccount("inv1").first()
        assertEquals(1, rows.size)
        assertEquals(12_000L, rows.first().valueCents)
    }
}
