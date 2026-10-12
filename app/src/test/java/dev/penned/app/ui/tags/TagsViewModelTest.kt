package dev.penned.app.ui.tags

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.TxType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 标签管理页的操作（M3-1）：新建两级 / 重命名 + 隐藏 / 删除（连带子标签与流水关联）/ 用量计数。
 *
 * 这些动作在 UI 上要经过"含文本输入框的弹窗"，而 Robolectric 下这种弹窗永远不 idle
 * （看板已知坑）——所以逻辑在 VM 层测；UI 层的列表渲染由 `TagsScreenTest` 钉，
 * 弹窗交互在模拟机走查里过一遍。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class TagsViewModelTest {

    private val app: PennedApplication = ApplicationProvider.getApplicationContext()
    private val bookId = "book-tags-vm"

    private fun seed() = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "测试账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "test")
        )
        app.settings.setCurrentBook(bookId)
    }

    /** viewModelScope 跑在 Robolectric 的主 looper 上（paused 模式），要手动 idle 才推进 */
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
    fun `新建一级与二级标签_顺序号按同级累加`() {
        seed()
        val vm = TagsViewModel(app)
        vm.addTag("出差", null)
        val parent = awaitDb { app.database.tagDao().allInBook(bookId).firstOrNull { it.name == "出差" } }

        vm.addTag("交通", parent.id)
        val child = awaitDb { app.database.tagDao().allInBook(bookId).firstOrNull { it.name == "交通" } }

        assertEquals(parent.id, child.parentId)
        assertEquals(0, child.sortOrder)
        // 空名字不建
        vm.addTag("   ", null)
        idle()
        assertEquals(2, runBlocking { app.database.tagDao().allInBook(bookId).size })
    }

    @Test
    fun `重命名与隐藏一次保存`() {
        seed()
        val vm = TagsViewModel(app)
        vm.addTag("出差", null)
        val tag = awaitDb { app.database.tagDao().allInBook(bookId).firstOrNull() }

        vm.saveTag(tag.id, "出差（旧）", hidden = true)
        val saved = awaitDb { app.database.tagDao().get(tag.id)?.takeIf { it.name == "出差（旧）" } }

        assertEquals("出差（旧）", saved.name)
        assertTrue(saved.hidden)
    }

    @Test
    fun `删除一级标签连带二级与流水关联_流水本身保留`() = runBlocking {
        seed()
        val vm = TagsViewModel(app)
        vm.addTag("出差", null)
        val parent = awaitDb { app.database.tagDao().allInBook(bookId).firstOrNull { it.name == "出差" } }
        vm.addTag("交通", parent.id)
        val child = awaitDb { app.database.tagDao().allInBook(bookId).firstOrNull { it.name == "交通" } }

        val now = System.currentTimeMillis()
        app.database.transactionDao().upsert(
            TransactionEntity(
                id = "tx-1", bookId = bookId, type = TxType.EXPENSE, amountCents = 100,
                dateEpochMinute = now / 60_000, creatorDeviceId = "test",
                createdAt = now, updatedAt = now, deviceId = "test",
            )
        )
        app.database.tagDao().setTagsForTx("tx-1", listOf(parent.id, child.id))
        // 每个标签各挂 1 条流水（用量按标签分别数）
        assertEquals(1, app.database.tagDao().usageCount(parent.id))
        assertEquals(1, app.database.tagDao().usageCount(child.id))

        vm.deleteTag(parent.id)
        idle()

        assertTrue(awaitDb { app.database.tagDao().get(parent.id)?.takeIf { it.deleted } } != null)
        assertTrue(awaitDb { app.database.tagDao().get(child.id)?.takeIf { it.deleted } } != null)
        assertEquals(0, app.database.tagDao().usageCount(child.id))
        // 流水保留（删标签不是删账）
        assertEquals(1, app.database.transactionDao().allInBook(bookId).size)
        assertNull(app.database.tagDao().tagsForTx("tx-1").firstOrNull())
    }
}
