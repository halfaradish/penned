package dev.penned.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.RefundMath
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.TxType
import dev.penned.app.ui.add.AddTransactionContent
import dev.penned.app.ui.add.ADD_CHIP_ROW_TAG
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
import dev.penned.app.ui.intelligence.KeywordRuleRow
import dev.penned.app.ui.intelligence.KeywordRulesContent
import dev.penned.app.ui.intelligence.KeywordRulesUiState
import dev.penned.app.domain.RuleTargetType
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

    /** 信息条 chips 单行横滑（优化轮 3）：把目标滚进视口再点（LazyRow 视口外的 chip 还没 compose）。 */
    private fun revealChip(text: String) =
        composeRule.onNodeWithTag(ADD_CHIP_ROW_TAG).performScrollToNode(hasText(text))

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

    /** 保存后的提示条（优化轮 3，D098）：深色下的顶部浅色横幅 + 动作键。 */
    @Test
    fun darkHomeFlash() {
        composeRule.mainClock.autoAdvance = false
        dark {
            val host = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                host.showSnackbar(
                    "已记账",
                    actionLabel = "存为模板",
                    duration = androidx.compose.material3.SnackbarDuration.Long,
                )
            }
            HomeContent(
                bookName = "日常账本",
                rows = listOf(
                    txRow("1", TxType.EXPENSE, 1600, "晚餐", "食品餐饮"),
                    txRow("2", TxType.INCOME, 150000, "工资", null),
                ),
                onAdd = {},
                snackbarHostState = host,
            )
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("已记账").assertIsDisplayed()
        composeRule.onNodeWithText("存为模板").assertIsDisplayed()
        capture("home_flash")
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
                onOpenBillFilters = {},
                onOpenCategories = {},
                onOpenTags = {},
                onOpenPinLock = {},
                onOpenAbout = {},
            )
        }
        composeRule.onNodeWithText("数据管理").assertIsDisplayed()
        capture("mine")
    }

    /** 分类关键词规则页（M4-1）：关键词 + 「→ 目标」+ 启停开关的深色观感。 */
    @Test
    fun darkKeywordRules() {
        dark {
            KeywordRulesContent(
                state = KeywordRulesUiState(
                    rows = listOf(
                        KeywordRuleRow("k1", "肯打鸡", true, RuleTargetType.CATEGORY, "食品餐饮"),
                        KeywordRuleRow("k2", "午饭", true, RuleTargetType.CATEGORY, "食品餐饮 / 午餐"),
                        KeywordRuleRow("k3", "出差", false, RuleTargetType.TAG, "出差"),
                    ),
                ),
                onBack = {}, onAdd = {},
            )
        }
        composeRule.onNodeWithText("分类关键词").assertIsDisplayed()
        composeRule.onNodeWithText("→ 分类：食品餐饮 / 午餐").assertIsDisplayed()
        capture("keyword_rules")
    }

    /** AI 设置页（M4-2）：开关 + 四样配置 + 测试连接 + 隐私说明的深色观感。 */
    @Test
    fun darkAiSettings() {
        dark {
            dev.penned.app.ui.intelligence.AiSettingsContent(
                enabled = true,
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                apiKey = "sk-abcdef123456",
                model = "glm-4.6",
                visionModel = "glm-4v",
                showKey = false,
                testState = dev.penned.app.ui.intelligence.TestState.Ok("glm-4.6"),
                onBack = {},
            )
        }
        composeRule.onNodeWithText("AI 设置").assertIsDisplayed()
        composeRule.onNodeWithText("连上了：glm-4.6 回话正常").assertIsDisplayed()
        capture("ai_settings")
    }

    /** 一句话记账（M4-3）：结果卡五行的深色观感。 */
    @Test
    fun darkQuickEntry() {
        dark {
            dev.penned.app.ui.intelligence.QuickEntryContent(
                text = "昨天打车 32.5",
                outcome = dev.penned.app.ui.intelligence.QuickParseOutcome(
                    dev.penned.app.domain.QuickParseResult.Ok(
                        dev.penned.app.domain.QuickDraft(
                            type = dev.penned.app.domain.TxType.EXPENSE,
                            amountCents = 3250,
                            note = "打车",
                            epochDay = java.time.LocalDate.now().minusDays(1).toEpochDay(),
                            dayWord = "昨天",
                        )
                    ),
                    categoryId = "c1", fromRule = true,
                ),
                type = dev.penned.app.domain.TxType.EXPENSE,
                amountCents = 3250,
                categoryName = "出行交通",
                categoryFromRule = true,
                dayLabel = "10月16日",
                note = "打车",
                saving = false,
                onTextChange = {}, onPaste = {}, onParse = {}, onToggleType = {},
                onPickCategory = {}, onPickDate = {}, onEditNote = {}, onSave = {}, onBack = {},
            )
        }
        composeRule.onNodeWithText("一句话记账").assertIsDisplayed()
        composeRule.onNodeWithText("来自关键词规则").assertIsDisplayed()
        capture("quick_entry")
    }

    /** 账单导入预览（M4-5）：识别结果 / 计数分列 / 账户映射的深色观感。 */
    @Test
    fun darkBillPreview() {
        val book = dev.penned.app.data.db.entity.BookEntity(
            id = "b1", name = "日常账本", sortOrder = 0, createdAt = 0, updatedAt = 0, deviceId = "d",
        )
        dark {
            dev.penned.app.ui.data.BillImportContent(
                state = dev.penned.app.ui.data.BillImportUiState(
                    loading = false,
                    fileName = "微信账单.xlsx",
                    source = dev.penned.app.data.importer.BillSource.WECHAT,
                    container = dev.penned.app.data.importer.BillContainer.XLSX,
                    charsetLabel = null,
                    headerRowIndex = 17,
                    counts = dev.penned.app.domain.BillRowCounts(
                        total = 42, importable = 42, duplicate = 0, statusSkipped = 0,
                        nonCashflowSkipped = 0, bad = 0,
                    ),
                    importable = listOf(
                        dev.penned.app.domain.BillRow(100L, 1200L, true, "某食堂", "WX-1", "零钱", null),
                    ),
                    mappings = listOf(
                        dev.penned.app.data.importer.AccountMapping(
                            clue = "零钱", newAccountName = "零钱",
                            newAccountType = dev.penned.app.domain.AccountType.DEBIT,
                        ),
                    ),
                    books = listOf(book),
                    targetBookId = "b1",
                    categoryFromColumn = 0,
                    categoryFromRule = 0,
                    categoryMissing = 1,
                ),
                onBack = {},
            )
        }
        composeRule.onNodeWithText("微信支付账单").assertIsDisplayed()
        composeRule.onNodeWithText("容器 xlsx · 表头在第 18 行").assertIsDisplayed()
        capture("bill_preview")
    }

    /** 自动记账页（M4-8）：状态卡 + 总开关 + 白名单 + 隐私说明的深色观感。 */
    @Test
    fun darkAutoEntry() {
        dark {
            dev.penned.app.ui.intelligence.AutoEntryContent(
                state = dev.penned.app.ui.intelligence.AutoEntryUiState(
                    enabled = true,
                    whitelist = setOf("com.tencent.mm"),
                    installedApps = listOf(
                        dev.penned.app.ui.intelligence.AppEntry("com.tencent.mm", "微信"),
                        dev.penned.app.ui.intelligence.AppEntry("com.eg.android.AlipayGphone", "支付宝"),
                    ),
                    pendingCount = 3,
                ),
                granted = true,
                onBack = {},
            )
        }
        composeRule.onNodeWithText("已获得通知使用权").assertIsDisplayed()
        composeRule.onNodeWithText("3 条等你确认").performScrollTo().assertIsDisplayed()
        capture("auto_entry")
    }

    /** 待确认页（M4-8）：候选行三态（支出/收入/截图来源）的深色观感。 */
    @Test
    fun darkPending() {
        fun row(id: String, app: String?, expense: Boolean, amount: Long, raw: String) =
            dev.penned.app.data.db.entity.PendingEntryEntity(
                id = id,
                kind = if (app == null) dev.penned.app.domain.PendingKind.SCREENSHOT
                else dev.penned.app.domain.PendingKind.NOTIFICATION,
                sourceApp = app, rawText = raw,
                type = if (expense) dev.penned.app.domain.PendingType.EXPENSE
                else dev.penned.app.domain.PendingType.INCOME,
                amountCents = amount, occurredAtEpochMinute = 29_870_021L,
                createdAt = 1L, updatedAt = 1L, deviceId = "d",
            )
        dark {
            dev.penned.app.ui.intelligence.PendingContent(
                rows = listOf(
                    row("p1", "com.tencent.mm", true, 1250, "微信支付 12.50 已支付"),
                    row("p2", "com.eg.android.AlipayGphone", false, 8800, "支付宝 收款 88.00 元已到账"),
                    row("p3", null, true, 999, "截图识别 · 某商户"),
                ),
                onBack = {}, onOpenAutoEntry = {},
            )
        }
        composeRule.onNodeWithText("12.50").assertIsDisplayed()
        composeRule.onNodeWithText("清空已处理").assertIsDisplayed()
        capture("pending")
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
        revealChip("待报销")
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
                    // M3-11：信用卡多一张年费卡（金额 + 收费月/日）
                    feeText = "480.00",
                    feeMonth = 3,
                    feeDay = 12,
                ),
                onBack = {},
                onSelectGroup = {},
                onNameChange = {}, onInitialChange = {}, onSave = {},
                onDelete = {},
            )
        }
        composeRule.onNodeWithText("编辑账户").assertIsDisplayed()
        composeRule.onNodeWithText("年费").assertIsDisplayed()
        composeRule.onNodeWithText("3 月").assertIsDisplayed()
        capture("account_edit")
    }

    /** 分期房贷（M3-11）：结果卡大数 + 三格 + 逐期计划在深色下同样成立。 */
    @Test
    fun darkLoan() {
        dark {
            dev.penned.app.ui.loan.LoanContent(
                principalText = "1000000",
                rateText = "4.9",
                periodsText = "360",
                firstEpochDay = java.time.LocalDate.of(2026, 11, 15).toEpochDay(),
                method = dev.penned.app.domain.LoanMath.Method.EQUAL_INSTALLMENT,
                onPrincipalChange = {},
                onRateChange = {},
                onPeriodsChange = {},
                onFirstDayChange = {},
                onMethodChange = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithText("首期月供").assertIsDisplayed()
        composeRule.onNodeWithText("总利息").assertIsDisplayed()
        capture("loan")
    }

    /** 日历（M3-17 优化轮）：网格的热力底 / 今天圈 / 选中描边 / 支收小字在深色下的着色。 */
    @Test
    fun darkCalendar() {
        val month = java.time.YearMonth.of(2026, 10)
        val day8 = java.time.LocalDate.of(2026, 10, 8).toEpochDay()
        val day4 = java.time.LocalDate.of(2026, 10, 4).toEpochDay()
        val day20 = java.time.LocalDate.of(2026, 10, 20).toEpochDay()
        dark {
            dev.penned.app.ui.calendar.CalendarContent(
                state = dev.penned.app.ui.calendar.CalendarViewModel.UiState(
                    month = month,
                    selectedDay = day8,
                    dayTotals = mapOf(
                        day8 to dev.penned.app.ui.home.HomeGroups.DayGroup(day8, 0L, 600L, emptyList()),
                        day4 to dev.penned.app.ui.home.HomeGroups.DayGroup(day4, 300_000L, 0L, emptyList()),
                        day20 to dev.penned.app.ui.home.HomeGroups.DayGroup(day20, 0L, 120_000L, emptyList()),
                    ),
                    monthTotals = HomeGroups.MonthTotals(incomeCents = 300_000L, expenseCents = 120_600L),
                    selectedItems = listOf(
                        dev.penned.app.data.db.dao.TxWithCategory(
                            id = "t1", type = dev.penned.app.domain.TxType.EXPENSE, amountCents = 600L,
                            accountId = null, categoryId = null, dateEpochMinute = 30_000_000L, note = "饮料",
                            categoryName = "食品餐饮", categoryParentName = null, categoryIconKey = null,
                            targetAccountName = null, excludeFromStats = false, refundCents = 0, discountCents = 0,
                        ),
                    ),
                ),
                today = java.time.LocalDate.of(2026, 10, 15),
            )
        }
        composeRule.onNodeWithText("2026年10月").assertIsDisplayed()
        // 深色同屏：9 号那格是热力档、4 号只有收入（绿底）、20 号是支出顶档
        composeRule.onNodeWithText("收 3,000.00").assertIsDisplayed()
        composeRule.onNodeWithText("支 1,206.00").assertIsDisplayed()
        composeRule.onNodeWithText("余 1,794.00").assertIsDisplayed()
        capture("calendar")
    }

    /** 日历收起态的周视图（优化轮）：上滑收起后的一行，深色下的热力/今天圈/选中描边。 */
    @Test
    fun darkCalendarWeekStrip() {
        val month = java.time.YearMonth.of(2026, 10)
        val day8 = java.time.LocalDate.of(2026, 10, 8).toEpochDay()
        val day4 = java.time.LocalDate.of(2026, 10, 4).toEpochDay()
        dark {
            dev.penned.app.ui.calendar.CalendarContent(
                state = dev.penned.app.ui.calendar.CalendarViewModel.UiState(
                    month = month,
                    selectedDay = day8,
                    dayTotals = mapOf(
                        day8 to dev.penned.app.ui.home.HomeGroups.DayGroup(day8, 0L, 600L, emptyList()),
                        day4 to dev.penned.app.ui.home.HomeGroups.DayGroup(day4, 0L, 120_000L, emptyList()),
                    ),
                    monthTotals = HomeGroups.MonthTotals(incomeCents = 0L, expenseCents = 120_600L),
                    months = dev.penned.app.domain.CalendarPages.months(null, month),
                    selectedItems = listOf(
                        dev.penned.app.data.db.dao.TxWithCategory(
                            id = "t1", type = dev.penned.app.domain.TxType.EXPENSE, amountCents = 600L,
                            accountId = null, categoryId = null, dateEpochMinute = 30_000_000L, note = "饮料",
                            categoryName = "食品餐饮", categoryParentName = null, categoryIconKey = null,
                            targetAccountName = null, excludeFromStats = false, refundCents = 0, discountCents = 0,
                        ),
                    ),
                ),
                today = java.time.LocalDate.of(2026, 10, 15),
            )
        }
        composeRule.onNodeWithContentDescription("收起日历").performClick()
        composeRule.waitForIdle()
        // 收起态：视口只剩一行（周视图），入口文案翻成"展开日历"
        composeRule.onNodeWithContentDescription("展开日历").assertExists()
        capture("dark_calendar_week")
    }

    /** 愿望清单（M3-15）：进度条 + 已达成 chip + 跟随账户副标题在深色下的着色。 */
    @Test
    fun darkWishlist() {
        dark {
            dev.penned.app.ui.wishlist.WishlistContent(
                rows = listOf(
                    dev.penned.app.domain.WishlistProgress(
                        "w1", "换电脑", 800_000L, 320_000L, null, dev.penned.app.domain.WishlistStatus.ACTIVE,
                    ),
                    dev.penned.app.domain.WishlistProgress(
                        "w2", "旅行基金", 500_000L, 500_000L, null, dev.penned.app.domain.WishlistStatus.ACTIVE,
                        accountName = "微信",
                    ),
                ),
                onBack = {},
            )
        }
        composeRule.onNodeWithText("换电脑").assertIsDisplayed()
        composeRule.onNodeWithText("跟随「微信」的余额").assertIsDisplayed()
        capture("wishlist")
    }

    /** 统计设置（M3-14）：模块开关 + 上移/下移在深色下的着色。 */
    @Test
    fun darkStatsSettings() {
        dark {
            dev.penned.app.ui.stats.StatsSettingsContent(
                layout = dev.penned.app.domain.StatsLayout(),
                onToggle = { _, _ -> },
                onMoveUp = {},
                onMoveDown = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithText("统计设置").assertIsDisplayed()
        composeRule.onNodeWithText("资产走势").assertIsDisplayed()
        capture("stats_settings")
    }

    /** 信用卡年费到期行（M3-11）：副标题 + 「记年费」胶囊在深色下的着色。 */
    @Test
    fun darkAssetsFeeDue() {
        dark {
            AssetsContent(
                state = AssetsUiState(
                    accounts = listOf(
                        dev.penned.app.data.db.dao.AccountWithBalance(
                            "c1", "招行信用卡", dev.penned.app.domain.AccountType.CREDIT, "信贷账户", -58_900L,
                            annualFeeCents = 48_000L, feeMonth = 3, feeDay = 12,
                        ),
                    ),
                    netCents = -58_900L, liabilityCents = 58_900L,
                ),
                onAddAccount = { _, _ -> },
                today = java.time.LocalDate.of(2026, 3, 12),
            )
        }
        composeRule.onNodeWithText("年费 ¥480.00 · 每年 3 月 12 日").assertIsDisplayed()
        composeRule.onNodeWithText("记年费").assertIsDisplayed()
        capture("assets_fee_due")
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

    @Test
    fun darkBillFilters() {
        dark {
            dev.penned.app.ui.filters.BillFiltersContent(
                rows = listOf(
                    dev.penned.app.ui.filters.BillFilterRow(
                        entity = dev.penned.app.data.db.entity.BillFilterEntity(
                            id = "f1", bookId = "b1", name = "上月餐饮",
                            type = TxType.EXPENSE, createdAt = 0, updatedAt = 0, deviceId = "d",
                        ),
                        summary = "支出 · 食品餐饮 · 近 30 天",
                    ),
                ),
                onBack = {}, onApply = {},
            )
        }
        composeRule.onNodeWithText("账单组合").assertIsDisplayed()
        capture("bill_filters")
    }

    @Test
    fun darkInvestDetail() {
        dark {
            dev.penned.app.ui.invest.InvestDetailContent(
                detail = dev.penned.app.ui.invest.InvestUi(
                    account = dev.penned.app.data.db.dao.AccountWithBalance(
                        id = "a1", name = "理财账户", type = dev.penned.app.domain.AccountType.INVEST,
                        groupName = null, balanceCents = 12_500L, netTransferCents = 2_000L, initialBalanceCents = 10_000L,
                    ),
                    summary = dev.penned.app.domain.InvestMath.summary(
                        snapshots = listOf(dev.penned.app.domain.InvestSnapshot(20_000L, 11_000L)),
                        balanceCents = 12_500L, initialBalanceCents = 10_000L, netTransferCents = 2_000L,
                    ),
                    snapshots = listOf(
                        dev.penned.app.data.db.entity.AssetSnapshotEntity(
                            id = "s1", accountId = "a1", dateEpochDay = 20_000L, valueCents = 11_000L,
                            note = "季度更新", createdAt = 0, updatedAt = 0, deviceId = "d",
                        ),
                    ),
                ),
                onBack = {},
            )
        }
        composeRule.onNodeWithText("理财详情").assertIsDisplayed()
        composeRule.onNodeWithText("盈亏 -10.00").assertIsDisplayed()
        capture("invest_detail")
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
