package dev.penned.app.domain

/** 账户大类的猜测结果——就是我们自己的 [AccountType]，只是"猜"出来的缺省值。 */
object AccountGuess {

    /** 信贷：先花后还、欠款挂在信贷组（B2 裁决）。 */
    private val CREDIT = listOf(
        "花呗", "白条", "信用卡", "京东金融", "借呗", "有钱花", "美团月付", "抖音月付", "分付", "月付", "分期",
    )

    /** 理财：钱在里面但不是随时花的（余额宝/零钱通这类）。 */
    private val INVEST = listOf("余额宝", "零钱通", "理财", "基金", "定期", "黄金", "国债", "保险理财", "股票")

    /** 负债：明确的欠款账户。 */
    private val DEBT = listOf("负债", "欠款")

    /**
     * 按支付方式的名字猜账户类型（M4-5，B2 裁决：**按名猜 + 预览页可改**）。
     *
     * 猜错不要紧——预览页那一行能改；猜对了省一次手点。判据只看名字里有没有关键词，
     * 顺序：信贷 → 理财 → 负债 → 资金（`DEBIT`，比 `CASH` 更贴"某个具体的支付渠道"）。
     */
    fun typeFor(name: String): AccountType {
        val value = name.trim()
        if (value.isEmpty()) return AccountType.CASH
        if (CREDIT.any { value.contains(it) }) return AccountType.CREDIT
        if (INVEST.any { value.contains(it) }) return AccountType.INVEST
        if (DEBT.any { value.contains(it) }) return AccountType.DEBT
        return AccountType.DEBIT
    }

    /** 猜出来的分组名（资产页分组：资金 / 信贷 / 理财 / 负债），预览页显示用。 */
    fun groupLabel(type: AccountType): String = when (type) {
        AccountType.CASH, AccountType.DEBIT -> "资金"
        AccountType.CREDIT -> "信贷"
        AccountType.INVEST -> "理财"
        AccountType.DEBT -> "负债"
    }
}
