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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedPigment
import dev.penned.app.ui.theme.pigmentInkAt
import dev.penned.app.ui.theme.pigmentSlotFor
import dev.penned.app.ui.theme.primaryInkColor

data class CategoryUi(val id: String, val name: String, val hasSubs: Boolean)

/**
 * 分类面板：5 列一级宫格，点一级在其下方就地展开子分类 chip 带（动效见原则五）。
 * 气泡走统一的淡灰绿底（AS 轮：分类色只在图表与小圆点上）；选中 = 品牌青绿实心圆 + 反白；
 * 有子分类的圆内右上带小圆点提示可展开（小圆点用该分类的全强度色）。
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
    /**
     * 尾部「新增」格。默认画（记一笔的宫格）；**分类关键词规则的目标选择器不画**——
     * 那里只选已有分类，不该出现一个点了没反应（或越权建分类）的格子。
     */
    showAddCell: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // 宫格是"一屏里同时出现的一组分类"，跟统计图表同口径走一次槽位分配（D040）：
    // 只按名字哈希的话，「食品餐饮 / 出行交通 / 休闲娱乐」会落在同一槽，一屏里三个同色气泡
    val names = parents.map { it.name }
    val slots = remember(names) { PennedPigment.assignSlots(names) }
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        parents.chunked(5).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { parent ->
                    CategoryCell(
                        category = parent,
                        selected = parent.id == selectedId,
                        compact = false,
                        slot = pigmentSlotFor(slots, parent.name),
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
        if (!showAddCell) return@Column
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onAddParent)
                    .padding(vertical = PennedSpacing.xs, horizontal = 2.dp),
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
                        modifier = Modifier.size(PennedDimens.iconInline),
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
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(vertical = PennedSpacing.sm),
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

@Composable
private fun CategoryCell(
    category: CategoryUi,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    slot: Int? = null,
) {
    val circleSize = if (compact) 40.dp else PennedDimens.categoryBubble
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = PennedSpacing.xs, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 气泡走统一的淡灰绿底（AS 轮：图标底不再分槽）
            CategoryAvatar(name = category.name, size = circleSize, selected = selected)
            // 有子分类：圆内右上小圆点（放到圆外会显得和单元格脱开），用该分类的全强度色
            if (category.hasSubs) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 3.dp)
                        .size(PennedDimens.categoryDot)
                        .clip(CircleShape)
                        .background(pigmentInkAt(slot ?: PennedPigment.slotOf(category.name))),
                )
            }
        }
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) primaryInkColor() else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
