package dev.penned.app.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.domain.CategoryType
import dev.penned.app.ui.theme.categoryColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import dev.penned.app.ui.theme.expenseColor

/**
 * 分类管理页：支出/收入两域树形列表，支持新增（一级/子级）、重命名、软删除。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onBack: () -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var addDomain by remember { mutableStateOf<CategoryType?>(null) }
    var editTarget by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分类管理") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ 返回") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            val list = categories.orEmpty()
            listOf(CategoryType.EXPENSE to "支出分类", CategoryType.INCOME to "收入分类").forEach { (domain, title) ->
                val parents = list.filter { it.type == domain && it.parentId == null }.sortedBy { it.sortOrder }
                SectionHeader(title) {
                    Button(onClick = { addDomain = domain }, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("+ 新增")
                    }
                }
                parents.forEach { parent ->
                    val subs = list.filter { it.parentId == parent.id }.sortedBy { it.sortOrder }
                    CategoryRow(parent = parent, subs = subs, onClick = { editTarget = parent })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    addDomain?.let { domain ->
        AddCategoryDialog(
            domain = domain,
            parents = categories.orEmpty().filter { it.type == domain && it.parentId == null }.sortedBy { it.sortOrder },
            onConfirm = { name, parentId ->
                viewModel.addCategory(name, parentId, domain)
                addDomain = null
            },
            onDismiss = { addDomain = null },
        )
    }
    editTarget?.let { target ->
        EditCategoryDialog(
            category = target,
            onRename = { newName ->
                viewModel.rename(target.id, newName)
                editTarget = null
            },
            onDelete = {
                viewModel.softDelete(target.id)
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }
}

@Composable
private fun SectionHeader(title: String, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
        )
        actions()
    }
}

@Composable
private fun CategoryRow(parent: CategoryEntity, subs: List<CategoryEntity>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(categoryColor(parent.name), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(parent.name.take(1), style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color.White)
        }
        Spacer(Modifier.size(12.dp))
        Text(parent.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (subs.isEmpty()) {
            Text(
                "点击编辑",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(3f), verticalAlignment = Alignment.CenterVertically) {
                subs.take(4).forEach { sub ->
                    Text(
                        sub.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
                if (subs.size > 4) Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(
    domain: CategoryType,
    parents: List<CategoryEntity>,
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    val parentName = parents.firstOrNull { it.id == parentId }?.name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (domain == CategoryType.EXPENSE) "新增支出分类" else "新增收入分类") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("分类名称") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "位置：${parentName?.let { "$it 之下" } ?: "新增一级分类"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { parentId = null }) { Text("一级") }
                    parents.forEach { parent ->
                        TextButton(onClick = { parentId = parent.id }) { Text(parent.name) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, parentId) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EditCategoryDialog(
    category: CategoryEntity,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(category.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑分类") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("分类名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除", color = dev.penned.app.ui.theme.expenseColor()) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
