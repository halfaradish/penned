package dev.penned.app.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** M1 收尾：账单详情「退款」只读展示（退款是流水属性，全额退款行金额为 0）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun row(
        amountCents: Long,
        refundCents: Long = 0,
        discountCents: Long = 0,
        note: String? = null,
    ) = TransactionEntity(
        id = "tx-1",
        bookId = "book-1",
        type = TxType.EXPENSE,
        amountCents = amountCents,
        categoryId = "cat-1",
        dateEpochMinute = 28_800_000L,
        note = note,
        refundCents = refundCents,
        discountCents = discountCents,
        reimburseState = ReimburseState.NONE,
        source = TxSource.IMPORT,
        creatorDeviceId = "creator-1",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        deviceId = "device-1",
    )

    private fun ui(tx: TransactionEntity) = DetailUi(
        tx = tx,
        categoryName = "食品餐饮",
        categoryParentName = null,
        accountName = null,
        targetAccountName = null,
        bookName = "日常账本",
    )

    @androidx.compose.runtime.Composable
    private fun content(tx: TransactionEntity) = DetailContent(
        detail = ui(tx),
        onEdit = {}, onDelete = {}, onToggleStats = {}, onToggleBudget = {}, onBack = {},
    )

    @Test
    fun captureFullRefundDetail() {
        // 全额退款：金额 0 + 退款列 14.54（导入的真实形态）
        composeRule.setContent { PennedTheme { content(row(amountCents = 0, refundCents = 1454, note = "遮光布")) } }
        composeRule.onNodeWithText("已全额退款").assertIsDisplayed()
        composeRule.onNodeWithText("退款").assertIsDisplayed()
        composeRule.onNodeWithText("14.54").assertIsDisplayed()
        composeRule.onNodeWithText("账单导入").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_detail_full_refund.png")
    }

    @Test
    fun capturePartialRefundWithDiscountDetail() {
        // 部分退款 + 优惠：金额保留原值，两个扣减列都展示
        composeRule.setContent {
            PennedTheme { content(row(amountCents = 2200, refundCents = 520, discountCents = 100, note = "遮光布")) }
        }
        composeRule.onNodeWithText("部分退款").assertIsDisplayed()
        composeRule.onNodeWithText("5.20").assertIsDisplayed()
        composeRule.onNodeWithText("优惠").assertIsDisplayed()
        composeRule.onNodeWithText("1.00").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_detail_partial_refund.png")
    }

    @Test
    fun capturePlainDetailHasNoRefundRows() {
        composeRule.setContent { PennedTheme { content(row(amountCents = 1600, note = "北苑餐厅")) } }
        composeRule.onNodeWithText("退款").assertDoesNotExist()
        composeRule.onNodeWithText("优惠").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_detail_plain.png")
    }
}
