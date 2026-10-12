package dev.penned.app.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedSpacing

/**
 * 标签抽屉（M3-1，D024 交互范式一：从名单里选走底部抽屉）：两级标签 + 多选 + 「新建标签」。
 *
 * 层级靠缩进表达（一级 chip 一行、它的二级 chip 缩进一行）；**不做折叠**——标签通常就几个，
 * 全展开比再点一层更省事。隐藏的标签由调用方在数据层滤掉（`AddViewModel.selectableTags`）。
 * 新建走 [dev.penned.app.ui.components.TagCreateDialog]（抽屉先收起，建完即选中）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerSheet(
    tags: List<TagEntity>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onCreateRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "选择标签") {
        val parents = tags.filter { it.parentId == null }.sortedBy { it.sortOrder }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PennedSpacing.xl),
        ) {
            if (parents.isEmpty()) {
                Text(
                    "还没有标签——下面「新建标签」建一个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = PennedSpacing.xs),
                )
            }
            parents.forEach { parent ->
                val children = tags.filter { it.parentId == parent.id }.sortedBy { it.sortOrder }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(PennedSpacing.xs),
                ) {
                    PennedChip(
                        label = parent.name,
                        selected = parent.id in selectedIds,
                        onClick = { onToggle(parent.id) },
                    )
                }
                if (children.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(start = PennedSpacing.lg, top = PennedSpacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(PennedSpacing.xs),
                    ) {
                        children.forEach { child ->
                            PennedChip(
                                label = child.name,
                                selected = child.id in selectedIds,
                                onClick = { onToggle(child.id) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(PennedSpacing.sm))
            }
            Spacer(Modifier.height(PennedSpacing.xs))
            PennedChip(
                label = "新建标签",
                leading = "＋",
                icon = PennedIcons.Tag,
                onClick = onCreateRequest,
            )
            Spacer(Modifier.height(PennedSpacing.lg))
            // 多选抽屉没法"选中即关"，给一个明确的收尾动作（不然只能点遮罩/返回键）
            PennedButton(
                text = "选好了",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
