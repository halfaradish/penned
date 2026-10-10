package dev.penned.app.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * 首页行左滑动作区（UI-12 / D053）走真库端到端，QQ 口径逐条钉住：
 * 左滑只把「编辑 / 删除」跟手露出来（不弹窗、不删）→ 松手超过半宽才定格 → 点按钮才动作。
 *
 * 误触防护的根据：**"滑到某个位置就触发"这条路径被整条删掉了**——旧的 `SwipeToDeleteRow`
 * 滑过 56dp 或甩速过 125dp/s 就弹确认（用户报的"极易误触"），这里滑永远只改露出宽度。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeRowActionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 露满宽度 = 两个 64dp 动作块（`PennedDimens.rowActionWidth` × 2）。 */
    private val revealedWidth = 128f

    private class Home(val app: PennedApplication, val vm: HomeViewModel) {
        var editedId: String? = null
        var openedId: String? = null
    }

    private fun seed(app: PennedApplication, rows: List<Pair<String, String>>) = runBlocking {
        val now = System.currentTimeMillis()
        val bookId = "book-row-action"
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
        app.settings.setCurrentBook(bookId)
        rows.forEach { (id, note) ->
            app.database.transactionDao().upsert(
                TransactionEntity(
                    id = id,
                    bookId = bookId,
                    type = TxType.EXPENSE,
                    amountCents = 1234,
                    dateEpochMinute = LocalDate.now(zone).atTime(12, 0).atZone(zone).toEpochSecond() / 60,
                    note = note,
                    creatorDeviceId = "test",
                    createdAt = now,
                    updatedAt = now,
                    deviceId = "test",
                ),
            )
        }
    }

    /**
     * 每行都有自己的动作区（都叫「编辑 / 删除」），所以除"开另一行"那条用例之外
     * 一律只种一行，节点才唯一。
     */
    private fun render(rows: List<Pair<String, String>> = listOf("tx-a" to "左滑测试流水")): Home {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app, rows)
        val home = Home(app, HomeViewModel(app))
        composeRule.setContent {
            PennedTheme {
                HomeScreen(
                    viewModel = home.vm,
                    onAdd = {},
                    onOpenDetail = { home.openedId = it },
                    onManageBooks = {},
                    onEditRow = { home.editedId = it },
                )
            }
        }
        rows.forEach { (_, note) -> composeRule.waitUntil(5_000) { count(note) == 1 } }
        return home
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** 行（内容层）左边界：露出动作区时整行被推向左边，用位移量断言展开/收起。 */
    private fun leftOf(text: String): Float =
        composeRule.onNodeWithText(text).getUnclippedBoundsInRoot().left.value

    private fun isDeleted(home: Home, id: String): Boolean =
        runBlocking { home.app.database.transactionDao().get(id)!!.deleted }

    @Test
    fun `左滑只露出动作区_不弹确认也不删`() {
        val home = render()
        val closedLeft = leftOf("左滑测试流水")

        composeRule.onNodeWithText("左滑测试流水").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertTrue("左滑后行没有被推向左边（动作区没露出来）", leftOf("左滑测试流水") < closedLeft - 64f)
        // 旧行为：滑过阈值弹「删除这条流水？」——现在这条路径整条不存在
        assertEquals(0, count("删除这条流水？"))
        assertFalse(isDeleted(home, "tx-a"))
        assertEquals(0, home.vm.deletedIds.value.size)
    }

    @Test
    fun `露满后继续左滑不再移动_松手按钮也不消失`() {
        render()
        val closedLeft = leftOf("左滑测试流水")

        composeRule.onNodeWithText("左滑测试流水").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        val openLeft = leftOf("左滑测试流水")
        assertEquals("露满宽度应等于两个动作块", revealedWidth, closedLeft - openLeft, 2f)

        // 已经露满：再甩一次也不再移动（QQ 口径：完全展示后不可再移动）
        composeRule.onNodeWithText("左滑测试流水").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(openLeft, leftOf("左滑测试流水"), 0.5f)

        // 松手后按钮仍在（不是"滑完就消失"）
        composeRule.onNodeWithText("编辑").assertIsDisplayed()
        composeRule.onNodeWithText("删除").assertIsDisplayed()
    }

    @Test
    fun `小幅左滑松手自己收回_往回拖也收回`() {
        render()
        val closedLeft = leftOf("左滑测试流水")

        // 40px < 半宽（64）：慢速拖动（300ms）不带甩速，松手应回位
        composeRule.onNodeWithText("左滑测试流水").performTouchInput {
            down(center)
            moveBy(Offset(-40f, 0f), delayMillis = 300)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(closedLeft, leftOf("左滑测试流水"), 0.5f)

        // 露满后往回拖到不足半宽 → 松手也收回
        composeRule.onNodeWithText("左滑测试流水").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertTrue(leftOf("左滑测试流水") < closedLeft - 64f)
        composeRule.onNodeWithText("左滑测试流水").performTouchInput {
            down(center)
            moveBy(Offset(140f, 0f), delayMillis = 300)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(closedLeft, leftOf("左滑测试流水"), 0.5f)
    }

    @Test
    fun `竖向滑动不会展开动作区（误触回归）`() {
        val home = render()
        val closedLeft = leftOf("左滑测试流水")

        composeRule.onNodeWithText("左滑测试流水").performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        assertEquals("竖向滑动把行横着推走了（误触）", closedLeft, leftOf("左滑测试流水"), 0.5f)
        assertEquals(0, count("删除这条流水？"))
        assertFalse(isDeleted(home, "tx-a"))
    }

    @Test
    fun `点删除才软删_撤销把行放回来`() {
        val home = render()
        val closedLeft = leftOf("左滑测试流水")

        composeRule.onNodeWithText("左滑测试流水").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        // 展开 ≠ 删除：库里还没动
        assertFalse(isDeleted(home, "tx-a"))

        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(5_000) { count("左滑测试流水") == 0 }
        composeRule.onNodeWithText("已删除 1 条").assertIsDisplayed()
        assertTrue(isDeleted(home, "tx-a"))

        // 撤销 → 行回到列表（先点撤销，缩短与 Snackbar 超时的竞争窗口）
        composeRule.waitForIdle()
        composeRule.onNodeWithText("撤销").performClick()
        composeRule.waitUntil(5_000) { count("左滑测试流水") == 1 }
        assertFalse(isDeleted(home, "tx-a"))
        composeRule.waitForIdle()
        assertEquals(0, home.vm.deletedIds.value.size)
        // 复原回来的行是收起态，且不会被"复活的展开状态"再删一次
        assertEquals(closedLeft, leftOf("左滑测试流水"), 0.5f)
        assertFalse(isDeleted(home, "tx-a"))
    }

    @Test
    fun `点编辑回传该行id且不删`() {
        val home = render()
        val closedLeft = leftOf("左滑测试流水")

        composeRule.onNodeWithText("左滑测试流水").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("编辑").performClick()
        composeRule.waitForIdle()

        assertEquals("tx-a", home.editedId)
        assertFalse(isDeleted(home, "tx-a"))
        // 动作区收起：从编辑页回来时不会还开着
        assertEquals(closedLeft, leftOf("左滑测试流水"), 0.5f)
    }

    @Test
    fun `展开态点行内容只收起_不进详情`() {
        val home = render()
        val closedLeft = leftOf("左滑测试流水")

        composeRule.onNodeWithText("左滑测试流水").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("左滑测试流水").performClick()
        composeRule.waitForIdle()
        assertNull("展开态点行内容不该进详情", home.openedId)
        assertEquals(closedLeft, leftOf("左滑测试流水"), 0.5f)

        // 收起态再点：照旧进详情
        composeRule.onNodeWithText("左滑测试流水").performClick()
        composeRule.waitForIdle()
        assertEquals("tx-a", home.openedId)
    }

    @Test
    fun `打开另一行会收起上一行`() {
        render(rows = listOf("tx-a" to "左滑测试流水", "tx-b" to "第二行流水"))
        val closedLeft = leftOf("左滑测试流水")

        composeRule.onNodeWithText("左滑测试流水").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertTrue(leftOf("左滑测试流水") < closedLeft - 64f)

        composeRule.onNodeWithText("第二行流水").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals("上一行没有收起（只允许一行为开）", closedLeft, leftOf("左滑测试流水"), 0.5f)
        assertTrue(leftOf("第二行流水") < closedLeft - 64f)
    }
}
