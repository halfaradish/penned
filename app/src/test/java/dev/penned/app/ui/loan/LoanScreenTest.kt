package dev.penned.app.ui.loan

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.DebtText
import dev.penned.app.domain.LoanMath
import dev.penned.app.domain.Money
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 分期房贷计算器（M3-11）：结果卡三数与逐期计划都出自 `LoanMath`（手算基准见 `LoanMathTest`），
 * 这里验的是 UI 与纯函数的接线：默认那组数（100 万 / 4.9% / 30 年）显示、改期数结果跟着变、
 * 输入不合法给提示、计划表列前 12 期、还款日按首期月对齐。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoanScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val firstDay = LocalDate.of(2026, 11, 15).toEpochDay()

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    private fun render(
        periodsText: String = "360",
        onRecordFirst: (Long, Long, String) -> Unit = { _, _, _ -> },
    ) {
        val periods = mutableStateOf(periodsText)
        composeRule.setContent {
            PennedTheme {
                LoanContent(
                    principalText = "1000000",
                    rateText = "4.9",
                    periodsText = periods.value,
                    firstEpochDay = firstDay,
                    method = LoanMath.Method.EQUAL_INSTALLMENT,
                    onPrincipalChange = {},
                    onRateChange = {},
                    onPeriodsChange = { periods.value = it },
                    onFirstDayChange = {},
                    onMethodChange = {},
                    onBack = {},
                    onRecordFirst = onRecordFirst,
                )
            }
        }
    }

    /** 默认一组数：首期月供（手算基准 5,307.27）+ 末月/总利息/总还款三格 + 计划首行。 */
    @Test
    fun captureLoanCalculator() {
        render()
        val plan = LoanMath.plan(100_000_000L, 4.9, 360, LoanMath.Method.EQUAL_INSTALLMENT)
        // 等额本息月供恒定：这个数字在首期月供、末月月供与每期计划行里都出现 → 取第一个断言
        composeRule.onAllNodesWithText("5,307.27").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("首期月供").assertIsDisplayed()
        // 方式名在两处（分段器 + 结果卡副标题）
        composeRule.onAllNodesWithText("等额本息").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("末月月供").assertIsDisplayed()
        composeRule.onNodeWithText("总利息").assertIsDisplayed()
        composeRule.onNodeWithText("总还款").assertIsDisplayed()
        composeRule.onNodeWithText(Money.format(plan.totalInterestCents)).assertIsDisplayed()
        composeRule.onNodeWithText(Money.format(plan.totalPaymentCents)).assertIsDisplayed()
        // 计划表：期号 + 还款日（首期按填的日期，第二期月对齐 +1 月）+ 本金/利息/剩余
        // （表比一屏长：往下那些行只断言存在，可见性由截图看）
        val first = plan.periods.first()
        composeRule.onNodeWithText("第 1 期").assertIsDisplayed()
        // 首期还款日两处（表单行 + 计划首行）
        composeRule.onAllNodesWithText(DebtText.fullDate(firstDay)).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText(
            "本金 ${Money.format(first.principalCents)} · 利息 ${Money.format(first.interestCents)}" +
                " · 剩余 ${Money.format(first.remainingCents)}",
        ).assertExists()
        composeRule.onNodeWithText("第 2 期").assertExists()
        composeRule.onNodeWithText(DebtText.fullDate(LocalDate.of(2026, 12, 15).toEpochDay())).assertExists()
        // 360 期只列前 12 期，尾巴上写明怎么循环
        composeRule.onNodeWithText("共 360 期，这里列前 12 期；想每期自动记一笔，用「周期记账」按月建规则")
            .assertExists()
        composeRule.onNodeWithText("第 12 期").assertExists()
        composeRule.onNodeWithText("第 13 期").assertDoesNotExist()
        capture("m3_loan")
    }

    /** 期数改小：计划表跟着变短（12 期全列、没有"共 N 期"那句），月供跟着变。 */
    @Test
    fun periodsChangeUpdatesPlan() {
        render()
        composeRule.onAllNodes(hasSetTextAction())[2].performTextClearance()
        composeRule.onAllNodes(hasSetTextAction())[2].performTextInput("12")
        composeRule.waitForIdle()
        val plan = LoanMath.plan(100_000_000L, 4.9, 12, LoanMath.Method.EQUAL_INSTALLMENT)
        composeRule.onNodeWithText("第 12 期").assertExists()
        composeRule.onNodeWithText("第 13 期").assertDoesNotExist()
        composeRule.onNodeWithText("共 12 期，这里列前 12 期；想每期自动记一笔，用「周期记账」按月建规则")
            .assertDoesNotExist()
        composeRule.onAllNodesWithText(Money.format(plan.firstPaymentCents)).onFirst().assertIsDisplayed()
    }

    /** 期数清空：结果卡退回提示语，「按首期记一笔」置灰（没结果就没得记）。 */
    @Test
    fun emptyPeriodsShowsHint() {
        render(periodsText = "")
        composeRule.onNodeWithText("填好金额、利率与期数就能算出月供").assertIsDisplayed()
        composeRule.onNodeWithText("还款计划").assertDoesNotExist()
        composeRule.onNodeWithText("按首期记一笔").assertIsNotEnabled()
    }

    /** 「按首期记一笔」：带出去的是首期月供、首期还款日与一句备注（分类由记一笔页选）。 */
    @Test
    fun recordFirstPassesAmountDayNote() {
        var recorded: Triple<Long, Long, String>? = null
        render(onRecordFirst = { cents, day, note -> recorded = Triple(cents, day, note) })
        // 按钮在长页最下面：先滑到底再点
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("按首期记一笔").performClick()
        composeRule.waitForIdle()
        assertNotNull(recorded)
        val plan = LoanMath.plan(100_000_000L, 4.9, 360, LoanMath.Method.EQUAL_INSTALLMENT)
        assertEquals(plan.firstPaymentCents, recorded!!.first)
        assertEquals(firstDay, recorded!!.second)
        assertEquals("房贷首期 · 等额本息", recorded!!.third)
    }
}
