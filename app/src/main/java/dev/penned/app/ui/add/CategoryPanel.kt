package dev.penned.app.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class CategoryUi(val id: String, val name: String, val hasSubs: Boolean)

/**
 * 分类面板（对标一木记一笔）：
 * 5 列一级网格 + 行内展开子分类条带；中性圆底 + 首字，选中青绿实心反白（D011/D019）。
 */
@Composable
fun CategoryPanel(
    parents: List<CategoryUi>,
    selectedId: String?,
    expandedId: String?,
    expandedSubs: List<CategoryUi>,
    onSelectParent: (CategoryUi) -> Unit,
    onSelectSub: (CategoryUi) -> Unit,
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
            if (rowItems.any { it.id == expandedId } && expandedSubs.isNotEmpty()) {
                SubBand(
                    subs = expandedSubs,
                    selectedId = selectedId,
                    onSelectSub = onSelectSub,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}

/** 展开的子分类条带：灰色圆角面 + 子分类 + 尾部"新增"（M1 接分类管理） */
@Composable
private fun SubBand(
    subs: List<CategoryUi>,
    selectedId: String?,
    onSelectSub: (CategoryUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(vertical = 8.dp)) {
            subs.chunked(5).forEach { rowItems ->
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEach { sub ->
                        CategoryCell(
                            category = sub,
                            selected = sub.id == selectedId,
                            compact = true,
                            onClick = { onSelectSub(sub) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(5 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = "+ 新增",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
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
    val circleSize = if (compact) 40.dp else 48.dp
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
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category.name.take(1),
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (category.hasSubs) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp)
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
