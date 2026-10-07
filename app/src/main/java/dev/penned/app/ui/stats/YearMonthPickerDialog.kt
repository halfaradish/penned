package dev.penned.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.penned.app.domain.StatsStrip
import dev.penned.app.ui.components.PennedDialog
import dev.penned.app.ui.theme.PennedSpacing
import java.time.YearMonth
import kotlin.math.abs
import kotlinx.coroutines.launch

/** 滚轮一格的固定高度（列高 = 5 格，中间那格就是选中项）。 */
private val WheelItemHeight = 40.dp

/** 滚轮可见格数（奇数，中间一格居中）。 */
private const val WHEEL_ROWS = 5

/**
 * 快速定位弹窗（AK 轮）：自绘「年 | 月」双列滚轮，两列都能滚、居中项放大加墨色，
 * 底部「取消 / 确定」——确定前不改统计期，取消就原样退回。
 *
 * [years] 与切换条同序（新→旧）；滚轮里翻成"自上而下递增"（照参考图，2023 在上、2027 在下），
 * 月份列同理 1…12 自上而下。
 */
@Composable
fun YearMonthPickerDialog(
    years: List<Int>,
    today: YearMonth,
    initial: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit,
) {
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.monthValue) }
    val months = StatsStrip.pickerMonths(year, today)
    // 滚回今年时月份列变短（只到本月）：把选中的月夹回可用范围
    LaunchedEffect(year) { month = StatsStrip.clampMonth(month, year, today) }

    PennedDialog(
        onDismiss = onDismiss,
        title = "选择年月",
        confirmLabel = "确定",
        onConfirm = { onConfirm(YearMonth.of(year, month)) },
    ) {
        YearMonthWheel(
            years = remember(years) { years.reversed() },
            months = months,
            selectedYear = year,
            selectedMonth = month,
            onYearChange = { year = it },
            onMonthChange = { month = it },
            modifier = Modifier.padding(top = PennedSpacing.sm),
        )
    }
}

/**
 * 双列滚轮本体：两列都按传入顺序自上而下排。从弹窗里抽出来是为了能单独截图与测试
 * （Robolectric 里弹窗窗口不便截图）。选中项 = 视口正中那一格，滚动中回调 `onYearChange` / `onMonthChange`。
 */
@Composable
fun YearMonthWheel(
    years: List<Int>,
    months: List<Int>,
    selectedYear: Int,
    selectedMonth: Int,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        WheelColumn(
            items = years.map { "$it" },
            selectedIndex = years.indexOf(selectedYear),
            onSelectIndex = { index -> years.getOrNull(index)?.let(onYearChange) },
            modifier = Modifier.weight(1f),
        )
        UnitLabel("年")
        WheelColumn(
            items = months.map { "$it" },
            selectedIndex = months.indexOf(selectedMonth),
            onSelectIndex = { index -> months.getOrNull(index)?.let(onMonthChange) },
            modifier = Modifier.weight(1f),
        )
        UnitLabel("月")
    }
}

@Composable
private fun UnitLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 单列滚轮：吸附居中（[SnapPosition.Center]），静态高亮带标出"中间这格"，
 * 上下相邻格的透明度递减做渐隐（不引渐变遮罩，靠 alpha 就够）。
 */
@Composable
private fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 初值直接把选中项摆在正中（探针实测：initialFirstVisibleItemIndex=k ⇒ 第 k 格居中），
    // 否则首帧会报"第 0 格居中"把外面的选中项顶掉
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val scope = rememberCoroutineScope()

    val centered by remember {
        derivedStateOf { centeredIndex(state) }
    }
    // 居中即选中：滚轮停稳后报给外面（首帧的居中项就是传进来的选中项，不会自己跳）
    LaunchedEffect(centered, items) {
        if (items.isNotEmpty()) onSelectIndex(centered)
    }
    // 外面改了选中（换年导致月份列变短、或弹窗刚打开）→ 滚到中间去
    LaunchedEffect(selectedIndex, items) {
        if (selectedIndex >= 0 && centered != selectedIndex) state.animateScrollToItem(selectedIndex)
    }

    Box(modifier = modifier.height(WheelItemHeight * WHEEL_ROWS)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(WheelItemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = WheelItemHeight * (WHEEL_ROWS / 2)),
        ) {
            itemsIndexed(items) { index, label ->
                WheelItem(
                    label = label,
                    distance = abs(index - centered),
                    onClick = { scope.launch { state.animateScrollToItem(index) } },
                )
            }
        }
    }
}

/** 一格：居中那格最大最墨，越远越淡（距离 ≥2 即最淡）。 */
@Composable
private fun WheelItem(label: String, distance: Int, onClick: () -> Unit) {
    val centered = distance == 0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WheelItemHeight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (centered) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (centered) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = when (distance) {
                    0 -> 1f
                    1 -> 0.45f
                    else -> 0.25f
                },
            ),
            maxLines = 1,
        )
    }
}

/**
 * 视口正中那一格的 index：用"各格位置 vs 视口中心"算，不依赖 firstVisibleItemScrollOffset 的口径
 * （内容 padding 参与偏移时那个值的语义容易踩坑）。滑到两格之间时以中点为界翻格。
 */
internal fun centeredIndex(state: LazyListState): Int {
    val info = state.layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return info.visibleItemsInfo
        .minByOrNull { item -> abs((item.offset + item.size / 2f) - center) }
        ?.index
        ?: 0
}
