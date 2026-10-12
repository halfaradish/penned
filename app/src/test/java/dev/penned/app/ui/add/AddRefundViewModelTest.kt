package dev.penned.app.ui.add

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.domain.CategoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 退款/优惠的记账侧口径（M3-2，VM 层）：键盘切格各写各的文本、实付行、保存净额、再记清空。
 * 走真库（保存要查分类），但不经 Compose——链路的界面部分在 `AddRefundFlowTest` 里。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class AddRefundViewModelTest {

    private val app: PennedApplication = ApplicationProvider.getApplicationContext()
    private val bookId = "book-refund-vm"

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
        app.settings.setCurrentBook(bookId)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /**
     * 起一个 ViewModel 并**订阅它用到的那几条流**：WhileSubscribed 的 stateIn 没人收时
     * `.value` 恒为初值（空表/null），保存时 `currentBook.value ?: return false` 会静默失败（D055 同型坑）。
     */
    private fun vmWithFlows(): Pair<AddViewModel, CoroutineScope> {
        val vm = AddViewModel(app)
        val scope = CoroutineScope(Dispatchers.Main)
        // 保存要查分类与账本、下面要读 showEquals/paySummary 的**当前值**——这些 stateIn 都是
        // WhileSubscribed，没人收时读到的永远是初值（空表 / null / false）
        scope.launch { vm.categories.collect { } }
        scope.launch { vm.accounts.collect { } }
        scope.launch { vm.showEquals.collect { } }
        scope.launch { vm.paySummary.collect { } }
        idle()
        // 账本 id 走 DataStore（异步读）：订阅 + idle 一次还不够，分类到位才算真的能保存
        awaitVm { vm.categories.value.isNotEmpty() }
        return vm to scope
    }

    /** 真时间轮询 VM 状态（DataStore 的读在别的线程上，虚拟时钟推不出来）。 */
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

    private fun stored(): LongArray? = runBlocking {
        app.database.transactionDao().allInBook(bookId).firstOrNull()
            ?.let { longArrayOf(it.amountCents, it.refundCents, it.discountCents) }
    }

    @Test
    fun `键盘切格：退款与优惠各写各的文本，主金额不受影响`() {
        val (vm, scope) = vmWithFlows()

        vm.pressKey("1"); vm.pressKey("0")
        vm.focusAmountField(AmountField.REFUND)
        vm.pressKey("3")
        vm.focusAmountField(AmountField.DISCOUNT)
        vm.pressKey("2"); vm.pressKey(".")
        assertEquals("10", vm.amountText.value)
        assertEquals("3", vm.refundText.value)
        assertEquals("2.", vm.discountText.value)

        // 主金额的算式待求值时右下键才显示 "="；退款格的算式按保存键时结算，不抢那个键
        vm.focusAmountField(AmountField.REFUND)
        vm.pressKey("+")                       // 退款格：3+
        vm.focusAmountField(AmountField.AMOUNT)
        vm.pressKey("+")                       // 主金额：10+
        idle()
        assertTrue("主金额有待求值算式时应显示 =", vm.showEquals.value)
        assertEquals("3+", vm.refundText.value)

        // 回到退款格求值：3+2 = 5.00（表达式引擎与主金额同一个）
        vm.focusAmountField(AmountField.REFUND)
        vm.pressKey("2")
        vm.evaluate()
        assertEquals("5.00", vm.refundText.value)
        assertEquals("10+", vm.amountText.value)
        scope.cancel()
    }

    @Test
    fun `实付行：金额没输不显示，填了退款或优惠写成算式`() {
        val (vm, scope) = vmWithFlows()
        idle()
        assertNull(vm.paySummary.value)

        vm.focusAmountField(AmountField.REFUND)
        vm.pressKey("3")
        idle()
        assertNull("金额还没输，算式缺项不如不显示", vm.paySummary.value)

        vm.focusAmountField(AmountField.AMOUNT)
        vm.pressKey("2"); vm.pressKey("0")
        idle()
        assertEquals("实付 17.00（20.00 − 退款 3.00）", vm.paySummary.value)

        vm.focusAmountField(AmountField.DISCOUNT)
        vm.pressKey("5")
        idle()
        assertEquals("实付 12.00（20.00 − 退款 3.00 − 优惠 5.00）", vm.paySummary.value)
        scope.cancel()
    }

    @Test
    fun `全额退款存得下去：净额 0、退款存原值`() {
        val (vm, scope) = vmWithFlows()
        vm.selectParent("cat-shopping", hasSubs = false)
        vm.pressKey("1"); vm.pressKey("0"); vm.pressKey("0")
        vm.focusAmountField(AmountField.REFUND)
        vm.pressKey("1"); vm.pressKey("0"); vm.pressKey("0")
        vm.save { }

        val row = awaitDb { stored() }
        assertEquals(0L, row[0])   // 净额 0：余额与统计里这一笔不影响钱
        assertEquals(10000L, row[1])
        assertEquals(0L, row[2])
        scope.cancel()
    }

    @Test
    fun `退款加优惠叠加：净额 = 原价 − 两者，再记会清空并回到主金额`() {
        val (vm, scope) = vmWithFlows()
        vm.selectParent("cat-shopping", hasSubs = false)
        vm.pressKey("2"); vm.pressKey("0"); vm.pressKey("0")
        vm.focusAmountField(AmountField.REFUND)
        vm.pressKey("3"); vm.pressKey("0")
        vm.focusAmountField(AmountField.DISCOUNT)
        vm.pressKey("2"); vm.pressKey("0")
        vm.saveAgain()

        val row = awaitDb { stored() }
        assertEquals(15000L, row[0])
        assertEquals(3000L, row[1])
        assertEquals(2000L, row[2])

        // 再记：金额/备注/退款/优惠都清空，键盘回到主金额（下一笔从零开始）
        idle()
        assertEquals("", vm.amountText.value)
        assertEquals("", vm.refundText.value)
        assertEquals("", vm.discountText.value)
        assertEquals(AmountField.AMOUNT, vm.amountField.value)
        scope.cancel()
    }
}
