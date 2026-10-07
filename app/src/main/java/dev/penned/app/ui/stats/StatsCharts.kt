package dev.penned.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.penned.app.domain.AddDate
import dev.penned.app.domain.Money
import dev.penned.app.domain.StatsPeriods
import dev.penned.app.domain.TrendAxis
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import java.time.LocalDate
import kotlin.math.ceil

/** 图表区的测试标记：按住看详情的用例要按坐标按下去。 */
internal const val CHART_TAG = "stats-chart"

private val ChartHeight = 96.dp
private val BarRadius = 3.dp

/** 刻度栏里标签与轴线之间的空隙。 */
private val TickGutterGap = 6.dp

/** 两条刻度之间至少留的行距（防"非零最小值"与 `0` 挤在一起）。 */
private val TickMinGap = 2.dp

/** 浮层的最大宽度（也用来判断该往柱子哪一侧放）。 */
private val TooltipWidth = 148.dp

/**
 * 自绘柱状图（D011：不引图表库）：统计页的「支出趋势/收入趋势」（四个时间视图共用）
 * 与分类详细页的「支出统计/收入统计」共用。
 * 纵轴只标三条（原点 / 最大值 / 非零最小值）；按住某根柱弹「日期 + 金额」浮层。
 */
@Composable
internal fun StatsBarChartCard(
    title: String,
    bars: List<Long>,
    xLabels: List<AxisLabel>,
    color: Color,
    modifier: Modifier = Modifier,
    emptyHint: String = "本期没有记录",
    seriesLabel: String? = null,
) {
    val scale = remember(bars) { chartScale(bars) }
    PennedCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(PennedSpacing.md))
        if (scale.isEmpty) {
            Text(
                emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@PennedCard
        }
        ChartBody(bars = bars, xLabels = xLabels, scale = scale, color = color, seriesLabel = seriesLabel)
    }
}

/**
 * 图表主体：左边一条刻度栏（纵轴）+ 柱区；按住柱子弹浮层、被按的整格铺淡色竖带。
 * 纵轴刻度用 Compose `Text` 绝对定位（字体跟随主题与系统缩放、测试里能直接断言文字），
 * `TextMeasurer` 只用来量刻度栏宽度与行高。
 */
@Composable
private fun ChartBody(
    bars: List<Long>,
    xLabels: List<AxisLabel>,
    scale: ChartScale,
    color: Color,
    seriesLabel: String?,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val axisStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val ticks = remember(scale) { chartTicks(scale) }
    // 刻度栏宽 = 最宽那条刻度 + 空隙；行高决定画布上下各留多少（顶/底那条刻度字不被裁）
    val gutterAndRow: Pair<Dp, Dp> = remember(ticks, axisStyle, density) {
        val sizes = ticks.map { measurer.measure(it.label, axisStyle).size }
        with(density) {
            (sizes.maxOf { it.width }.toDp() + TickGutterGap) to sizes.maxOf { it.height }.toDp()
        }
    }
    val gutter = gutterAndRow.first
    val labelHeight = gutterAndRow.second

    // 按下的柱子下标（按住显示、左右滑连着看、松手消失）
    var pressed by remember(bars) { mutableStateOf<Int?>(null) }

    BoxWithConstraints(Modifier.fillMaxWidth().height(ChartHeight)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { ChartHeight.toPx() }
        val gutterPx = with(density) { gutter.toPx() }
        val labelHeightPx = with(density) { labelHeight.toPx() }
        val minGapPx = with(density) { TickMinGap.toPx() }
        val gapPx = with(density) { PennedSpacing.sm.toPx() }
        val tooltipPx = with(density) { TooltipWidth.toPx() }
        val slotPx = (widthPx - gutterPx) / bars.size.coerceAtLeast(1)
        // 刻度中心 y（面板高 = 画布高 − 一个行高，上下各留半行，顶/底刻度字不被裁）
        val tickYs = tickCentersPx(ticks, heightPx - labelHeightPx, labelHeightPx, labelHeightPx + minGapPx)
        val track = MaterialTheme.colorScheme.outlineVariant

        Box(
            Modifier
                .fillMaxSize()
                .testTag(CHART_TAG)
                .pointerInput(bars.size, gutterPx, slotPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = indexAt(down.position.x, gutterPx, slotPx, bars.size)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // 手指抬起、或手势被外层滚动抢走 → 收起浮层
                            if (!change.pressed || change.isConsumed) break
                            pressed = indexAt(change.position.x, gutterPx, slotPx, bars.size)
                        }
                        pressed = null
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val panelTop = labelHeightPx / 2f
                val panelBottom = size.height - labelHeightPx / 2f
                val panelHeight = (panelBottom - panelTop).coerceAtLeast(1f)
                // 纵轴：竖线 + 基线
                drawLine(track, Offset(gutterPx, panelTop), Offset(gutterPx, panelBottom), 1.dp.toPx())
                drawLine(track, Offset(gutterPx, panelBottom), Offset(size.width, panelBottom), 1.dp.toPx())
                val pressedIndex = pressed
                if (pressedIndex != null) {
                    // 被按的那一格：整条淡色竖带（与分类头像淡底同一档透明度）
                    drawRect(
                        color = color.copy(alpha = 0.14f),
                        topLeft = Offset(gutterPx + pressedIndex * slotPx, panelTop),
                        size = Size(slotPx, panelHeight),
                    )
                }
                // 稀疏窗口（近七日/年）别把柱子画成方块，宽了封顶
                val barWidth = (slotPx * 0.62f).coerceIn(1f, 18.dp.toPx())
                val corner = minOf(BarRadius.toPx(), barWidth / 2f)
                bars.forEachIndexed { index, cents ->
                    if (cents <= 0) return@forEachIndexed
                    // 顶部圆角要留出高度，短柱也画得出圆角
                    val usable = (panelHeight - corner * 2).coerceAtLeast(1f)
                    val height = (cents.toFloat() / scale.max) * usable + corner
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(
                            gutterPx + index * slotPx + (slotPx - barWidth) / 2f,
                            panelBottom - height,
                        ),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(corner, corner),
                    )
                }
            }

            // 纵轴三条刻度：贴轴线右对齐，中心落在各自数值的高度上
            ticks.forEachIndexed { index, tick ->
                Text(
                    tick.label,
                    style = axisStyle,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .width(gutter)
                        .offset(y = with(density) { (tickYs[index] - labelHeightPx / 2f).toDp() }),
                )
            }

            val index = pressed
            if (index != null && index < xLabels.size) {
                val centerPx = gutterPx + slotPx * (index + 0.5f)
                // 右侧放不下就翻到左边（按浮层最大宽度估，免得先渲染一帧再跳）
                val placeLeft = centerPx + gapPx + tooltipPx > widthPx
                Tooltip(
                    label = xLabels[index],
                    cents = bars.getOrElse(index) { 0L },
                    seriesLabel = seriesLabel,
                    color = color,
                    x = with(density) { (if (placeLeft) centerPx - gapPx else centerPx + gapPx).toDp() } -
                        (if (placeLeft) TooltipWidth else 0.dp),
                    alignEnd = placeLeft,
                )
            }
        }
    }

    Spacer(Modifier.height(PennedSpacing.xs))
    AxisLabels(bars = bars, labels = xLabels, gutter = gutter)
}

/** 按住时的小浮层：日期 + 「支出 15.30」。 */
@Composable
private fun Tooltip(
    label: AxisLabel,
    cents: Long,
    seriesLabel: String?,
    color: Color,
    x: Dp,
    alignEnd: Boolean,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.offset(x = x, y = PennedSpacing.xs).width(TooltipWidth),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(PennedDimens.hairline, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(horizontal = PennedSpacing.md, vertical = PennedSpacing.sm),
        ) {
            val date = label.tooltip
            if (date != null) {
                Text(
                    date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text(
                text = if (seriesLabel != null) "$seriesLabel ${Money.format(cents)}" else Money.format(cents),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** x 轴一格：`top` 有值就上下两行（`周四` / `9/24`），没有就一行（`9月`）；[tooltip] 是按住浮层用的完整日期。 */
internal data class AxisLabel(val bottom: String, val top: String? = null, val tooltip: String? = null)

/**
 * x 轴标签：每根柱一个等宽格，只给稀疏选中的格写字，与柱心对齐；[gutter] 是左侧纵轴刻度栏，
 * 左边留出同宽占位，格心才继续与柱心对齐。
 * 首尾两格的字比格子宽（31 天时一格才 ~11dp），居中对齐会被卡片边缘裁掉最后一位
 * ——所以首格靠左、末格靠右（图表库里"边缘标签贴边"的常规做法）。
 */
@Composable
private fun AxisLabels(
    bars: List<Long>,
    labels: List<AxisLabel>,
    gutter: Dp,
    modifier: Modifier = Modifier,
) {
    val shown = labelIndices(bars.size).toSet()
    val first = shown.minOrNull()
    val last = shown.maxOrNull()
    Row(modifier.fillMaxWidth()) {
        if (gutter > 0.dp) Spacer(Modifier.width(gutter))
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

// —— 纵轴刻度（纯函数，表驱动测试）——

/** 柱高范围：最大值 + 非零最小值（没有正数柱子时 min 为 null）。 */
internal data class ChartScale(val max: Long, val minNonZero: Long?) {
    val isEmpty: Boolean get() = max <= 0
}

internal fun chartScale(bars: List<Long>): ChartScale =
    ChartScale(max = bars.maxOrNull() ?: 0L, minNonZero = bars.filter { it > 0 }.minOrNull())

/** 纵轴一条刻度：[fraction] 是它占面板高度的比例（0 = 原点，1 = 最大值）。 */
internal data class AxisTick(val cents: Long, val label: String, val fraction: Float)

/**
 * 纵轴只标三条：最大值（顶）、非零最小值（按比例）、原点 `0`（底）。
 * 最大值与非零最小值是同一个数时只标一条（比如全月只有一天有支出）。
 */
internal fun chartTicks(scale: ChartScale): List<AxisTick> {
    if (scale.isEmpty) return emptyList()
    val max = AxisTick(scale.max, Money.format(scale.max), 1f)
    val origin = AxisTick(0L, "0", 0f)
    val min = scale.minNonZero
        ?.takeIf { it < scale.max }
        ?.let { AxisTick(it, Money.format(it), it.toFloat() / scale.max) }
    return listOfNotNull(max, min, origin)
}

/**
 * 三条刻度在画布里的中心 y（像素）：数值越高越靠上，面板下沿是 0、上沿是最大值。
 * 非零最小值远小于最大值时会与 `0` 那条挤在一起（如最大 1000、最小 1）——那就把它上提到
 * `0` 之上够一个行高 + [minGapPx]（数值照显示，位置做最小让步）。
 */
internal fun tickCentersPx(
    ticks: List<AxisTick>,
    panelHeightPx: Float,
    labelHeightPx: Float,
    minGapPx: Float = labelHeightPx + 2f,
): List<Float> {
    // 刻度表是 最大 → 最小 → 原点；标签中心 = 面板顶 + (1 − 比例) × 面板高
    val centers = ticks.map { panelHeightPx * (1f - it.fraction) }.toMutableList()
    for (i in centers.indices.reversed()) {
        if (i == centers.lastIndex) continue
        val below = centers[i + 1]
        if (below - centers[i] < minGapPx) centers[i] = (below - minGapPx).coerceAtLeast(0f)
    }
    return centers
}

/** 按 x 坐标算按到了哪根柱：落在刻度栏里也算第 0 根，右侧溢出算最后一根。 */
internal fun indexAt(x: Float, gutterPx: Float, slotPx: Float, count: Int): Int {
    if (count <= 0) return 0
    val raw = ((x - gutterPx) / slotPx.coerceAtLeast(1f)).toInt()
    return raw.coerceIn(0, count - 1)
}

/**
 * 标签取哪些下标：≤12 根全标（月份、近七日），更多则每 ceil(n/6) 根标一个且必带最后一根。
 */
internal fun labelIndices(count: Int): List<Int> {
    if (count <= 12) return (0 until count).toList()
    val step = ceil(count / 6.0).toInt().coerceAtLeast(1)
    val heads = (0 until count step step).toList()
    return if (heads.last() == count - 1) heads else heads + (count - 1)
}

/**
 * 按日柱的 x 轴文案：一个多月内标日号（`1`…`31`），跨季度就标月份（`10月`）；
 * [today] 只影响按住浮层里的日期（非本年带年份）。
 */
internal fun dayAxisLabels(
    startEpochDay: Long,
    count: Int,
    today: LocalDate = LocalDate.now(),
): List<AxisLabel> = (0 until count).map { index ->
    val date = LocalDate.ofEpochDay(startEpochDay + index)
    AxisLabel(
        bottom = if (count <= TWO_LINE_DAY_LIMIT) "${date.dayOfMonth}" else "${date.monthValue}月",
        tooltip = StatsPeriods.dayLabel(date.toEpochDay(), today),
    )
}

/**
 * 趋势卡的横轴（AL/AM 轮）：按日窗口给两行——上行周几、下行 `月/日`（`周四` / `9/24`）；
 * 超过两个月（[TWO_LINE_DAY_LIMIT]）退回单行月份（与分类详细页同口径，日号会重复）；
 * 年统计的月轴单行 `1月…12月`。每格都带按住浮层用的日期（[AxisLabel.tooltip]）。
 */
internal fun trendAxisLabels(axis: TrendAxis, today: LocalDate = LocalDate.now()): List<AxisLabel> = when (axis) {
    is TrendAxis.Days -> if (axis.count <= TWO_LINE_DAY_LIMIT) {
        (0 until axis.count).map { index ->
            val date = LocalDate.ofEpochDay(axis.startEpochDay + index)
            AxisLabel(
                bottom = "${date.monthValue}/${date.dayOfMonth}",
                top = AddDate.weekdayLabel(date),
                tooltip = StatsPeriods.dayLabel(date.toEpochDay(), today),
            )
        }
    } else {
        dayAxisLabels(axis.startEpochDay, axis.count, today)
    }
    is TrendAxis.Months -> (1..12).map { month ->
        AxisLabel(bottom = "${month}月", tooltip = "${axis.year}年${month}月")
    }
}

/** 两行标签的日数上限：再多就标月份（与 [dayAxisLabels] 同一条线）。 */
private const val TWO_LINE_DAY_LIMIT = 62
