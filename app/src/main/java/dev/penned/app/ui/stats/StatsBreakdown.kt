package dev.penned.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.penned.app.domain.Money
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.MoneyText
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.categoryColor

/**
 * 统计的「占比 + 数据」两张卡（AI 轮抽出，统计页与分类详细页共用）。
 * 版式照参考图只借结构：标题在卡内、右侧留给调用方放切换/动作；
 * 数据行 = 颜料头像 + 名称 + 「N笔 P%」+ 自家颜料条 + 等宽金额，行可点下钻。
 */

/** 占比卡：标题行（[trailing] 放切换器）+ 环形图 + 图例（取前 4，全量在数据卡里）。 */
@Composable
fun StatsDonutCard(
    title: String,
    centerLabel: String,
    categories: List<StatsMath.CategoryTotal>,
    totalCents: Long,
    totalColor: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    PennedCard(
        // 上留 8：与汇总卡分得开；下面由数据卡自己的上间距负责
        modifier = modifier.fillMaxWidth().padding(start = PennedSpacing.lg, end = PennedSpacing.lg, top = PennedSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = PennedSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(PennedDimens.donut), contentAlignment = Alignment.Center) {
                DonutChart(categories = categories, modifier = Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        centerLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                            percentLabel(cat),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 数据卡：分类行清单。[drillable] 决定哪些行能点进详细页
 * （「其他」合并桶与「未分类」不是真实分类，不可点）。
 */
@Composable
fun StatsDataCard(
    title: String,
    categories: List<StatsMath.CategoryTotal>,
    type: TxType,
    modifier: Modifier = Modifier,
    drillable: (StatsMath.CategoryTotal) -> Boolean = { false },
    onOpenCategory: (String) -> Unit = {},
) {
    PennedCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        contentPadding = PaddingValues(vertical = PennedSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = PennedSpacing.lg,
                end = PennedSpacing.lg,
                top = PennedSpacing.sm,
                bottom = PennedSpacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        categories.forEachIndexed { index, cat ->
            val canDrill = drillable(cat)
            if (index > 0) PennedDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PennedDimens.rowMinHeight)
                    .then(if (canDrill) Modifier.clickable { onOpenCategory(cat.name) } else Modifier)
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryAvatar(cat.name, size = 28.dp)
                Spacer(Modifier.width(PennedSpacing.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            cat.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(PennedSpacing.sm))
                        Text(
                            metaLabel(cat),
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
                    type = type,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.width(84.dp),
                )
            }
        }
    }
}

/** 行里的元信息：`110笔 60%`（笔数 + 占比，参考图口径）。 */
internal fun metaLabel(cat: StatsMath.CategoryTotal): String = "${cat.count}笔  ${percentLabel(cat)}"

internal fun percentLabel(cat: StatsMath.CategoryTotal): String = "${(cat.fraction * 100).toInt()}%"

/** 环形图：每类一段，起于 12 点方向；分段间留 3° 缝（端头用 Butt，圆头会互相盖住）。 */
@Composable
internal fun DonutChart(categories: List<StatsMath.CategoryTotal>, modifier: Modifier = Modifier) {
    // 颜色先在 composable 上下文里解析：Canvas 的 DrawScope lambda 不是 composable，读不了主题
    val colors = categories.map { categoryColor(it.name) }
    Canvas(modifier = modifier) {
        val stroke = PennedDimens.donutStroke.toPx()
        val inset = stroke / 2 + 1.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        var start = -90f
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
