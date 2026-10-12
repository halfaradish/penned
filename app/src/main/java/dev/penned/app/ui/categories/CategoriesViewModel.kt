package dev.penned.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.domain.CategoryType
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
 * 分类管理：支出/收入两域分类树的增改删（软删）。
 */
class CategoriesViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    @OptIn(ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<CategoryEntity>?> = db.bookDao().observeAll()
        .flatMapLatest { books ->
            app.settings.currentBookId.flatMapLatest { id ->
                val bookId = id?.let { wanted -> books.firstOrNull { it.id == wanted } }?.id ?: books.firstOrNull()?.id
                if (bookId == null) flowOf(emptyList()) else db.categoryDao().observeByBook(bookId)
            }
        }
        .map<List<CategoryEntity>, List<CategoryEntity>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun bookId(): String? =
        app.settings.currentBookId.firstOrNull()
            ?: db.bookDao().observeAll().first().firstOrNull()?.id

    /** 新增分类；parentId 为空 = 一级。完成时回调新分类 id。 */
    fun addCategory(name: String, parentId: String?, type: CategoryType, onCreated: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@launch
            val bid = bookId() ?: return@launch
            val siblings = db.categoryDao().allInBook(bid).filter { it.parentId == parentId && it.type == type }
            val now = System.currentTimeMillis()
            val category = CategoryEntity(
                id = UUID.randomUUID().toString(),
                bookId = bid,
                parentId = parentId,
                name = trimmed,
                type = type,
                sortOrder = siblings.size,
                createdAt = now,
                updatedAt = now,
                deviceId = app.deviceId,
            )
            db.categoryDao().upsert(category)
            onCreated(category.id)
        }
    }

    /** 重命名 + 隐藏一次保存（编辑弹窗的「保存」；M3-1 起含隐藏，D074）。 */
    fun saveCategory(id: String, newName: String, hidden: Boolean) {
        viewModelScope.launch {
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) return@launch
            val original = db.categoryDao().get(id) ?: return@launch
            db.categoryDao().upsert(
                original.copy(name = trimmed, hidden = hidden, updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun softDelete(id: String) {
        viewModelScope.launch {
            db.categoryDao().softDelete(id, System.currentTimeMillis())
        }
    }
}
