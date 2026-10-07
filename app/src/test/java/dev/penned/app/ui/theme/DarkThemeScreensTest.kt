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
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.TxType
import dev.penned.app.ui.add.AddTransactionContent
import dev.penned.app.ui.add.CategoryUi
import dev.penned.app.ui.budgets.BudgetsViewModel
import dev.penned.app.ui.budgets.BudgetScreen
import dev.penned.app.ui.categories.CategoriesViewModel
import dev.penned.app.ui.categories.CategoriesScreen
import dev.penned.app.ui.data.DataViewModel
import dev.penned.app.ui.data.DataManagementScreen
import dev.penned.app.ui.assets.AccountEditContent
import dev.penned.app.ui.assets.AccountEditUiState
import dev.penned.app.ui.assets.AccountGroup
import dev.penned.app.ui.assets.AssetsContent
import dev.penned.app.ui.assets.AssetsUiState
import dev.penned.app.ui.books.BooksContent
import dev.penned.app.ui.data.ImportBatchesScreen
import dev.penned.app.ui.detail.DetailContent
import dev.penned.app.ui.detail.DetailUi
import dev.penned.app.ui.home.HomeContent
import dev.penned.app.ui.home.HomeGroups
import dev.penned.app.ui.stats.StatsContent
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
                    // 转账行：显示"转账 · 转入 X"而非"未分类"
                    txRow("4", TxType.TRANSFER, 8200, null, null).copy(targetAccountName = "储蓄卡"),
                ),
                onAdd = {},
            )
        }
        // 合并卡：月汇总与预算同卡（文案从"本月支出"简化为"支出"）
        composeRule.onNodeWithText("支出").assertIsDisplayed()
        composeRule.onNodeWithText("结余").assertIsDisplayed()
        composeRule.onNodeWithText("转账 · 转入 储蓄卡").assertIsDisplayed()
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

    @Test
    fun darkDetail() {
        dark {
            DetailContent(
                detail = DetailUi(
                    tx = dev.penned.app.data.db.entity.TransactionEntity(
                        id = "t1", bookId = "b1", type = TxType.EXPENSE, amountCents = 0,
                        categoryId = null, dateEpochMinute = 28_800_000L, note = "遮光布",
                        refundCents = 1454, source = dev.penned.app.domain.TxSource.IMPORT,
                        creatorDeviceId = "d", createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_000_000L,
                        deviceId = "d",
                    ),
                    categoryName = "购物消费", categoryParentName = null,
                    accountName = null, targetAccountName = null, bookName = "日常账本",
                ),
                onEdit = {}, onDelete = {}, onToggleStats = {}, onToggleBudget = {}, onBack = {},
            )
        }
        composeRule.onNodeWithText("已全额退款").assertIsDisplayed()
        capture("detail")
    }

    @Test
    fun darkStats() {
        val period = dev.penned.app.domain.StatsPeriod.Month(java.time.YearMonth.of(2026, 9))
        dark {
            StatsContent(
                period = period,
                window = dev.penned.app.domain.StatsPeriods.window(period),
                totals = StatsMath.PeriodTotals(incomeCents = 350000, expenseCents = 295570),
                expenseCategories = listOf(
                    StatsMath.CategoryTotal("食品餐饮", 180000, 110, 0.61f),
                    StatsMath.CategoryTotal("购物消费", 80000, 15, 0.27f),
                    StatsMath.CategoryTotal("其他", 35570, 4, 0.12f),
                ),
                incomeCategories = listOf(StatsMath.CategoryTotal("工资", 350000, 1, 1f)),
                // 趋势卡：2026-09 按日（30 天），月中有几笔
                trendBars = List(30) { 0L }.toMutableList().also { it[3] = 12600; it[11] = 8000; it[26] = 35570 },
                trendAxis = dev.penned.app.domain.TrendAxis.Days(
                    java.time.LocalDate.of(2026, 9, 1).toEpochDay(),
                    30,
                ),
                stripMonths = dev.penned.app.domain.StatsStrip.months(
                    earliest = java.time.YearMonth.of(2024, 3),
                    today = java.time.YearMonth.of(2026, 10),
                    include = java.time.YearMonth.of(2026, 9),
                ),
                stripYears = dev.penned.app.domain.StatsStrip.years(
                    earliest = java.time.YearMonth.of(2024, 3),
                    today = java.time.YearMonth.of(2026, 10),
                ),
                // 切换条上的「本月/上月」跟着"今天"走，固定住才可复现
                today = LocalDate.of(2026, 10, 7),
            )
        }
        composeRule.onNodeWithText("支出占比").assertIsDisplayed()
        capture("stats")
    }

    @Test
    fun darkAssets() {
        dark {
            AssetsContent(
                state = AssetsUiState(
                    accounts = listOf(
                        dev.penned.app.data.db.dao.AccountWithBalance("a1", "支付宝", dev.penned.app.domain.AccountType.DEBIT, "资金账户", 128_450L),
                        dev.penned.app.data.db.dao.AccountWithBalance("a2", "招行信用卡", dev.penned.app.domain.AccountType.CREDIT, "信贷账户", -58_900L),
                    ),
                    netCents = 69_550L, assetCents = 128_450L, liabilityCents = 58_900L,
                ),
                onAddAccount = { _, _ -> },
            )
        }
        composeRule.onNodeWithText("净资产(元)").assertIsDisplayed()
        capture("assets")
    }

    @Test
    fun darkAccountEdit() {
        dark {
            AccountEditContent(
                state = AccountEditUiState(
                    accountId = "a1",
                    group = AccountGroup.CREDIT,
                    type = dev.penned.app.domain.AccountType.CREDIT,
                    presetLabel = "信用卡",
                    name = "招行信用卡",
                    initialText = "0.00",
                ),
                onBack = {},
                onSelectGroup = {},
                onNameChange = {}, onInitialChange = {}, onSave = {},
                onDelete = {},
            )
        }
        composeRule.onNodeWithText("编辑账户").assertIsDisplayed()
        capture("account_edit")
    }

    @Test
    fun darkTransferTab() {
        dark {
            AddTransactionContent(
                type = TxType.TRANSFER,
                amountText = "8200", note = "还信用卡",
                selectedId = null, expandedId = null, canSave = true, showEquals = false,
                accountId = null, fromId = "a1", toId = "a2", feeText = "2.00",
                accounts = listOf(
                    dev.penned.app.data.db.entity.AccountEntity("a1", "b1", "支付宝", dev.penned.app.domain.AccountType.DEBIT, createdAt = 0, updatedAt = 0, deviceId = "d"),
                    dev.penned.app.data.db.entity.AccountEntity("a2", "b1", "储蓄卡", dev.penned.app.domain.AccountType.DEBIT, createdAt = 0, updatedAt = 0, deviceId = "d"),
                ),
                parents = emptyList(), expandedSubs = emptyList(),
                onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
                onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
                onSwap = {}, onFeeChange = {}, onEvaluate = {}, onSave = {}, onSaveAgain = {},
                onBack = {}, onEdit = {},
            )
        }
        composeRule.onNodeWithText("支付宝").assertIsDisplayed()
        composeRule.onNodeWithText("手续费 2.00").assertIsDisplayed()
        capture("add_transfer")
    }

    @Test
    fun darkCalculator() {
        dark {
            AddTransactionContent(
                type = TxType.EXPENSE,
                amountText = "12×3÷4", note = "",
                selectedId = "p0", expandedId = null, canSave = true, showEquals = true,
                accountId = null, fromId = null, toId = null, feeText = "",
                accounts = emptyList(),
                parents = listOf(CategoryUi("p0", "其他", hasSubs = false)), expandedSubs = emptyList(),
                onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
                onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
                onSwap = {}, onFeeChange = {}, onEvaluate = {}, onSave = {}, onSaveAgain = {},
                onBack = {}, onEdit = {},
            )
        }
        composeRule.onNodeWithText("12×3÷4").assertIsDisplayed()
        composeRule.onNodeWithText("=").assertIsDisplayed()
        capture("add_calculator")
    }

    @Test
    fun darkBooks() {
        dark {
            BooksContent(
                books = listOf(
                    dev.penned.app.data.db.entity.BookEntity("b1", "日常账本", 0, 1L, 1L, deviceId = "d"),
                    dev.penned.app.data.db.entity.BookEntity("b2", "旅行账本", 1, 1L, 1L, deviceId = "d"),
                ),
                currentId = "b2",
            )
        }
        composeRule.onNodeWithText("当前账本").assertIsDisplayed()
        capture("books")
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
        targetAccountName = null,
        excludeFromStats = false,
    )
}
