package dev.penned.app.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing

data class CategoryUi(val id: String, val name: String, val hasSubs: Boolean)

/**
 * 分类面板：5 列一级宫格，点一级在其下方就地展开子分类 chip 带（动效见原则五）。
 * 选中 = 主色实心圆反白；有子分类的圆内右上带小圆点提示可展开。
 */
@Composable
fun CategoryPanel(
    parents: List<CategoryUi>,
    selectedId: String?,
    expandedId: String?,
    expandedSubs: List<CategoryUi>,
    onSelectParent: (CategoryUi) -> Unit,
    onSelectSub: (CategoryUi) -> Unit,
    onAddParent: () -> Unit = {},
    onAddSub: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        parents.chunked(5).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { parent ->
                    CategoryCell(
                        category = parent,
                        selected = parent.id == selectedId,
                        compact = false,
                        onClick = { onSelectParent(parent) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(5 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            val expandedHere = rowItems.any { it.id == expandedId }
            AnimatedVisibility(
                visible = expandedHere && expandedSubs.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SubBand(
                    subs = expandedSubs,
                    selectedId = selectedId,
                    expandedParentId = expandedId,
                    onSelectSub = onSelectSub,
                    onAddSub = onAddSub,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // 宫格尾部「新增」格（M1 第二批：宫格内新增分类入口）
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAddParent)
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(PennedDimens.categoryBubble)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        PennedIcons.Plus,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    "新增",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            repeat(4) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

/** 展开的子分类带：次级底色 + chip 流（含尾部"新增"）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubBand(
    subs: List<CategoryUi>,
    selectedId: String?,
    expandedParentId: String?,
    onSelectSub: (CategoryUi) -> Unit,
    onAddSub: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.padding(vertical = PennedSpacing.sm),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(PennedSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
        ) {
            subs.forEach { sub ->
                PennedChip(
                    label = sub.name,
                    selected = sub.id == selectedId,
                    onClick = { onSelectSub(sub) },
                )
            }
            PennedChip(
                label = "新增子分类",
                icon = PennedIcons.Plus,
                onClick = { expandedParentId?.let(onAddSub) },
            )
        }
    }
}

@Composable
private fun CategoryCell(
    category: CategoryUi,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val circleSize = if (compact) 40.dp else PennedDimens.categoryBubble
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category.name.take(1),
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
            // 有子分类：圆内右上小圆点（放到圆外会显得和单元格脱开）
            if (category.hasSubs) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 3.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
