package dev.penned.app.data.importer

import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import java.io.IOException

/**
 * penned 自有 CSV 备份格式（M1 范围：当前账本的流水级备份，分类/账户/账本实体不导出，
 * 引用列按原 ID 保存）。UTF-8（写入方建议带 BOM 方便 Excel 识别）。
 */
object PennedCsv {

    const val FILE_EXTENSION = "csv"

    val COLUMNS = listOf(
        "id", "type", "amountCents", "accountId", "targetAccountId", "categoryId",
        "dateEpochMinute", "note", "refundCents", "discountCents",
        "reimburseState", "reimburseAccountId", "feeCents",
        "currencyCode", "rate", "address",
        "excludeFromStats", "excludeFromBudget", "source", "importBatchId",
        "creatorDeviceId", "createdAt", "updatedAt", "deviceId",
        // M4-0（D100）：外部单号补在**末尾**——旧文件（24 列）没有这一列，导入时按表头名取不到 = 空，
        // 老版本读新文件也只会多出一列被忽略（`idx` 是按名字对齐的，不认位置）。
        "externalId",
    )

    class CsvFormatException(message: String) : IOException(message)

    fun export(transactions: List<TransactionEntity>): String {
        val rows = ArrayList<List<String>>()
        rows.add(COLUMNS)
        for (t in transactions) {
            rows.add(
                listOf(
                    t.id, t.type.name, t.amountCents.toString(), t.accountId.orEmpty(), t.targetAccountId.orEmpty(),
                    t.categoryId.orEmpty(), t.dateEpochMinute.toString(), t.note.orEmpty(),
                    t.refundCents.toString(), t.discountCents.toString(),
                    t.reimburseState.name, t.reimburseAccountId.orEmpty(), t.feeCents.toString(),
                    t.currencyCode.orEmpty(), t.rate?.toString().orEmpty(), t.address.orEmpty(),
                    if (t.excludeFromStats) "1" else "0", if (t.excludeFromBudget) "1" else "0",
                    t.source.name, t.importBatchId.orEmpty(),
                    t.creatorDeviceId, t.createdAt.toString(), t.updatedAt.toString(), t.deviceId,
                    t.externalId.orEmpty(),
                )
            )
        }
        return CsvCodec.serialize(rows)
    }

    /** 解析并重建流水实体；bookId 由调用方（当前账本）指定。 */
    fun import(content: String, bookId: String): List<TransactionEntity> {
        val rows = CsvCodec.parse(content)
        if (rows.isEmpty()) throw CsvFormatException("CSV 为空")
        val header = rows[0].map { it.trim() }
        if (header.firstOrNull() != "id" || !header.contains("type") || !header.contains("amountCents")) {
            throw CsvFormatException("表头不符——请导入 penned 导出的 CSV 备份")
        }
        val idx = COLUMNS.associateWith { header.indexOf(it) }
        val out = ArrayList<TransactionEntity>()
        for (line in 1 until rows.size) {
            val row = rows[line]
            if (row.size == 1 && row[0].isBlank()) continue
            fun field(name: String): String = idx[name]?.takeIf { it >= 0 && it < row.size }?.let { row[it] } ?: ""
            fun reqLong(name: String): Long = field(name).toLongOrNull() ?: throw CsvFormatException("第 ${line + 1} 行 $name 不是数字：${field(name)}")
            fun reqEnum(name: String): String {
                val v = field(name)
                if (v.isEmpty()) throw CsvFormatException("第 ${line + 1} 行缺少 $name")
                return v
            }
            val tx = TransactionEntity(
                id = field("id").ifEmpty { throw CsvFormatException("第 ${line + 1} 行缺少 id") },
                bookId = bookId,
                type = reqEnum("type").let { v -> enumOr(TxType::class.java, v) ?: throw CsvFormatException("第 ${line + 1} 行 type 无法识别：$v") },
                amountCents = reqLong("amountCents"),
                accountId = field("accountId").ifEmpty { null },
                targetAccountId = field("targetAccountId").ifEmpty { null },
                categoryId = field("categoryId").ifEmpty { null },
                dateEpochMinute = reqLong("dateEpochMinute"),
                note = field("note").ifEmpty { null },
                refundCents = field("refundCents").toLongOrNull() ?: 0,
                discountCents = field("discountCents").toLongOrNull() ?: 0,
                reimburseState = field("reimburseState").ifEmpty { "NONE" }.let { v -> enumOr(ReimburseState::class.java, v) ?: ReimburseState.NONE },
                reimburseAccountId = field("reimburseAccountId").ifEmpty { null },
                feeCents = field("feeCents").toLongOrNull() ?: 0,
                currencyCode = field("currencyCode").ifEmpty { null },
                rate = field("rate").toDoubleOrNull(),
                address = field("address").ifEmpty { null },
                excludeFromStats = field("excludeFromStats") == "1",
                excludeFromBudget = field("excludeFromBudget") == "1",
                source = field("source").ifEmpty { "MANUAL" }.let { v -> enumOr(TxSource::class.java, v) ?: TxSource.MANUAL },
                importBatchId = field("importBatchId").ifEmpty { null },
                externalId = field("externalId").ifEmpty { null },
                creatorDeviceId = field("creatorDeviceId").ifEmpty { "unknown" },
                createdAt = field("createdAt").toLongOrNull() ?: 0,
                updatedAt = field("updatedAt").toLongOrNull() ?: 0,
                deviceId = field("deviceId").ifEmpty { "unknown" },
            )
            out.add(tx)
        }
        return out
    }

    private fun <E : Enum<E>> enumOr(clazz: Class<E>, value: String): E? =
        clazz.enumConstants.firstOrNull { it.name == value }
}
