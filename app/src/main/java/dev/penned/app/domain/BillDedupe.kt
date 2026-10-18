package dev.penned.app.domain

/**
 * 一行账单（字段已归一；**坏行在解析阶段就被判掉**，不会出现在这里）。
 *
 * 它是 M4-5 预览页与落库的输入：`externalId` 用于去重、`accountClue` 走账户映射表、
 * `categoryHint` 是平台分类原文（**按名匹配、绝不新建**留给 M4-5）。
 */
data class BillRow(
    val dateEpochMinute: Long,
    val amountCents: Long,
    val expense: Boolean,
    val note: String?,
    val externalId: String?,
    val accountClue: String?,
    val categoryHint: String?,
    /** 原始行（预览页"看看这一行"用；**不落库**）。 */
    val raw: List<String> = emptyList(),
) {
    /** 无单号时的"疑似重复"键：时间 + 金额 + 方向 + 备注（R15 的退路口径）。 */
    val fallbackKey: String get() = "$dateEpochMinute|$amountCents|${if (expense) "E" else "I"}|${note.orEmpty()}"
}

/**
 * 去重（M4-4，纯函数）：单号优先、无单号退"时间 + 金额 + 方向 + 备注"；**文件内自身也去重**。
 *
 * 实测两家样本单号都齐（微信交易单号 42/42、支付宝 517/517），所以退路规则在真实数据上不会触发；
 * 保留它是给"缺单号那一行"兜底——宁可提示重复，也不要把同一笔记两遍。
 */
object BillDedupe {

    /**
     * 逐行判定，返回 (将导入, 重复)。[existingIds] = 库内已有单号（`TransactionDao.externalIdsInBook`），
     * [existingFallbackKeys] = 库里无单号流水的兜底键（M4-5 拉窗口流水算出来）。
     */
    fun partition(
        rows: List<BillRow>,
        existingIds: Set<String> = emptySet(),
        existingFallbackKeys: Set<String> = emptySet(),
    ): Pair<List<BillRow>, List<BillRow>> {
        val flags = duplicateFlags(rows, existingIds, existingFallbackKeys)
        val importing = ArrayList<BillRow>()
        val duplicates = ArrayList<BillRow>()
        rows.forEachIndexed { index, row -> if (flags[index]) duplicates += row else importing += row }
        return importing to duplicates
    }

    /**
     * 逐行标出"是不是重复"（**与 [partition] 同一口径与同一顺序**）。
     *
     * 为什么要有它：截图预览页要**按屏幕顺序**把每一行都显示出来（重复行也显示、标一下、只是不导入），
     * 按 fallbackKey 反查会把"同一键的第 1 行（该导入的那行）"也一起标成重复——M4-7 改批量时实测踩到。
     */
    fun duplicateFlags(
        rows: List<BillRow>,
        existingIds: Set<String> = emptySet(),
        existingFallbackKeys: Set<String> = emptySet(),
    ): List<Boolean> {
        val seenIds = HashSet<String>()
        val seenFallback = HashSet<String>()
        return rows.map { row ->
            val id = row.externalId
            if (id != null) {
                id in existingIds || !seenIds.add(id)
            } else {
                val key = row.fallbackKey
                key in existingFallbackKeys || !seenFallback.add(key)
            }
        }
    }

    /**
     * 单行归类（**每行只归一类**，规格 §二·16 的优先级）：
     * 坏行（null）→ 重复 → 状态跳过（退款/关闭）→ 不计收支跳过 → 将导入。
     *
     * [status] / [direction] 由调用方按 `BillRows` 的口径先算好；这里只管顺序。
     */
    fun bucket(
        row: BillRow?,
        duplicate: Boolean,
        statusSkipped: Boolean,
        nonCashflow: Boolean,
    ): BillBucket = when {
        row == null -> BillBucket.BAD
        duplicate -> BillBucket.DUPLICATE
        statusSkipped -> BillBucket.STATUS_SKIP
        nonCashflow -> BillBucket.NON_CASHFLOW_SKIP
        else -> BillBucket.IMPORT
    }

    /** 把逐行桶数汇总成预览页那块计数（**跳过与重复分列**，不静默丢）。 */
    fun counts(buckets: List<BillBucket>): BillRowCounts = BillRowCounts(
        total = buckets.size,
        importable = buckets.count { it == BillBucket.IMPORT },
        duplicate = buckets.count { it == BillBucket.DUPLICATE },
        statusSkipped = buckets.count { it == BillBucket.STATUS_SKIP },
        nonCashflowSkipped = buckets.count { it == BillBucket.NON_CASHFLOW_SKIP },
        bad = buckets.count { it == BillBucket.BAD },
    )
}
