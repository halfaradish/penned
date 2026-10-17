package dev.penned.app.domain

import java.time.LocalDate
import java.time.ZoneId

/** 一行的收支方向（规格里"不计收支/其他"直接判成跳过，不进流水）。 */
enum class BillDirection { EXPENSE, INCOME, SKIP }

/** 交易状态：只有明确"成功"的才算数（退款/关闭/R13 一律跳过，**不做退款与原单匹配**）。 */
enum class BillStatus { OK, SKIP }

/**
 * 一行的归类（**每行只归一类**，规格 §二·16 的计数优先级）。
 *
 * 优先级：坏行 → 重复 → 状态跳过（退款/关闭）→ 不计收支跳过 → 将导入。
 * 实测样本里"不计收支"与"退款/关闭"**重叠**（支付宝 8 行两条都中）——所以先判状态，
 * 预览页因此可能显示"不计收支 0 条、退款/关闭 9 条"，那是对的，不是漏算。
 */
enum class BillBucket { IMPORT, DUPLICATE, STATUS_SKIP, NON_CASHFLOW_SKIP, BAD }

/** 分列计数（预览页那块"总行数 / 将导入 / 重复 / 跳过…"）。 */
data class BillRowCounts(
    val total: Int = 0,
    val importable: Int = 0,
    val duplicate: Int = 0,
    val statusSkipped: Int = 0,
    val nonCashflowSkipped: Int = 0,
    val bad: Int = 0,
) {
    val skipped: Int get() = statusSkipped + nonCashflowSkipped + bad
}

/**
 * 账单行的**字段口径**（M4-4，纯函数 + 表驱动）。
 *
 * 两家导出（微信 / 支付宝）与通用模式共用这一份判断；UI 与导入器都不许自己写一套。
 * 口径逐条来自规格 §三 M4-4 与实测样本（附 A）：
 * - `/` 是两家的**空值占位** → 归一成空；**每个字段 trim（含 `\t`）**（支付宝单号每行带尾随制表符，
 *   不 trim 则去重永远不命中）
 * - 金额：文本交 `MoneyParse`（`¥1,234.56` / 带 `元`），数值（xlsx 单元格）按 `Math.round(v*100)`；
 *   空 / `-` / 非数字 = 坏行（返回 null）
 * - 时间：文本 `2026-10-17 12:30[:00]`（容 `-` 与 `/` 分隔）→ `dateEpochMinute`；
 *   数值 = **Excel 序列日期**（基准 1899-12-30）→ 同上。**必须过时区换算**（墙钟 → 真 epoch 分钟）
 * - 方向/状态词表见下；`&` 组合的支付方式**只取第一段**（`X储蓄卡(1234)&红包` → `X储蓄卡(1234)`）
 */
object BillRows {

    /** Excel 序列日期的基准（1900 日期系统，含"1900 是闰年"那个历史坑的标准修正）。 */
    private val SERIAL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    private val EXPENSE_WORDS = setOf("支出", "付款", "支")
    private val INCOME_WORDS = setOf("收入", "收款", "收")
    private val SKIP_WORDS = setOf("不计收支", "其他", "中性交易", "/", "")

    /** 成功态（实测命中：微信"支付成功/对方已收钱/已存入零钱"，支付宝"交易成功"）。 */
    private val OK_STATUS = setOf(
        "支付成功", "交易成功", "已收款", "收款成功", "充值成功", "对方已收钱", "已存入零钱", "交易完成",
    )

    /** 跳过态（R13：退款不冲原单、关闭行不记账）。 */
    private val SKIP_STATUS = setOf(
        "已退款", "退款成功", "部分退款", "交易关闭", "已关闭", "已全额退款", "退款",
    )

    /** 单元格归一：`/`（空值占位）与纯空白 → 空串；其余去首尾空白（含 `\t`）。 */
    fun cell(raw: String?): String {
        val text = raw?.trim().orEmpty()
        return if (text == "/") "" else text
    }

    /** 金额：文本走宽松解析，数值按 `Math.round`；坏值返回 null（**不猜**）。 */
    fun amountCents(text: String?): Long? {
        val value = cell(text)
        if (value.isEmpty() || value == "-") return null
        // 纯数值（xlsx 单元格 / 少数导出）——含小数点的也走这里，免得 MoneyParse 再纠一次全角
        value.toDoubleOrNull()?.let { return MoneyParse.centsFromNumber(it) }
        return MoneyParse.toCents(value)
    }

    /** 金额（xlsx 数值单元格直给）。 */
    fun amountCentsFromNumber(value: Double): Long = MoneyParse.centsFromNumber(value)

    /** 时间（文本）→ `dateEpochMinute`；认不出返回 null。 */
    fun minuteFromText(text: String?): Long? {
        val value = cell(text)
        if (value.isEmpty()) return null
        val match = Regex(
            """^(\d{4})[-/](\d{1,2})[-/](\d{1,2})[ T](\d{1,2}):(\d{2})(?::(\d{2}))?$"""
        ).find(value) ?: return null
        val (year, month, day, hour, minute) = match.destructured
        return try {
            LocalDate.of(year.toInt(), month.toInt(), day.toInt())
                .atTime(hour.toInt(), minute.toInt())
                .atZone(ZoneId.systemDefault())
                .toEpochSecond() / 60
        } catch (_: Exception) {
            null // 2026-02-30 这类
        }
    }

    /**
     * 时间（**Excel 序列日期数值**）→ `dateEpochMinute`。
     *
     * 基准 1899-12-30：**整数部分是"第几天"**（从基准日往后推），**小数部分是当天的秒数**
     * （`round(frac × 86400)`，实测样本逐秒对上）。两段分开算、目标日在系统时区上取 0 点——
     * **不能**写成"1899-12-30 0 点的 epoch 秒 + serial×86400"：1899 年那会儿不少时区还是 LMT
     * （Asia/Shanghai 是 +08:05:43），拿它当锚点会整体偏 5 分 43 秒（本片实踩）。
     */
    fun minuteFromSerial(serial: Double): Long? {
        if (serial <= 0 || serial > 200_000) return null // 不合理范围：不是日期
        val days = Math.floor(serial).toLong()
        val secondsOfDay = Math.round((serial - days) * 86_400.0)
        return try {
            val date = SERIAL_EPOCH.plusDays(days)
            date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond() / 60 + secondsOfDay / 60
        } catch (_: Exception) {
            null
        }
    }

    /** 方向：`支出`/`收入` 认得出；`不计收支`、`其他`、空 = 跳过；其它（含冻结尾注）= 坏值 null。 */
    fun direction(text: String?): BillDirection? {
        val value = cell(text)
        if (value in SKIP_WORDS) return BillDirection.SKIP
        if (value in EXPENSE_WORDS) return BillDirection.EXPENSE
        if (value in INCOME_WORDS) return BillDirection.INCOME
        // 带后缀的写法（"支出（已退款）"这类）：包含关系再兜一层
        if (EXPENSE_WORDS.any { value.startsWith(it) }) return BillDirection.EXPENSE
        if (INCOME_WORDS.any { value.startsWith(it) }) return BillDirection.INCOME
        if (value.contains("不计收支")) return BillDirection.SKIP
        return null
    }

    /** 状态：成功态 = OK；退款/关闭 = SKIP；**空或认不出的词按 OK**（两家都有导出没这一列的形态）。 */
    fun status(text: String?): BillStatus {
        val value = cell(text)
        if (value.isEmpty()) return BillStatus.OK
        if (value in SKIP_STATUS) return BillStatus.SKIP
        if (value in OK_STATUS) return BillStatus.OK
        // 认不出的状态：含"退款/关闭"就跳过（"已退款成功"这类变体），其余当成功
        val skipByWord = listOf("退款", "已关闭", "关闭", "失败", "取消").any { value.contains(it) }
        return if (skipByWord) BillStatus.SKIP else BillStatus.OK
    }

    /** 备注 = 交易对方 + 商品/商品说明（同值去重）+ 原「备注」列（非空才加）。 */
    fun note(merchant: String?, item: String?, extra: String?): String? {
        val parts = ArrayList<String>(3)
        val m = cell(merchant)
        val i = cell(item)
        val e = cell(extra)
        if (m.isNotEmpty()) parts += m
        if (i.isNotEmpty() && i != m) parts += i
        if (e.isNotEmpty() && e != m && e != i) parts += e
        return parts.joinToString(" · ").ifEmpty { null }
    }

    /**
     * 账户线索（`支付方式 / 收付款方式`）：**`&` 组合只取第一段**——`&` 后面是红包/立减/抵价券，
     * 不是账户；**掩码照留**（`(1234)` 留在名字里，B3 裁决：不同卡号本就是不同账户）。
     */
    fun accountClue(text: String?): String? {
        val value = cell(text)
        if (value.isEmpty()) return null
        val first = value.split('&').firstOrNull()?.trim().orEmpty()
        return first.ifEmpty { null }
    }

    /** 外部单号：主单号优先，缺了退副单号；都没有 = null（`/` 已被 [cell] 归一成空）。 */
    fun externalId(primary: String?, secondary: String? = null): String? {
        val a = cell(primary)
        if (a.isNotEmpty()) return a
        val b = cell(secondary)
        return b.ifEmpty { null }
    }

    /** 支付宝「交易分类」这类平台词汇：只回原文，**按名匹配与"绝不新建"在 M4-5 做**。 */
    fun categoryHint(text: String?): String? = cell(text).ifEmpty { null }
}
