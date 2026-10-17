package dev.penned.app.data.importer

/** 认出来的账单来源（也进 `import_batches.sourceKind` / 预览页的"识别结果"行）。 */
enum class BillSource(val label: String, val sourceKind: String) {
    WECHAT("微信支付账单", "WECHAT"),
    ALIPAY("支付宝交易明细", "ALIPAY"),
    GENERIC("通用账单（自己列映射）", "GENERIC"),
}

/**
 * 一家的表头 → 我们需要的列（下标；-1 = 这一家没有这一列）。
 *
 * 只记"在哪一列"，不记列名——列名在 [BillFormat.header] 里（预览页要显示"识别到的来源"）。
 */
data class BillColumnMap(
    val time: Int,
    val amount: Int,
    val direction: Int,
    val merchant: Int = -1,
    val item: Int = -1,
    val status: Int = -1,
    val externalId: Int = -1,
    val subExternalId: Int = -1,
    val payment: Int = -1,
    val category: Int = -1,
    val note: Int = -1,
) {
    val valid: Boolean get() = time >= 0 && amount >= 0 && direction >= 0
}

/** 一家的规则：来源 + 表头列名（**代码常量、不落库、不热更新**，R6/§二·12 同一姿势）。 */
data class BillFormat(val source: BillSource, val header: List<String>)

/**
 * 两家已知来源的规则表（M4-4，规格 §三 M4-4）。
 *
 * 列名逐字来自**真实导出样本**（附 A 实测）：微信表头 11 列里是 `收/支` 与 `金额(元)`；
 * 支付宝 12 列 + 末尾一个无名列（`交易订单号`/`商家订单号` 里带尾随制表符，由 `BillRows.cell` 归一）。
 * **认不出 = 走通用模式**（列映射 UI 在 M4-5），本表不试图猜。
 */
object BillFormats {

    val WECHAT = BillFormat(
        source = BillSource.WECHAT,
        header = listOf(
            "交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)",
            "支付方式", "当前状态", "交易单号", "商户单号", "备注",
        ),
    )

    val ALIPAY = BillFormat(
        source = BillSource.ALIPAY,
        header = listOf(
            "交易时间", "交易分类", "交易对方", "对方账号", "商品说明", "收/支", "金额",
            "收/付款方式", "交易状态", "交易订单号", "商家订单号", "备注",
        ),
    )

    /** 通用模式要用户/LLM 指认的字段（M4-5 的列映射 UI 就按这个顺序列）。 */
    val GENERIC_FIELDS = listOf(
        "时间", "金额", "收支", "商户", "商品", "状态", "单号", "支付方式", "分类",
    )

    /**
     * 表头定位：**同时含"交易时间"与"金额"的那一行**就是表头（实测：微信第 18 行、支付宝第 24 行）。
     * 前置说明行里也可能出现"金额"字样（提示文案），所以要求两样同时出现；找不到返回 -1。
     */
    fun headerRowIndex(rows: List<List<String>>): Int = rows.indexOfFirst { row ->
        val joined = row.joinToString("\u0000")
        joined.contains("交易时间") && joined.contains("金额")
    }

    /**
     * 按表头认来源：**整行签名打分**（不能只看"时间/金额/收支都在不在"——支付宝的表头也满足
     * 微信那三列，谁先判谁赢就错了，本片实测踩到）。判据：必需列齐 + **特征列**在
     * （微信 `商户单号` / 支付宝 `商家订单号`，两家这两个名字不一样）+ 命中列数过半。
     */
    fun detect(header: List<String>): BillFormat? {
        val cells = header.map { BillRowsCellNormalizer.normalize(it) }
        val hits = listOf(WECHAT to "商户单号", ALIPAY to "商家订单号")
            .filter { (_, signature) -> signature in cells }
            .maxByOrNull { (format, _) -> format.header.count { it in cells } }
            ?: return null
        val format = hits.first
        val score = format.header.count { it in cells }
        return if (mapping(cells, format).valid && score * 2 >= format.header.size) format else null
    }

    /** 把表头映射成列下标（**按列名精确匹配**，改了名的导出会落到通用模式）。 */
    fun mapping(header: List<String>, format: BillFormat): BillColumnMap {
        val cells = header.map { BillRowsCellNormalizer.normalize(it) }
        fun indexOf(vararg names: String): Int {
            for (name in names) {
                val i = cells.indexOfFirst { it == name }
                if (i >= 0) return i
            }
            return -1
        }
        return when (format.source) {
            BillSource.WECHAT -> BillColumnMap(
                time = indexOf("交易时间"),
                amount = indexOf("金额(元)", "金额"),
                direction = indexOf("收/支", "收支"),
                merchant = indexOf("交易对方"),
                item = indexOf("商品"),
                status = indexOf("当前状态", "交易状态"),
                externalId = indexOf("交易单号"),
                subExternalId = indexOf("商户单号"),
                payment = indexOf("支付方式", "收/付款方式"),
                category = indexOf("交易分类"),
                note = indexOf("备注"),
            )

            BillSource.ALIPAY -> BillColumnMap(
                time = indexOf("交易时间"),
                amount = indexOf("金额", "金额(元)"),
                direction = indexOf("收/支", "收支"),
                merchant = indexOf("交易对方"),
                item = indexOf("商品说明", "商品"),
                status = indexOf("交易状态", "当前状态"),
                externalId = indexOf("交易订单号"),
                subExternalId = indexOf("商家订单号"),
                payment = indexOf("收/付款方式", "支付方式"),
                category = indexOf("交易分类"),
                note = indexOf("备注"),
            )

            BillSource.GENERIC -> BillColumnMap(time = -1, amount = -1, direction = -1)
        }
    }
}

/**
 * 表头单元格归一：与 `BillRows.cell` 同一口径（trim 掉 `\t`、`/` 当空），
 * 但**保留列名原文里的括号**（`金额(元)` 要能对上）。
 */
private object BillRowsCellNormalizer {
    fun normalize(raw: String): String = raw.trim().trim('\t').let { if (it == "/") "" else it }
}
