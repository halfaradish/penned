package dev.penned.app.domain

import java.time.LocalDate

/**
 * 一句话解析出来的草稿（M4-3，规格 §三 M4-3）。
 *
 * **它只是草稿**：解析完进结果卡，类型/金额/分类/日期/备注每一行用户都能改，确认后才入账（§二·6）。
 * 分类不在这里——交给关键词规则（`KeywordRules.match`），命中的来源由 UI 标记。
 */
data class QuickDraft(
    val type: TxType,
    val amountCents: Long,
    /** 备注 = 剔掉日期词与金额后的剩余文本（压过空格）；没有剩余就是 null。 */
    val note: String?,
    /** 命中的日期词换算出的"哪一天"（epochDay）；没命中就是 null（= 今天）。 */
    val epochDay: Long?,
    /** 命中的日期词原文（结果卡上回显"昨天"这类词）；没命中为 null。 */
    val dayWord: String?,
)

/** 一句话解析的结果：成功 / 没找到金额 / 输入是空的。 */
sealed interface QuickParseResult {
    data class Ok(val draft: QuickDraft) : QuickParseResult

    /** **没有金额 = 解析失败**（规格 §三 M4-3：提示"没找到金额"，不猜）。 */
    data object NoAmount : QuickParseResult

    data object Empty : QuickParseResult
}

/**
 * 一句话记账的本地解析器（M4-3，纯函数 + 表驱动）。
 *
 * 口径（规格 §三 M4-3，逐条钉在 `QuickParseTest` 里）：
 * - **金额**：交 `MoneyParse`（千分位 / 全角数字 / `元` / `块` 都吃）；**取最后一个金额候选**——
 *   `肯打鸡花了15` → 15；紧跟量词（个/张/杯/碗/件/只/包…）的整数**不算金额**（`买了2个包子花了10` → 10）；
 *   一个候选都没有 = [QuickParseResult.NoAmount]（提示"没找到金额"，**不猜**）
 * - **类型**：出现 `收到 / 工资 / 报销 / 退款 / 利息 / 红包` 任一词 → 收入，否则支出
 * - **日期词**：`今天 / 昨天 / 前天 / 大前天 / N天前 / 上周X / 周X / 星期X / 本月X日 / M月D日`；
 *   缺省 = 今天（`epochDay = null`）。`周X`/`星期X` 取**最近一次**（本周还没到就退到上周）；
 *   `M月D日` 若今年还没到就退到去年——记账记的是已经发生的事
 * - **备注** = 剔掉日期词与金额候选后的剩余文本（去首尾空白、把连续空白压成一个空格）
 *
 * 时间只到"哪一天"：时分由记录时的当前时刻提供（用户可在结果卡里连时分一起改，走 D097 那个弹窗）。
 */
object QuickParse {

    /** 金额候选：可带货币符号、千分位、小数；全角数字由 `MoneyParse` 归一。 */
    private val AMOUNT = Regex("""[¥￥]?[0-9０-９][0-9０-９,，]*(?:[.．][0-9０-９]+)?\s*(?:元|块)?""")

    /** 量词：紧跟其后的整数是"几个/几杯"，不是钱（`买了2个包子花了10` → 10）。 */
    private const val QUANTIFIERS = "个张杯碗件只包份瓶斤袋盒双套台本杯碟根支把盒"

    private val INCOME_WORDS = listOf("收到", "工资", "报销", "退款", "利息", "红包", "收入")

    private val WEEKDAYS = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7,
    )

    fun parse(text: String, today: LocalDate): QuickParseResult {
        val input = text.trim()
        if (input.isEmpty()) return QuickParseResult.Empty

        val dates = parseDateWords(input, today)
        val kept = stripAmountCandidates(dates.cleaned)

        val lastAmount = kept.lastAmount
            ?: return QuickParseResult.NoAmount

        val amountCents = MoneyParse.toCents(lastAmount)
            ?: return QuickParseResult.NoAmount

        val type = if (INCOME_WORDS.any { input.contains(it) }) TxType.INCOME else TxType.EXPENSE

        return QuickParseResult.Ok(
            QuickDraft(
                type = type,
                amountCents = amountCents,
                note = normalize(kept.text).ifEmpty { null },
                epochDay = dates.epochDay,
                dayWord = dates.word,
            )
        )
    }

    // ---- 日期词 ----

    private data class DatePart(val cleaned: String, val epochDay: Long?, val word: String?)

    /**
     * 把日期词从文本里摘出来，同时算出它是哪一天。
     *
     * 顺序有讲究：先长后短（`大前天` 不能被 `前天` 抢、`上周三` 不能被 `周三` 抢、
     * `本月 5 日` 与 `10月1日` 各自成词）；命中的第一处即采用（一句话里通常只有一个日期词）。
     */
    private fun parseDateWords(text: String, today: LocalDate): DatePart {
        // 相对日
        // **先长后短**：`大前天` 里含 `前天`，短的先判会把它读成 -2（本片实测踩到）
        val relative = listOf(
            "大前天" to -3L,
            "今天" to 0L, "今日" to 0L, "昨天" to -1L, "昨日" to -1L,
            "前天" to -2L,
        )
        for ((word, delta) in relative) {
            if (text.contains(word)) {
                return DatePart(text.replace(word, " "), today.toEpochDay() + delta, word)
            }
        }

        // N 天前
        Regex("""(\d+|[一二三四五六七八九十]+)\s*天前""").find(text)?.let { match ->
            val count = chineseOrArabic(match.groupValues[1]) ?: return@let
            return DatePart(text.replace(match.value, " "), today.toEpochDay() - count, match.value)
        }

        // 上周X / 本周X / 周三 / 星期三（上周优先，免得被周X抢走）
        Regex("""(上上?周|本周|这周|周|星期)([一二三四五六日天])""").find(text)?.let { match ->
            val day = WEEKDAYS[match.groupValues[2]] ?: return@let
            val prefix = match.groupValues[1]
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val dayInThisWeek = monday.plusDays((day - 1).toLong())
            val target = when (prefix) {
                "上周" -> dayInThisWeek.minusDays(7)
                "上上周" -> dayInThisWeek.minusDays(14)
                else -> if (dayInThisWeek.isAfter(today)) dayInThisWeek.minusDays(7) else dayInThisWeek
            }
            return DatePart(text.replace(match.value, " "), target.toEpochDay(), match.value)
        }

        // 本月 X 日 / X 日
        Regex("""本月\s*(\d{1,2})\s*[日号]""").find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            val target = safeDate(today.year, today.monthValue, day) ?: return@let
            return DatePart(text.replace(match.value, " "), target.toEpochDay(), match.value)
        }

        // M月D日（含 10月1日 / 10月1号）；今年还没到就退到去年
        Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]""").find(text)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            val thisYear = safeDate(today.year, month, day) ?: return@let
            val target = if (thisYear.isAfter(today)) safeDate(today.year - 1, month, day) ?: thisYear else thisYear
            return DatePart(text.replace(match.value, " "), target.toEpochDay(), match.value)
        }

        return DatePart(text, null, null)
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (_: Exception) {
        null // 2月30日 / 13月1日 这类不存在的日期：不当日期词
    }

    /** 中文数字（一到九十九够用：`三天前` / `十五天前`）。 */
    private fun chineseOrArabic(raw: String): Long? {
        raw.toLongOrNull()?.let { return it }
        val digits = mapOf(
            '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
            '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10,
        )
        if (raw.isEmpty()) return null
        if (raw.length == 1) return digits[raw[0]]?.toLong()
        if (raw[0] == '十') return (10 + (digits[raw.getOrNull(1)] ?: 0)).toLong()
        val tens = digits[raw[0]] ?: return null
        if (raw.getOrNull(1) != '十') return null
        val ones = raw.getOrNull(2)?.let { digits[it] } ?: 0
        return (tens * 10 + ones).toLong()
    }

    // ---- 金额候选 ----

    private data class AmountPart(val text: String, val lastAmount: String?)

    /**
     * 摘掉所有金额候选，留下备注文本；同时记住**最后一个**候选
     * （取末 = 一句话里前面往往是数量：`买了2个包子花了10` 要的是 10）。
     * 紧跟量词的整数不算金额（`2个` / `三杯`）。
     */
    private fun stripAmountCandidates(text: String): AmountPart {
        val kept = StringBuilder()
        var cursor = 0
        var last: String? = null
        for (match in AMOUNT.findAll(text)) {
            val next = text.getOrNull(match.range.last + 1)
            val isQuantity = next != null && QUANTIFIERS.contains(next)
            if (!isQuantity) last = match.value.trim()
            kept.append(text, cursor, match.range.first).append(' ')
            cursor = match.range.last + 1
        }
        kept.append(text, cursor, text.length)
        return AmountPart(kept.toString(), last)
    }

    private fun normalize(text: String): String =
        text.replace(Regex("""\s+"""), " ").trim()
}
