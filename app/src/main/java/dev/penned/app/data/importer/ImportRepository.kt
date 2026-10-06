package dev.penned.app.data.importer

import androidx.room.withTransaction
import dev.penned.app.PennedApplication
import dev.penned.app.data.SettingsRepository
import dev.penned.app.data.db.PennedDatabase
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.ImportBatchEntity
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 导入/导出编排：
 * - 一木 xls：解析 → 分类/账户/账本按名匹配、缺则创建 → 事务性写入（source=IMPORT + importBatchId）
 * - penned CSV：当前账本流水级备份（见 [PennedCsv]）
 * - 批次管理：列表 / 按批次整体软删
 */
class ImportRepository(
    private val db: PennedDatabase,
    private val settings: SettingsRepository,
    private val deviceId: String,
) {

    companion object {
        fun from(app: PennedApplication) = ImportRepository(app.database, app.settings, app.deviceId)
    }

    data class ImportResult(val batchId: String, val imported: Int, val skipped: Int, val problemSummary: String?)

    /** 一木 xls 导入。任何结构性问题抛异常；个别坏行跳过并在结果里报告。 */
    suspend fun importYimuXls(fileName: String, bytes: ByteArray): ImportResult = withContext(Dispatchers.IO) {
        val parsed = YimuXlsParser.parse(bytes)
        if (parsed.rows.isEmpty()) {
            throw YimuXlsParser.YimuFormatException("没有可导入的流水行" + parsed.problems.joinToString("；") { "第 ${it.rowIndex} 行：${it.message}" })
        }
        val now = System.currentTimeMillis()
        val batchId = UUID.randomUUID().toString()

        db.withTransaction {
            val batch = ImportBatchEntity(
                id = batchId,
                bookId = currentBookId(),
                fileName = fileName,
                count = parsed.rows.size,
                importedAt = now,
                createdAt = now,
                updatedAt = now,
                deviceId = deviceId,
            )
            db.importBatchDao().upsert(batch)

            val txs = ArrayList<TransactionEntity>(parsed.rows.size)
            for (row in parsed.rows) {
                val book = resolveBook(row.bookName)
                val categoryId = resolveCategory(book.id, row)
                val accountId = row.accountName?.let { resolveAccount(book.id, it) }
                txs.add(
                    TransactionEntity(
                        id = UUID.randomUUID().toString(),
                        bookId = book.id,
                        type = if (row.isIncome) TxType.INCOME else TxType.EXPENSE,
                        amountCents = row.amountCents,
                        accountId = accountId,
                        targetAccountId = null,
                        categoryId = categoryId,
                        dateEpochMinute = row.dateEpochMinute,
                        note = row.note,
                        refundCents = row.refundCents,
                        discountCents = row.discountCents,
                        source = TxSource.IMPORT,
                        importBatchId = batchId,
                        creatorDeviceId = deviceId,
                        createdAt = now,
                        updatedAt = now,
                        deviceId = deviceId,
                    )
                )
            }
            db.transactionDao().upsertAll(txs)
        }

        ImportResult(
            batchId = batchId,
            imported = parsed.rows.size,
            skipped = parsed.problems.size,
            problemSummary = parsed.problems.take(3).joinToString("；") { "第 ${it.rowIndex} 行：${it.message}" }
                .ifEmpty { null }?.let { if (parsed.problems.size > 3) "$it……" else it },
        )
    }

    /** penned CSV 导入（当前账本）。 */
    suspend fun importCsv(fileName: String, content: String): ImportResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val batchId = UUID.randomUUID().toString()
        val bookId = currentBookId()
        val txs = PennedCsv.import(content, bookId)
        if (txs.isEmpty()) throw PennedCsv.CsvFormatException("CSV 里没有流水行")

        db.withTransaction {
            db.importBatchDao().upsert(
                ImportBatchEntity(
                    id = batchId, bookId = bookId, fileName = fileName, count = txs.size,
                    importedAt = now, createdAt = now, updatedAt = now, deviceId = deviceId,
                )
            )
            db.transactionDao().upsertAll(txs)
        }
        ImportResult(batchId, txs.size, 0, null)
    }

    /** 导出当前账本全部有效流水为 penned CSV。 */
    suspend fun exportCurrentBookCsv(): String = withContext(Dispatchers.IO) {
        val bookId = currentBookId()
        PennedCsv.export(db.transactionDao().allInBook(bookId))
    }

    fun observeBatches() = db.importBatchDao().observeAll()

    /** 批次明细钻取：批次信息 + 该批次未删除的流水。 */
    fun observeBatch(id: String) = db.importBatchDao().observeById(id)

    fun observeBatchTransactions(batchId: String) = db.transactionDao().observeByBatch(batchId)

    /** 按批次整体软删：批次与其名下流水一起标记删除。 */
    suspend fun deleteBatch(batchId: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.transactionDao().softDeleteByBatch(batchId, now)
            db.importBatchDao().softDelete(batchId, now)
        }
    }

    private suspend fun currentBookId(): String {
        settings.currentBookId.firstOrNull()?.let { return it }
        // 兜底：设置缺失时取第一个账本（正常情况下 Application 启动时已初始化）
        return db.bookDao().observeAll().first().firstOrNull()?.id
            ?: throw YimuXlsParser.YimuFormatException("没有任何账本，请先创建账本")
    }

    private suspend fun resolveBook(name: String?): BookEntity {
        val books = db.bookDao().observeAll().first()
        if (name.isNullOrBlank()) {
            val currentId = settings.currentBookId.firstOrNull()
            return books.firstOrNull { it.id == currentId } ?: books.first()
                ?: throw YimuXlsParser.YimuFormatException("没有任何账本，请先创建账本")
        }
        books.firstOrNull { it.name == name }?.let { return it }
        val now = System.currentTimeMillis()
        val book = BookEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            sortOrder = books.size,
            createdAt = now,
            updatedAt = now,
            deviceId = deviceId,
        )
        db.bookDao().upsert(book)
        return book
    }

    /** 分类按名匹配（二级挂到一级下），缺则创建。 */
    private suspend fun resolveCategory(bookId: String, row: YimuXlsParser.YimuTx): String? {
        val categories = db.categoryDao().allInBook(bookId)
        val domain = if (row.isIncome) CategoryType.INCOME else CategoryType.EXPENSE
        val parent = categories.firstOrNull { it.parentId == null && it.type == domain && it.name == row.categoryL1 }
            ?: createCategory(bookId, null, row.categoryL1, domain, categories.size)
        val l2 = row.categoryL2?.takeIf { it.isNotBlank() } ?: return parent.id
        val sub = categories.firstOrNull { it.parentId == parent.id && it.name == l2 }
            ?: createCategory(bookId, parent.id, l2, domain, categories.size + 1)
        return sub.id
    }

    private suspend fun createCategory(bookId: String, parentId: String?, name: String, type: CategoryType, sortOrder: Int): CategoryEntity {
        val now = System.currentTimeMillis()
        val category = CategoryEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            parentId = parentId,
            name = name.trim(),
            type = type,
            sortOrder = sortOrder,
            createdAt = now,
            updatedAt = now,
            deviceId = deviceId,
        )
        db.categoryDao().upsert(category)
        return category
    }

    /** 账户按名匹配，缺则创建（导出无账户类型信息，统一建为现金类型）。 */
    private suspend fun resolveAccount(bookId: String, name: String): String {
        val existing = db.accountDao().allInBook(bookId)
        existing.firstOrNull { it.name == name }?.let { return it.id }
        val now = System.currentTimeMillis()
        val account = AccountEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            name = name.trim(),
            type = AccountType.CASH,
            initialBalanceCents = 0,
            createdAt = now,
            updatedAt = now,
            deviceId = deviceId,
        )
        db.accountDao().upsert(account)
        return account.id
    }
}
