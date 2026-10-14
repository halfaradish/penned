package dev.penned.app.domain

import dev.penned.app.data.db.entity.BillFilterEntity

/**
 * 账单组合的条件编解码（M3-8，纯函数，表驱动测试在 `BillFilterCodecTest`）。
 *
 * 多值条件在 `bill_filters` 里存**逗号分隔的一列**，编解码口径写死在这里：
 * - **空列表 ↔ null**（不限）：空列表编码成 `null`（不是空串——空串会被读成"一个空 id"）
 * - 值里的 `\` 与 `,` 转义成 `\\` / `\,`（id 里不该有逗号，但编解码不假设；反过来脏数据也不会把一列拆错）
 * - 解码时忽略空段（`"a,,b"` → `[a, b]`），全空 → 空列表
 * - 三个开关与数值/枚举条件照搬 `TxFilter`（`null` = 不限）
 *
 * `bill_filters` 的列与 `TxFilter` 一一对应，[toFilter]/[toEntity] 是唯一转换入口。
 */
object BillFilterCodec {

    /** 多值 id 列表 → 列值（空列表 → null = 不限）。 */
    fun encodeIds(ids: List<String>): String? {
        val cleaned = ids.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return null
        return cleaned.joinToString(",") { it.replace("\\", "\\\\").replace(",", "\\,") }
    }

    /** 列值 → 多值 id 列表（null/空串 → 空列表）。 */
    fun decodeIds(encoded: String?): List<String> {
        if (encoded.isNullOrEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        encoded.forEach { ch ->
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }
                ch == '\\' -> escaped = true
                ch == ',' -> {
                    if (current.isNotEmpty()) out += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    /** 筛选条件 → 落库实体（新建用；`id`/时间戳由调用方给）。 */
    fun toEntity(
        id: String,
        bookId: String,
        name: String,
        filter: TxFilter,
        sortOrder: Int,
        now: Long,
        deviceId: String,
    ): BillFilterEntity = BillFilterEntity(
        id = id,
        bookId = bookId,
        name = name,
        keyword = filter.keyword.trim().ifEmpty { null },
        type = filter.type,
        fromEpochDay = filter.fromEpochDay,
        toEpochDay = filter.toEpochDay,
        categoryIds = encodeIds(filter.categoryIds),
        accountIds = encodeIds(filter.accountIds),
        tagIds = encodeIds(filter.tagIds),
        amountMinCents = filter.amountMinCents,
        amountMaxCents = filter.amountMaxCents,
        excludeFromStats = filter.excludeFromStats,
        excludeFromBudget = filter.excludeFromBudget,
        includeTransfer = filter.includeTransfer,
        sortOrder = sortOrder,
        createdAt = now,
        updatedAt = now,
        deviceId = deviceId,
    )

    /** 落库实体 → 筛选条件（点组合行时回填筛选页）。 */
    fun toFilter(entity: BillFilterEntity): TxFilter = TxFilter(
        keyword = entity.keyword.orEmpty(),
        type = entity.type,
        fromEpochDay = entity.fromEpochDay,
        toEpochDay = entity.toEpochDay,
        categoryIds = decodeIds(entity.categoryIds),
        accountIds = decodeIds(entity.accountIds),
        tagIds = decodeIds(entity.tagIds),
        amountMinCents = entity.amountMinCents,
        amountMaxCents = entity.amountMaxCents,
        excludeFromStats = entity.excludeFromStats,
        excludeFromBudget = entity.excludeFromBudget,
        includeTransfer = entity.includeTransfer,
    )
}
