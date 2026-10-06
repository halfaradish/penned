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
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.theme.categoryColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import dev.penned.app.ui.theme.expenseColor
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedSpacing

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
        topBar = { PennedTopBar(title = "分类管理", onBack = onBack) },
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
                    TextButton(onClick = { addDomain = domain }) { Text("+ 新增") }
                }
                PennedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    if (parents.isEmpty()) {
                        Text(
                            "还没有分类",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(PennedSpacing.lg),
                        )
                    }
                    parents.forEachIndexed { index, parent ->
                        if (index > 0) PennedDivider(startIndent = 60.dp)
                        val subs = list.filter { it.parentId == parent.id }.sortedBy { it.sortOrder }
                        CategoryRow(parent = parent, subs = subs, onClick = { editTarget = parent })
                    }
                }
                Spacer(Modifier.height(PennedSpacing.xs))
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
        Modifier
            .fillMaxWidth()
            .padding(start = PennedSpacing.xl, end = PennedSpacing.sm, top = PennedSpacing.md, bottom = PennedSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
private fun CategoryRow(parent: CategoryEntity, subs: List<CategoryEntity>, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(parent.name, size = 32.dp)
        Spacer(Modifier.width(PennedSpacing.md))
        Text(parent.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (subs.isEmpty()) {
            Text(
                "点击编辑",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(3f), verticalAlignment = Alignment.CenterVertically) {
                subs.take(3).forEach { sub ->
                    Text(
                        sub.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
                if (subs.size > 3) Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            PennedIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
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
