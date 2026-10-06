package dev.penned.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.penned.app.ui.theme.expenseColor

/**
 * 统一弹窗：只保留纯文本输入 / 二次确认两类（从名单里选一个走 [PennedSheet]，见 D024）。
 */
@Composable
fun PennedDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    confirmLabel: String = "保存",
    confirmEnabled: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String? = "取消",
    danger: Boolean = false,
    properties: DialogProperties = DialogProperties(),
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val body = content
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        properties = properties,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = if (body != null) { { Column { body() } } } else null,
        confirmButton = {
            if (onConfirm != null) {
                TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                    Text(
                        confirmLabel,
                        color = if (danger) expenseColor() else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        dismissButton = if (dismissLabel != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else null,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
    )
}

/** 二次确认（删除类动作）。 */
@Composable
fun ConfirmDialog(
    onDismiss: () -> Unit,
    title: String,
    text: String,
    confirmLabel: String = "删除",
    onConfirm: () -> Unit,
) {
    PennedDialog(
        onDismiss = onDismiss,
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        danger = true,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
