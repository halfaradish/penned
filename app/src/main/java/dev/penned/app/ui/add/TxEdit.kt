package dev.penned.app.ui.add

import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import java.util.UUID

/**
 * 记一笔保存时的实体构造/合并（纯函数，可脱离 Android 单测）。
 *
 * 编辑必须走 [applyEdit]：它只覆盖记一笔页能改的字段，其余（不计收支/不计预算/
 * 导入批次/报销/币种/汇率/地址/来源/创建者/软删…）一律原样保留。
 * 曾经用"重建实体"代替，编辑一次就把这些字段全部重置为默认值（2026-10-06 复查 P0）。
 */
object TxEdit {

    /**
     * 记一笔页能改的字段（[Editable] 之外的一切字段编辑时不可变）。
     * 退款/优惠 M3-2 起可改——注意 `amountCents` 存的是**净额**，退款/优惠是各自原值（D069）。
     * 字段**有意不给默认值**：新增调用点必须显式写出这三个数，免得"忘了传"被静默当成"清空"。
     */
    data class Editable(
        val type: TxType,
        val amountCents: Long,
        val accountId: String?,
        val targetAccountId: String?,
        val categoryId: String?,
        val dateEpochMinute: Long,
        val note: String?,
        val feeCents: Long,
        val refundCents: Long,
        val discountCents: Long,
    )

    /** 编辑：在原记录上覆盖本页字段。 */
    fun applyEdit(
        original: TransactionEntity,
        editable: Editable,
        now: Long,
        deviceId: String,
    ): TransactionEntity = original.copy(
        type = editable.type,
        amountCents = editable.amountCents,
        accountId = editable.accountId,
        targetAccountId = editable.targetAccountId,
        categoryId = editable.categoryId,
        dateEpochMinute = editable.dateEpochMinute,
        note = editable.note,
        feeCents = editable.feeCents,
        refundCents = editable.refundCents,
        discountCents = editable.discountCents,
        updatedAt = now,
        deviceId = deviceId,
    )

    /** 新建：手记流水默认 source=MANUAL。 */
    fun newTransaction(
        bookId: String,
        editable: Editable,
        now: Long,
        deviceId: String,
    ): TransactionEntity = TransactionEntity(
        id = UUID.randomUUID().toString(),
        bookId = bookId,
        type = editable.type,
        amountCents = editable.amountCents,
        accountId = editable.accountId,
        targetAccountId = editable.targetAccountId,
        categoryId = editable.categoryId,
        dateEpochMinute = editable.dateEpochMinute,
        note = editable.note,
        feeCents = editable.feeCents,
        refundCents = editable.refundCents,
        discountCents = editable.discountCents,
        source = TxSource.MANUAL,
        creatorDeviceId = deviceId,
        createdAt = now,
        updatedAt = now,
        deviceId = deviceId,
    )
}
