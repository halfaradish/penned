package dev.penned.app.domain

import java.time.LocalDate

/**
 * 债务页面的文案（M3-4）。纯函数集中一处：列表 / 详情 / 编辑三个页面共用同一套日期与摘要口径，
 * 各写一份必然漂移（"到期 10月14日" vs "2026/10/14"）。表驱动测试在 `DebtTextTest`。
 */
object DebtText {

    /** 完整日期："2026年10月14日"（编辑页与详情页的字段值）。 */
    fun fullDate(epochDay: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        return "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
    }

    /** 到期日："10月14日"，跨年才带年份；没设就写「未设到期」（列表副标题用）。 */
    fun dueLabel(epochDay: Long?, today: LocalDate): String {
        if (epochDay == null) return "未设到期"
        val d = LocalDate.ofEpochDay(epochDay)
        return if (d.year == today.year) "${d.monthValue}月${d.dayOfMonth}日" else fullDate(epochDay)
    }

    /** 列表与详情的两数摘要："本金 1,000.00 · 已还 400.00"。 */
    fun principalRepaidLabel(principalCents: Long, repaidCents: Long): String =
        "本金 ${Money.format(principalCents)} · 已还 ${Money.format(repaidCents)}"

    /** 债务详情页的主按钮：我欠人 → 记一笔还款；人欠我 → 记一笔收款。 */
    fun settleCtaLabel(direction: DebtDirection): String =
        if (direction == DebtDirection.BORROW) "记一笔还款" else "记一笔收款"
}
