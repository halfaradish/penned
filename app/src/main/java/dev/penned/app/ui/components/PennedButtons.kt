package dev.penned.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.expenseColor

enum class PennedButtonVariant { Primary, Secondary, Danger }

/**
 * 统一按钮：圆角 12dp（不再是胶囊——扁平语言里方一点的按钮更像"工具"）、48dp 高、主/次/危险三级。
 * - Primary：`primary` 填充 + 白字（5.47:1）
 * - Secondary：`surfaceContainer` 填充（**不再用 outlined**：白卡上再描一圈边是"盒子套盒子"的老毛病）
 * - Danger：`errorContainer` 填充 + `onErrorContainer` 字（10.87:1），软红不刺眼
 */
@Composable
fun PennedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: PennedButtonVariant = PennedButtonVariant.Primary,
    fillWidth: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val shape = MaterialTheme.shapes.small
    val base = modifier
        .heightIn(min = PennedDimens.touchTarget)
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
    val content: @Composable RowScope.() -> Unit = {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(PennedDimens.iconSmall))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    when (variant) {
        PennedButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = base,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            content = content,
        )

        PennedButtonVariant.Secondary -> Button(
            onClick = onClick,
            modifier = base,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            content = content,
        )

        PennedButtonVariant.Danger -> Button(
            onClick = onClick,
            modifier = base,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            content = content,
        )
    }
}

/** 危险动作的文字色（弹窗确认键、行内删除文案统一取它）。 */
@Composable
fun dangerColor(): Color = expenseColor()

/** 顶栏/卡片角落的小动作按钮（"+ 新增""重命名"这类）：文字键，`primaryInk` 上色。 */
@Composable
fun PennedTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
