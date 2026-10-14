package dev.penned.app.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    private fun ui(
        tx: TransactionEntity,
        tagNames: List<String> = emptyList(),
        reimburseAccountName: String? = null,
    ) = DetailUi(
        tx = tx,
        categoryName = "食品餐饮",
        categoryParentName = null,
        accountName = null,
        targetAccountName = null,
        bookName = "日常账本",
        tagNames = tagNames,
        reimburseAccountName = reimburseAccountName,
    )

    @androidx.compose.runtime.Composable
    private fun content(tx: TransactionEntity, reimburseAccountName: String? = null) = DetailContent(
        detail = ui(tx, reimburseAccountName = reimburseAccountName),
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
    fun captureDetailWithAddressCurrencyAndReimburse() {
        // 全字段展示补齐：地点/原币种+汇率/报销状态（有值才显示）
        composeRule.setContent {
            PennedTheme {
                content(
                    row(amountCents = 2200, note = "机场免税店").copy(
                        address = "上海市浦东新区",
                        currencyCode = "JPY",
                        rate = 0.048,
                        reimburseState = ReimburseState.PENDING,
                        reimburseAccountId = "acc-9",
                    ),
                    reimburseAccountName = "招行信用卡",
                )
            }
        }
        composeRule.onNodeWithText("地点").assertIsDisplayed()
        composeRule.onNodeWithText("上海市浦东新区").assertIsDisplayed()
        composeRule.onNodeWithText("原币种").assertIsDisplayed()
        composeRule.onNodeWithText("JPY · 汇率 0.048").assertIsDisplayed()
        composeRule.onNodeWithText("报销").assertIsDisplayed()
        // M3-3：一行写全 状态 · 报销金额（缺省 0 = 按净额全额）· 收到款的账户
        composeRule.onNodeWithText("待报销 · 22.00 · 招行信用卡").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_detail_full_fields.png")
    }

    @Test
    fun captureDetailReimbursePartialAmount() {
        // 自定报销金额（部分报销）：行里按填的数显示，不是按全额
        composeRule.setContent {
            PennedTheme {
                content(
                    row(amountCents = 10_000, note = "住酒店").copy(
                        reimburseState = ReimburseState.DONE,
                        reimburseCents = 4_000,
                    )
                )
            }
        }
        composeRule.onNodeWithText("已报销 · 40.00").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_detail_reimburse.png")
    }

    @Test
    fun captureDetailWithTags() {
        // 标签行（M3-1）：多个标签用 `·` 连接；没有标签时这一行不出现（见 capturePlainDetailHasNoRefundRows）
        composeRule.setContent {
            PennedTheme {
                DetailContent(
                    detail = ui(row(amountCents = 1600, note = "北苑餐厅"), tagNames = listOf("出差", "交通")),
                    onEdit = {}, onDelete = {}, onToggleStats = {}, onToggleBudget = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("标签").assertIsDisplayed()
        composeRule.onNodeWithText("出差 · 交通").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_detail_with_tags.png")
    }

    @Test
    fun capturePlainDetailHasNoRefundRows() {
        composeRule.setContent { PennedTheme { content(row(amountCents = 1600, note = "北苑餐厅")) } }
        composeRule.onNodeWithText("退款").assertDoesNotExist()
        composeRule.onNodeWithText("优惠").assertDoesNotExist()
        composeRule.onNodeWithText("标签").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_detail_plain.png")
    }

    @Test
    fun refundRowsOpenTheEditor() {
        // M3-2：退款/优惠两行可点 → 跳编辑页（原来只能从底部「编辑」进）
        var edits = 0
        composeRule.setContent {
            PennedTheme {
                DetailContent(
                    detail = ui(row(amountCents = 2200, refundCents = 520, discountCents = 100)),
                    onEdit = { edits++ }, onDelete = {}, onToggleStats = {}, onToggleBudget = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("退款").performScrollTo().performClick()
        composeRule.onNodeWithText("优惠").performScrollTo().performClick()
        org.junit.Assert.assertEquals(2, edits)
        // 其余行（备注/账单日期）照旧不可点：点它不该触发编辑
        composeRule.onNodeWithText("备注").performScrollTo().performClick()
        org.junit.Assert.assertEquals(2, edits)
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_detail_refund_rows.png")
    }
}
