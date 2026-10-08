package dev.penned.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedMotion
import dev.penned.app.ui.theme.iconMutedColor
import dev.penned.app.ui.theme.rememberAnimationsEnabled

/**
 * 分段选择（记一笔的支出/收入/转账、统计的时间维度）：轨道 `surfaceContainer`，
 * **选中是一块白色滑块**（浅色主题里读作"被选中的那一格浮起来"，比整段填主色更像现代工具），
 * 滑块随选中项**位移**（220ms），文字颜色跟着过渡。
 *
 * 热区：段高 36dp + 轨道内边距 3dp（WCAG 2.5.8 要求 ≥24dp，实际可点高度 42dp）。
 */
@Composable
fun <T> PennedSegmentedTabs(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
) {
    val shape = MaterialTheme.shapes.small
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(3.dp),
    ) {
        val count = options.size.coerceAtLeast(1)
        val density = LocalDensity.current
        val trackWidth = maxWidth
        val segmentWidth = trackWidth / count
        val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
        val animate = rememberAnimationsEnabled()
        val progress by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = tween(if (animate) PennedMotion.base else 0, easing = PennedMotion.ease),
            label = "segmentThumb",
        )
        val offset = with(density) { (segmentWidth * progress).toPx() }
        Box(
            Modifier
                .offset { IntOffset(offset.toInt(), 0) }
                .width(segmentWidth)
                .height(PennedDimens.segmentHeight)
                .clip(shape)
                // 滑块要比轨道"浮起来"：浅色用白（surfaceBright=白），深色用更亮的表面
                .background(MaterialTheme.colorScheme.surfaceBright),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            options.forEach { option ->
                val isSelected = option == selected
                val fg by animateColorAsState(
                    targetValue = when {
                        !enabled(option) -> iconMutedColor()
                        isSelected -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(PennedMotion.base),
                    label = "segmentLabel",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(PennedDimens.segmentHeight)
                        .clip(shape)
                        .clickable(enabled = enabled(option)) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = fg,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
