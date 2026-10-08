package dev.penned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.iconMutedColor

/**
 * 分组卡里的一行：可选前导图标 + 标题 + 副标题 + 尾部内容 + 右箭头。
 * 前置图标是**圆角方块**（32dp/圆角 10dp）而不是圆——设置类列表用方块 tile 是这一代工具 App 的通行形态，
 * 也跟"分类头像用圆"区分开（圆 = 分类/账户身份，方块 = 功能入口）。
 */
@Composable
fun PennedListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: Color? = null,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    content: (@Composable () -> Unit)? = null,
) {
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = PennedDimens.rowMinHeight)
        .pennedClickable(onClick)
        .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md)
    Row(base, verticalAlignment = Alignment.CenterVertically) {
        if (leadingIcon != null) {
            Box(
                Modifier
                    .size(PennedDimens.iconTile)
                    .clip(RoundedCornerShape(10.dp))
                    .background(leadingTint?.copy(alpha = 0.14f) ?: MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = leadingTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(PennedDimens.iconInline),
                )
            }
            Spacer(Modifier.width(PennedSpacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = PennedTextStyles.rowTitle, color = MaterialTheme.colorScheme.onSurface)
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
                tint = iconMutedColor(),
                modifier = Modifier.size(PennedDimens.iconChevron),
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
            Text(title, style = PennedTextStyles.rowTitle)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
