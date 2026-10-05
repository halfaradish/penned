package dev.penned.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.penned.app.PennedApplication
import dev.penned.app.ui.add.AddTransactionScreen
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.home.HomeScreen
import dev.penned.app.ui.home.HomeViewModel
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
fun PennedApp(app: PennedApplication) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
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
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                val vm: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    initializer = { HomeViewModel(app.database) },
                )
                HomeScreen(
                    viewModel = vm,
                    bookName = "日常账本", // M1：随账本管理改为动态
                    onAdd = { navController.navigate("add") },
                )
            }
            composable("add") {
                AddTransactionScreen(app = app, onDone = { navController.popBackStack() })
            }
            composable("calendar") { StubScreen("日历", "M1") }
            composable("assets") { StubScreen("资产", "M1/M3") }
            composable("stats") { StubScreen("统计", "M3") }
            composable("mine") { StubScreen("我的", "M1") }
        }
    }
}
