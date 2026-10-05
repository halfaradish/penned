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
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class BooksViewModel(private val app: PennedApplication) : ViewModel() {

    val books: StateFlow<List<BookEntity>> = app.database.bookDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentBookId: StateFlow<String?> = combine(books, app.settings.currentBookId) { list, id ->
        id?.takeIf { wanted -> list.any { it.id == wanted } } ?: list.firstOrNull()?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
            app.database.bookDao().upsert(book)
            app.settings.setCurrentBook(book.id)
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            val original = app.database.bookDao().get(id) ?: return@launch
            app.database.bookDao().upsert(original.copy(name = name.trim().ifEmpty { original.name }, updatedAt = System.currentTimeMillis()))
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

    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<BookEntity?>(null) }

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
                onClick = { showAdd = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) { Text("新增账本") }
            LazyColumn {
                items(books, key = { it.id }) { book ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.switch(book.id) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
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
                        TextButton(onClick = { renameTarget = book }) { Text("重命名") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAdd) {
        TextDialog("新增账本", onConfirm = { viewModel.add(it); showAdd = false }, onDismiss = { showAdd = false })
    }
    renameTarget?.let { target ->
        TextDialog("重命名账本", initial = target.name, onConfirm = { viewModel.rename(target.id, it); renameTarget = null }, onDismiss = { renameTarget = null })
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
