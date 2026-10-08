package dev.penned.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.penned.app.domain.StatsStrip
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.primaryInkColor
import java.time.YearMonth

/** 切换条高度（与图标按钮热区同高，格子在条里垂直居中）。 */
private val StripHeight = 44.dp

/** 格子高度：32 会把热区做小，36 与胶囊 chips 一致。 */
private val CellHeight = 36.dp

/**
 * 月统计的时间切换条（AK 轮，替代原来的「‹ 2026年10月 ›」箭头行）：
 * 左边一列常驻年份，右边是可横滑的月份格（新→旧，最前是本月）。
 * 年份列跟着**条上最左可见的月份**变——滑到去年，左边那句就从 2026 变 2025；
 * 点它开快速定位弹窗（[YearMonthPickerDialog]）。
 */
@Composable
fun MonthStrip(
    months: List<YearMonth>,
    selected: YearMonth,
    today: YearMonth,
    onSelect: (YearMonth) -> Unit,
    onPickYearMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    AutoScrollToSelected(state, months.indexOf(selected))

    val leftYear by remember(months) {
        derivedStateOf { months.getOrNull(state.firstVisibleItemIndex)?.year ?: selected.year }
    }

    Row(
        modifier = modifier.fillMaxWidth().height(StripHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YearCell(
            year = leftYear,
            onClick = onPickYearMonth,
            modifier = Modifier.padding(start = PennedSpacing.sm),
        )
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(PennedSpacing.xs),
            contentPadding = PaddingValues(end = PennedSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(months, key = { _, month -> month.toString() }) { _, month ->
                StripCell(
                    label = StatsStrip.monthLabel(month, today),
                    selected = month == selected,
                    onClick = { onSelect(month) },
                )
            }
        }
    }
}

/** 年统计的切换条：一行年份格（新→旧，最前是今年），同款胶囊选中态。 */
@Composable
fun YearStrip(
    years: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    AutoScrollToSelected(state, years.indexOf(selected))

    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(PennedSpacing.xs),
        contentPadding = PaddingValues(horizontal = PennedSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().height(StripHeight),
    ) {
        items(years, key = { it }) { year ->
            StripCell(
                label = "$year",
                selected = year == selected,
                onClick = { onSelect(year) },
            )
        }
    }
}

/** 选中格：`primaryContainer` 胶囊 + 青绿墨色字（D024 的选中态口径）。 */
@Composable
private fun StripCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(CellHeight)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

/** 年份列：`2026 ⌄`——点击开快速定位，箭头表示"可点、可选"。 */
@Composable
private fun YearCell(year: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .height(StripHeight)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("$year", style = MaterialTheme.typography.titleMedium, color = primaryInkColor())
        Icon(
            PennedIcons.ChevronDown,
            contentDescription = "快速定位",
            tint = primaryInkColor(),
            modifier = Modifier.size(PennedDimens.iconChevron),
        )
    }
}

/**
 * 选中项必须在可见范围里：切视图 / 弹窗定位后滚过去（首帧还没布局时直接落位，不做动画）。
 * 已经在可见范围里就不动——用户刚点的格子别被推到边上。
 */
@Composable
private fun AutoScrollToSelected(state: LazyListState, index: Int) {
    LaunchedEffect(index) {
        if (index < 0) return@LaunchedEffect
        val visible = state.layoutInfo.visibleItemsInfo.map { it.index }
        when {
            index in visible -> Unit
            visible.isEmpty() -> state.scrollToItem(index)
            else -> state.animateScrollToItem(index)
        }
    }
}
