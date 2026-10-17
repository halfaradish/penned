package dev.penned.app.ui.nav

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.TxType
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.runBlocking
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
 * 记一笔的进出链路（UI-11 回归）：真库 + 真导航图，覆盖 flash 提示条的读写。
 *
 * 之前的 bug：`getStateFlow(key, null)` 的裸 null 让泛型推成 `Nothing?`（JVM `Void`），
 * 首页读取时插 `checkcast java/lang/Void` —— 只要真的读到值（保存完成 / 点顶栏返回写入了
 * "已记账"）就 `ClassCastException` 闪退。两条用例都在"读 flash"这条路径上，
 * 修前必红（第一条直接崩），修后必须绿。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddFlowNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        val bookId = "book-add-flow"
        app.database.bookDao().upsert(
            BookEntity(
                id = bookId,
                name = "测试账本",
                sortOrder = 0,
                createdAt = now,
                updatedAt = now,
                deviceId = "test",
            ),
        )
        // 记一笔页面的宫格要有分类可点（种子只在"库里没有任何账本"时跑；这里自建账本就得自建分类）
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-food",
                bookId = bookId,
                name = "食品餐饮",
                type = CategoryType.EXPENSE,
                createdAt = now,
                updatedAt = now,
                deviceId = "test",
            ),
        )
        // 一条已有流水：给"左滑 → 编辑"用例当被编辑对象
        app.database.transactionDao().upsert(
            TransactionEntity(
                id = "tx-edit-flow",
                bookId = bookId,
                type = TxType.EXPENSE,
                amountCents = 1234,
                categoryId = "cat-food",
                dateEpochMinute = LocalDate.now(ZoneId.systemDefault()).atTime(12, 0)
                    .atZone(ZoneId.systemDefault()).toEpochSecond() / 60,
                note = "左滑测试流水",
                creatorDeviceId = "test",
                createdAt = now,
                updatedAt = now,
                deviceId = "test",
            ),
        )
        app.settings.setCurrentBook(bookId)
    }

    private fun renderApp() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        composeRule.setContent {
            PennedTheme {
                CompositionLocalProvider(LocalApp provides app) {
                    PennedApp(app)
                }
            }
        }
        // 首页悬浮入口（顶部展开态）：等它渲染出来再动手
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }
    }

    @Test
    fun `顶栏返回只回退_不写提示也不崩`() {
        renderApp()
        composeRule.onNodeWithText("添加一条新记账").performClick()
        composeRule.waitUntil(5_000) { count("添加备注") == 1 }

        // 点顶栏返回：应只回退到首页（修复前这里 ClassCastException 闪退）
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }
        // 放弃记账不该出现"已记账"这种假提示
        composeRule.onNodeWithText("已记账").assertDoesNotExist()
    }

    @Test
    fun `保存完成回首页显示已记账提示条`() {
        renderApp()
        composeRule.onNodeWithText("添加一条新记账").performClick()
        composeRule.waitUntil(5_000) { count("添加备注") == 1 }

        // 选分类 + 输入金额 → 完成（走 onDone：写 flash + 回首页）
        composeRule.waitUntil(5_000) { count("食品餐饮") >= 1 }   // 分类列表是异步到达的
        composeRule.onAllNodesWithText("食品餐饮").onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("完成").assertIsEnabled()

        // 停表：Snackbar 的展示窗口（4s）必须由我们逐帧推进才看得到——直接
        // waitUntil/waitForIdle 会把虚拟时钟一路推过去，提示条出现即被等没了
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("完成").performClick()
        var shown = false
        var frames = 0
        while (!shown && frames < 400) {   // 400 帧 ≈ 6.4s 虚拟时间，足够覆盖保存 + 转场
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            shown = count("已记账") > 0
            frames++
        }
        composeRule.mainClock.autoAdvance = true
        assertTrue("保存后首页没有显示「已记账」提示条", shown)
        composeRule.onNodeWithText("已记账").assertIsDisplayed()
        // 优化轮 3（用户反馈）：提示条在**顶部**（浅色横幅），且不再压住底部的悬浮记账入口
        val rootHeight = composeRule.onRoot().fetchSemanticsNode().size.height
        val bannerTop = composeRule.onNodeWithText("已记账").fetchSemanticsNode().boundsInRoot.top
        assertTrue("提示条该在顶部（top=$bannerTop, 屏高=$rootHeight）", bannerTop < rootHeight / 4f)
        composeRule.onNodeWithText("添加一条新记账").assertIsDisplayed()

        // 停留时长（D099）：5s 到点自己走——不能像 10s 那样一直占着顶部。
        // 停表逐帧推（默认 4s/10s 那套时长也走同一根虚拟钟），7s 内必须已经收掉
        composeRule.mainClock.autoAdvance = false
        var gone = false
        var framesToGone = 0
        while (!gone && framesToGone < 440) {   // 440 帧 ≈ 7s 虚拟时间
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            gone = count("已记账") == 0
            framesToGone++
        }
        composeRule.mainClock.autoAdvance = true
        assertTrue("提示条该在 5s 左右自己走（等了 $framesToGone 帧 ≈ ${framesToGone * 16}ms 还没走）", gone)
    }

    /**
     * 首页行左滑动作区的「编辑」（UI-12）：点它进的是记一笔的编辑态，且带出原值。
     * 这条入口是新加的（此前首页行只能点进详情、再从详情的「编辑」进）——路由复用 `add?editId=`。
     */
    @Test
    fun `左滑动作区点编辑_编辑页带出原值`() {
        renderApp()
        composeRule.waitUntil(5_000) { count("左滑测试流水") == 1 }   // 流水是从库里异步到达的

        composeRule.onNodeWithText("左滑测试流水").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.waitUntil(5_000) { count("完成") == 1 }   // 记一笔页（明细页没有「完成」）

        // 备注在输入框里（首页那行也有同一串字，用"可输入"把范围钉在编辑页）
        composeRule.onNode(hasText("左滑测试流水") and hasSetTextAction()).assertIsDisplayed()
        // 金额不带符号：12.34（首页行是 -12.34）
        composeRule.onNodeWithText("12.34").assertIsDisplayed()
        composeRule.onNodeWithText("完成").assertIsEnabled()
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size
}
