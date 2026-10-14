package dev.penned.app.ui.invest

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.AccountWithBalance
import dev.penned.app.data.db.entity.AssetSnapshotEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.InvestMath
import dev.penned.app.domain.InvestSnapshot
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 理财详情（M3-10）：三数（总额/成本/盈亏）+ 快照列表 + 记快照抽屉。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InvestScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.now()

    private fun account() = AccountWithBalance(
        id = "a1", name = "理财账户", type = AccountType.INVEST, groupName = null,
        balanceCents = 12_500L, netTransferCents = 2_000L, initialBalanceCents = 10_000L,
    )

    private fun snapshot(id: String, daysAgo: Long, cents: Long, note: String? = null) = AssetSnapshotEntity(
        id = id, accountId = "a1", dateEpochDay = today.minusDays(daysAgo).toEpochDay(),
        valueCents = cents, note = note, createdAt = 0, updatedAt = 0, deviceId = "d",
    )

    private fun detail(snapshots: List<AssetSnapshotEntity>) = InvestUi(
        account = account(),
        summary = InvestMath.summary(
            snapshots = snapshots.map { InvestSnapshot(it.dateEpochDay, it.valueCents) },
            balanceCents = 12_500L,
            initialBalanceCents = 10_000L,
            netTransferCents = 2_000L,
        ),
        snapshots = snapshots,
    )

    @Test
    fun captureInvestDetail() {
        composeRule.setContent {
            PennedTheme {
                InvestDetailContent(
                    detail = detail(listOf(snapshot("s1", 3, 13_000L, "季度更新"), snapshot("s2", 40, 11_500L))),
                    onBack = {},
                )
            }
        }
        // 总额取最近快照 130.00；成本 = 初始 100 + 净转入 20 = 120.00；盈亏 +10.00
        composeRule.onNodeWithText("理财详情").assertIsDisplayed()
        // 总额与快照行同额（两处）
        composeRule.onAllNodesWithText("130.00").assertCountEquals(2)
        composeRule.onNodeWithText("成本 120.00").assertIsDisplayed()
        composeRule.onNodeWithText("盈亏 10.00").assertIsDisplayed()
        composeRule.onNodeWithText("季度更新").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_invest_detail.png")
    }

    @Test
    fun captureInvestDetailNoSnapshot() {
        composeRule.setContent {
            PennedTheme { InvestDetailContent(detail = detail(emptyList()), onBack = {}) }
        }
        // 无快照：总额退回余额 125.00、盈亏 5.00；空态给出路
        composeRule.onNodeWithText("还没有快照：总额按账户余额算").assertIsDisplayed()
        composeRule.onAllNodesWithText("125.00").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("盈亏 5.00").assertIsDisplayed()
        composeRule.onNodeWithText("还没有快照——「记快照」把今天的现值记下来，盈亏就按它算").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_invest_detail_empty.png")
    }

    @Test
    fun captureInvestNegativeProfit() {
        composeRule.setContent {
            PennedTheme { InvestDetailContent(detail = detail(listOf(snapshot("s1", 1, 11_000L))), onBack = {}) }
        }
        // 负盈亏照算：110.00 − 120.00 = −10.00
        composeRule.onNodeWithText("盈亏 -10.00").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_invest_loss.png")
    }

    @Test
    fun captureRecordSheet() {
        composeRule.setContent {
            PennedTheme { InvestDetailContent(detail = detail(emptyList()), onBack = {}) }
        }
        composeRule.onNodeWithText("记快照").performClick()
        composeRule.onNodeWithText("现值").assertIsDisplayed()
        composeRule.onNodeWithText("日期").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m3_invest_record_sheet.png")
    }
}
