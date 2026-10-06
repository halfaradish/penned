package dev.penned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing

/**
 * 分组卡里的一行：可选前导图标 + 标题 + 副标题 + 尾部内容 + 右箭头。
 * 取代 `MineScreen`/`DataScreens` 里两份重复的 MenuRow/InfoRow。
 */
@Composable
fun PennedListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: androidx.compose.ui.graphics.Color? = null,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    content: (@Composable () -> Unit)? = null,
) {
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = PennedDimens.rowMinHeight)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md)
    Row(base, verticalAlignment = Alignment.CenterVertically) {
        if (leadingIcon != null) {
            Box(
                Modifier
                    .size(PennedDimens.avatar)
                    .background(leadingTint?.copy(alpha = 0.14f) ?: MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = leadingTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(PennedSpacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(PennedSpacing.md))
            Text(
                trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (content != null) {
            Spacer(Modifier.width(PennedSpacing.md))
            content()
        }
        if (showChevron) {
            Spacer(Modifier.width(PennedSpacing.sm))
            Icon(
                PennedIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 开关行（账单详情的两个开关、预算设置等）。 */
@Composable
fun PennedSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
