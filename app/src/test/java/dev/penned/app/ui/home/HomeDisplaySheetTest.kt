package dev.penned.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.HomeDisplay
import dev.penned.app.domain.HomeDisplaySettings
import dev.penned.app.domain.HomeStatItem
import dev.penned.app.domain.TxType
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.nav.PennedApp
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * 首页数据自定义（M3-13）：月卡头按勾选渲染（全开 / 部分 / 全关兜底）+ 「⋯」抽屉 + 真库真导航链路。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeDisplaySheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.systemDefault()

    /**
     * DataStore 是静态单例：本类改过显示项，收工一律复位成"全开"，别串给别的测试。
     *
     * **不能放在 `@After` 里**：JUnit 的 @After 在 Robolectric 的 SDK 主线程上跑，而 `runBlocking`
     * 写 DataStore 会等它的内部协程——主线程被堵住就永远等不到（实测：整条测试挂死 5 分钟，
     * 线程栈停在 `HomeDisplaySheetTest.resetDisplay` 的 runBlocking 上）。所以复位写在**测试体里**。
     */
    private fun resetDisplay() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        app.settings.setHomeStatItems(HomeDisplay.encode(HomeStatItem.entries.toSet()))
    }

    private fun rows(): List<TxWithCategory> {
        val today = LocalDate.now(zone)
        return listOf(
            tx("e1", today.withDayOfMonth(1), TxType.EXPENSE, 1200, "晚餐"),
            tx("i1", today, TxType.INCOME, 300000, "工资"),
        )
    }

    private fun tx(id: String, date: LocalDate, type: TxType, cents: Long, name: String) = TxWithCategory(
        id = id, type = type, amountCents = cents, accountId = null, categoryId = null,
        dateEpochMinute = date.atTime(12, 30).atZone(zone).toEpochSecond() / 60,
        note = null, categoryName = name, categoryParentName = null, categoryIconKey = null,
        targetAccountName = null, excludeFromStats = false, refundCents = 0, discountCents = 0,
    )

    private fun render(display: HomeDisplaySettings) {
        composeRule.setContent {
            PennedTheme {
                HomeContent(
                    bookName = "日常账本",
                    rows = rows(),
                    onAdd = {},
                    budgetSummary = HomeViewModel.BudgetSummary(totalCents = 200_000L, spentCents = 2_200L),
                    display = display,
                )
            }
        }
    }

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    /** 全开（默认）：三格 + 预算块，与 M3-13 之前完全一致。 */
    @Test
    fun allOnShowsEverything() {
        render(HomeDisplaySettings())
        composeRule.onNodeWithText("支出").assertIsDisplayed()
        composeRule.onNodeWithText("收入").assertIsDisplayed()
        composeRule.onNodeWithText("结余").assertIsDisplayed()
        composeRule.onNodeWithText("预算").assertIsDisplayed()
        capture("m3_home_display_all")
    }

    /** 部分勾选：只留支出 + 结余 → 收入那格与整个预算块都不渲染。 */
    @Test
    fun partialHidesUnchecked() {
        render(HomeDisplaySettings(items = setOf(HomeStatItem.EXPENSE, HomeStatItem.BALANCE)))
        composeRule.onNodeWithText("支出").assertIsDisplayed()
        composeRule.onNodeWithText("结余").assertIsDisplayed()
        composeRule.onNodeWithText("收入").assertDoesNotExist()
        composeRule.onNodeWithText("预算").assertDoesNotExist()
        capture("m3_home_display_partial")
    }

    /** 全关：兜底只留「支出」（月卡头不能空着）。 */
    @Test
    fun allOffFallsBackToExpense() {
        render(HomeDisplaySettings(items = emptySet()))
        composeRule.onNodeWithText("支出").assertIsDisplayed()
        composeRule.onNodeWithText("收入").assertDoesNotExist()
        composeRule.onNodeWithText("结余").assertDoesNotExist()
        composeRule.onNodeWithText("预算").assertDoesNotExist()
        capture("m3_home_display_fallback")
    }

    /** 「⋯」入口在月卡头右侧，点它回调出去（真正的抽屉由 HomeScreen 打开）。 */
    @Test
    fun monthCardMoreButtonOpensSettings() {
        var opened = false
        composeRule.setContent {
            PennedTheme {
                HomeContent(
                    bookName = "日常账本",
                    rows = rows(),
                    onAdd = {},
                    onOpenDisplaySettings = { opened = true },
                )
            }
        }
        composeRule.onNodeWithContentDescription("首页显示设置").performClick()
        composeRule.waitForIdle()
        assertTrue(opened)
    }

    /** 抽屉：四项 switch + 兜底说明；关一项 → 回调 (项, false) 且月卡那格跟着消失。 */
    @Test
    fun displaySheetToggles() {
        val state = mutableStateOf(HomeDisplaySettings())
        val calls = mutableListOf<Pair<HomeStatItem, Boolean>>()
        composeRule.setContent {
            PennedTheme {
                Column {
                    HomeContent(
                        bookName = "日常账本",
                        rows = rows(),
                        onAdd = {},
                        display = state.value,
                    )
                    HomeDisplaySheet(
                        display = state.value,
                        onToggle = { item, on ->
                            calls += item to on
                            state.value = state.value.toggle(item, on)
                        },
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("首页显示设置").assertIsDisplayed()
        // 抽屉里的项名与月卡头里的格名重名（支出/收入/结余）→ 按"存在"断言，不按"唯一"
        HomeStatItem.entries.forEach { item ->
            assertTrue(item.label, count(item.label) >= 1)
        }
        composeRule.onNodeWithText("全都不勾时至少保留「支出」——月卡头不能空着").assertIsDisplayed()
        capture("m3_home_display_sheet")

        // 第一个开关是「支出」：关掉它 → 回调 + 月卡里那格消失
        composeRule.onAllNodes(isToggleable())[0].performClick()
        composeRule.waitForIdle()
        assertEquals(HomeStatItem.EXPENSE to false, calls.first())
        // 月卡里那一格没了，抽屉里的开关行还在（名字仍出现一次）
        assertEquals(1, count("支出"))
    }

    /**
     * 真库真导航：首页 → 月卡头「⋯」→ 关掉「收入」→ DataStore 落键、月卡不再有收入那格
     * （读取侧与写入侧是同一个键，重启后同样关着）。
     */
    @Test
    fun togglePersistsThroughDataStore() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        val bookId = "book-m313"
        runBlocking {
            val now = System.currentTimeMillis()
            app.database.bookDao().upsert(
                BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "t")
            )
            val today = LocalDate.now(zone).atTime(12, 30).atZone(zone).toEpochSecond() / 60
            app.database.transactionDao().upsert(
                TransactionEntity(
                    id = "t-exp", bookId = bookId, type = TxType.EXPENSE, amountCents = 1_200L,
                    dateEpochMinute = today, creatorDeviceId = "t", createdAt = now, updatedAt = now, deviceId = "t",
                )
            )
            app.database.transactionDao().upsert(
                TransactionEntity(
                    id = "t-inc", bookId = bookId, type = TxType.INCOME, amountCents = 300_000L,
                    dateEpochMinute = today, creatorDeviceId = "t", createdAt = now, updatedAt = now, deviceId = "t",
                )
            )
            app.settings.setCurrentBook(bookId)
            app.settings.setHomeStatItems(HomeDisplay.encode(HomeStatItem.entries.toSet()))
        }
        composeRule.setContent {
            PennedTheme {
                CompositionLocalProvider(LocalApp provides app) { PennedApp(app) }
            }
        }
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }
        composeRule.waitUntil(5_000) { count("收入") == 1 }

        composeRule.onNodeWithContentDescription("首页显示设置").performClick()
        composeRule.waitUntil(5_000) { count("首页显示设置") == 1 }   // 抽屉标题（卡头「⋯」是图标按钮，没有文字）
        // 抽屉里第二个开关是「收入」
        composeRule.onAllNodes(isToggleable())[1].performClick()
        composeRule.onNodeWithText("选好了").performClick()
        composeRule.waitUntil(5_000) { count("首页显示设置") == 0 }   // 抽屉关掉：标题没了
        // 月卡里那格没了——**这就是持久化的证据**：`display` 是 DataStore 流的映射，
        // 写没成功 UI 就不会变（读 DataStore 在 Robolectric 主线程上会死等，别去读）。
        // 等"收入"彻底消失：抽屉退场动画期间它的开关行也写着「收入」，别抢跑
        composeRule.waitUntil(5_000) { count("收入") == 0 }
        composeRule.onNodeWithText("支出").assertIsDisplayed()
        // 再开一次抽屉：那个开关仍是关的（值确实落库并回读）
        composeRule.onNodeWithContentDescription("首页显示设置").performClick()
        composeRule.waitUntil(5_000) { count("首页显示设置") == 1 }
        composeRule.waitForIdle()
        composeRule.onAllNodes(isToggleable())[1].assertIsOff()
        composeRule.onNodeWithText("选好了").performClick()
        // 收工复位（写在测试体里，见 resetDisplay 的注释）
        resetDisplay()
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size
}
