package dev.penned.app.ui.add

import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 记一笔页的**预填口径**（候选池点进来 / 房贷首期进来这几种入口）。
 *
 * 这里钉住的是一个真 bug（M4-8 埋的、改账单列表导入时抓到）：
 * `init` 原来先判 `presetCents != null` → 走房贷预填，而房贷那支写死 `type = EXPENSE`，
 * 于是**带金额的通知/截图预填一律变成支出**（通知里的收入 +88.00 会被预填成支出）。
 * 现在的顺序：编辑 > 债务 > **来源（含方向）** > 房贷。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class AddPrefillTest {

    private val app: PennedApplication = ApplicationProvider.getApplicationContext()
    private val bookId = "book-prefill"

    @Before
    fun setUp() = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "预填账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "t")
        )
        app.settings.setCurrentBook(bookId)
    }

    private fun vm(
        cents: Long? = null,
        type: TxType? = null,
        source: TxSource = TxSource.MANUAL,
        note: String? = null,
        day: Long? = null,
    ) = AddViewModel(
        app, editId = null, presetDebtId = null, presetDebtAction = null,
        presetCents = cents, presetDay = day, presetNote = note,
        presetSource = source, presetType = type,
    )

    @Test
    fun `通知来的收入预填仍然是收入`() = runBlocking {
        val model = vm(cents = 8800, type = TxType.INCOME, source = TxSource.NOTIFICATION, note = "微信红包")
        assertEquals(TxType.INCOME, model.type.value)
        assertEquals("88.00", model.amountText.value)
        assertEquals("微信红包", model.note.value)
    }

    @Test
    fun `通知来的支出方向也对`() = runBlocking {
        val model = vm(cents = 1250, type = TxType.EXPENSE, source = TxSource.NOTIFICATION, note = "微信支付 12.50")
        assertEquals(TxType.EXPENSE, model.type.value)
        assertEquals("12.50", model.amountText.value)
    }

    @Test
    fun `房贷首期预填照旧 金额日期备注都进且是支出`() = runBlocking {
        val model = vm(cents = 350_000, note = "首期", day = 20_000L)
        assertEquals(TxType.EXPENSE, model.type.value)
        assertEquals("3500.00", model.amountText.value)
        assertEquals(20_000L, model.dateEpochDay.value)
        assertEquals("首期", model.note.value)
    }

    @Test
    fun `没预填就是干净的新账`() = runBlocking {
        val model = vm()
        assertEquals(TxType.EXPENSE, model.type.value)
        assertEquals("", model.amountText.value)
        assertEquals("", model.note.value)
    }
}
