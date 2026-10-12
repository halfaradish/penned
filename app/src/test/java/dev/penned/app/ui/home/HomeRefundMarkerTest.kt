package dev.penned.app.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首页流水行的退款/优惠标记（M3-2，看板遗留）：金额列给的是**净额**，
 * 全额退款那一行原本显示 "-0.00"，谁也看不出为什么是 0——现在行上说清来路。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeRefundMarkerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.systemDefault()

    private fun row(
        amountCents: Long,
        refundCents: Long = 0,
        discountCents: Long = 0,
        note: String? = null,
    ) = TxWithCategory(
        id = "tx-$amountCents-$refundCents-$discountCents",
        type = TxType.EXPENSE,
        amountCents = amountCents,
        accountId = null,
        categoryId = null,
        dateEpochMinute = LocalDate.now(zone).atTime(12, 30).atZone(zone).toEpochSecond() / 60,
        note = note,
        categoryName = "购物消费",
        categoryParentName = null,
        categoryIconKey = null,
        targetAccountName = null,
        excludeFromStats = false,
        refundCents = refundCents,
        discountCents = discountCents,
    )

    @Test
    fun `标记口径：全额退款、退款、优惠、无`() {
        assertEquals("全额退款", refundMarker(row(amountCents = 0, refundCents = 700)))
        assertEquals("退款", refundMarker(row(amountCents = 4800, refundCents = 1200)))
        assertEquals("优惠", refundMarker(row(amountCents = 1700, discountCents = 300)))
        // 一行只挂一个标记：两个都有时退款优先（优惠额在那行的算式里，不挤第二个章）
        assertEquals("退款", refundMarker(row(amountCents = 1200, refundCents = 500, discountCents = 300)))
        assertNull(refundMarker(row(amountCents = 1600)))
    }

    @Test
    fun captureHomeRowsWithRefunds() {
        composeRule.setContent {
            PennedTheme {
                HomeContent(
                    bookName = "日常账本",
                    rows = listOf(
                        row(amountCents = 0, refundCents = 700, note = "全额退的午餐"),
                        row(amountCents = 4800, refundCents = 1200, note = "退了邮费的外套"),
                        row(amountCents = 1700, discountCents = 300, note = "用了券"),
                        row(amountCents = 1600, note = "普通一笔"),
                    ),
                    onAdd = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("全额退款").assertIsDisplayed()
        // 全额退款行的金额是 0.00，不再写成 "-0.00"（月卡合计里也有 0.00，取第一处）
        composeRule.onAllNodesWithText("0.00").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("-0.00").assertDoesNotExist()
        // 部分退款行按净额显示，标记说明它为什么比原价小
        composeRule.onNodeWithText("-48.00").assertIsDisplayed()
        composeRule.onAllNodesWithText("退款").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("优惠").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_home_refund_marks.png")
    }
}
