package dev.penned.app.domain

/**
 * 记一笔键盘的表达式求值（specs/M1.md S2）。
 * 表达式如 "12+8"、"3.5×2"；以"分"为最小单位结算；除零/非法输入求值返回 null。
 * 交互约定（对标一木）：表达式含运算符时右下键显示 "="，求值后显示 "完成"。
 */
object MoneyExpression {

    const val OP_ADD = '+'
    const val OP_SUB = '-'
    const val OP_MUL = '×'
    const val OP_DIV = '÷'
    private val OPERATORS = charArrayOf(OP_ADD, OP_SUB, OP_MUL, OP_DIV)
    private const val MAX_INT_DIGITS = 9
    private const val MAX_FRAC_DIGITS = 2

    fun isOperator(c: Char): Boolean = c in OPERATORS

    fun hasPendingOperator(expr: String): Boolean = expr.any { isOperator(it) }

    /** 追加一个键（数字/小数点/运算符）。非法输入静默忽略，返回新表达式。 */
    fun append(expr: String, key: String): String {
        if (key.length != 1) return expr
        val c = key[0]
        return when {
            c.isDigit() -> appendDigit(expr, c)
            c == '.' -> appendDot(expr)
            isOperator(c) -> appendOperator(expr, c)
            else -> expr
        }
    }

    private fun lastSegment(expr: String): String =
        expr.substringAfterLast('+').substringAfterLast('-').substringAfterLast('×').substringAfterLast('÷')

    private fun appendDigit(expr: String, c: Char): String {
        val seg = lastSegment(expr)
        if (seg.contains('.')) {
            val fracLen = seg.length - seg.indexOf('.') - 1
            return if (fracLen >= MAX_FRAC_DIGITS) expr else expr + c
        }
        if (seg.trimStart('0').length >= MAX_INT_DIGITS) return expr
        return if (seg == "0") expr.dropLast(1) + c else expr + c
    }

    private fun appendDot(expr: String): String {
        val seg = lastSegment(expr)
        if (seg.contains('.')) return expr
        return if (seg.isEmpty()) "${expr}0." else "$expr."
    }

    private fun appendOperator(expr: String, op: Char): String {
        if (expr.isEmpty()) return expr
        val last = expr.last()
        return when {
            isOperator(last) -> expr.dropLast(1) + op
            last == '.' -> expr
            else -> expr + op
        }
    }

    /** 求值为分。除零/空/非法 → null。乘除优先（链式从左到右），再加减。 */
    fun evaluate(expr: String): Long? {
        if (expr.isEmpty() || hasLeadingOrTrailingOperator(expr)) return null
        val tokens = tokenize(expr) ?: return null
        if (tokens.size == 1) return (tokens[0] as? Token.Num)?.cents
        // 先乘除：线性扫描，链式归约
        val pass1 = ArrayList<Token>()
        var pending = (tokens[0] as? Token.Num)?.cents ?: return null
        var i = 1
        while (i < tokens.size) {
            val op = (tokens[i] as? Token.Op)?.c ?: return null
            val num = (tokens.getOrNull(i + 1) as? Token.Num)?.cents ?: return null
            when (op) {
                OP_MUL -> pending = mul(pending, num)
                OP_DIV -> pending = div(pending, num) ?: return null
                OP_ADD -> {
                    pass1.add(Token.Num(pending)); pass1.add(Token.Op(op)); pending = num
                }
                OP_SUB -> {
                    pass1.add(Token.Num(pending)); pass1.add(Token.Op(op)); pending = num
                }
            }
            i += 2
        }
        pass1.add(Token.Num(pending))
        // 再加减
        var acc = (pass1.getOrNull(0) as? Token.Num)?.cents ?: return null
        var j = 1
        while (j < pass1.size) {
            val op = pass1[j] as? Token.Op ?: return null
            val num = pass1.getOrNull(j + 1) as? Token.Num ?: return null
            acc = when (op.c) {
                OP_ADD -> add(acc, num.cents)
                OP_SUB -> add(acc, -num.cents)
                else -> return null
            }
            j += 2
        }
        return acc
    }

    private fun hasLeadingOrTrailingOperator(expr: String): Boolean =
        isOperator(expr.first()) || isOperator(expr.last())

    private sealed class Token {
        data class Num(val cents: Long) : Token()
        data class Op(val c: Char) : Token()
    }

    private fun tokenize(expr: String): List<Token>? {
        val tokens = ArrayList<Token>()
        var seg = StringBuilder()
        var ok = true
        fun flush() {
            if (seg.isEmpty()) return
            val cents = Money.parseToCents(seg.toString())
            if (cents == null) {
                ok = false
                return
            }
            tokens.add(Token.Num(cents))
            seg = StringBuilder()
        }
        for (c in expr) {
            if (isOperator(c)) {
                flush()
                if (!ok) return null
                if (tokens.isEmpty() || tokens.last() is Token.Op) return null
                tokens.add(Token.Op(c))
            } else {
                seg.append(c)
            }
        }
        flush()
        if (!ok) return null
        if (tokens.isEmpty() || tokens.last() is Token.Op) return null
        return tokens
    }

    private fun add(a: Long, b: Long): Long = a + b
    private fun mul(a: Long, b: Long): Long = java.math.BigDecimal(a)
        .multiply(java.math.BigDecimal(b))
        .divide(java.math.BigDecimal(100), 0, java.math.RoundingMode.HALF_UP)
        .toLong()

    private fun div(a: Long, b: Long): Long? {
        if (b == 0L) return null
        return java.math.BigDecimal(a)
            .multiply(java.math.BigDecimal(100))
            .divide(java.math.BigDecimal(b), 0, java.math.RoundingMode.HALF_UP)
            .toLong()
    }
}
