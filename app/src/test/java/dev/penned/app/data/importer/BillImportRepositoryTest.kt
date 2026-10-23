package dev.penned.app.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.PennedDatabase
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.KeywordRuleEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.BillRow
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.RuleTargetType
import dev.penned.app.domain.TxSource
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
 * 账单导入的落库（M4-5）：账户映射（同名命中 / 新建 / 不指定）、**分类只按名匹配不新建**、
 * 批次四字段（`sourceKind`/`skippedCount`/`duplicateCount`/`externalId`）逐条对、
 * 同一份文件导两次 → 第二次全进"重复"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class BillImportRepositoryTest {

    private lateinit var db: PennedDatabase
    private lateinit var repo: BillImportRepository
    private val bookId = "book-bill"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<PennedApplication>()
        db = Room.inMemoryDatabaseBuilder(context, PennedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BillImportRepository(db, deviceId = "test-device")
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(
        externalId: String? = "X-1",
        amount: Long = 1200,
        note: String? = "某食堂 · 炒饭",
        accountClue: String? = null,
        categoryHint: String? = null,
        minute: Long = 1000L,
        merchant: String? = null,
    ) = BillRow(
        dateEpochMinute = minute, amountCents = amount, expense = true, note = note,
        externalId = externalId, accountClue = accountClue, categoryHint = categoryHint, merchant = merchant,
    )

    @Test
    fun `账户映射：同名命中 缺则给新建建议 空白线索不列`() = runTest {
        db.accountDao().upsert(
            AccountEntity(
                id = "acc-cash", bookId = bookId, name = "零钱", type = AccountType.CASH,
                createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        val mappings = repo.buildAccountMappings(
            bookId,
            listOf(row(accountClue = "零钱"), row(accountClue = "花呗")),
        )
        assertEquals(2, mappings.size)
        val cash = mappings.first { it.clue == "零钱" }
        assertEquals("acc-cash", cash.matchedAccountId)
        assertEquals(false, cash.willCreate)

        val huabei = mappings.first { it.clue == "花呗" }
        assertNull(huabei.matchedAccountId)
        assertTrue(huabei.willCreate)
        assertEquals("花呗", huabei.newAccountName)
        assertEquals(AccountType.CREDIT, huabei.newAccountType) // 按名猜
    }

    @Test
    fun `分类只按名匹配 不新建`() = runTest {
        db.categoryDao().upsert(
            CategoryEntity(
                id = "cat-food", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE,
                createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        val categories = db.categoryDao().allInBook(bookId)

        // 平台词汇「餐饮美食」与我们的一级树不重合 → 留空，绝不新建
        assertEquals(null, repo.resolveCategoryId(row(categoryHint = "餐饮美食"), categories, emptyList()))
        // 同名才命中
        assertEquals("cat-food", repo.resolveCategoryId(row(categoryHint = "食品餐饮"), categories, emptyList()))

        // 关键词规则命中商户 → 也填（M4-1 的引擎在这里生效）
        db.keywordRuleDao().upsert(
            KeywordRuleEntity(
                id = "r1", bookId = bookId, keyword = "肯打鸡", targetType = RuleTargetType.CATEGORY,
                targetId = "cat-food", priority = 0, enabled = true,
                createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        val rules = db.keywordRuleDao().allInBook(bookId)
        assertEquals(
            "cat-food",
            repo.resolveCategoryId(row(note = "肯打鸡 · 全家桶"), categories, rules),
        )
        // 一条新分类都不许冒出来
        assertEquals(1, db.categoryDao().allInBook(bookId).size)
    }

    @Test
    fun `AI 建议在列线索与规则都没命中时落库 规则优先`() = runTest {
        db.categoryDao().upsertAll(
            listOf(
                CategoryEntity(
                    id = "cat-food", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE,
                    createdAt = 1L, updatedAt = 1L, deviceId = "t",
                ),
                CategoryEntity(
                    id = "cat-shop", bookId = bookId, name = "购物消费", type = CategoryType.EXPENSE,
                    createdAt = 1L, updatedAt = 1L, deviceId = "t",
                ),
            )
        )
        val categories = db.categoryDao().allInBook(bookId)
        db.keywordRuleDao().upsert(
            KeywordRuleEntity(
                id = "r1", bookId = bookId, keyword = "肯打鸡", targetType = RuleTargetType.CATEGORY,
                targetId = "cat-food", priority = 0, enabled = true,
                createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        val rules = db.keywordRuleDao().allInBook(bookId)

        // 规则命中优先于 AI 建议
        val byRule = row(externalId = "X-1", note = "肯打鸡 · 全家桶", merchant = "肯打鸡")
            .copy(suggestedCategoryId = "cat-shop")
        assertEquals("cat-food", repo.resolveCategoryId(byRule, categories, rules))
        // 规则没命中 → 用 AI 建议
        val byAi = row(externalId = "X-2", note = "陌生商户 · 小玩意", merchant = "陌生商户")
            .copy(suggestedCategoryId = "cat-shop")
        assertEquals("cat-shop", repo.resolveCategoryId(byAi, categories, rules))

        val result = repo.import(
            bookId = bookId,
            fileName = "微信账单.xlsx",
            sourceKind = "WECHAT",
            rows = listOf(byRule, byAi),
            mappings = emptyList(),
            duplicateCount = 0,
            skippedCount = 0,
        )
        val txs = db.transactionDao().allInBook(bookId)
        assertEquals(2, txs.size)
        assertEquals("cat-food", txs.first { it.externalId == "X-1" }.categoryId)
        assertEquals("cat-shop", txs.first { it.externalId == "X-2" }.categoryId)
        assertEquals(result.batchId, txs.first().importBatchId)
    }

    @Test
    fun `AI 建议收支类型不符时不采用`() = runTest {
        db.categoryDao().upsert(
            CategoryEntity(
                id = "cat-salary", bookId = bookId, name = "工资", type = CategoryType.INCOME,
                createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        val categories = db.categoryDao().allInBook(bookId)
        val expenseRow = row(note = "某公司").copy(suggestedCategoryId = "cat-salary")
        assertNull(repo.resolveCategoryId(expenseRow, categories, emptyList()))
    }

    @Test
    fun `落库：批次四字段逐条对 账户按映射 分类按名`() = runTest {
        db.categoryDao().upsert(
            CategoryEntity(
                id = "cat-food", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE,
                createdAt = 1L, updatedAt = 1L, deviceId = "t",
            )
        )
        val rows = listOf(
            row(externalId = "WX-1", accountClue = "零钱", categoryHint = "食品餐饮"),
            row(externalId = "WX-2", amount = 8800, accountClue = "花呗", categoryHint = "餐饮美食"),
        )
        val mappings = repo.buildAccountMappings(bookId, rows)

        val result = repo.import(
            bookId = bookId,
            fileName = "微信账单.xlsx",
            sourceKind = "WECHAT",
            rows = rows,
            mappings = mappings,
            duplicateCount = 3,
            skippedCount = 5,
        )
        assertEquals(2, result.imported)

        val txs = db.transactionDao().allInBook(bookId)
        assertEquals(2, txs.size)
        assertTrue(txs.all { it.source == TxSource.IMPORT })
        assertTrue(txs.all { it.importBatchId == result.batchId })
        assertEquals(setOf("WX-1", "WX-2"), txs.mapNotNull { it.externalId }.toSet())
        // 分类：一条按名命中、一条平台词汇留空
        assertEquals("cat-food", txs.first { it.externalId == "WX-1" }.categoryId)
        assertNull(txs.first { it.externalId == "WX-2" }.categoryId)

        // 账户：零钱不存在 → 按映射新建了一个；花呗按名猜成信贷
        val accounts = db.accountDao().allInBook(bookId)
        assertEquals(setOf("零钱", "花呗"), accounts.map { it.name }.toSet())
        assertEquals(AccountType.CREDIT, accounts.first { it.name == "花呗" }.type)
        assertEquals(
            accounts.first { it.name == "零钱" }.id,
            txs.first { it.externalId == "WX-1" }.accountId,
        )

        // 批次四字段
        val batch = db.importBatchDao().get(result.batchId)!!
        assertEquals("WECHAT", batch.sourceKind)
        assertEquals(2, batch.count)
        assertEquals(5, batch.skippedCount)
        assertEquals(3, batch.duplicateCount) // 旧批次默认 YIMU 的语义不受影响
    }

    @Test
    fun `同一份文件导两次：第二次全进重复 库内条数不变`() = runTest {
        val rows = listOf(row(externalId = "WX-1"), row(externalId = "WX-2", amount = 500))
        repo.import(bookId, "bill.csv", "WECHAT", rows, emptyList(), 0, 0)
        assertEquals(2, db.transactionDao().allInBook(bookId).size)

        // 第二次：先查重（M4-5 预览页就是这么做的），命中即全部算重复
        val existing = db.transactionDao().externalIdsInBook(bookId, rows.mapNotNull { it.externalId }).toSet()
        val (keep, dup) = dev.penned.app.domain.BillDedupe.partition(rows, existingIds = existing)
        assertEquals(0, keep.size)
        assertEquals(2, dup.size)

        // 只导 keep（空）→ 库内条数不变
        repo.import(bookId, "bill.csv", "WECHAT", keep, emptyList(), dup.size, 0)
        assertEquals(2, db.transactionDao().allInBook(bookId).size)
        assertEquals(2, db.importBatchDao().observeAll().first().first().duplicateCount)
    }

    @Test
    fun `批次可整体删除：流水跟着软删`() = runTest {
        val rows = listOf(row(externalId = "W1"), row(externalId = "W2"))
        val result = repo.import(bookId, "b.csv", "WECHAT", rows, emptyList(), 0, 0)
        assertEquals(2, db.transactionDao().allInBook(bookId).size)

        // 既有能力（批次页）：软删批次 → 该批流水一起软删
        db.importBatchDao().softDelete(result.batchId, now = 2L)
        db.transactionDao().softDeleteByBook(bookId, now = 2L)
        assertEquals(0, db.transactionDao().allInBook(bookId).size)
        // 软删不是物理删：行还在库里（all() 只查 deleted = 0，所以这里用裸 SQL 数）
        val physical = db.query("SELECT COUNT(*) FROM transactions", null).use { c ->
            c.moveToFirst(); c.getInt(0)
        }
        assertEquals(2, physical)
    }

    @Test
    fun `单号查重分批查全：超过 500 条也一个不漏`() = runTest {
        // 真机走查实拍：507 行的文件导第二次时只认出 500 条重复、又插进去 7 条
        val rows = (1..1200).map { row(externalId = "B-$it", amount = 100, minute = 1000L + it) }
        repo.import(bookId, "big.csv", "ALIPAY", rows, emptyList(), 0, 0)
        assertEquals(1200, db.transactionDao().allInBook(bookId).size)

        val existing = repo.existingExternalIds(bookId, rows.mapNotNull { it.externalId })
        assertEquals(1200, existing.size)
        val (keep, dup) = dev.penned.app.domain.BillDedupe.partition(rows, existingIds = existing)
        assertEquals(0, keep.size)
        assertEquals(1200, dup.size)
    }

    @Test
    fun `无单号的行用兜底键查重`() = runTest {
        val first = row(externalId = null, minute = 700L, amount = 300, note = "早饭")
        repo.import(bookId, "a.csv", "WECHAT", listOf(first), emptyList(), 0, 0)

        val keys = repo.existingFallbackKeys(bookId, minMinute = 0L, maxMinute = 10_000L)
        assertEquals(setOf("700|300|E|早饭"), keys)

        val again = row(externalId = null, minute = 700L, amount = 300, note = "早饭")
        val (keep, dup) = dev.penned.app.domain.BillDedupe.partition(
            listOf(again),
            existingFallbackKeys = keys,
        )
        assertEquals(0, keep.size)
        assertEquals(1, dup.size)
    }

    @Test
    fun `分块写库：超过一块也一次不落`() = runTest {
        val rows = (1..1200).map { row(externalId = "C-$it", amount = 100, minute = 1000L + it) }
        var lastProgress = 0
        val result = repo.import(
            bookId, "big.csv", "ALIPAY", rows, emptyList(), 0, 0,
            onProgress = { done, total -> lastProgress = done; assertEquals(rows.size, total) },
        )
        assertEquals(1200, result.imported)
        assertEquals(1200, lastProgress)
        assertEquals(1200, db.transactionDao().allInBook(bookId).size)
    }
}
