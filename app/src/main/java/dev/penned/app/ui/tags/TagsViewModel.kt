package dev.penned.app.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.TagEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 标签管理（M3-1）：两级标签的增改删 + 隐藏。
 *
 * 隐藏（`hidden = true`）与软删的区别：隐藏的标签不出现在记账选择器与筛选里，
 * **已记账的流水仍照常显示**；软删（连带摘掉流水关联）是真删除。
 */
class TagsViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    @OptIn(ExperimentalCoroutinesApi::class)
    val tags: StateFlow<List<TagEntity>?> = db.bookDao().observeAll()
        .flatMapLatest { books ->
            app.settings.currentBookId.flatMapLatest { id ->
                val bookId = id?.let { wanted -> books.firstOrNull { it.id == wanted } }?.id ?: books.firstOrNull()?.id
                if (bookId == null) flowOf(emptyList()) else db.tagDao().observeByBook(bookId)
            }
        }
        .map<List<TagEntity>, List<TagEntity>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun bookId(): String? =
        app.settings.currentBookId.firstOrNull()
            ?: db.bookDao().observeAll().first().firstOrNull()?.id

    /** 新增标签；parentId 为空 = 一级。 */
    fun addTag(name: String, parentId: String?) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@launch
            val bid = bookId() ?: return@launch
            val siblings = db.tagDao().allInBook(bid).filter { it.parentId == parentId }
            val now = System.currentTimeMillis()
            db.tagDao().upsert(
                TagEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bid,
                    parentId = parentId,
                    name = trimmed,
                    sortOrder = siblings.size,
                    createdAt = now,
                    updatedAt = now,
                    deviceId = app.deviceId,
                )
            )
        }
    }

    /** 重命名 + 隐藏状态一次保存（编辑弹窗的「保存」）。 */
    fun saveTag(id: String, newName: String, hidden: Boolean) {
        viewModelScope.launch {
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) return@launch
            val original = db.tagDao().get(id) ?: return@launch
            db.tagDao().upsert(
                original.copy(name = trimmed, hidden = hidden, updatedAt = System.currentTimeMillis())
            )
        }
    }

    /** 删除：软删该标签（一级连它的二级一起）、并摘掉与流水的关联。 */
    fun deleteTag(id: String) {
        viewModelScope.launch {
            db.tagDao().deleteTag(id, System.currentTimeMillis())
        }
    }

    /** 标签挂在多少条未删流水上（编辑弹窗里的「已用于 N 笔流水」）。 */
    suspend fun usageCount(id: String): Int = db.tagDao().usageCount(id)
}
