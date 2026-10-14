package dev.penned.app.ui.add

import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * 复查 P0 回归（纯函数）：编辑流水只改记一笔页能改的字段，
 * 不计收支/不计预算/导入批次/报销等一律原样保留。
 * 退款/优惠自 M3-2 起**属于**可改字段（D069：金额存净额、退优惠存原值）；
 * 报销三字段自 M3-3 起同样可改（状态/账户/金额，金额缺省 0 = 按净额全额）。
 */
class TxEditTest {

    /** 一条"所有字段都不是默认值"的导入流水：任何字段被重置都会在断言里现形。 */
    private fun fullyNonDefault() = TransactionEntity(
        id = "tx-1",
        bookId = "book-1",
        type = TxType.EXPENSE,
        amountCents = 5000,
        accountId = "acc-1",
        targetAccountId = "acc-9",
        categoryId = "cat-1",
        dateEpochMinute = 1000,
        note = "原备注",
        refundCents = 2000,
        discountCents = 100,
        reimburseState = ReimburseState.PENDING,
        reimburseAccountId = "acc-2",
        reimburseCents = 1500,
        currencyCode = "USD",
        rate = 7.12,
        address = "上海市",
        feeCents = 300,
        excludeFromStats = true,
        excludeFromBudget = true,
        source = TxSource.IMPORT,
        importBatchId = "batch-x",
        creatorDeviceId = "creator-other",
        createdAt = 111L,
        updatedAt = 222L,
        deleted = true,
        deviceId = "device-other",
    )

    private val editable = TxEdit.Editable(
        type = TxType.TRANSFER,
        amountCents = 999,
        accountId = "acc-3",
        targetAccountId = "acc-4",
        categoryId = null,
        dateEpochMinute = 7,
        note = "新备注",
        feeCents = 55,
        refundCents = 300,
        discountCents = 20,
        reimburseState = ReimburseState.DONE,
        reimburseAccountId = "acc-5",
        reimburseCents = 800,
        debtId = "debt-1",
    )

    @Test
    fun `applyEdit keeps every field the add screen cannot edit`() {
        val original = fullyNonDefault()
        val edited = TxEdit.applyEdit(original, editable, now = 5000L, deviceId = "device-1")

        // 用户改的
        assertEquals(TxType.TRANSFER, edited.type)
        assertEquals(999L, edited.amountCents)
        assertEquals("acc-3", edited.accountId)
        assertEquals("acc-4", edited.targetAccountId)
        assertEquals(null, edited.categoryId)
        assertEquals(7L, edited.dateEpochMinute)
        assertEquals("新备注", edited.note)
        assertEquals(55L, edited.feeCents)
        assertEquals(300L, edited.refundCents)
        assertEquals(20L, edited.discountCents)
        assertEquals(ReimburseState.DONE, edited.reimburseState)
        assertEquals("acc-5", edited.reimburseAccountId)
        assertEquals(800L, edited.reimburseCents)
        assertEquals("debt-1", edited.debtId)
        assertEquals(5000L, edited.updatedAt)
        assertEquals("device-1", edited.deviceId)

        // 本页管不着的：一个都不能丢
        assertEquals("tx-1", edited.id)
        assertEquals("book-1", edited.bookId)
        assertEquals("USD", edited.currencyCode)
        assertEquals(7.12, edited.rate!!, 1e-9)
        assertEquals("上海市", edited.address)
        assertTrue(edited.excludeFromStats)
        assertTrue(edited.excludeFromBudget)
        assertEquals(TxSource.IMPORT, edited.source)
        assertEquals("batch-x", edited.importBatchId)
        assertEquals("creator-other", edited.creatorDeviceId)
        assertEquals(111L, edited.createdAt)
        assertTrue(edited.deleted)
    }

    /**
     * 白名单守卫：applyEdit 改动的字段集必须**恰好**等于记一笔页能改的字段 + updatedAt/deviceId。
     * 以后谁把新字段加进 applyEdit 的覆盖列表（或反过来漏了），这里会红。
     */
    @Test
    fun `applyEdit changes exactly the editable whitelist`() {
        val original = fullyNonDefault()
        val edited = TxEdit.applyEdit(original, editable, now = 5000L, deviceId = "device-1")

        val changed = mutableSetOf<String>()
        for (f in TransactionEntity::class.java.declaredFields) {
            if (Modifier.isStatic(f.modifiers) || f.isSynthetic) continue
            f.isAccessible = true
            if (f.get(original) != f.get(edited)) changed += f.name
        }

        assertEquals(
            setOf(
                "type", "amountCents", "accountId", "targetAccountId", "categoryId",
                "dateEpochMinute", "note", "feeCents", "refundCents", "discountCents",
                "reimburseState", "reimburseAccountId", "reimburseCents", "debtId",
                "updatedAt", "deviceId",
            ),
            changed,
        )
    }

    @Test
    fun `newTransaction writes manual defaults and keeps id fresh`() {
        val tx = TxEdit.newTransaction(bookId = "book-1", editable = editable, now = 5000L, deviceId = "device-1")

        assertEquals("book-1", tx.bookId)
        assertEquals(999L, tx.amountCents)
        assertEquals(TxSource.MANUAL, tx.source)
        assertEquals("device-1", tx.creatorDeviceId)
        assertEquals(5000L, tx.createdAt)
        assertEquals(5000L, tx.updatedAt)
        assertEquals(300L, tx.refundCents)
        assertEquals(20L, tx.discountCents)
        assertEquals(ReimburseState.DONE, tx.reimburseState)
        assertEquals("acc-5", tx.reimburseAccountId)
        assertEquals(800L, tx.reimburseCents)
        assertEquals("debt-1", tx.debtId)
        assertEquals(null, tx.importBatchId)
        assertFalse(tx.excludeFromStats)
        assertFalse(tx.excludeFromBudget)
        assertFalse(tx.deleted)
        assertTrue(tx.id.isNotBlank())
    }
}
