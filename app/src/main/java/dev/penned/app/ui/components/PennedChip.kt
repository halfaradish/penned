package dev.penned.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing

/**
 * 胶囊按钮（信息条 chips、筛选项）：可点则带涟漪，不可点就是静态标签。
 * 取代 SelectableChip / StaticChip / M3 FilterChip 三套并存。
 */
@Composable
fun PennedChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    leading: String? = null,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val accent = tint ?: MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(percent = 50)
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainer
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (selected || !enabled) null else BorderStroke(PennedDimens.hairline, MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = PennedSpacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) {
            Text(leading, style = MaterialTheme.typography.labelMedium, color = accent)
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}
