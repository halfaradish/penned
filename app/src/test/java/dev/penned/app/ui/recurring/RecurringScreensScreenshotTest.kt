package dev.penned.app.ui.recurring

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.RuleWithSummary
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.RecurringPeriod
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 周期记账两屏（M3-6）：列表（启停开关 + 下次日期）/ 新建编辑（周期 + 起止）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecurringScreensScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun rule(
        id: String = "r1",
        name: String = "房租",
        period: RecurringPeriod = RecurringPeriod.MONTHLY,
        interval: Int = 1,
        enabled: Boolean = true,
    ) = RuleWithSummary(
        id = id, name = name, type = TxType.EXPENSE, amountCents = 200_000,
        categoryId = null, accountId = null, targetAccountId = null, note = null, feeCents = 0,
        periodType = period, interval = interval,
        startEpochDay = LocalDate.now().minusDays(30).toEpochDay(),
        nextEpochDay = LocalDate.now().plusDays(3).toEpochDay(),
        endEpochDay = null, enabled = enabled,
        categoryName = "居家生活", accountName = "储蓄卡TestCard", targetAccountName = null,
    )

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    @Test
    fun captureRuleList() {
        composeRule.setContent {
            PennedTheme {
                RecurringContent(
                    rules = listOf(rule(), rule(id = "r2", name = "话费", period = RecurringPeriod.MONTHLY, interval = 3, enabled = false)),
                    onBack = {}, onOpenRule = {}, onNewRule = {},
                )
            }
        }
        composeRule.onNodeWithText("周期记账").assertIsDisplayed()
        composeRule.onNodeWithText("房租").assertIsDisplayed()
        // 副标题两行：摘要 + 「每月 · 下次 M月D日」；停用的写「已停用」
        // 副标题是一个两行文本（摘要 + 周期/下次），按子串匹配
        composeRule.onNodeWithText("每月 · 下次 ${nextLabel()}", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("每 3 个月 · 已停用", substring = true).assertIsDisplayed()
        capture("m3_recurring_list")
    }

    private fun nextLabel(): String {
        val d = LocalDate.now().plusDays(3)
        return "${d.monthValue}月${d.dayOfMonth}日"
    }

    @Test
    fun captureRuleListEmpty() {
        composeRule.setContent {
            PennedTheme { RecurringContent(rules = emptyList(), onBack = {}, onOpenRule = {}, onNewRule = {}) }
        }
        composeRule.onNodeWithText("还没有周期记账").assertIsDisplayed()
        composeRule.onNodeWithText("新建规则").assertIsDisplayed()
        capture("m3_recurring_empty")
    }

    @Test
    fun captureNewRule() {
        composeRule.setContent {
            PennedTheme {
                RecurringEditContent(
                    state = RecurringEditUiState(
                        name = "房租", amountText = "2000.00", categoryId = "c1", accountId = "a1", loaded = true,
                    ),
                    categories = listOf(
                        CategoryEntity(
                            id = "c1", bookId = "b1", name = "居家生活", type = CategoryType.EXPENSE,
                            createdAt = 0, updatedAt = 0, deviceId = "d",
                        ) to "",
                    ),
                    accounts = listOf(
                        AccountEntity(
                            id = "a1", bookId = "b1", name = "储蓄卡TestCard", type = AccountType.DEBIT,
                            createdAt = 0, updatedAt = 0, deviceId = "d",
                        ),
                    ),
                    onBack = {},
                    onNameChange = {}, onPickType = {}, onAmountChange = {}, onPickCategory = {},
                    onPickAccount = {}, onPickTargetAccount = {}, onNoteChange = {}, onFeeChange = {},
                    onPickPeriod = {}, onIntervalChange = {}, onPickStartDay = {}, onPickEndDay = {},
                    onToggleEnabled = {}, onSave = {},
                )
            }
        }
        composeRule.onNodeWithText("新建规则").assertIsDisplayed()
        // 周期四格 + 每隔 + 起止
        listOf("天", "周", "月", "年").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithText("每隔").assertIsDisplayed()
        composeRule.onNodeWithText("个月一次").assertIsDisplayed()
        composeRule.onNodeWithText("起始日").assertIsDisplayed()
        composeRule.onNodeWithText("不设").assertIsDisplayed()
        // 新建时没有启停行与删除键
        composeRule.onNodeWithText("已启用").assertDoesNotExist()
        composeRule.onNodeWithText("删除规则").assertDoesNotExist()
        capture("m3_recurring_new")
    }

    @Test
    fun saveDisabledWithoutRequiredFields() {
        composeRule.setContent {
            PennedTheme {
                RecurringEditContent(
                    state = RecurringEditUiState(loaded = true),
                    categories = emptyList(),
                    accounts = emptyList(),
                    onBack = {},
                    onNameChange = {}, onPickType = {}, onAmountChange = {}, onPickCategory = {},
                    onPickAccount = {}, onPickTargetAccount = {}, onNoteChange = {}, onFeeChange = {},
                    onPickPeriod = {}, onIntervalChange = {}, onPickStartDay = {}, onPickEndDay = {},
                    onToggleEnabled = {}, onSave = {},
                )
            }
        }
        composeRule.onNodeWithText("保存").assertIsNotEnabled()
    }

    @Test
    fun captureEditRule() {
        composeRule.setContent {
            PennedTheme {
                RecurringEditContent(
                    state = RecurringEditUiState(
                        ruleId = "r1", name = "房租", amountText = "2000.00", categoryId = "c1", accountId = "a1",
                        period = RecurringPeriod.MONTHLY, intervalText = "1", enabled = false, loaded = true,
                    ),
                    categories = listOf(
                        CategoryEntity(
                            id = "c1", bookId = "b1", name = "居家生活", type = CategoryType.EXPENSE,
                            createdAt = 0, updatedAt = 0, deviceId = "d",
                        ) to "",
                    ),
                    accounts = listOf(
                        AccountEntity(
                            id = "a1", bookId = "b1", name = "储蓄卡TestCard", type = AccountType.DEBIT,
                            createdAt = 0, updatedAt = 0, deviceId = "d",
                        ),
                    ),
                    onBack = {},
                    onNameChange = {}, onPickType = {}, onAmountChange = {}, onPickCategory = {},
                    onPickAccount = {}, onPickTargetAccount = {}, onNoteChange = {}, onFeeChange = {},
                    onPickPeriod = {}, onIntervalChange = {}, onPickStartDay = {}, onPickEndDay = {},
                    onToggleEnabled = {}, onSave = {}, onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("编辑规则").assertIsDisplayed()
        // 启停行在长页末尾（可能不在视口内）：断言存在即可；删除键在常驻底栏
        composeRule.onNodeWithText("已停用").assertExists()
        composeRule.onNodeWithText("删除规则").assertIsDisplayed()
        capture("m3_recurring_edit")
    }
}
