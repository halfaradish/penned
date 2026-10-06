package dev.penned.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

/** 统一按钮：胶囊、48dp 高、主/次/危险三级。 */
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
    val shape = RoundedCornerShape(percent = 50)
    val base = modifier
        .heightIn(min = 48.dp)
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
    val content: @Composable RowScope.() -> Unit = {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    when (variant) {
        PennedButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = base,
            enabled = enabled,
            shape = shape,
            content = content,
        )

        PennedButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = base,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            content = content,
        )

        PennedButtonVariant.Danger -> TextButton(
            onClick = onClick,
            modifier = base,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(contentColor = expenseColor()),
            content = content,
        )
    }
}

/** 危险动作的文字色（删除类文案统一取它）。 */
@Composable
fun dangerColor(): Color = expenseColor()
