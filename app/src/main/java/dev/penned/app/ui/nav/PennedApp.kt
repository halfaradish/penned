package dev.penned.app.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedElevation
import dev.penned.app.ui.theme.PennedMotion
import dev.penned.app.ui.theme.PennedTextStyles
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.penned.app.data.RecurringGenerator
import dev.penned.app.ui.add.AddTransactionScreen
import dev.penned.app.ui.about.AboutScreen
import dev.penned.app.ui.about.AboutViewModel
import dev.penned.app.ui.about.UpdateAvailableDialog
import dev.penned.app.ui.about.UpdateUiState
import dev.penned.app.ui.assets.AccountEditScreen
import dev.penned.app.ui.assets.AccountGroup
import dev.penned.app.ui.assets.AssetsScreen
import dev.penned.app.ui.books.BooksScreen
import dev.penned.app.ui.calendar.CalendarScreen
import dev.penned.app.ui.budgets.BudgetScreen
import dev.penned.app.ui.budgets.BudgetsViewModel
import dev.penned.app.ui.categories.CategoriesScreen
import dev.penned.app.ui.categories.CategoriesViewModel
import dev.penned.app.ui.data.DataManagementScreen
import dev.penned.app.ui.data.DataViewModel
import dev.penned.app.ui.data.ImportBatchDetailScreen
import dev.penned.app.ui.data.ImportBatchesScreen
import dev.penned.app.ui.appearance.AppearanceScreen
import dev.penned.app.ui.debts.DebtDetailScreen
import dev.penned.app.ui.debts.DebtEditScreen
import dev.penned.app.ui.debts.DebtListScreen
import dev.penned.app.ui.detail.DetailScreen
import dev.penned.app.ui.filters.BillFiltersScreen
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.primaryInkColor
import dev.penned.app.ui.theme.rememberAnimationsEnabled
import dev.penned.app.ui.home.HomeScreen
import dev.penned.app.ui.invest.InvestDetailScreen
import dev.penned.app.ui.loan.LoanScreen
import dev.penned.app.ui.home.HomeViewModel
import dev.penned.app.ui.home.SearchScreen
import dev.penned.app.ui.lock.PinManageScreen
import dev.penned.app.ui.lock.PinViewModel
import dev.penned.app.ui.mine.MineScreen
import dev.penned.app.ui.stats.CategoryDetailScreen
import dev.penned.app.ui.stats.StatsScreen
import dev.penned.app.ui.stats.StatsSettingsScreen
import dev.penned.app.ui.stats.categoryDetailRoute
import dev.penned.app.ui.stats.StatsViewModel
import dev.penned.app.ui.stub.StubScreen
import dev.penned.app.ui.recurring.RecurringEditScreen
import dev.penned.app.ui.recurring.RecurringScreen
import dev.penned.app.ui.tags.TagsScreen
import dev.penned.app.ui.templates.TemplateEditScreen
import dev.penned.app.ui.templates.TemplatesScreen
import dev.penned.app.ui.wishlist.WishlistEditScreen
import dev.penned.app.ui.wishlist.WishlistScreen
import dev.penned.app.ui.tags.TagsViewModel

/** 首页提示条的一次性消息键（保存完成后由记一笔页写进首页的 savedStateHandle）。 */
private const val HOME_FLASH_KEY = "home-flash"

/** 刚保存的流水 id（M3-5）：首页提示条上的「存为模板」用它跳到模板新建页。 */
private const val HOME_FLASH_TX_KEY = "home-flash-tx"

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec("home", "首页", PennedIcons.Home),
    TabSpec("calendar", "日历", PennedIcons.Calendar),
    TabSpec("assets", "资产", PennedIcons.Wallet),
    TabSpec("stats", "统计", PennedIcons.Pie),
    TabSpec("mine", "我的", PennedIcons.Person),
)

@Composable
fun PennedApp(app: dev.penned.app.PennedApplication) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val motion = rememberAnimationsEnabled()
    val tabRoutes = remember { TABS.map { it.route }.toSet() }

    // 更新检查（规格 M2 S2/S6）：状态机与「关于」页共用同一个 VM。启动自动检查每天至多一次、
    // 无新版静默；有新版且这个版本还没提示过 → 弹一次**可取消**的提示（D059，本 App 从不强制）。
    // 挂在 PennedApp 层：LockGate 之外不渲染主界面，提示不会盖到锁屏上。
    val updateVm: AboutViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        initializer = { AboutViewModel(app) },
    )
    var updateNotice by remember { mutableStateOf<UpdateUiState.Available?>(null) }
    LaunchedEffect(Unit) {
        updateNotice = updateVm.autoCheckOnLaunch()
    }

    // 周期记账「打开即补」（M3-6，D070）：**独立协程**，不与上面的更新检查互相阻塞；
    // 补出来的条数交给首页给一条可关闭的提示（不引后台任务、不预生成未来流水）。
    var recurringNotice by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        val generated = RecurringGenerator(app.database, app.deviceId).catchUp()
        if (generated > 0) recurringNotice = generated
    }

    Scaffold(
        bottomBar = {
            // 进出下钻页时底栏跟着收起/展开：以前它是瞬时消失，父页可用高度会在那一瞬跳一截。
            // expandFrom/shrinkTowards 取 Top，底栏才是"从屏幕下沿滑上来/滑下去"（Bottom 是原地揭开）
            AnimatedVisibility(
                visible = currentRoute in tabRoutes,
                enter = if (motion) {
                    fadeIn(tween(MotionSpec.BAR)) +
                        expandVertically(tween(MotionSpec.BAR), expandFrom = Alignment.Top)
                } else {
                    EnterTransition.None
                },
                exit = if (motion) {
                    fadeOut(tween(MotionSpec.BAR)) +
                        shrinkVertically(tween(MotionSpec.BAR), shrinkTowards = Alignment.Top)
                } else {
                    ExitTransition.None
                },
            ) {
                // 底栏：白底 + 顶 1dp 发丝线（与卡片同一套边界语言），零 tonal elevation
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(PennedDimens.hairline)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = PennedElevation.flat,
                    ) {
                        TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            // 选中图标轻微放大（220ms，克制）：颜色之外再给一个形状信号
                            val scale by animateFloatAsState(
                                targetValue = if (selected) 1.08f else 1f,
                                animationSpec = tween(PennedMotion.base, easing = PennedMotion.ease),
                                label = "tabIcon",
                            )
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        tab.icon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        },
                                    )
                                },
                                label = { Text(tab.label, style = PennedTextStyles.caption) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = primaryInkColor(),
                                    selectedTextColor = primaryInkColor(),
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            // 转场（原则五：动效回答动作）：Tab 之间淡入淡出；下钻页整幅从右推入、
            // 父页左退 1/3 视差，返回反向。口径与时长在 ui/nav/ScreenTransitions.kt；
            // 系统"动画时长缩放"为 0 时全部关闭
            enterTransition = { screenMotion(tabRoutes, isPop = false, motion).enter.toTransition() },
            exitTransition = { screenMotion(tabRoutes, isPop = false, motion).exit.toTransition() },
            popEnterTransition = { screenMotion(tabRoutes, isPop = true, motion).enter.toTransition() },
            popExitTransition = { screenMotion(tabRoutes, isPop = true, motion).exit.toTransition() },
            // consumeWindowInsets：普通 padding 不标记 inset 已消费，内层 Scaffold/TopAppBar 会
            // 再加一遍状态栏/导航栏高度（真机搜索页顶部 2× 状态栏死带，D025）
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable("home") {
                val vm: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    initializer = { HomeViewModel(app) },
                )
                val entry = navController.currentBackStackEntry
                // 类型必须显式写：getStateFlow(key, null) / mutableStateOf(null) 里的裸 null 会让
                // Kotlin 把泛型推成 Nothing?（JVM Void），读取处插 checkcast java/lang/Void——
                // 一旦真的读到值（"已记账"/"已保存"）就 ClassCastException 闪退（UI-11）
                val flash: String? by entry?.savedStateHandle
                    ?.getStateFlow<String?>(HOME_FLASH_KEY, null)
                    ?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
                val flashTxId: String? by entry?.savedStateHandle
                    ?.getStateFlow<String?>(HOME_FLASH_TX_KEY, null)
                    ?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
                HomeScreen(
                    viewModel = vm,
                    flashMessage = flash,
                    flashTxId = flashTxId,
                    recurringNotice = recurringNotice,
                    onRecurringNoticeShown = { recurringNotice = null },
                    // 「存为模板」（M3-5）：带着刚保存的流水去模板新建页预填
                    onSaveAsTemplate = { id -> navController.navigate("template?fromTx=$id") },
                    onFlashShown = {
                        entry?.savedStateHandle?.set<String?>(HOME_FLASH_KEY, null)
                        entry?.savedStateHandle?.set<String?>(HOME_FLASH_TX_KEY, null)
                    },
                    onAdd = { navController.navigate("add") },
                    onOpenDetail = { id -> navController.navigate("detail/$id") },
                    onManageBooks = { navController.navigate("books") },
                    onManageBudgets = { navController.navigate("budgets") },
                    onSearch = { navController.navigate("search") },
                    // 行左滑动作区的「编辑」：与详情页同一条编辑路由
                    onEditRow = { id -> navController.navigate("add?editId=$id") },
                )
            }
            composable(
                route = "add?editId={editId}&debtId={debtId}&debtAction={debtAction}" +
                    "&amountCents={amountCents}&day={day}&note={note}",
                arguments = listOf(
                    navArgument("editId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("debtId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("debtAction") { type = NavType.StringType; defaultValue = "" },
                    // 房贷首期预填（M3-11）：-1 = 没带
                    navArgument("amountCents") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("day") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("note") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val editId = entry.arguments?.getString("editId")?.ifEmpty { null }
                AddTransactionScreen(
                    app = app,
                    editId = editId,
                    // 「记一笔还款」预填（M3-4）：债务详情页带 debtId + SETTLE 进来
                    presetDebtId = entry.arguments?.getString("debtId")?.ifEmpty { null },
                    presetDebtAction = entry.arguments?.getString("debtAction")?.ifEmpty { null }
                        ?.let { name -> dev.penned.app.domain.DebtAction.entries.firstOrNull { it.name == name } },
                    // 「按首期记一笔」预填（M3-11）：分期房贷页带金额/日期/备注进来（只预填这一笔）
                    presetCents = entry.arguments?.getLong("amountCents")?.takeIf { it > 0L },
                    presetDay = entry.arguments?.getLong("day")?.takeIf { it > 0L },
                    presetNote = entry.arguments?.getString("note")?.ifEmpty { null },
                    // 保存完成给一次反馈：把消息挂到上一个目的地（首页）的 savedStateHandle 上，
                    // 由首页的提示条读出来（此前保存完静默返回，用户不知道成没成）
                    onDone = { savedId ->
                        val prev = navController.previousBackStackEntry?.savedStateHandle
                        prev?.set(HOME_FLASH_KEY, if (editId == null) "已记账" else "已保存")
                        // 只有新建才给「存为模板」动作（编辑时模板已存过，不该再提示）
                        prev?.set(HOME_FLASH_TX_KEY, if (editId == null) savedId else null)
                        navController.popBackStack()
                    },
                    // 顶栏返回 = 放弃这次记账：只回退、不写提示。
                    // 此前 onBack 错接成 onDone（返回也写"已记账"）——UI-11 与闪退一并修掉
                    onBack = { navController.popBackStack() },
                    onManageCategories = { navController.navigate("categories") },
                    // 债务选择器里的「新建债务」：推一层新建页，建完回到本页即可选中
                    onNewDebt = { navController.navigate("debt") },
                    // 模板抽屉里的「管理模板」
                    onManageTemplates = { navController.navigate("templates") },
                )
            }
            composable(
                route = "detail/{txId}",
                arguments = listOf(navArgument("txId") { type = NavType.StringType }),
            ) { entry ->
                DetailScreen(
                    txId = entry.arguments?.getString("txId").orEmpty(),
                    onEdit = { id ->
                        navController.navigate("add?editId=$id")
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "search?filterId={filterId}",
                arguments = listOf(navArgument("filterId") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                SearchScreen(
                    onOpenDetail = { id -> navController.navigate("detail/$id") },
                    onBack = { navController.popBackStack() },
                    // 从账单组合点进来：条件回填、结果即出（M3-8）
                    filterId = entry.arguments?.getString("filterId")?.ifEmpty { null },
                )
            }
            composable(
                route = "invest/{accountId}",
                arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
            ) { entry ->
                InvestDetailScreen(
                    accountId = entry.arguments?.getString("accountId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("filters") {
                BillFiltersScreen(
                    onBack = { navController.popBackStack() },
                    onApply = { id -> navController.navigate("search?filterId=$id") },
                )
            }
            composable("books") {
                BooksScreen(onBack = { navController.popBackStack() })
            }
            composable("budgets") {
                val vm: BudgetsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    initializer = { BudgetsViewModel(app) },
                )
                BudgetScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable("categories") {
                val vm: CategoriesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    initializer = { CategoriesViewModel(app) },
                )
                CategoriesScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable("tags") {
                val vm: TagsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    initializer = { TagsViewModel(app) },
                )
                TagsScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable("pin") {
                val vm: PinViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    initializer = { PinViewModel(app) },
                )
                PinManageScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable("mine") {
                MineScreen(
                    onOpenDataManagement = { navController.navigate("data") },
                    onOpenTemplates = { navController.navigate("templates") },
                    onOpenRecurring = { navController.navigate("recurring") },
                    onOpenBillFilters = { navController.navigate("filters") },
                    onOpenCategories = { navController.navigate("categories") },
                    onOpenTags = { navController.navigate("tags") },
                    onOpenPinLock = { navController.navigate("pin") },
                    onOpenAbout = { navController.navigate("about") },
                    onOpenLoan = { navController.navigate("loan") },
                    onOpenAppearance = { navController.navigate("appearance") },
                    onOpenWishlist = { navController.navigate("wishlist") },
                    onOpenKeywordRules = { navController.navigate("keywords") },
                )
            }
            // 分类关键词规则（M4-1）：关键词 → 分类 / 标签（只是建议值的源头，不改已入库的账）
            composable("keywords") {
                dev.penned.app.ui.intelligence.KeywordRulesScreen(onBack = { navController.popBackStack() })
            }
            // 愿望清单（M3-15）：攒钱目标列表 + 新建/编辑（不生成流水）
            composable("wishlist") {
                WishlistScreen(
                    onBack = { navController.popBackStack() },
                    onOpenItem = { id -> navController.navigate("wishlist/edit?editId=$id") },
                    onNewItem = { navController.navigate("wishlist/edit") },
                )
            }
            composable(
                route = "wishlist/edit?editId={editId}",
                arguments = listOf(navArgument("editId") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                WishlistEditScreen(
                    editId = entry.arguments?.getString("editId")?.ifEmpty { null },
                    onBack = { navController.popBackStack() },
                )
            }
            // 主题外观（M3-12）：品牌色相 4 套 + 深浅模式，选中即生效
            composable("appearance") {
                AppearanceScreen(onBack = { navController.popBackStack() })
            }
            // 分期房贷计算器（M3-11）：只算不落库；「按首期记一笔」把金额/日期/备注带给记一笔预填
            composable("loan") {
                LoanScreen(
                    onBack = { navController.popBackStack() },
                    onRecordFirst = { cents, day, note ->
                        navController.navigate(
                            "add?amountCents=$cents&day=$day&note=${android.net.Uri.encode(note)}"
                        )
                    },
                )
            }
            composable("about") {
                AboutScreen(viewModel = updateVm, onBack = { navController.popBackStack() })
            }
            composable("data") {
                val vm: DataViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    initializer = { DataViewModel(app) },
                )
                DataManagementScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenBatches = { navController.navigate("data/batches") },
                )
            }
            composable("data/batches") {
                val vm: DataViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    initializer = { DataViewModel(app) },
                )
                ImportBatchesScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenBatch = { id -> navController.navigate("data/batches/$id") },
                )
            }
            composable(
                route = "data/batches/{batchId}",
                arguments = listOf(navArgument("batchId") { type = NavType.StringType }),
            ) { entry ->
                val vm: DataViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    initializer = { DataViewModel(app) },
                )
                ImportBatchDetailScreen(
                    viewModel = vm,
                    batchId = entry.arguments?.getString("batchId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            // 日历视图（M3-17）：月网格 + 每日支/收 + 点选看当日流水 + 「今」
            composable("calendar") {
                CalendarScreen(onOpenDetail = { id -> navController.navigate("detail/$id") })
            }
            composable("assets") {
                AssetsScreen(
                    onAddAccount = { group, preset ->
                        navController.navigate(
                            "account?group=${group.name}&preset=${android.net.Uri.encode(preset.orEmpty())}"
                        )
                    },
                    onOpenAccount = { id -> navController.navigate("account?editId=$id") },
                    // 债务卡点进债务列表（M3-4）
                    onOpenDebts = { navController.navigate("debts") },
                    // 理财卡点进理财详情（M3-10）
                    onOpenInvest = { id -> navController.navigate("invest/$id") },
                )
            }
            composable("recurring") {
                RecurringScreen(
                    onBack = { navController.popBackStack() },
                    // 编辑页走独立路径（`recurring/edit`）：同一个 path 上挂"列表 + 带 query 的编辑页"
                    // 会撞（navigate("recurring") 直接落到编辑页——M3-6 踩到过）
                    onOpenRule = { id -> navController.navigate("recurring/edit?editId=$id") },
                    onNewRule = { navController.navigate("recurring/edit") },
                )
            }
            composable(
                route = "recurring/edit?editId={editId}",
                arguments = listOf(navArgument("editId") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                RecurringEditScreen(
                    editId = entry.arguments?.getString("editId")?.ifEmpty { null },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("templates") {
                TemplatesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTemplate = { id -> navController.navigate("template?editId=$id") },
                    onNewTemplate = { navController.navigate("template") },
                )
            }
            composable(
                route = "template?editId={editId}&fromTx={fromTx}",
                arguments = listOf(
                    navArgument("editId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("fromTx") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                TemplateEditScreen(
                    editId = entry.arguments?.getString("editId")?.ifEmpty { null },
                    fromTxId = entry.arguments?.getString("fromTx")?.ifEmpty { null },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("debts") {
                DebtListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDebt = { id -> navController.navigate("debt/$id") },
                    onNewDebt = { navController.navigate("debt") },
                )
            }
            composable(
                route = "debt?editId={editId}",
                arguments = listOf(navArgument("editId") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                DebtEditScreen(
                    editId = entry.arguments?.getString("editId")?.ifEmpty { null },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "debt/{debtId}",
                arguments = listOf(navArgument("debtId") { type = NavType.StringType }),
            ) { entry ->
                DebtDetailScreen(
                    debtId = entry.arguments?.getString("debtId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate("debt?editId=$id") },
                    // 「记一笔还款」= 记一笔页预填这笔债务与清偿那一格
                    onSettle = { id -> navController.navigate("add?debtId=$id&debtAction=SETTLE") },
                )
            }
            composable(
                route = "account?editId={editId}&group={group}&preset={preset}",
                arguments = listOf(
                    navArgument("editId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("group") { type = NavType.StringType; defaultValue = "" },
                    navArgument("preset") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val groupName = entry.arguments?.getString("group").orEmpty()
                AccountEditScreen(
                    app = app,
                    editId = entry.arguments?.getString("editId")?.ifEmpty { null },
                    presetLabel = entry.arguments?.getString("preset")?.ifEmpty { null },
                    group = AccountGroup.entries.firstOrNull { it.name == groupName },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("stats") {
                val vm: StatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    initializer = { StatsViewModel(app) },
                )
                StatsScreen(
                    viewModel = vm,
                    onOpenCategory = { target, window ->
                        navController.navigate(categoryDetailRoute(target, window))
                    },
                    // 右上「⋯」→ 统计设置（M3-14：模块开关 + 顺序）
                    onOpenSettings = { navController.navigate("stats/settings") },
                )
            }
            // 统计设置（M3-14）：模块显示开关 + 顺序，改动即生效
            composable("stats/settings") {
                // M3-16：同一个「⋯」里给「分享当期小卡片」——数据取统计页当前那一页
                val statsVm: StatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    initializer = { StatsViewModel(app) },
                )
                val state by statsVm.state.collectAsStateWithLifecycle()
                val context = androidx.compose.ui.platform.LocalContext.current
                // 主题要在 composition 里读出来（渲染器不在 composition 里）
                val shareAccent = dev.penned.app.ui.theme.currentThemeAccent()
                val shareDark = dev.penned.app.ui.theme.isDarkTheme()
                StatsSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSharePeriodCard = {
                        val page = state.pages[state.view] ?: dev.penned.app.ui.stats.StatsPage()
                        val type = state.type
                        val unit = if (type == dev.penned.app.domain.TxType.EXPENSE) "支出" else "收入"
                        val shown = if (type == dev.penned.app.domain.TxType.EXPENSE) {
                            page.expenseCategories
                        } else {
                            page.incomeCategories
                        }
                        val plan = dev.penned.app.domain.ShareCardLayout.categoryShare(
                            periodLabel = page.window.label,
                            unit = unit,
                            totalCents = if (type == dev.penned.app.domain.TxType.EXPENSE) {
                                page.totals.expenseCents
                            } else {
                                page.totals.incomeCents
                            },
                            categories = shown,
                        )
                        dev.penned.app.ui.share.ShareCardSharing.share(
                            context, plan, "period-share", shareAccent, shareDark,
                        )
                    },
                )
            }
            // 分类详细页：统计页「支出/收入数据」行点进来，可逐级下钻，窗口跟着参数走
            composable(
                route = "stats/category?type={type}&name={name}&parent={parent}" +
                    "&from={from}&to={to}&days={days}&label={label}",
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                    navArgument("parent") { type = NavType.StringType; defaultValue = "" },
                    navArgument("from") { type = NavType.LongType },
                    navArgument("to") { type = NavType.LongType },
                    navArgument("days") { type = NavType.IntType },
                    navArgument("label") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val args = entry.arguments
                val fromMinute = args?.getLong("from") ?: 0L
                val toMinute = args?.getLong("to") ?: 0L
                val dayCount = args?.getInt("days") ?: 1
                val periodLabel = args?.getString("label").orEmpty()
                CategoryDetailScreen(
                    type = dev.penned.app.domain.TxType.entries
                        .firstOrNull { it.name == args?.getString("type") }
                        ?: dev.penned.app.domain.TxType.EXPENSE,
                    name = args?.getString("name").orEmpty(),
                    parent = args?.getString("parent")?.ifEmpty { null },
                    fromMinute = fromMinute,
                    toMinute = toMinute,
                    days = dayCount,
                    periodLabel = periodLabel,
                    onBack = { navController.popBackStack() },
                    onOpenCategory = { target ->
                        navController.navigate(
                            categoryDetailRoute(target, fromMinute, toMinute, dayCount, periodLabel),
                        )
                    },
                    onOpenTx = { id -> navController.navigate("detail/$id") },
                )
            }
        }
    }

    // 启动检查发现新版时的提示：可取消（点遮罩/返回键/「稍后」），不拦任何操作（D059）
    updateNotice?.let { notice ->
        UpdateAvailableDialog(
            manifest = notice.manifest,
            stronglyRecommended = notice.stronglyRecommended,
            onDismiss = { updateNotice = null },
            onViewDetails = {
                updateNotice = null
                navController.navigate("about")
            },
        )
    }
}
