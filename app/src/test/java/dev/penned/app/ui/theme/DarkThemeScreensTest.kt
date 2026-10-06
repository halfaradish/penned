package dev.penned.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.TxType
import dev.penned.app.ui.add.AddTransactionContent
import dev.penned.app.ui.add.CategoryUi
import dev.penned.app.ui.budgets.BudgetsViewModel
import dev.penned.app.ui.budgets.BudgetScreen
import dev.penned.app.ui.categories.CategoriesViewModel
import dev.penned.app.ui.categories.CategoriesScreen
import dev.penned.app.ui.data.DataViewModel
import dev.penned.app.ui.data.DataManagementScreen
import dev.penned.app.ui.data.ImportBatchesScreen
import dev.penned.app.ui.home.HomeContent
import dev.penned.app.ui.lock.LockScreen
import dev.penned.app.ui.mine.MineScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * 深色主题逐页自检（specs/M1.md 第二批）：每页一张暗色截图，人工/看板留档。
 * 断言只针对静态文本，避免异步数据带来的不稳定。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DarkThemeScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun dark(content: @Composable () -> Unit) {
        composeRule.setContent {
            PennedTheme(darkTheme = true) {
                // 对齐真实应用的 Scaffold 行为：Surface 提供 contentColor，否则文本落回黑色
                androidx.compose.material3.Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/dark_$name.png")
    }

    @Test
    fun darkHome() {
        dark {
            HomeContent(
                bookName = "日常账本",
                rows = listOf(
                    txRow("1", TxType.EXPENSE, 1600, "晚餐", "食品餐饮"),
                    txRow("2", TxType.INCOME, 150000, "工资", null),
                    txRow("3", TxType.EXPENSE, 700, "打车", "出行交通"),
                ),
                onAdd = {},
            )
        }
        composeRule.onNodeWithText("本月支出").assertIsDisplayed()
        capture("home")
    }

    @Test
    fun darkAdd() {
        dark {
            AddTransactionContent(
                type = TxType.EXPENSE,
                amountText = "16.5",
                note = "",
                selectedId = "p0",
                expandedId = "p0",
                canSave = true,
                showEquals = false,
                accountId = null,
                fromId = null,
                toId = null,
                feeText = "",
                accounts = emptyList(),
                parents = listOf(CategoryUi("p0", "其他", hasSubs = true)),
                expandedSubs = listOf(CategoryUi("s0", "罚款赔偿", hasSubs = false)),
                onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
                onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
                onSwap = {}, onFeeChange = {}, onEvaluate = {},
                onSave = {}, onSaveAgain = {}, onBack = {}, onEdit = {},
            )
        }
        composeRule.onNodeWithText("其他").assertIsDisplayed()
        composeRule.onNodeWithText("罚款赔偿").assertIsDisplayed()
        capture("add")
    }

    @Test
    fun darkMine() {
        dark { MineScreen(onOpenDataManagement = {}, onOpenCategories = {}, onOpenPinLock = {}) }
        composeRule.onNodeWithText("数据管理").assertIsDisplayed()
        capture("mine")
    }

    @Test
    fun darkDataManagement() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        val viewModel = DataViewModel(app)
        dark { DataManagementScreen(viewModel, onBack = {}, onOpenBatches = {}) }
        composeRule.onNodeWithText("一木记账 xls 导入").assertIsDisplayed()
        capture("data_management")
    }

    @Test
    fun darkImportBatches() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        val viewModel = DataViewModel(app)
        dark { ImportBatchesScreen(viewModel, onBack = {}) }
        composeRule.onNodeWithText("还没有导入记录").assertIsDisplayed()
        capture("import_batches")
    }

    @Test
    fun darkBudget() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        val viewModel = BudgetsViewModel(app)
        dark { BudgetScreen(viewModel, onBack = {}) }
        composeRule.onNodeWithText("设置本月总预算").assertIsDisplayed()
        capture("budget")
    }

    @Test
    fun darkCategories() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        val viewModel = CategoriesViewModel(app)
        dark { CategoriesScreen(viewModel, onBack = {}) }
        composeRule.onNodeWithText("支出分类").assertIsDisplayed()
        capture("categories")
    }

    @Test
    fun darkLock() {
        dark { LockScreen(storedHash = "salt:abcd", onUnlock = {}) }
        composeRule.onNodeWithText("一笔一记已锁定").assertIsDisplayed()
        capture("lock")
    }

    private fun txRow(
        id: String,
        type: TxType,
        cents: Long,
        name: String?,
        parent: String?,
    ) = TxWithCategory(
        id = id,
        type = type,
        amountCents = cents,
        accountId = null,
        categoryId = null,
        dateEpochMinute = LocalDate.of(2026, 10, 4).atTime(19, 0).atZone(ZoneId.systemDefault()).toEpochSecond() / 60,
        note = null,
        categoryName = name,
        categoryParentName = parent,
        categoryIconKey = null,
        excludeFromStats = false,
    )
}
