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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import dev.penned.app.ui.components.PennedBadge
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.components.PennedDialog
import dev.penned.app.ui.components.PennedSectionHeader
import dev.penned.app.ui.components.PennedTextAction
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles

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
                PennedSectionHeader(
                    title = title,
                    action = { PennedTextAction("+ 新增", onClick = { addDomain = domain }) },
                )
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
                        if (index > 0) PennedDivider(startIndent = PennedDimens.rowTextIndent)
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
private fun CategoryRow(parent: CategoryEntity, subs: List<CategoryEntity>, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(parent.name, size = PennedDimens.iconTile)
        Spacer(Modifier.width(PennedSpacing.md))
        Text(parent.name, style = PennedTextStyles.rowTitle, modifier = Modifier.weight(1f))
        if (subs.isEmpty()) {
            Text(
                "点击编辑",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(PennedSpacing.xs), modifier = Modifier.weight(3f), verticalAlignment = Alignment.CenterVertically) {
                subs.take(3).forEach { sub ->
                    // 只读的子分类标签走徽标（不可点、不抢行主标题的注意力）
                    PennedBadge(text = sub.name, modifier = Modifier.weight(1f, fill = false))
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
    PennedDialog(
        onDismiss = onDismiss,
        title = if (domain == CategoryType.EXPENSE) "新增支出分类" else "新增收入分类",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onConfirm(name, parentId) },
    ) {
        CategoryNameField(name = name, onNameChange = { name = it })
        Spacer(Modifier.height(PennedSpacing.md))
        Text(
            "位置：${parentName?.let { "$it 之下" } ?: "新增一级分类"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(PennedSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm)) {
            PennedChip(label = "一级", selected = parentId == null, onClick = { parentId = null })
            parents.forEach { parent ->
                PennedChip(
                    label = parent.name,
                    selected = parentId == parent.id,
                    onClick = { parentId = parent.id },
                )
            }
        }
    }
}

/** 分类名输入：填充式（与预算/账本弹窗同一套）。 */
@Composable
private fun CategoryNameField(name: String, onNameChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(PennedSpacing.md),
    ) {
        if (name.isEmpty()) {
            Text("分类名称", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EditCategoryDialog(
    category: CategoryEntity,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(category.name) }
    PennedDialog(
        onDismiss = onDismiss,
        title = "编辑分类",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onRename(name) },
    ) {
        CategoryNameField(name = name, onNameChange = { name = it })
        Spacer(Modifier.height(PennedSpacing.md))
        PennedButton(text = "删除分类", onClick = onDelete, variant = PennedButtonVariant.Danger)
    }
}
