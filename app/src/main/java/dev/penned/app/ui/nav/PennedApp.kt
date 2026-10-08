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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.penned.app.ui.add.AddTransactionScreen
import dev.penned.app.ui.assets.AccountEditScreen
import dev.penned.app.ui.assets.AccountGroup
import dev.penned.app.ui.assets.AssetsScreen
import dev.penned.app.ui.books.BooksScreen
import dev.penned.app.ui.budgets.BudgetScreen
import dev.penned.app.ui.budgets.BudgetsViewModel
import dev.penned.app.ui.categories.CategoriesScreen
import dev.penned.app.ui.categories.CategoriesViewModel
import dev.penned.app.ui.data.DataManagementScreen
import dev.penned.app.ui.data.DataViewModel
import dev.penned.app.ui.data.ImportBatchDetailScreen
import dev.penned.app.ui.data.ImportBatchesScreen
import dev.penned.app.ui.detail.DetailScreen
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.primaryInkColor
import dev.penned.app.ui.theme.rememberAnimationsEnabled
import dev.penned.app.ui.home.HomeScreen
import dev.penned.app.ui.home.HomeViewModel
import dev.penned.app.ui.home.SearchScreen
import dev.penned.app.ui.lock.PinManageScreen
import dev.penned.app.ui.lock.PinViewModel
import dev.penned.app.ui.mine.MineScreen
import dev.penned.app.ui.stats.CategoryDetailScreen
import dev.penned.app.ui.stats.StatsScreen
import dev.penned.app.ui.stats.categoryDetailRoute
import dev.penned.app.ui.stats.StatsViewModel
import dev.penned.app.ui.stub.StubScreen

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
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
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
                HomeScreen(
                    viewModel = vm,
                    onAdd = { navController.navigate("add") },
                    onOpenDetail = { id -> navController.navigate("detail/$id") },
                    onManageBooks = { navController.navigate("books") },
                    onManageBudgets = { navController.navigate("budgets") },
                    onSearch = { navController.navigate("search") },
                )
            }
            composable(
                route = "add?editId={editId}",
                arguments = listOf(navArgument("editId") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                AddTransactionScreen(
                    app = app,
                    editId = entry.arguments?.getString("editId")?.ifEmpty { null },
                    onDone = { navController.popBackStack() },
                    onManageCategories = { navController.navigate("categories") },
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
            composable("search") {
                SearchScreen(
                    onOpenDetail = { id -> navController.navigate("detail/$id") },
                    onBack = { navController.popBackStack() },
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
            composable("pin") {
                val vm: PinViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    initializer = { PinViewModel(app) },
                )
                PinManageScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable("mine") {
                MineScreen(
                    onOpenDataManagement = { navController.navigate("data") },
                    onOpenCategories = { navController.navigate("categories") },
                    onOpenPinLock = { navController.navigate("pin") },
                )
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
            composable("calendar") { StubScreen("日历", "日历视图还没做，先在首页按日回看流水") }
            composable("assets") {
                AssetsScreen(
                    onAddAccount = { group, preset ->
                        navController.navigate(
                            "account?group=${group.name}&preset=${android.net.Uri.encode(preset.orEmpty())}"
                        )
                    },
                    onOpenAccount = { id -> navController.navigate("account?editId=$id") },
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
}
