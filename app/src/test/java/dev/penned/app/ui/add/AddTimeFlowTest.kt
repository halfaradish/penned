package dev.penned.app.ui.add

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.domain.CategoryType
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.components.TIME_WHEEL_HOUR_TAG
import dev.penned.app.ui.components.TIME_WHEEL_MINUTE_TAG
import dev.penned.app.ui.nav.PennedApp
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * 时间到分链路（优化轮 3）：真库 + 真导航图——
 * 记一笔点「时间」chip 滚轮选 16:38 → 落库 minute 精确到分 → 编辑页回填该时分 →
 * 不动它再存原值原样（分钟不漂移）→ 改时分只动时间、日期不动。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddTimeFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-time-flow"

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-shopping", bookId = bookId, name = "购物消费", type = CategoryType.EXPENSE,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.settings.setCurrentBook(bookId)
    }

    private fun renderApp(app: PennedApplication) {
        composeRule.setContent {
            PennedTheme {
                CompositionLocalProvider(LocalApp provides app) { PennedApp(app) }
            }
        }
        composeRule.waitUntil(5_000) { count("添加一条新记账") == 1 }
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** 按键盘键（tag 定位，避开与宫格重名的文本）。 */
    private fun press(key: String) = composeRule.onNodeWithTag("amount-key-$key").performClick()

    private fun awaitDb(timeoutMs: Long = 5_000, block: suspend () -> Long?): Long {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            runBlocking { block() }?.let { return it }
            Thread.sleep(20)
        }
        error("等数据库结果超时")
    }

    /** 真时间轮询界面条件（Room 的查询在后台线程上，虚拟时钟推不出结果）。 */
    private fun awaitUi(timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (predicate()) return
            Thread.sleep(20)
        }
        error("等界面超时")
    }

    /** 库里的 dateEpochMinute（本账本唯一一条）。 */
    private fun stored(app: PennedApplication): suspend () -> Long? = {
        app.database.transactionDao().allInBook(bookId).firstOrNull()?.dateEpochMinute
    }

    /** 滚轮列上选一个值：先滚到目标（performScrollToNode 只保证可见），点一下才落位到正中。 */
    private fun pickInWheel(tag: String, value: String) {
        composeRule.onNodeWithTag(tag).performScrollToNode(hasText(value))
        // 按列限定：时/分两列都是数字，值可能重名（比如现在 08:16，两列各有一个「16」）
        composeRule.onNode(hasText(value) and hasAnyAncestor(hasTestTag(tag))).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `选时分保存精确到分_编辑不动原值原样_改时分只动时间`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)
        val zone = ZoneId.systemDefault()

        // 记一笔：备注 + 分类 + 100
        composeRule.onNodeWithText("添加一条新记账").performClick()
        composeRule.waitUntil(5_000) { count("添加备注") == 1 }
        composeRule.waitUntil(5_000) { count("购物消费") >= 1 }
        composeRule.onNode(hasSetTextAction()).performTextInput("改时分的外卖")
        composeRule.onAllNodesWithText("购物消费").onFirst().performClick()
        press("1"); press("0"); press("0")

        // 时间 chip：默认「现在」；点开滚轮选 16:38
        composeRule.onNodeWithText("现在").assertIsDisplayed()
        composeRule.onNodeWithText("现在").performClick()
        composeRule.onNodeWithText("选择时间").assertIsDisplayed()
        pickInWheel(TIME_WHEEL_HOUR_TAG, "16")
        pickInWheel(TIME_WHEEL_MINUTE_TAG, "38")
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitUntil(5_000) { count("选择时间") == 0 }
        composeRule.onNodeWithText("16:38").assertIsDisplayed()

        press("完成")
        composeRule.waitUntil(5_000) { count("已记账") == 1 || count("已保存") == 1 }

        // 落库：今天 16:38（真 epoch 分钟、本地墙钟——与 AddDate/列表解码同口径）
        val expected = LocalDate.now(zone).atTime(16, 38).atZone(zone).toEpochSecond() / 60
        assertEquals(expected, awaitDb { stored(app)() })

        // 详情 → 编辑：时间 chip 回填 16:38（选中态）
        composeRule.waitUntil(5_000) { count("改时分的外卖") == 1 }
        composeRule.onNodeWithText("改时分的外卖").performClick()
        awaitUi { count("编辑") == 1 }
        // 底部「编辑」在折叠线以下：先滚到它再点（直接点会落在屏幕外、什么都不发生）
        composeRule.onNodeWithText("编辑").performScrollTo().performClick()
        awaitUi { count("16:38") == 1 }

        // 不动时间直接存：原值原样（分钟精度不漂移）
        press("完成")
        awaitUi { count("账单详情") == 1 }
        assertEquals(expected, awaitDb { stored(app)() })

        // 改成 09:05：只动时间，日期仍是今天
        composeRule.onNodeWithText("编辑").performScrollTo().performClick()
        awaitUi { count("16:38") == 1 }
        composeRule.onNodeWithText("16:38").performClick()
        composeRule.onNodeWithText("选择时间").assertIsDisplayed()
        pickInWheel(TIME_WHEEL_HOUR_TAG, "09")
        pickInWheel(TIME_WHEEL_MINUTE_TAG, "05")
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitUntil(5_000) { count("选择时间") == 0 }
        composeRule.onNodeWithText("09:05").assertIsDisplayed()
        press("完成")
        awaitUi { count("账单详情") == 1 }
        val changed = LocalDate.now(zone).atTime(9, 5).atZone(zone).toEpochSecond() / 60
        assertEquals(changed, awaitDb { stored(app)() })
    }
}
