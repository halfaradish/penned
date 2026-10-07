package dev.penned.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.penned.app.domain.AddDate
import dev.penned.app.domain.Money
import dev.penned.app.domain.TrendAxis
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.theme.PennedSpacing
import java.time.LocalDate
import kotlin.math.ceil

/**
 * 自绘柱状图（D011：不引图表库）：统计页的「支出趋势/收入趋势」（四个时间视图共用）
 * 与分类详细页的「支出统计/收入统计」共用。柱高按最大值等比，0 值不画柱。
 */
@Composable
internal fun StatsBarChartCard(
    title: String,
    bars: List<Long>,
    xLabels: List<AxisLabel>,
    color: Color,
    modifier: Modifier = Modifier,
    emptyHint: String = "本期没有记录",
) {
    val maxCents = bars.maxOrNull() ?: 0L
    PennedCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (maxCents > 0) {
                Text(
                    "最多 ${Money.format(maxCents)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(PennedSpacing.md))
        if (maxCents <= 0) {
            Text(
                emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@PennedCard
        }
        BarChart(bars = bars, maxCents = maxCents, color = color)
        Spacer(Modifier.height(PennedSpacing.xs))
        AxisLabels(bars = bars, labels = xLabels)
    }
}

/** 柱体：底对齐、圆角柱；每根占一个等宽槽，柱心与下方标签的格心对齐。 */
@Composable
private fun BarChart(bars: List<Long>, maxCents: Long, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val radius = 3.dp
    Canvas(modifier.fillMaxWidth().height(ChartHeight)) {
        val slot = size.width / bars.size.coerceAtLeast(1)
        // 稀疏窗口（近七日/年）别把柱子画成方块，宽了封顶
        val barWidth = (slot * 0.62f).coerceIn(1f, 18.dp.toPx())
        val corner = minOf(radius.toPx(), barWidth / 2f)
        drawLine(
            color = track,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        bars.forEachIndexed { index, cents ->
            if (cents <= 0) return@forEachIndexed
            // 顶部圆角要留出高度，短柱也画得出圆角
            val usable = (size.height - corner * 2).coerceAtLeast(1f)
            val height = (cents.toFloat() / maxCents) * usable + corner
            val left = index * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(left, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(corner, corner),
            )
        }
    }
}

/** x 轴一格：`top` 有值就上下两行（`周四` / `9/24`），没有就一行（`9月`）。 */
internal data class AxisLabel(val bottom: String, val top: String? = null)

/**
 * x 轴标签：每根柱一个等宽格，只给稀疏选中的格写字，与柱心对齐。
 * 首尾两格的字比格子宽（31 天时一格才 ~11dp），居中对齐会被卡片边缘裁掉最后一位
 * ——所以首格靠左、末格靠右（图表库里"边缘标签贴边"的常规做法）。
 */
@Composable
private fun AxisLabels(bars: List<Long>, labels: List<AxisLabel>, modifier: Modifier = Modifier) {
    val shown = labelIndices(bars.size).toSet()
    val first = shown.minOrNull()
    val last = shown.maxOrNull()
    Row(modifier.fillMaxWidth()) {
        bars.indices.forEach { index ->
            val edge = when (index) {
                first -> Alignment.CenterStart
                last -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            Box(Modifier.weight(1f), contentAlignment = edge) {
                if (index in shown && index < labels.size) {
                    val label = labels[index]
                    // unbounded：让标签按真实字宽测量（默认会按格宽测量，字是"溢出画"的，
                    // 靠边对齐就会算错位置、末位被卡片裁掉）
                    Column(
                        Modifier.wrapContentWidth(unbounded = true),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val top = label.top
                        if (top != null) AxisLine(top, weak = true)
                        AxisLine(label.bottom, weak = false)
                    }
                }
            }
        }
    }
}

/**
 * 轴上一行字：不换行、长了就地溢出——等宽格比字窄时靠溢出保持与柱心对齐，不截断。
 * 两行时上面那行（周几）比下面那行（月/日）弱一档，扫读时先看到日期。
 */
@Composable
private fun AxisLine(text: String, weak: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (weak) 0.75f else 1f),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        textAlign = TextAlign.Center,
    )
}

private val ChartHeight = 96.dp

/**
 * 标签取哪些下标：≤12 根全标（月份、近七日），更多则每 ceil(n/6) 根标一个且必带最后一根。
 */
internal fun labelIndices(count: Int): List<Int> {
    if (count <= 12) return (0 until count).toList()
    val step = ceil(count / 6.0).toInt().coerceAtLeast(1)
    val heads = (0 until count step step).toList()
    return if (heads.last() == count - 1) heads else heads + (count - 1)
}

/** 按日柱的 x 轴文案：一个多月内标日号（`1`…`31`），跨季度就标月份（`10月`）。 */
internal fun dayAxisLabels(startEpochDay: Long, count: Int): List<String> = (0 until count).map { index ->
    val date = LocalDate.ofEpochDay(startEpochDay + index)
    if (count <= TWO_LINE_DAY_LIMIT) "${date.dayOfMonth}" else "${date.monthValue}月"
}

/** 年统计的 x 轴文案。 */
internal fun monthAxisLabels(): List<String> = (1..12).map { "${it}月" }

/**
 * 趋势卡的横轴（AL 轮）：按日窗口给两行——上行周几、下行 `月/日`（`周四` / `9/24`）；
 * 超过两个月（[TWO_LINE_DAY_LIMIT]）退回单行月份（与分类详细页同口径，日号会重复）；
 * 年统计的月轴单行 `1月…12月`。
 */
internal fun trendAxisLabels(axis: TrendAxis): List<AxisLabel> = when (axis) {
    is TrendAxis.Days -> if (axis.count <= TWO_LINE_DAY_LIMIT) {
        (0 until axis.count).map { index ->
            val date = LocalDate.ofEpochDay(axis.startEpochDay + index)
            AxisLabel(bottom = "${date.monthValue}/${date.dayOfMonth}", top = AddDate.weekdayLabel(date))
        }
    } else {
        dayAxisLabels(axis.startEpochDay, axis.count).asOneLineLabels()
    }
    TrendAxis.Months -> monthAxisLabels().asOneLineLabels()
}

/** 单行文案 → 轴标签（分类详细页与月轴用）。 */
internal fun List<String>.asOneLineLabels(): List<AxisLabel> = map { AxisLabel(bottom = it) }

/** 两行标签的日数上限：再多就标月份（与 [dayAxisLabels] 同一条线）。 */
private const val TWO_LINE_DAY_LIMIT = 62
