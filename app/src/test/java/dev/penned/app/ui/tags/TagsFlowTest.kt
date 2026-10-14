package dev.penned.app.ui.tags

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.domain.CategoryType
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.add.AddViewModel
import dev.penned.app.ui.nav.PennedApp
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 标签链路（M3-1）：真库 + 真导航图——选标签 → 保存 → 详情页可见 → 编辑回填；抽屉两级与多选。
 * 「新建标签即选中」在 VM 层验（含文本输入的弹窗在 Robolectric 下不 idle，见各用例注释）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TagsFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookId = "book-tags-flow"

    private fun seed(app: PennedApplication) = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-food", bookId = bookId, name = "食品餐饮", type = CategoryType.EXPENSE,
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        listOf(
            TagEntity("tag-trip", bookId, null, "出差", 0, false, now, now, false, "test"),
            TagEntity("tag-transport", bookId, "tag-trip", "交通", 1, false, now, now, false, "test"),
            TagEntity("tag-old", bookId, null, "旧标签", 2, true, now, now, false, "test"),
        ).forEach { app.database.tagDao().upsert(it) }
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

    private fun openAddScreen() {
        composeRule.onNodeWithText("添加一条新记账").performClick()
        composeRule.waitUntil(5_000) { count("添加备注") == 1 }
        composeRule.waitUntil(5_000) { count("食品餐饮") >= 1 }   // 分类是异步到达的
    }

    private fun count(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun `选标签保存后详情页可见_编辑页回填`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)
        openAddScreen()

        // 备注 + 分类 + 金额
        composeRule.onNode(hasSetTextAction()).performTextInput("带标签的流水")
        composeRule.onAllNodesWithText("食品餐饮").onFirst().performClick()
        composeRule.onNodeWithText("1").performClick()

        // 标签抽屉：一级 + 二级多选，隐藏的「旧标签」不出现
        composeRule.onNodeWithText("标签").performClick()
        composeRule.waitUntil(5_000) { count("选择标签") == 1 }
        composeRule.onNodeWithText("出差").assertIsDisplayed()
        composeRule.onNodeWithText("交通").assertIsDisplayed()
        composeRule.onNodeWithText("旧标签").assertDoesNotExist()
        composeRule.onNodeWithText("出差").performClick()
        composeRule.onNodeWithText("交通").performClick()
        composeRule.onNodeWithText("选好了").performClick()
        composeRule.waitUntil(5_000) { count("标签 · 2") == 1 }

        // 保存 → 回首页（判据用首页提示条「已记账」：M3-5 起保存后提示条带「存为模板」动作、
        // 10s 内挡着浮动记账入口；备注文本此刻还在记账表单里，也不能当"已保存"的信号）
        composeRule.onNodeWithText("完成").performClick()
        composeRule.waitUntil(5_000) { count("已记账") == 1 || count("已保存") == 1 }

        // 落库：这条流水挂了两个标签（按 sortOrder 排序）——保存是单事务，流水在则关系在
        val saved = awaitDb {
            app.database.transactionDao().allInBook(bookId).firstOrNull()?.let { tx ->
                app.database.tagDao().tagsForTx(tx.id).map { it.name }.takeIf { it.isNotEmpty() }
            }
        }
        assertEquals(listOf("出差", "交通"), saved)

        // 详情页「标签」行（首页行从库里异步到达）
        composeRule.waitUntil(5_000) { count("带标签的流水") == 1 }
        composeRule.onNodeWithText("带标签的流水").performClick()
        composeRule.waitUntil(5_000) { count("账单详情") == 1 }
        // 详情页先画顶栏占位、数据到了才出明细（载入帧只有标题）。这里要等的是**两次 Room 查询**
        // 的回填：`waitUntil` 花的是虚拟时间，背景线程可能一秒真时间都没分到——用真时间轮询更稳
        awaitUi { count("出差 · 交通") == 1 }
        composeRule.onNodeWithText("标签").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("出差 · 交通").assertIsDisplayed()

        // 编辑回填：信息条 chip 回显数量（标签引用是到库里查的，不靠异步列表）
        composeRule.onNodeWithText("编辑").performScrollTo().performClick()
        awaitUi { count("标签 · 2") == 1 }
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

    /**
     * 新建即选中：**在 VM 层验**——新建标签要经过"含文本输入框的弹窗"，而 Robolectric 下这种弹窗
     * 永远不 idle（看板已知坑）。UI 上"新建标签"入口的存在性由 `TagPickerScreenshotTest` 钉，
     * 弹窗交互在模拟机走查里过一遍。
     */
    @Test
    fun `新建标签即选中并落库`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        runBlocking { seed(app) }
        val vm = AddViewModel(app)
        // 让 currentBook 的 stateIn 有订阅者：WhileSubscribed 的流没人收时 .value 恒为初值（null），
        // addTagAndSelect 会直接 return（D055 同型坑）。两条流要分别订：它们是各自独立的 stateIn。
        val scope = CoroutineScope(Dispatchers.Main)
        val jobTags = scope.launch { vm.allTags.collect { } }
        val jobCategories = scope.launch { vm.categories.collect { } }
        awaitDb { vm.allTags.value.takeIf { it.isNotEmpty() } }
        awaitDb { vm.categories.value.firstOrNull { it.id == "cat-food" } }

        vm.addTagAndSelect("报销中", null)
        val created = awaitDb {
            app.database.tagDao().allInBook(bookId).firstOrNull { it.name == "报销中" }
        }
        // 选中与建标签是同一协程里的两步：库里有只是第一步成了，选中要单独等
        val selected = awaitDb { vm.selectedTagIds.value.takeIf { it.isNotEmpty() } }
        assertTrue("新建的标签没有被选中", selected.contains(created.id))

        // 落库：把选中的标签随流水写进关系表
        vm.selectParent("cat-food", hasSubs = false)
        vm.pressKey("1")
        vm.save { }
        val saved = awaitDb {
            app.database.transactionDao().allInBook(bookId).firstOrNull()?.let { tx ->
                app.database.tagDao().tagsForTx(tx.id).map { it.name }.takeIf { it.isNotEmpty() }
            }
        }
        assertEquals(listOf("报销中"), saved)
        jobTags.cancel()
        jobCategories.cancel()
    }

    /**
     * 隐藏一级标签 = 连它的子树一起收起来（与分类同一口径）。这条是在模拟机上走出来的：
     * 只滤掉隐藏的那一个，会让"二级还可见但父 chip 不画"——二级就没地方显示了。
     */
    @Test
    fun `隐藏一级标签时它的二级也不进选择器`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        runBlocking { seed(app) }
        val vm = AddViewModel(app)
        // selectableTags 也是 WhileSubscribed 的 stateIn：**必须自己订阅**，否则 .value 恒是初值空表
        // （第一版就是因为只订了 allTags，断言"看不见"其实什么都没证）
        val scope = CoroutineScope(Dispatchers.Main)
        val jobTags = scope.launch { vm.allTags.collect { } }
        val jobVisible = scope.launch { vm.selectableTags.collect { } }
        awaitDb { vm.allTags.value.firstOrNull { it.id == "tag-trip" } }
        awaitDb { vm.selectableTags.value.firstOrNull { it.id == "tag-transport" } }

        val before = vm.selectableTags.value.map { it.id }
        runBlocking { app.database.tagDao().setHidden("tag-trip", hidden = true, now = System.currentTimeMillis()) }
        awaitDb { vm.selectableTags.value.map { it.id }.takeIf { ids -> ids != before } }
        assertTrue(
            "隐藏的一级标签的二级标签不该还在选择器里",
            vm.selectableTags.value.none { it.id == "tag-transport" },
        )

        // 取消隐藏 → 二级回来
        runBlocking { app.database.tagDao().setHidden("tag-trip", hidden = false, now = System.currentTimeMillis()) }
        awaitDb { vm.selectableTags.value.firstOrNull { it.id == "tag-transport" } }
        jobTags.cancel()
        jobVisible.cancel()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun <T> awaitDb(timeoutMs: Long = 5_000, block: suspend () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            idle()
            runBlocking { block() }?.let { return it }
            Thread.sleep(20)
        }
        error("等数据库结果超时")
    }

    @Test
    fun `标签抽屉多选可取消`() {
        val app = ApplicationProvider.getApplicationContext<PennedApplication>()
        seed(app)
        renderApp(app)
        openAddScreen()

        composeRule.onNodeWithText("标签").performClick()
        composeRule.waitUntil(5_000) { count("选择标签") == 1 }
        composeRule.onNodeWithText("出差").performClick()
        composeRule.onNodeWithText("选好了").performClick()
        composeRule.waitUntil(5_000) { count("标签 · 1") == 1 }

        // 再开抽屉取消选中（多选是"点一下切换"，不是一次性单选）
        composeRule.onNodeWithText("标签 · 1").performClick()
        composeRule.waitUntil(5_000) { count("选择标签") == 1 }
        composeRule.onNodeWithText("出差").performClick()
        composeRule.onNodeWithText("选好了").performClick()
        composeRule.waitUntil(5_000) { count("标签") >= 1 }
        composeRule.onNodeWithText("标签 · 1").assertDoesNotExist()
    }
}
