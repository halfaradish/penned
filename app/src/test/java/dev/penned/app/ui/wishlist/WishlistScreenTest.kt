package dev.penned.app.ui.wishlist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.domain.WishlistProgress
import dev.penned.app.domain.WishlistStatus
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.nav.PennedApp
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.flow.first
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

/**
 * 愿望清单（M3-15）：卡片渲染（手动 / 跟随账户 / 已达成）+ 存入抽屉 + 编辑页 + 真库真导航链路。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WishlistScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** waitForIdle 轮询（VM 协程在 Robolectric 主线程上跑，waitUntil 推不动 DataStore 的读）。 */
    private fun awaitUi(timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (predicate()) return
            Thread.sleep(20)
        }
        error("等界面超时")
    }

    private val rows = listOf(
        WishlistProgress("w1", "换电脑", 800_000L, 320_000L, null, WishlistStatus.ACTIVE),
        WishlistProgress("w2", "旅行基金", 500_000L, 500_000L, null, WishlistStatus.ACTIVE, accountName = "微信"),
        WishlistProgress(
            "w3", "新手机", 300_000L, 120_000L,
            dueEpochDay = LocalDate.now().plusMonths(3).toEpochDay(), status = WishlistStatus.ACTIVE,
        ),
    )

    /** 卡片三态：手动（进度 + 还差）、跟随账户（副标题 + 无「存入一笔」）、有截止日（状态文案带截至）。 */
    @Test
    fun captureWishlist() {
        composeRule.setContent {
            PennedTheme {
                WishlistContent(rows = rows, onBack = {})
            }
        }
        composeRule.onNodeWithText("换电脑").assertIsDisplayed()
        composeRule.onNodeWithText("已存 3,200.00 / 目标 8,000.00").assertIsDisplayed()
        composeRule.onNodeWithText("还差 4,800.00").assertIsDisplayed()
        composeRule.onNodeWithText("跟随「微信」的余额").assertIsDisplayed()
        // 跟随账户的那张卡不给「存入一笔」（余额由账户说了算）
        assertEquals(2, count("存入一笔"))
        // 已达成（金额够）的那张：chip + 状态文案
        assertTrue(count("已达成") >= 1)
        capture("m3_wishlist")
    }

    @Test
    fun captureWishlistEmpty() {
        composeRule.setContent { PennedTheme { WishlistContent(rows = emptyList(), onBack = {}) } }
        composeRule.onNodeWithText("还没有攒钱目标").assertIsDisplayed()
        capture("m3_wishlist_empty")
    }

    /** 存入抽屉：输入金额 → 存入给正数、取出给负数；说明写明不生成流水。 */
    @Test
    fun depositSheetSignsAmount() {
        val calls = mutableListOf<Long>()
        val item = mutableStateOf(rows[0])
        composeRule.setContent {
            PennedTheme {
                DepositSheet(item = item.value, onConfirm = { calls += it }, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("存入一笔 · 换电脑").assertIsDisplayed()
        composeRule.onNodeWithText("只更新这条目标的「已存」，不会生成流水（攒钱目标是规划，不进统计与净资产）")
            .assertIsDisplayed()
        capture("m3_wishlist_deposit_sheet")

        // 输入 500 → 存入
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput("500")
        composeRule.onAllNodesWithText("存入")[1].performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(500_00L), calls)

        // 切「取出」再输 100 → 给负数
        composeRule.onAllNodesWithText("取出")[0].performClick()
        // 上一轮的 500 还在框里：先清空再输（performTextInput 是"追加"不是"替换"）
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextClearance()
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput("100")
        composeRule.onAllNodesWithText("取出")[1].performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(500_00L, -100_00L), calls)
    }

    /** 编辑页：手动/跟随两态 + 截止日 + 标记达成（仅编辑）+ 删除。 */
    @Test
    fun captureWishlistEdit() {
        val state = mutableStateOf(
            WishlistEditUiState(
                loaded = true, itemId = "w1", name = "换电脑",
                targetText = "8000", savedText = "3200",
            )
        )
        composeRule.setContent {
            PennedTheme {
                WishlistEditContent(
                    state = state.value,
                    onBack = {},
                    onDelete = {},
                    onModeChange = { state.value = state.value.copy(mode = it) },
                )
            }
        }
        composeRule.onNodeWithText("编辑目标").assertIsDisplayed()
        composeRule.onNodeWithText("存入方式").assertIsDisplayed()
        composeRule.onNodeWithText("手动存").assertIsDisplayed()
        composeRule.onNodeWithText("跟随账户").assertIsDisplayed()
        composeRule.onNodeWithText("标记达成").assertIsDisplayed()
        composeRule.onNodeWithText("删除目标").assertIsDisplayed()
        capture("m3_wishlist_edit")

        // 切到跟随账户：出现账户选择行（还没选 → 提示语）
        composeRule.onNodeWithText("跟随账户").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选一个账户，已存跟着它的余额走").assertExists()
    }

    /**
     * 真库真导航：我的 → 愿望清单（新建入口能进编辑页）→ 列表显示真库里的目标 →
     * 「存入一笔」+500 → 已存跟着变；**库里一条流水都没多**（攒钱目标不生成流水）。
     *
     * 表单填写那一段走 VM（`WishlistEditViewModel`）单独验：Robolectric 里在 NavHost 的长表单上
     * 打字的节点定位不稳（占位文字与输入框同屏、nav 转场期间旧页还在树上），
     * 而"保存落库"这条链路在 VM 上验得更实。
     */
    @Test
    fun createAndDepositThroughNav() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        val bookId = "book-wishlist"
        runBlocking {
            val now = System.currentTimeMillis()
            app.database.bookDao().upsert(
                BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "t")
            )
            app.settings.setCurrentBook(bookId)
        }

        // ① 起真导航（setContent 一个测试只能调一次）
        composeRule.setContent {
            PennedTheme {
                CompositionLocalProvider(LocalApp provides app) { PennedApp(app) }
            }
        }
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }

        // ② VM 层：新建（名称 + 目标）→ 落库（composition 已在跑，viewModelScope 能执行）
        val vm = WishlistEditViewModel(app, editId = null)
        vm.setName("换电脑")
        vm.setTarget("8000")
        // 用 AtomicBoolean：回调在 VM 的协程里跑，普通局部变量的写不保证对测试线程可见
        val saved = java.util.concurrent.atomic.AtomicBoolean(false)
        vm.save(onDone = { saved.set(true) })
        // 本仓库验过的姿势：waitForIdle 轮询（waitUntil 里 DataStore 读不推进——M3-13 的坑）
        awaitUi { saved.get() }
        assertEquals(1, runBlocking { app.database.wishlistDao().countInBook(bookId) })

        // ③ 导航：我的 → 愿望清单 → 列表显示这条目标 → 新建入口能进编辑页
        composeRule.onNodeWithText("我的").performClick()
        composeRule.waitUntil(5_000) { count("愿望清单") == 1 }
        composeRule.onNodeWithText("愿望清单").performClick()
        composeRule.waitUntil(5_000) { count("换电脑") == 1 }
        composeRule.onNodeWithText("还差 8,000.00").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("新建目标").performClick()
        composeRule.waitUntil(5_000) { count("存入方式") == 1 }
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitUntil(5_000) { count("换电脑") == 1 }

        // ③ 存入一笔 +500（抽屉里输入框只有一格：先清空再输）
        composeRule.onNodeWithText("存入一笔", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitUntil(5_000) { count("存入一笔 · 换电脑") == 1 }
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput("500")
        composeRule.onAllNodesWithText("存入")[1].performClick()
        composeRule.waitUntil(5_000) { count("已存 500.00 / 目标 8,000.00") == 1 }
        composeRule.onNodeWithText("还差 7,500.00").assertIsDisplayed()

        // ④ 库里：一条目标、**零条流水**
        val items = runBlocking { app.database.wishlistDao().observeByBook(bookId).first() }
        assertEquals(1, items.size)
        assertEquals(500_00L, items[0].item.savedCents)
        assertEquals(0, runBlocking { app.database.transactionDao().countInBook(bookId) })
    }
}
