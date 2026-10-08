package dev.penned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.iconMutedColor

/**
 * 分区小标题 + 右侧动作（"+ 新增"这类）——预算/分类/数据管理三处私有克隆的唯一实现。
 * 左对齐 20dp、右动作 8dp（按钮自带内边距），上下与 `SectionLabel` 同档。
 */
@Composable
fun PennedSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = PennedSpacing.lg, end = PennedSpacing.sm, top = PennedSpacing.md, bottom = PennedSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = PennedTextStyles.sectionLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

private const val LARGE_TEXT_SCALE = 1.3f

/**
 * 三列汇总（首页月卡头部、资产汇总、统计汇总共用）：
 * 标签 `labelSmall` 弱化、数值 `titleSmall` 等宽——金额是主角但这一档是"概览"，
 * 别把它顶成 display（那是 hero 的活）。
 */
@Composable
fun PennedStatCells(
    cells: List<StatCell>,
    modifier: Modifier = Modifier,
) {
    // 字体放大到 1.3× 以上时三列挤不下（金额会被截成 "1,500.…"）——换成"标签左 + 数值右"的竖排，
    // 宁可多占几行也不能把金额读成半个（D044 字体缩放检查）
    val largeText = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE
    if (largeText) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PennedSpacing.xs)) {
            cells.forEach { cell -> StatCellRow(cell) }
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm)) {
            cells.forEach { cell ->
                StatCellColumn(cell, Modifier.weight(1f))
            }
        }
    }
}

/** 大字号下的汇总行：标签在左、数值在右（数值不再被截断）。 */
@Composable
private fun StatCellRow(cell: StatCell) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            cell.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            cell.value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = cell.color ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** 一格汇总：标签 + 数值（+ 可选副标签）。 */
data class StatCell(
    val label: String,
    val value: String,
    val color: Color? = null,
    val sub: String? = null,
)

@Composable
private fun StatCellColumn(cell: StatCell, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            cell.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            cell.value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = cell.color ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (cell.sub != null) {
            Text(
                cell.sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 唯一的进度条实现（首页预算块、预算管理页共用）：4dp 全圆、填充 `primary`、轨道 `surfaceContainerHigh`。
 *
 * ⚠️ **调用方不要给这块加圆角裁剪**（D042a）：进度条压在容器底边上时，圆角会把两端各吃掉约 23px，
 * 极小比例的填充（0.73% ≈ 3px）整段落在被裁区域里，画了也看不见。
 */
@Composable
fun PennedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    testTag: String? = null,
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(percent = 50))
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** 徽标：退款/报销/手续费这类小标记（8dp 圆角、20dp 高、labelMedium）。 */
@Composable
fun PennedBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(container)
            .padding(horizontal = PennedSpacing.sm, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor, maxLines = 1)
    }
}

/**
 * 空态：柔底图标圈 + 标题 + 说明 +（可选）动作。
 * **有出路就必须给动作**（D044 六态审查）——别只留一句"还没有记录"。
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(PennedSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(PennedDimens.emptyIcon)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconMutedColor(), modifier = Modifier.size(PennedDimens.icon))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) Box(Modifier.padding(top = PennedSpacing.xs)) { action() }
    }
}
