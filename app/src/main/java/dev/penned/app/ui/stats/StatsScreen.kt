package dev.penned.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
import dev.penned.app.ui.home.HomeGroups
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.theme.categoryColor
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.incomeColor
import java.time.YearMonth

/**
 * 统计页第一版（specs/M1.md 第二批）：月汇总卡 + 支出/收入分类占比环形图 + 分类排行。
 * 桑基图后置（M6 打磨阶段评估）。
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
            .verticalScroll(rememberScrollState()),
    ) {
        // 月份切换
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("统计", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("‹", style = MaterialTheme.typography.titleLarge, modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onPrevMonth)
                .padding(horizontal = 10.dp))
            Text(
                "${month.year}年${month.monthValue}月",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text("›", style = MaterialTheme.typography.titleLarge, modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onNextMonth)
                .padding(horizontal = 10.dp))
        }

        // 月汇总卡
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SummaryCell("支出", Money.format(totals.expenseCents), expenseColor())
                SummaryCell("收入", Money.format(totals.incomeCents), incomeColor())
                SummaryCell("结余", Money.format(totals.incomeCents - totals.expenseCents), MaterialTheme.colorScheme.onSurface)
            }
        }

        if (expenseCategories.isEmpty() && incomeCategories.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("本月没有收支记录", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("记几笔之后回来看统计", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        // 支出环形图 + 排行
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
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryCell(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
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
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DonutChart(
            categories = categories,
            modifier = Modifier.size(150.dp),
        )
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("合计", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.format(totalCents), style = MaterialTheme.typography.titleLarge, color = totalColor)
            categories.take(3).forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(categoryColor(cat.name), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${cat.name} ${(cat.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    // 排行列表
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        categories.forEachIndexed { index, cat ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp),
                    textAlign = TextAlign.Center,
                )
                CategoryAvatar(cat.name, size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${(cat.fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    // 占比条
                    Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)) {
                        Box(
                            Modifier
                                .fillMaxWidth(cat.fraction.coerceIn(0.02f, 1f))
                                .height(4.dp)
                                .background(categoryColor(cat.name), MaterialTheme.shapes.small),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    Money.format(cat.cents),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.width(84.dp),
                    textAlign = TextAlign.End,
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
        val stroke = 26.dp.toPx()
        val inset = stroke / 2 + 1.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        var start = -90f
        val gap = 2f
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
