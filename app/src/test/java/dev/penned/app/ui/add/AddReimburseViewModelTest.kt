package dev.penned.app.ui.add

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.TxType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 报销的记账侧口径（M3-3，VM 层）：报销金额占金额键盘第四格、「全额」的基准是**净额**、
 * 三态/账户/金额的保存与清理、转账清空、再记保留状态。链路界面部分在 `AddReimburseFlowTest`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class AddReimburseViewModelTest {

    private val app: PennedApplication = ApplicationProvider.getApplicationContext()
    private val bookId = "book-reimburse-vm"

    @Before
    fun setUp() = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-shopping", bookId = bookId, name = "购物消费", type = CategoryType.EXPENSE,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.accountDao().upsert(
            AccountEntity(
                id = "acc-wechat", bookId = bookId, name = "微信", type = AccountType.DEBIT,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.settings.setCurrentBook(bookId)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** 起 VM 并订阅用到的流（WhileSubscribed 的 stateIn 没人收时读到的永远是初值）。 */
    private fun vmWithFlows(): Pair<AddViewModel, CoroutineScope> {
        val vm = AddViewModel(app)
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch { vm.categories.collect { } }
        scope.launch { vm.accounts.collect { } }
        scope.launch { vm.claimLabel.collect { } }
        idle()
        // 账本 id 走 DataStore（异步读）：等分类到位再动手，否则保存会静默返回 false
        awaitVm { vm.categories.value.isNotEmpty() }
        return vm to scope
    }

    private fun awaitVm(timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            idle()
            if (predicate()) return
            Thread.sleep(20)
        }
        error("等 VM 状态超时")
    }

    private fun <T> awaitDb(timeoutMs: Long = 5_000, block: suspend () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            idle()
            runBlocking { block() }?.let { return it }
            Thread.sleep(20)
        }
        error("等数据库结果超时")
    }

    private fun stored(): TransactionEntity? = runBlocking {
        app.database.transactionDao().allInBook(bookId).firstOrNull()
    }

    @Test
    fun `报销金额占键盘第四格_全额的基准是净额`() {
        val (vm, scope) = vmWithFlows()
        vm.selectParent("cat-shopping", hasSubs = false)
        vm.pressKey("1"); vm.pressKey("0"); vm.pressKey("0")
        vm.pickReimburseState(ReimburseState.PENDING)
        awaitVm { vm.claimLabel.value == "全额 100.00" }

        // 退款 30 → 净额 70：缺省的"全额"跟着净额走（报销按实付算，不按标价算）
        vm.focusAmountField(AmountField.REFUND)
        vm.pressKey("3"); vm.pressKey("0")
        awaitVm { vm.claimLabel.value == "全额 70.00" }

        // 键盘第四格：键位落到报销金额，主金额与退款都不受影响
        vm.focusAmountField(AmountField.REIMBURSE)
        vm.pressKey("4"); vm.pressKey("0")
        awaitVm { vm.claimLabel.value == "40.00" }
        assertEquals("100", vm.amountText.value)
        assertEquals("30", vm.refundText.value)
        assertEquals("40", vm.reimburseText.value)
        scope.cancel()
    }

    @Test
    fun `保存三态账户金额_改回不报销会清掉附带项`() {
        val (vm, scope) = vmWithFlows()
        vm.selectParent("cat-shopping", hasSubs = false)
        vm.pressKey("1"); vm.pressKey("0"); vm.pressKey("0")
        vm.pickReimburseState(ReimburseState.PENDING)
        vm.pickReimburseAccount("acc-wechat")
        vm.focusAmountField(AmountField.REIMBURSE)
        vm.pressKey("4"); vm.pressKey("0")
        vm.save { }

        val saved = awaitDb { stored() }
        assertEquals(ReimburseState.PENDING, saved.reimburseState)
        assertEquals("acc-wechat", saved.reimburseAccountId)
        assertEquals(4_000L, saved.reimburseCents)

        // 改回「不报销」：账户与金额一起清掉，键盘若在报销格也回主金额
        vm.pickReimburseState(ReimburseState.NONE)
        assertNull(vm.reimburseAccountId.value)
        assertEquals("", vm.reimburseText.value)
        assertEquals(AmountField.AMOUNT, vm.amountField.value)
        scope.cancel()
    }

    @Test
    fun `转账清空报销_再记保留状态与账户只清金额`() {
        val (vm, scope) = vmWithFlows()
        vm.selectParent("cat-shopping", hasSubs = false)
        vm.pressKey("2"); vm.pressKey("0")
        vm.pickReimburseState(ReimburseState.PENDING)
        vm.pickReimburseAccount("acc-wechat")
        vm.focusAmountField(AmountField.REIMBURSE)
        vm.pressKey("5")
        vm.saveAgain()

        val saved = awaitDb { stored() }
        assertEquals(ReimburseState.PENDING, saved.reimburseState)
        assertEquals("acc-wechat", saved.reimburseAccountId)
        assertEquals(500L, saved.reimburseCents)
        // 再记：金额清空，状态与账户留着（一摞单子连着记，不必每笔重选）
        // （清空在落库**之后**：等它真的发生，别只 idle() 一次——同 AddRefundViewModelTest 的那条坑）
        awaitVm { vm.reimburseText.value.isEmpty() }
        assertEquals("", vm.reimburseText.value)
        assertEquals(ReimburseState.PENDING, vm.reimburseState.value)
        assertEquals("acc-wechat", vm.reimburseAccountId.value)

        // 切到转账：三项全清（转账没有报销）
        vm.selectType(TxType.TRANSFER)
        assertEquals(ReimburseState.NONE, vm.reimburseState.value)
        assertNull(vm.reimburseAccountId.value)
        assertEquals("", vm.reimburseText.value)
        scope.cancel()
    }
}
