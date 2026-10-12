package dev.penned.app.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.PennedBadge
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDialog
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedNameField
import dev.penned.app.ui.components.PennedSectionHeader
import dev.penned.app.ui.components.PennedSwitchRow
import dev.penned.app.ui.components.PennedTextAction
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.TagCreateDialog
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles

/**
 * 标签管理页（M3-1）：两级标签列表 + 新增 / 重命名 / 隐藏 / 删除。
 *
 * 隐藏与软删的区别写在页脚：隐藏只是"不再出现在记账选择器与筛选"，已经记过的流水照常显示；
 * 删除会连带它的二级标签与流水关联（编辑弹窗里先告诉用户"已用于 N 笔流水"）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    viewModel: TagsViewModel,
    onBack: () -> Unit,
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var addOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<TagEntity?>(null) }

    Scaffold(topBar = { PennedTopBar(title = "标签管理", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            val list = tags.orEmpty()
            val parents = list.filter { it.parentId == null }.sortedBy { it.sortOrder }
            PennedSectionHeader(
                title = "标签",
                action = { PennedTextAction("+ 新增", onClick = { addOpen = true }) },
            )
            PennedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                contentPadding = PaddingValues(0.dp),
            ) {
                if (parents.isEmpty()) {
                    Text(
                        "还没有标签，点右上「+ 新增」建一个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(PennedSpacing.lg),
                    )
                }
                parents.forEachIndexed { index, parent ->
                    if (index > 0) PennedDivider(startIndent = PennedDimens.rowTextIndent)
                    TagRow(tag = parent, child = false, onClick = { editTarget = parent })
                    list.filter { it.parentId == parent.id }.sortedBy { it.sortOrder }.forEach { child ->
                        TagRow(tag = child, child = true, onClick = { editTarget = child })
                    }
                }
            }
            Text(
                "隐藏的标签不出现在记账选择器与筛选里，已经记过的流水照常显示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
            )
            Spacer(Modifier.height(PennedSpacing.xl))
        }
    }

    if (addOpen) {
        TagCreateDialog(
            parents = tags.orEmpty().filter { it.parentId == null }.sortedBy { it.sortOrder },
            onConfirm = { name, parentId ->
                viewModel.addTag(name, parentId)
                addOpen = false
            },
            onDismiss = { addOpen = false },
        )
    }
    editTarget?.let { tag ->
        TagEditDialog(
            tag = tag,
            childCount = tags.orEmpty().count { it.parentId == tag.id },
            loadUsage = { id -> viewModel.usageCount(id) },
            onSave = { name, hidden ->
                viewModel.saveTag(tag.id, name, hidden)
                editTarget = null
            },
            onDelete = {
                viewModel.deleteTag(tag.id)
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }
}

@Composable
private fun TagRow(tag: TagEntity, child: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = if (child) PennedDimens.rowTextIndent else PennedSpacing.lg,
                end = PennedSpacing.lg,
                top = PennedSpacing.sm,
                bottom = PennedSpacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!child) {
            CategoryAvatar(tag.name, size = PennedDimens.iconTile)
            Spacer(Modifier.width(PennedSpacing.md))
        }
        Text(
            tag.name,
            style = if (child) MaterialTheme.typography.bodyLarge else PennedTextStyles.rowTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (tag.hidden) {
            PennedBadge(text = "已隐藏")
            Spacer(Modifier.width(PennedSpacing.sm))
        }
        Icon(
            PennedIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(PennedDimens.iconSmall),
        )
    }
}

/** 编辑标签：名称 + 隐藏（一次保存）+ 用量提示 + 删除。 */
@Composable
private fun TagEditDialog(
    tag: TagEntity,
    childCount: Int,
    loadUsage: suspend (String) -> Int,
    onSave: (name: String, hidden: Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(tag.name) }
    var hidden by remember { mutableStateOf(tag.hidden) }
    var usage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(tag.id) { usage = loadUsage(tag.id) }
    PennedDialog(
        onDismiss = onDismiss,
        title = "编辑标签",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onSave(name, hidden) },
    ) {
        PennedNameField(name = name, placeholder = "标签名称", onNameChange = { name = it })
        Spacer(Modifier.height(PennedSpacing.sm))
        PennedSwitchRow(
            title = "隐藏",
            subtitle = "不出现在记账选择器与筛选里",
            checked = hidden,
            onCheckedChange = { hidden = it },
        )
        Spacer(Modifier.height(PennedSpacing.sm))
        val used = usage
        Text(
            text = when {
                used == null -> ""
                used > 0 -> "已用于 $used 笔流水"
                else -> "还没有流水用过这个标签"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (childCount > 0) {
            Text(
                "删除会一并删掉它的 $childCount 个二级标签",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(PennedSpacing.md))
        PennedButton(text = "删除标签", onClick = onDelete, variant = PennedButtonVariant.Danger)
    }
}
