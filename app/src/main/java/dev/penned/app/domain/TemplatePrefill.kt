package dev.penned.app.domain

import dev.penned.app.data.db.dao.TemplateWithTags
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.data.db.entity.TxTemplateEntity

/**
 * 模板 → 记一笔的预填（M3-5，纯函数，表驱动测试在 `TemplatePrefillTest`）。
 *
 * 口径：**用模板不直接入库**——进记一笔页预填，用户在记账页确认后才保存（模板是"草稿来源"不是流水）。
 * 反向的「存为模板」把刚保存的流水抽成模板（金额取净额——落库额，退款/优惠不是模板的一部分）。
 */
data class TemplatePrefill(
    val type: TxType,
    val amountCents: Long,
    val categoryId: String?,
    val accountId: String?,
    val targetAccountId: String?,
    val note: String?,
    val feeCents: Long,
    val tagIds: List<String>,
) {
    /** 金额输入框文本（不带千分位，与编辑回填同口径）；0 显示为空（不是"0 元"）。 */
    val amountText: String get() = if (amountCents > 0L) Money.format(amountCents, grouped = false) else ""
}

object Templates {

    /** 模板 → 预填（标签引用由调用方查好传进来）。 */
    fun of(template: TxTemplateEntity, tagIds: List<String>): TemplatePrefill = TemplatePrefill(
        type = template.type,
        amountCents = template.amountCents,
        categoryId = template.categoryId,
        accountId = template.accountId,
        targetAccountId = template.targetAccountId,
        note = template.note,
        feeCents = template.feeCents,
        tagIds = tagIds,
    )

    /** 流水 → 预填（「存为模板」的草稿来源：字段照搬，模板名由用户填）。 */
    fun of(tx: TransactionEntity, tagIds: List<String>): TemplatePrefill = TemplatePrefill(
        type = tx.type,
        amountCents = tx.amountCents,
        categoryId = tx.categoryId,
        accountId = tx.accountId,
        targetAccountId = tx.targetAccountId,
        note = tx.note,
        feeCents = tx.feeCents,
        tagIds = tagIds,
    )

    /** 类型中文（模板列表与预填摘要共用一处，免得"支出/收入/转账"三处各写一份）。 */
    fun typeLabel(type: TxType): String = when (type) {
        TxType.EXPENSE -> "支出"
        TxType.INCOME -> "收入"
        TxType.TRANSFER -> "转账"
    }

    /** 列表行副标题：`支出 · 16.50 · 食品餐饮 · 微信`（转账写两端 `招行 → 现金`）。 */
    fun summaryText(row: TemplateWithTags): String {
        val amount = Money.format(row.amountCents)
        return if (row.type == TxType.TRANSFER) {
            listOfNotNull(
                typeLabel(row.type),
                amount,
                row.accountName?.let { from -> row.targetAccountName?.let { "$from → $it" } ?: from },
            ).joinToString(" · ")
        } else {
            listOfNotNull(
                typeLabel(row.type),
                amount,
                row.categoryName ?: "未分类",
                row.accountName,
            ).joinToString(" · ")
        }
    }
}
