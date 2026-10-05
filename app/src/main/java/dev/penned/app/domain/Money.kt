package dev.penned.app.domain

/**
 * 金额工具。存储单位恒为"分"（Long），D001。
 */
object Money {

    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        val whole = abs / 100
        val frac = (abs % 100).toString().padStart(2, '0')
        return "$sign$whole.$frac"
    }

    /** 金额输入框文本 → 分。空/非法/0 位返回 null。整数部分上限 9 位（约千万级）。 */
    fun parseToCents(input: String): Long? {
        val s = input.trim()
        if (s.isEmpty()) return null
        val normalized = if (s.startsWith(".")) "0$s" else s
        if (!Regex("""^\d+(\.\d{1,2})?$""").matches(normalized)) return null
        val intPart = normalized.substringBefore('.').trimStart('0').ifEmpty { "0" }
        if (intPart.length > 9) return null
        val frac = normalized.substringAfter('.', "").padEnd(2, '0')
        return intPart.toLong() * 100 + frac.toLong()
    }
}

/**
 * 金额键盘输入状态机（纯函数，便于测试）。current 为原始输入文本，"" 表示未输入。
 */
object MoneyInput {

    private const val MAX_INT_DIGITS = 9
    private const val MAX_FRAC_DIGITS = 2

    fun append(current: String, key: Char): String {
        return when {
            key == '.' -> if (current.isEmpty()) "0." else if (!current.contains('.')) "$current." else current
            key.isDigit() -> {
                if (current.contains('.')) {
                    val fracLen = current.length - current.indexOf('.') - 1
                    if (fracLen >= MAX_FRAC_DIGITS) current else current + key
                } else {
                    val intLen = current.trimStart('0').length
                    if (intLen >= MAX_INT_DIGITS) current else if (current == "0") key.toString() else current + key
                }
            }
            else -> current
        }
    }

    fun backspace(current: String): String = if (current.isEmpty()) "" else current.dropLast(1)

    fun clear(): String = ""
}
