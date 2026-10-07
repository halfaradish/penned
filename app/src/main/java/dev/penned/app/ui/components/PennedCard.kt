package dev.penned.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing

/**
 * 卡片：白底 + 1dp 发丝线 + 16dp 圆角，全局不用阴影（深色靠表面提亮表达层级）。
 * 见 docs/界面重构方案.md 第二节原则二。
 */
@Composable
fun PennedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(PennedSpacing.lg),
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shaped = modifier
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .border(BorderStroke(PennedDimens.hairline, MaterialTheme.colorScheme.outlineVariant), shape)
    Box(if (onClick != null) shaped.clickable(onClick = onClick) else shaped) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** 分段卡片：一张卡在 LazyColumn 里横跨多个 item 时，每段带自己的位置。 */
enum class PennedCardSectionPosition { Top, Middle, Bottom, Single }

/**
 * 分段卡片容器：一张卡 = 顶段 + 若干中段 + 底段（首页月卡就是这种拼法）。
 * 画法：外框铺 outline 色、内缩一个发丝线的 surface 面；接缝那一侧不内缩（也就不画横线），
 * 于是多段拼起来仍读作一张卡，圆角只出现在顶/底段。
 *
 * 注意：`modifier` 里的 padding 要放在调用方（圆角跟卡片走，间距留在卡片外）。
 */
@Composable
fun PennedCardSection(
    position: PennedCardSectionPosition,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val corner = PennedDimens.cardCorner
    val line = PennedDimens.hairline
    val shape = when (position) {
        PennedCardSectionPosition.Top -> RoundedCornerShape(topStart = corner, topEnd = corner)
        PennedCardSectionPosition.Bottom -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
        PennedCardSectionPosition.Single -> RoundedCornerShape(corner)
        PennedCardSectionPosition.Middle -> RectangleShape
    }
    val inset = when (position) {
        PennedCardSectionPosition.Top -> PaddingValues(start = line, top = line, end = line)
        PennedCardSectionPosition.Bottom -> PaddingValues(start = line, end = line, bottom = line)
        PennedCardSectionPosition.Single -> PaddingValues(line)
        PennedCardSectionPosition.Middle -> PaddingValues(start = line, end = line)
    }
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(inset)
                .background(MaterialTheme.colorScheme.surface),
            content = content,
        )
    }
}

/** 卡片内分隔线（默认与行内文字左对齐）。 */
@Composable
fun PennedDivider(modifier: Modifier = Modifier, startIndent: Dp = PennedSpacing.lg) {    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(PennedDimens.hairline)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 分组标题（卡片外的弱化小标题）。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = PennedSpacing.xl,
            end = PennedSpacing.xl,
            top = PennedSpacing.md,
            bottom = PennedSpacing.sm,
        ),
    )
}

/** 分组卡：可选标题 + 一张卡包住若干行（设置类页面的标准形态）。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) SectionLabel(title)
        PennedCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
            contentPadding = PaddingValues(0.dp),
            content = content,
        )
    }
}

/** 空态：图标 + 标题 + 说明 +（可选）动作。替换各页自己写的空态。 */
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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
