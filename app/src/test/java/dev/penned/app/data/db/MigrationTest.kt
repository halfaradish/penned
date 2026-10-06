package dev.penned.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.seed.DefaultSeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 迁移测试：不依赖 MigrationTestHelper 的 assets 机制，直接读取导出的 schema JSON
 * 在裸 SQLite 上重建旧版本库，再由 Room 执行迁移并做 schema 校验。
 * 测试工作目录 = app 模块目录，schemas/ 即导出目录（D015）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"
    private val schemaDir = File("schemas/dev.penned.app.data.db.PennedDatabase")

    /** 按导出的 schema JSON 创建旧版本数据库，并可执行数据播种。 */
    private fun createLegacyDatabase(version: Int, seed: (SQLiteDatabase) -> Unit) {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val schema = JSONObject(schemaDir.resolve("$version.json").readText())
            .getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            val indices = e.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                val idx = indices.getJSONObject(j)
                db.execSQL(idx.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
        }
        seed(db)
        db.version = version
        db.close()
    }

    @Test
    fun `migrate 1 to 2 keeps data and adds new columns and tables`() {
        createLegacyDatabase(1) { db ->
            db.execSQL(
                "INSERT INTO books (id, name, sortOrder, createdAt, updatedAt, deleted, deviceId) " +
                    "VALUES ('b1', '测试账本', 0, 1, 1, 0, 'd')"
            )
            db.execSQL(
                "INSERT INTO transactions (id, bookId, type, amountCents, accountId, targetAccountId, categoryId, " +
                    "dateEpochMinute, note, refundCents, discountCents, reimburseState, reimburseAccountId, " +
                    "currencyCode, rate, address, creatorDeviceId, createdAt, updatedAt, deleted, deviceId) " +
                    "VALUES ('t1', 'b1', 'EXPENSE', 1600, NULL, NULL, NULL, 100, NULL, 0, 0, 'NONE', NULL, " +
                    "NULL, NULL, NULL, 'd', 1, 1, 0, 'd')"
            )
        }
        val real = Room.databaseBuilder(context, PennedDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            runTest {
                // 旧数据保留
                val txs = real.transactionDao().all()
                assertEquals(1, txs.size)
                assertEquals(1600L, txs[0].amountCents)
                // 新列默认值生效
                val c = real.openHelper.readableDatabase
                    .query("SELECT excludeFromStats, excludeFromBudget, source, feeCents FROM transactions WHERE id = 't1'")
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
                assertEquals(0, c.getInt(1))
                assertEquals("MANUAL", c.getString(2))
                assertEquals(0L, c.getLong(3))
                c.close()
            }
        } finally {
            real.close()
        }
    }

    @Test
    fun `migrate 2 to 3 adds importBatchId column and keeps data`() {
        createLegacyDatabase(2) { db ->
            db.execSQL(
                "INSERT INTO books (id, name, sortOrder, createdAt, updatedAt, deleted, deviceId) " +
                    "VALUES ('b1', '测试账本', 0, 1, 1, 0, 'd')"
            )
            db.execSQL(
                "INSERT INTO transactions (id, bookId, type, amountCents, accountId, targetAccountId, categoryId, " +
                    "dateEpochMinute, note, refundCents, discountCents, reimburseState, reimburseAccountId, " +
                    "currencyCode, rate, address, feeCents, excludeFromStats, excludeFromBudget, source, " +
                    "creatorDeviceId, createdAt, updatedAt, deleted, deviceId) " +
                    "VALUES ('t1', 'b1', 'EXPENSE', 1600, NULL, NULL, NULL, 100, NULL, 0, 0, 'NONE', NULL, " +
                    "NULL, NULL, NULL, 0, 0, 0, 'IMPORT', 'd', 1, 1, 0, 'd')"
            )
        }
        val real = Room.databaseBuilder(context, PennedDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            runTest {
                val txs = real.transactionDao().all()
                assertEquals(1, txs.size)
                assertEquals("IMPORT", txs[0].source.name)
                // 新列默认 null
                val c = real.openHelper.readableDatabase
                    .query("SELECT importBatchId FROM transactions WHERE id = 't1'")
                c.moveToFirst()
                assertTrue(c.isNull(0))
                c.close()
            }
        } finally {
            real.close()
        }
    }

    @Test
    fun `migrate 3 to 4 adds account remark and scope columns and keeps data`() {
        createLegacyDatabase(3) { db ->
            db.execSQL(
                "INSERT INTO books (id, name, sortOrder, createdAt, updatedAt, deleted, deviceId) " +
                    "VALUES ('b1', '测试账本', 0, 1, 1, 0, 'd')"
            )
            db.execSQL(
                "INSERT INTO accounts (id, bookId, name, type, groupName, initialBalanceCents, currencyCode, " +
                    "archived, createdAt, updatedAt, deleted, deviceId) " +
                    "VALUES ('a1', 'b1', '现金', 'CASH', NULL, 100000, 'CNY', 0, 1, 1, 0, 'd')"
            )
        }
        val real = Room.databaseBuilder(context, PennedDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            runTest {
                // 旧数据保留，余额重算不受迁移影响
                val account = real.accountDao().get("a1")!!
                assertEquals("现金", account.name)
                assertEquals(100_000L, account.initialBalanceCents)
                assertEquals(100_000L, real.accountDao().observeWithBalance("b1").first().single().balanceCents)
                // 新列默认值：备注空、生效账本仍是"仅本账本"（老账户行为不变）
                assertNull(account.remark)
                assertFalse(account.scopeAllBooks)
            }
        } finally {
            real.close()
        }
    }

    @Test
    fun `seed still works on fresh v2 database`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, PennedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val book = DefaultSeed.seedIfEmpty(db, "d", 1L)
            assertNotNull(book)
            assertTrue(db.categoryDao().observeByBook(book!!.id).first().isNotEmpty())
        } finally {
            db.close()
        }
    }
}
