package dev.penned.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.domain.Money
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.MoneyText
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.SectionLabel
import dev.penned.app.ui.home.HomeGroups
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.categoryColor
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.incomeColor
import java.time.YearMonth

/**
 * 统计页（specs/M1.md 第二批）：月汇总卡 + 支出/收入分类占比环形图 + 分类排行。
 * 图表配色与首页头像共用一套分类颜料（D024），不再各画各的。
 */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsContent(
        month = state.month,
        totals = state.totals,
        expenseCategories = state.expenseCategories,
        incomeCategories = state.incomeCategories,
        onPrevMonth = viewModel::prevMonth,
        onNextMonth = viewModel::nextMonth,
        modifier = modifier,
    )
}

@Composable
fun StatsContent(
    month: YearMonth,
    totals: HomeGroups.MonthTotals,
    expenseCategories: List<HomeGroups.CategoryTotal>,
    incomeCategories: List<HomeGroups.CategoryTotal>,
    onPrevMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = PennedSpacing.xxl),
    ) {
        // Tab 页页头：大标题 + 月份切换（图标按钮，替代 ‹ › 字形）
        Row(
            Modifier.fillMaxWidth().padding(
                start = PennedSpacing.lg,
                end = PennedSpacing.sm,
                top = PennedSpacing.md,
                bottom = PennedSpacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("统计", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onPrevMonth, modifier = Modifier.size(PennedDimens.iconButton)) {
                Icon(PennedIcons.Back, contentDescription = "上个月", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${month.year}年${month.monthValue}月",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = PennedSpacing.xs),
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.size(PennedDimens.iconButton)) {
                Icon(
                    PennedIcons.ChevronRight,
                    contentDescription = "下个月",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 月汇总卡
        PennedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryCell("支出", Money.format(totals.expenseCents), expenseColor(), Modifier.weight(1f))
                SummaryCell("收入", Money.format(totals.incomeCents), incomeColor(), Modifier.weight(1f))
                SummaryCell(
                    "结余",
                    Money.format(totals.incomeCents - totals.expenseCents),
                    MaterialTheme.colorScheme.onSurface,
                    Modifier.weight(1f),
                )
            }
        }

        if (expenseCategories.isEmpty() && incomeCategories.isEmpty()) {
            EmptyState(
                title = "本月没有收支记录",
                hint = "记几笔之后回来看统计",
                icon = PennedIcons.Pie,
                modifier = Modifier.padding(top = PennedSpacing.xxl),
            )
            return@Column
        }

        if (expenseCategories.isNotEmpty()) {
            DonutSection(
                title = "支出构成",
                categories = expenseCategories,
                totalCents = totals.expenseCents,
                totalColor = expenseColor(),
            )
        }
        if (incomeCategories.isNotEmpty()) {
            DonutSection(
                title = "收入构成",
                categories = incomeCategories,
                totalCents = totals.incomeCents,
                totalColor = incomeColor(),
            )
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}

@Composable
private fun DonutSection(
    title: String,
    categories: List<HomeGroups.CategoryTotal>,
    totalCents: Long,
    totalColor: Color,
) {
    SectionLabel(title)
    PennedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(PennedDimens.donut), contentAlignment = Alignment.Center) {
                DonutChart(categories = categories, modifier = Modifier.fillMaxSize())
                // 合计放环心，右侧腾给图例
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("合计", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(totalCents), style = MaterialTheme.typography.titleMedium, color = totalColor)
                }
            }
            Spacer(Modifier.width(PennedSpacing.lg))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
            ) {
                categories.take(4).forEach { cat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(categoryColor(cat.name), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            cat.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(cat.fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // 排行
    PennedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = PennedSpacing.xs),
    ) {
        categories.forEachIndexed { index, cat ->
            if (index > 0) PennedDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(18.dp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(PennedSpacing.sm))
                CategoryAvatar(cat.name, size = 28.dp)
                Spacer(Modifier.width(PennedSpacing.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            cat.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${(cat.fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(PennedSpacing.xs))
                    // 占比条：用该分类自己的颜料，和环图/头像对得上
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(cat.fraction.coerceIn(0.02f, 1f))
                                .height(4.dp)
                                .background(categoryColor(cat.name), RoundedCornerShape(percent = 50)),
                        )
                    }
                }
                Spacer(Modifier.width(PennedSpacing.md))
                MoneyText(
                    cents = cat.cents,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    signed = false,
                    modifier = Modifier.width(84.dp),
                )
            }
        }
    }
}

/** 环形图：每类一段，起于 12 点方向；分段间留 2° 缝。 */
@Composable
private fun DonutChart(categories: List<HomeGroups.CategoryTotal>, modifier: Modifier = Modifier) {
    // 颜色先在 composable 上下文里解析：Canvas 的 DrawScope lambda 不是 composable，读不了主题
    val colors = categories.map { categoryColor(it.name) }
    Canvas(modifier = modifier) {
        val stroke = PennedDimens.donutStroke.toPx()
        val inset = stroke / 2 + 1.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        var start = -90f
        // 端头用 Butt：圆头会在 2° 缝隙上互相盖住（圆头半径 ≈ 11°）
        val gap = 3f
        categories.forEachIndexed { index, cat ->
            val sweep = (cat.fraction.coerceIn(0f, 1f) * 360f).coerceAtLeast(0.5f)
            drawArc(
                color = colors[index],
                startAngle = start,
                sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            start += sweep
        }
    }
}
