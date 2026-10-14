package dev.penned.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.SearchQuery
import dev.penned.app.domain.TxFilter
import dev.penned.app.domain.TxFilters
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * 组合筛选 SQL（M3-7）：每个条件单独、两两组合、空结果、软删不出现；
 * `hasX` 标志位 + 占位列表的写法（空列表不能直接进 `IN`）也在这里钉住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionFilterDaoTest {

    private lateinit var db: PennedDatabase
    private val bookId = "book-filter"
    private val zone: ZoneId = ZoneId.systemDefault()

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, PennedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val now = 1L
        db.categoryDao().upsert(
            CategoryEntity(id = "p1", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE, createdAt = now, updatedAt = now, deviceId = "d")
        )
        db.categoryDao().upsert(
            CategoryEntity(id = "s1", bookId = bookId, parentId = "p1", name = "午餐", type = CategoryType.EXPENSE, sortOrder = 1, createdAt = now, updatedAt = now, deviceId = "d")
        )
        db.categoryDao().upsert(
            CategoryEntity(id = "p2", bookId = bookId, name = "工资", type = CategoryType.INCOME, createdAt = now, updatedAt = now, deviceId = "d")
        )
        db.accountDao().upsert(
            AccountEntity(id = "a1", bookId = bookId, name = "微信", type = AccountType.DEBIT, createdAt = now, updatedAt = now, deviceId = "d")
        )
        db.accountDao().upsert(
            AccountEntity(id = "a2", bookId = bookId, name = "现金", type = AccountType.CASH, createdAt = now, updatedAt = now, deviceId = "d")
        )
        db.tagDao().upsert(
            dev.penned.app.data.db.entity.TagEntity(
                id = "g1", bookId = bookId, name = "出差", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "d",
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun tx(
        id: String,
        type: TxType = TxType.EXPENSE,
        cents: Long = 1000,
        day: Int = 10,
        categoryId: String? = "s1",
        accountId: String? = "a1",
        note: String? = null,
        excludeFromStats: Boolean = false,
        deleted: Boolean = false,
    ) = TransactionEntity(
        id = id, bookId = bookId, type = type, amountCents = cents, accountId = accountId,
        categoryId = categoryId, dateEpochMinute = LocalDate.of(2026, 10, day).atTime(12, 0)
            .atZone(zone).toEpochSecond() / 60,
        note = note, excludeFromStats = excludeFromStats, creatorDeviceId = "d",
        createdAt = 1L, updatedAt = 1L, deleted = deleted, deviceId = "d",
    )

    private suspend fun query(filter: TxFilter): List<String> {
        val categories = db.categoryDao().observeByBook(bookId).first()
        val (from, to) = TxFilters.minuteRange(filter.fromEpochDay, filter.toEpochDay, zone)
        val categoryIds = TxFilters.expandCategoryIds(categories, filter.categoryIds)
        return db.transactionDao().observeFiltered(
            bookId = bookId,
            pattern = filter.keyword.trim().takeIf { it.isNotEmpty() }?.let { SearchQuery.likePattern(it) },
            keywordCents = SearchQuery.amountCentsOrNull(filter.keyword),
            type = filter.type,
            fromMinute = from,
            toMinute = to,
            hasCategories = if (categoryIds.isEmpty()) 0 else 1,
            categoryIds = categoryIds.ifEmpty { listOf("") },
            hasAccounts = if (filter.accountIds.isEmpty()) 0 else 1,
            accountIds = filter.accountIds.ifEmpty { listOf("") },
            hasTags = if (filter.tagIds.isEmpty()) 0 else 1,
            tagIds = filter.tagIds.ifEmpty { listOf("") },
            minCents = filter.amountMinCents,
            maxCents = filter.amountMaxCents,
            excludeFromStats = filter.excludeFromStats,
            excludeFromBudget = filter.excludeFromBudget,
            includeTransfer = filter.includeTransfer?.let { if (it) 1 else 0 },
        ).first().map { it.id }
    }

    private suspend fun seedAll() {
        db.transactionDao().upsert(tx("e-lunch", cents = 1650, day = 10, note = "公司楼下"))
        db.transactionDao().upsert(tx("e-taxi", cents = 3000, day = 12, categoryId = "p1", note = "打车去机场"))
        db.transactionDao().upsert(tx("i-salary", type = TxType.INCOME, cents = 350_000, day = 5, categoryId = "p2", accountId = "a2"))
        db.transactionDao().upsert(tx("t-card", type = TxType.TRANSFER, cents = 100_000, day = 8, categoryId = null, accountId = "a1"))
        db.transactionDao().upsert(tx("e-off", cents = 500, day = 11, excludeFromStats = true))
        db.transactionDao().upsert(tx("e-gone", cents = 999, day = 11, deleted = true))
        db.tagDao().setTagsForTx("e-taxi", listOf("g1"))
    }

    @Test
    fun `每个条件单独用：关键词 类型 日期 分类 账户 标签 金额 开关`() = runTest {
        seedAll()

        // 关键词：备注
        assertEquals(listOf("e-lunch"), query(TxFilter(keyword = "公司")))
        // 关键词：分类名（二级「午餐」下有两笔：e-lunch 与 e-off）
        assertEquals(listOf("e-lunch", "e-off"), query(TxFilter(keyword = "午餐")).sorted())
        // 关键词：金额精确（16.5 → 1650 分）
        assertEquals(listOf("e-lunch"), query(TxFilter(keyword = "16.5")))
        // 类型
        assertEquals(listOf("i-salary"), query(TxFilter(type = TxType.INCOME)))
        assertEquals(listOf("t-card"), query(TxFilter(type = TxType.TRANSFER)))
        // 日期：10/11 之后（含 11）
        assertEquals(listOf("e-off", "e-taxi"), query(TxFilter(fromEpochDay = LocalDate.of(2026, 10, 11).toEpochDay())).sorted())
        // 分类：选一级 = 连二级一起（食品餐饮下：午餐两笔 + 打车一笔）
        assertEquals(listOf("e-lunch", "e-off", "e-taxi"), query(TxFilter(categoryIds = listOf("p1"))).sorted())
        // 账户
        assertEquals(listOf("i-salary"), query(TxFilter(accountIds = listOf("a2"))))
        // 标签
        assertEquals(listOf("e-taxi"), query(TxFilter(tagIds = listOf("g1"))))
        // 金额区间
        assertEquals(listOf("e-lunch", "e-taxi"), query(TxFilter(amountMinCents = 1000, amountMaxCents = 5000)).sorted())
        // 开关：只看不计收支的
        assertEquals(listOf("e-off"), query(TxFilter(excludeFromStats = true)))
        // 开关：排除转账
        assertEquals(listOf("e-lunch", "e-taxi", "i-salary", "e-off").sorted(), query(TxFilter(includeTransfer = false)).sorted())
        // 不限（空条件）——UI 不会发这个查询，但 SQL 本身要稳（hasX = 0）
        assertEquals(5, query(TxFilter()).size)
    }

    @Test
    fun `两两组合与空结果`() = runTest {
        seedAll()
        // 类型 + 日期
        assertEquals(
            listOf("e-taxi"),
            query(TxFilter(type = TxType.EXPENSE, fromEpochDay = LocalDate.of(2026, 10, 12).toEpochDay())),
        )
        // 分类 + 金额下限
        assertEquals(listOf("e-taxi"), query(TxFilter(categoryIds = listOf("p1"), amountMinCents = 2000)))
        // 账户 + 标签（现金的流水没有标签 → 空）
        assertEquals(emptyList<String>(), query(TxFilter(accountIds = listOf("a2"), tagIds = listOf("g1"))))
        // 关键词 + 类型（"打车" 是支出 ✓；按收入筛就空）
        assertEquals(listOf("e-taxi"), query(TxFilter(keyword = "打车", type = TxType.EXPENSE)))
        assertEquals(emptyList<String>(), query(TxFilter(keyword = "打车", type = TxType.INCOME)))
    }

    @Test
    fun `软删的流水不出现；别的账本不出现`() = runTest {
        seedAll()
        db.transactionDao().upsert(
            TransactionEntity(
                id = "other-book", bookId = "book-other", type = TxType.EXPENSE, amountCents = 100,
                dateEpochMinute = 1, creatorDeviceId = "d", createdAt = 1, updatedAt = 1, deviceId = "d",
            )
        )
        val ids = query(TxFilter(keyword = ""))
        assertEquals(false, "e-gone" in ids)
        assertEquals(false, "other-book" in ids)
    }

    @Test
    fun `导出取整行实体：按结果顺序能对上`() = runTest {
        seedAll()
        val rows = db.transactionDao().observeFiltered(
            bookId = bookId, pattern = null, keywordCents = null, type = TxType.EXPENSE,
            fromMinute = null, toMinute = null, hasCategories = 0, categoryIds = listOf(""),
            hasAccounts = 0, accountIds = listOf(""), hasTags = 0, tagIds = listOf(""),
            minCents = null, maxCents = null, excludeFromStats = null, excludeFromBudget = null,
            includeTransfer = null,
        ).first()
        val entities = db.transactionDao().entitiesByIds(rows.map { it.id }).associateBy { it.id }
        assertEquals(rows.size, entities.size)
        assertEquals(1650L, entities.getValue("e-lunch").amountCents)
        // CSV 口径：导出的条数与结果一致（内容格式由 PennedCsv 自己的测试钉）
        assertEquals(
            rows.size,
            dev.penned.app.data.importer.PennedCsv.export(rows.mapNotNull { entities[it.id] }).trim().lines().size - 1,
        )
    }
}
