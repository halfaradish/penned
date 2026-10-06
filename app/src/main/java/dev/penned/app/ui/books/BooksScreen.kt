package dev.penned.app.ui.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class BooksViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    val books: StateFlow<List<BookEntity>> = db.bookDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentBookId: StateFlow<String?> = combine(books, app.settings.currentBookId) { list, id ->
        id?.takeIf { wanted -> list.any { it.id == wanted } } ?: list.firstOrNull()?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 待删账本的流水条数（确认弹窗用）；null = 还没查出来。 */
    private val _deleteCount = MutableStateFlow<Int?>(null)
    val deleteCount: StateFlow<Int?> = _deleteCount

    fun switch(id: String) {
        viewModelScope.launch { app.settings.setCurrentBook(id) }
    }

    fun add(name: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val book = BookEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifEmpty { "新账本" },
                sortOrder = books.value.size,
                createdAt = now,
                updatedAt = now,
                deviceId = app.deviceId,
            )
            db.bookDao().upsert(book)
            app.settings.setCurrentBook(book.id)
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            val original = db.bookDao().get(id) ?: return@launch
            db.bookDao().upsert(original.copy(name = name.trim().ifEmpty { original.name }, updatedAt = System.currentTimeMillis()))
        }
    }

    fun prepareDelete(bookId: String) {
        viewModelScope.launch { _deleteCount.value = db.transactionDao().countInBook(bookId) }
    }

    fun cancelDelete() {
        _deleteCount.value = null
    }

    /**
     * 软删账本及其全部数据（流水/账户/分类/预算/导入批次，同一事务）。
     * 至少保留一个账本；删的是当前账本时先把当前账本切到别的本，避免切到已删账本。
     */
    fun delete(id: String) {
        viewModelScope.launch {
            val list = books.value
            if (list.size <= 1) return@launch
            val now = System.currentTimeMillis()
            if (currentBookId.value == id) {
                list.firstOrNull { it.id != id }?.let { app.settings.setCurrentBook(it.id) }
            }
            db.withTransaction {
                db.bookDao().softDelete(id, now)
                db.transactionDao().softDeleteByBook(id, now)
                db.accountDao().softDeleteByBook(id, now)
                db.categoryDao().softDeleteByBook(id, now)
                db.budgetDao().softDeleteByBook(id, now)
                db.importBatchDao().softDeleteByBook(id, now)
            }
            _deleteCount.value = null
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(onBack: () -> Unit) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: BooksViewModel = viewModel(initializer = { BooksViewModel(app) })
    val books by viewModel.books.collectAsStateWithLifecycle()
    val currentId by viewModel.currentBookId.collectAsStateWithLifecycle()
    val deleteCount by viewModel.deleteCount.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<BookEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<BookEntity?>(null) }

    BooksContent(
        books = books,
        currentId = currentId,
        deleteCount = deleteCount,
        onSwitch = viewModel::switch,
        onAdd = { showAdd = true },
        onRename = { renameTarget = it },
        onDelete = { book ->
            deleteTarget = book
            viewModel.prepareDelete(book.id)
        },
        onBack = onBack,
    )

    if (showAdd) {
        TextDialog("新增账本", onConfirm = { viewModel.add(it); showAdd = false }, onDismiss = { showAdd = false })
    }
    renameTarget?.let { target ->
        TextDialog("重命名账本", initial = target.name, onConfirm = { viewModel.rename(target.id, it); renameTarget = null }, onDismiss = { renameTarget = null })
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null; viewModel.cancelDelete() },
            title = { Text("删除账本「${target.name}」？") },
            text = {
                Text(
                    "该账本的 " + (deleteCount?.let { "$it 条流水与" } ?: "") +
                        "账户、分类、预算、导入批次将一并删除，删除后不再显示。",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(target.id); deleteTarget = null }) {
                    Text("删除", color = dev.penned.app.ui.theme.expenseColor())
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null; viewModel.cancelDelete() }) { Text("取消") }
            },
        )
    }
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BooksContent(
    books: List<BookEntity>,
    currentId: String?,
    deleteCount: Int? = null,
    onSwitch: (String) -> Unit = {},
    onAdd: () -> Unit = {},
    onRename: (BookEntity) -> Unit = {},
    onDelete: (BookEntity) -> Unit = {},
    onBack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理账本") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹ 返回") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) { Text("新增账本") }
            LazyColumn {
                items(books, key = { it.id }) { book ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSwitch(book.id) }
                            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                book.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (book.id == currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (book.id == currentId) {
                                Text("当前账本", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        TextButton(onClick = { onRename(book) }) { Text("重命名") }
                        // 至少保留一个账本
                        TextButton(onClick = { onDelete(book) }, enabled = books.size > 1) {
                            Text("删除", color = if (books.size > 1) dev.penned.app.ui.theme.expenseColor() else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TextDialog(title: String, initial: String = "", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("账本名称") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
