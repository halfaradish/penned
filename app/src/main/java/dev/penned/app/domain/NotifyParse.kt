package dev.penned.app.domain

/**
 * 一条通知解析出来的候选（M4-8）。**解析不出金额或方向就整条丢弃**（宁可漏，不可错记）。
 */
data class NotifyDraft(
    /** 通知来自哪个 App（包名）。 */
    val packageName: String,
    val rawText: String,
    val expense: Boolean,
    val amountCents: Long,
)

/**
 * 通知文案解析（M4-8，纯函数 + 表驱动）：**规则 = 代码内常量表**（per App 一组特征词与正则，
 * 不落库、不热更新——R6 裁决；热更新进提案区）。
 *
 * 口径（规格 §三 M4-8）：
 * - 只看**白名单里的 App**（调用方先过滤）；**只处理服务启用后新到的**通知（不回扫历史）
 * - 特征词命中 + 金额能解析出（交 `MoneyParse`）+ 方向能判出 → 才算一条；
 *   **任一不满足就丢弃**（宁可漏，不可错记）
 * - 金额正则 `¥?([\d,]+\.\d{2})`：只认带两位小数的金额（通知里的"12.5"这种半截数字不猜）
 */
object NotifyParse {

    /** 一家的规则：包名 + 特征词 +（可选的）方向词表。 */
    data class Rule(
        val packageName: String,
        val amountHint: List<String>,
        val expenseWords: List<String>,
        val incomeWords: List<String>,
    )

    /**
     * 起步覆盖微信 / 支付宝（两家实测通知文案形态见 `NotifyParseTest` 的脱敏 fixture）。
     *
     * **debug 构型额外收 `com.android.shell`**：规格 §三 M4-8 的验收要求用
     * `adb shell cmd notification post` 造通知走一遍，而 adb 发出来的通知包名就是 shell——
     * 没有这条规则那条验收根本跑不起来。release 不含（用 `BuildConfig.DEBUG` 判定）。
     */
    val RULES: List<Rule> = buildList {
        add(
            Rule(
                packageName = "com.tencent.mm",
                amountHint = listOf("微信支付", "零钱", "收款", "到账", "支付成功", "扣款", "转账", "红包"),
                expenseWords = listOf("支付成功", "付款", "扣款", "支出", "转账", "已支付", "消费"),
                incomeWords = listOf("收款", "到账", "收入", "已收款", "红包到账", "退款"),
            )
        )
        add(
            Rule(
                packageName = "com.eg.android.AlipayGphone",
                amountHint = listOf("支付宝", "花呗", "余额宝", "支付成功", "付款", "收款", "到账", "支出", "收入"),
                expenseWords = listOf("支付成功", "付款", "支出", "扣款", "消费", "花呗", "转账"),
                incomeWords = listOf("收款", "到账", "收入", "已存入", "退款", "收益"),
            )
        )
        if (dev.penned.app.BuildConfig.DEBUG) {
            add(
                Rule(
                    packageName = "com.android.shell",
                    amountHint = listOf("微信支付", "支付宝", "支付成功", "付款", "收款", "到账", "扣款", "转账"),
                    expenseWords = listOf("支付成功", "付款", "支出", "扣款", "消费", "转账"),
                    incomeWords = listOf("收款", "到账", "收入", "已收款", "退款"),
                )
            )
        }
    }


    /** 金额：`¥1,234.56` / `1234.56` / `12.00元`（只认两位小数，避免把时间/单号里的数字当钱）。 */
    private val AMOUNT = Regex("""[¥￥]?\s?([0-9][0-9,]*\.[0-9]{2})\s?元?""")

    /** 从通知里解析出候选；解析不出返回 null（**丢弃**）。 */
    fun parse(packageName: String, title: String?, text: String?): NotifyDraft? {
        val rule = RULES.firstOrNull { it.packageName == packageName } ?: return null
        val body = listOfNotNull(title, text).joinToString(" ").trim()
        if (body.isEmpty()) return null
        // 特征词：整条通知得是"钱"的通知（不然群里喊一句"到账"都会被认）
        if (rule.amountHint.none { body.contains(it) }) return null

        val amountText = AMOUNT.find(body)?.groupValues?.get(1) ?: return null
        val amountCents = MoneyParse.toCents(amountText) ?: return null
        if (amountCents <= 0) return null

        val expense = rule.expenseWords.any { body.contains(it) }
        val income = rule.incomeWords.any { body.contains(it) }
        // 方向判不出（两边都没命中）或两边都命中（"退款到账"这种含糊文案）→ 丢弃，不猜
        if (expense == income) return null

        return NotifyDraft(
            packageName = packageName,
            rawText = body,
            expense = expense,
            amountCents = amountCents,
        )
    }

    /** 通知提醒的文案（本 App 自己发的那条）。 */
    fun alertText(draft: NotifyDraft): String =
        "识别到一笔 ${Money.format(draft.amountCents)} 的${if (draft.expense) "支出" else "收入"}，点这里确认"

    /** 白名单解析：逗号串 → 包名集合（默认空 = 什么都不处理）。 */
    fun whitelist(encoded: String?): Set<String> =
        encoded?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
}
