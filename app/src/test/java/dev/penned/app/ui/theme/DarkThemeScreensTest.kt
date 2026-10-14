package dev.penned.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.RefundMath
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.TxType
import dev.penned.app.ui.add.AddTransactionContent
import dev.penned.app.ui.add.AmountField
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
import dev.penned.app.ui.tags.TagsScreen
import dev.penned.app.ui.tags.TagsViewModel
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
        // 本月卡头部展开态（AQ 轮：月汇总＋预算块进了月卡，不再是顶部单独的合并卡）
        composeRule.onNodeWithText("支出").assertIsDisplayed()
        composeRule.onNodeWithText("结余").assertIsDisplayed()
        composeRule.onNodeWithText("转账 · 转入 储蓄卡").assertIsDisplayed()
        capture("home")
    }

    /** 左滑动作区露满（UI-12）：深色下两个实心块（编辑 = surfaceContainerHigh，删除 = error）。 */
    @Test
    fun darkHomeRowAction() {
        dark {
            HomeContent(
                bookName = "日常账本",
                rows = listOf(
                    txRow("1", TxType.EXPENSE, 1600, "晚餐", "食品餐饮"),
                    txRow("2", TxType.INCOME, 150000, "工资", null),
                ),
                onAdd = {},
                revealedRowId = "1",
            )
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("删除").onFirst().assertIsDisplayed()
        capture("home_row_action")
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
        dark {
            MineScreen(
                onOpenDataManagement = {},
                onOpenTemplates = {},
                onOpenRecurring = {},
                onOpenCategories = {},
                onOpenTags = {},
                onOpenPinLock = {},
                onOpenAbout = {},
            )
        }
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
    fun darkAddRefund() {
        // 退款态（M3-2）：两个 chip 回显、实付行、键盘提示条在深色下同样成立
        dark {
            AddTransactionContent(
                type = TxType.EXPENSE,
                amountText = "20.00",
                note = "",
                selectedId = "p0",
                expandedId = null,
                canSave = true,
                showEquals = false,
                accountId = null,
                fromId = null,
                toId = null,
                feeText = "",
                refundText = "3.00",
                discountText = "5.00",
                amountField = AmountField.REFUND,
                paySummary = RefundMath.summaryText(2000L, 300L, 500L),
                accounts = emptyList(),
                parents = listOf(CategoryUi("p0", "其他", hasSubs = false)),
                expandedSubs = emptyList(),
                onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
                onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
                onSwap = {}, onFeeChange = {}, onEvaluate = {},
                onSave = {}, onSaveAgain = {}, onBack = {}, onEdit = {},
            )
        }
        composeRule.onNodeWithText("正在输入：退款").assertIsDisplayed()
        composeRule.onNodeWithText("实付 12.00（20.00 − 退款 3.00 − 优惠 5.00）").assertIsDisplayed()
        capture("add_refund")
    }

    @Test
    fun darkAddReimburse() {
        // 报销抽屉（M3-3）深色：三态 chip + 账户/金额两行 + 收尾按钮
        dark {
            AddTransactionContent(
                type = TxType.EXPENSE,
                amountText = "100",
                note = "住酒店",
                selectedId = "p0",
                expandedId = null,
                canSave = true,
                showEquals = false,
                accountId = null,
                fromId = null,
                toId = null,
                feeText = "",
                reimburseState = ReimburseState.PENDING,
                claimLabel = "全额 100.00",
                accounts = emptyList(),
                parents = listOf(CategoryUi("p0", "其他", hasSubs = false)),
                expandedSubs = emptyList(),
                onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
                onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
                onSwap = {}, onFeeChange = {}, onEvaluate = {},
                onSave = {}, onSaveAgain = {}, onBack = {}, onEdit = {},
            )
        }
        composeRule.onNodeWithText("待报销").performClick()
        composeRule.onNodeWithText("报销金额").assertIsDisplayed()
        composeRule.onNodeWithText("选好了").assertIsDisplayed()
        capture("add_reimburse")
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
    fun darkTags() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        val viewModel = TagsViewModel(app)
        dark { TagsScreen(viewModel, onBack = {}) }
        composeRule.onNodeWithText("标签管理").assertIsDisplayed()
        capture("tags")
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
                view = dev.penned.app.domain.StatsView.MONTH,
                pages = mapOf(
                    dev.penned.app.domain.StatsView.MONTH to dev.penned.app.ui.stats.StatsPage(
                        period = period,
                        window = dev.penned.app.domain.StatsPeriods.window(period),
                        totals = StatsMath.PeriodTotals(incomeCents = 350000, expenseCents = 296770),
                        expenseCategories = listOf(
                            StatsMath.CategoryTotal("食品餐饮", 180000, 110, 0.607f),
                            StatsMath.CategoryTotal("购物消费", 80000, 15, 0.270f),
                            StatsMath.CategoryTotal("其他", 35570, 4, 0.120f),
                            // 未分类行：中性灰、不可点（UI-9/D049 起由 drillable 决定）
                            StatsMath.CategoryTotal("未分类", 1200, 1, 0.004f, drillable = false),
                        ),
                        incomeCategories = listOf(StatsMath.CategoryTotal("工资", 350000, 1, 1f)),
                        // 趋势卡：2026-09 按日（30 天），月中有几笔
                        trendBars = List(30) { 0L }.toMutableList().also { it[3] = 12600; it[11] = 8000; it[26] = 35570 },
                        trendAxis = dev.penned.app.domain.StatsTrend.axisOf(
                            period,
                            dev.penned.app.domain.StatsPeriods.window(period),
                        ),
                    ),
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

    /** 账户行左滑动作区露满（UI-13）：深色下删除块 = error 实心 + onError 字（7.31:1）。 */
    @Test
    fun darkAssetsRowAction() {
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
                revealedRowId = "a1",
            )
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("删除").onFirst().assertIsDisplayed()
        capture("assets_row_action")
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

    @Test
    fun darkDebtList() {
        dark {
            dev.penned.app.ui.debts.DebtListContent(
                debts = listOf(
                    dev.penned.app.data.db.dao.DebtWithRepaid(
                        "d1", dev.penned.app.domain.DebtDirection.BORROW, "张三",
                        100_000L, 20_000L, 20_100L, null, false, 0, 40_000L,
                    ),
                    dev.penned.app.data.db.dao.DebtWithRepaid(
                        "d2", dev.penned.app.domain.DebtDirection.LEND, "李四",
                        30_000L, 20_000L, null, null, false, 1, 0L,
                    ),
                ),
                onBack = {}, onOpenDebt = {}, onNewDebt = {},
            )
        }
        composeRule.onNodeWithText("应付 · 我欠对方").assertIsDisplayed()
        composeRule.onNodeWithText("应收 · 对方欠我").assertIsDisplayed()
        capture("debt_list")
    }

    @Test
    fun darkDebtDetail() {
        dark {
            dev.penned.app.ui.debts.DebtDetailContent(
                detail = dev.penned.app.ui.debts.DebtDetailUi(
                    debt = dev.penned.app.data.db.dao.DebtWithRepaid(
                        "d1", dev.penned.app.domain.DebtDirection.BORROW, "张三",
                        100_000L, 20_000L, 20_100L, "周转", false, 0, 40_000L,
                    ),
                    flows = listOf(
                        dev.penned.app.data.db.dao.DebtFlowRow(
                            "f2", TxType.EXPENSE, 40_000L, "a1", 30_000_000L, null, "现金",
                        ),
                    ),
                ),
                onBack = {}, onEdit = {}, onSettle = {},
            )
        }
        composeRule.onNodeWithText("还款记录").assertIsDisplayed()
        composeRule.onNodeWithText("记一笔还款").assertIsDisplayed()
        capture("debt_detail")
    }

    @Test
    fun darkAddDebt() {
        // 债务 Tab（M3-4）：深色下面板 / 分段 / 口径说明同样成立
        dark {
            AddTransactionContent(
                type = TxType.EXPENSE,
                amountText = "1000",
                note = "",
                selectedId = null,
                expandedId = null,
                canSave = true,
                showEquals = false,
                accountId = "a1",
                fromId = null,
                toId = null,
                feeText = "",
                debtTab = true,
                debts = listOf(
                    dev.penned.app.data.db.dao.DebtWithRepaid(
                        "d1", dev.penned.app.domain.DebtDirection.BORROW, "张三",
                        100_000L, 20_000L, 20_100L, null, false, 0, 40_000L,
                    ),
                ),
                debtId = "d1",
                debtAction = dev.penned.app.domain.DebtAction.OPEN,
                accounts = emptyList(),
                parents = emptyList(),
                expandedSubs = emptyList(),
                onSelectType = {}, onKey = {}, onSelectParent = {}, onSelectSub = {},
                onNoteChange = {}, onPickAccount = {}, onPickFrom = {}, onPickTo = {},
                onSwap = {}, onFeeChange = {}, onEvaluate = {},
                onSave = {}, onSaveAgain = {}, onBack = {}, onEdit = {},
            )
        }
        composeRule.onNodeWithText("张三").assertIsDisplayed()
        composeRule.onNodeWithText("借入").assertIsDisplayed()
        capture("add_debt")
    }

    @Test
    fun darkTemplates() {
        dark {
            dev.penned.app.ui.templates.TemplatesContent(
                templates = listOf(
                    dev.penned.app.data.db.dao.TemplateWithTags(
                        "t1", "午饭", TxType.EXPENSE, 1650, null, null, null, null, 0, 0,
                        "食品餐饮", "微信", null, "报销中",
                    ),
                    dev.penned.app.data.db.dao.TemplateWithTags(
                        "t2", "还信用卡", TxType.TRANSFER, 100_000, null, null, null, null, 0, 1,
                        null, "招行信用卡", "储蓄卡", null,
                    ),
                ),
                onBack = {}, onOpenTemplate = {}, onNewTemplate = {},
            )
        }
        composeRule.onNodeWithText("模板记账").assertIsDisplayed()
        composeRule.onNodeWithText("支出 · 16.50 · 食品餐饮 · 微信 · 报销中").assertIsDisplayed()
        capture("templates")
    }

    @Test
    fun darkTemplateEdit() {
        dark {
            dev.penned.app.ui.templates.TemplateEditContent(
                state = dev.penned.app.ui.templates.TemplateEditUiState(
                    name = "午饭", amountText = "16.50", categoryId = "c1", accountId = "a1", loaded = true,
                ),
                categories = listOf(
                    dev.penned.app.data.db.entity.CategoryEntity(
                        id = "c1", bookId = "b1", name = "食品餐饮",
                        type = dev.penned.app.domain.CategoryType.EXPENSE,
                        createdAt = 0, updatedAt = 0, deviceId = "d",
                    ) to "",
                ),
                accounts = listOf(
                    dev.penned.app.data.db.entity.AccountEntity(
                        id = "a1", bookId = "b1", name = "微信",
                        type = dev.penned.app.domain.AccountType.DEBIT,
                        createdAt = 0, updatedAt = 0, deviceId = "d",
                    ),
                ),
                tags = emptyList(),
                onBack = {},
                onNameChange = {}, onPickType = {}, onAmountChange = {}, onPickCategory = {},
                onPickAccount = {}, onPickTargetAccount = {}, onNoteChange = {}, onFeeChange = {},
                onToggleTag = {}, onSave = {},
            )
        }
        composeRule.onNodeWithText("新建模板").assertIsDisplayed()
        capture("template_edit")
    }

    @Test
    fun darkRecurring() {
        dark {
            dev.penned.app.ui.recurring.RecurringContent(
                rules = listOf(
                    dev.penned.app.data.db.dao.RuleWithSummary(
                        "r1", "房租", TxType.EXPENSE, 200_000, null, null, null, null, 0,
                        dev.penned.app.domain.RecurringPeriod.MONTHLY, 1, 20_000L, 20_100L, null, true,
                        "居家生活", "储蓄卡TestCard", null,
                    ),
                ),
                onBack = {}, onOpenRule = {}, onNewRule = {},
            )
        }
        composeRule.onNodeWithText("房租").assertIsDisplayed()
        capture("recurring")
    }

    @Test
    fun darkRecurringEdit() {
        dark {
            dev.penned.app.ui.recurring.RecurringEditContent(
                state = dev.penned.app.ui.recurring.RecurringEditUiState(
                    name = "房租", amountText = "2000.00", categoryId = "c1", accountId = "a1", loaded = true,
                ),
                categories = listOf(
                    dev.penned.app.data.db.entity.CategoryEntity(
                        id = "c1", bookId = "b1", name = "居家生活",
                        type = dev.penned.app.domain.CategoryType.EXPENSE,
                        createdAt = 0, updatedAt = 0, deviceId = "d",
                    ) to "",
                ),
                accounts = listOf(
                    dev.penned.app.data.db.entity.AccountEntity(
                        id = "a1", bookId = "b1", name = "储蓄卡TestCard",
                        type = dev.penned.app.domain.AccountType.DEBIT,
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
        composeRule.onNodeWithText("新建规则").assertIsDisplayed()
        capture("recurring_edit")
    }

    @Test
    fun darkFilter() {
        dark {
            dev.penned.app.ui.home.SearchContent(
                filter = dev.penned.app.domain.TxFilter(
                    type = TxType.EXPENSE,
                    categoryIds = listOf("c1"),
                    amountMinCents = 1000,
                ),
                results = listOf(
                    txRow("1", TxType.EXPENSE, 1600, "晚餐", "食品餐饮"),
                    txRow("2", TxType.EXPENSE, 700, "打车", "出行交通"),
                ),
                summary = dev.penned.app.ui.home.FilterSummary(2, 0, 2300),
                autofocus = false,
            )
        }
        composeRule.onNodeWithText("筛选").assertIsDisplayed()
        composeRule.onNodeWithText("分类 · 1").assertIsDisplayed()
        capture("filter")
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
        // 固定在本月 4 日：月汇总块只在"本月"那张卡上默认展开（AQ 轮），写死月份会在跨月后让断言失效
        dateEpochMinute = LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1).plusDays(3)
            .atTime(19, 0).atZone(ZoneId.systemDefault()).toEpochSecond() / 60,
        note = null,
        categoryName = name,
        categoryParentName = parent,
        categoryIconKey = null,
        targetAccountName = null,
        excludeFromStats = false,
        refundCents = 0, discountCents = 0,
    )
}
