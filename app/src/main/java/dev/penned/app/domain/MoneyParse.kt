package dev.penned.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 宽松金额解析（M4-0，规格 M4 §二·7 / D103）：**M4 三条入账路径共用**——
 * 账单文件的行（M4-4/5）、一句话（M4-3）、LLM 出参（M4-2/6/7）。
 *
 * 与 [Money.parseToCents] 的分工：那个是**金额输入框的契约**（只吃 `^\d+(\.\d{1,2})?$`，
 * 不吃符号 / 千分位 / 全角 / 单位），改宽会破坏光标与回填的既有口径，所以**一个字不动**；
 * 本函数负责"从别处抄来的金额"：`¥1,234.56` / `1,234.56` / `１２３４`（全角）/ `12元` / `12块` / `-12.5`。
 *
 * 口径：
 * - 剥货币符号与单位（`¥` `￥` `元` `块`）、去千分位、全角转半角、丢空白
 * - **解析不出返回 null（不猜）**：空、`-`、`/`（账单里的空值占位）、夹杂文字都算失败
 * - 超过两位小数**四舍五入到分**（真实账单样本全是两位；超两位只可能来自 LLM 回的数字）
 * - 负数按符号位处理（退款/冲正的行会带 `-`）；整数部分超过 12 位直接判失败（防溢出）
 *
 * 单位是**分**（Long），与全仓一致（D001）。
 */
object MoneyParse {

    /** 整数部分位数上限：12 位 ≈ 千亿，远超任何账单；再长就是脏数据或溢出了。 */
    private const val MAX_INT_DIGITS = 12

    private val NUMBER = Regex("""^(\d+(\.\d*)?|\.\d+)$""")

    /** 文本金额 → 分；解析不出返回 null。 */
    fun toCents(text: String): Long? {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return null

        val negative = normalized.startsWith("-")
        val body = if (negative || normalized.startsWith("+")) normalized.substring(1) else normalized
        if (!NUMBER.matches(body)) return null

        val intDigits = body.substringBefore('.').trimStart('0')
        if (intDigits.length > MAX_INT_DIGITS) return null

        val cents = BigDecimal(body).movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        return if (negative) -cents else cents
    }

    /**
     * 数值金额（xlsx 的数值单元格、LLM 回的数字）→ 分。
     *
     * 一律 `Math.round`（不是截断）：浮点表示下 `12.1 * 100 = 1209.9999…` 这类值截断会少 1 分。
     */
    fun centsFromNumber(value: Double): Long = Math.round(value * 100.0)

    /** 全角转半角 + 剥货币符号 / 单位 / 千分位 / 空白。 */
    private fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (raw in text) {
            val c = when (raw) {
                in '０'..'９' -> '0' + (raw - '０')
                '．' -> '.'
                '－', '−' -> '-'
                '＋' -> '+'
                '，' -> ','
                else -> raw
            }
            when {
                c.isWhitespace() -> Unit
                c == ',' -> Unit // 千分位
                c == '¥' || c == '￥' || c == '元' || c == '块' -> Unit
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
