package dev.penned.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing

/**
 * 底部抽屉：从名单里选一个（账户、分类）这类操作走它，不再用居中弹窗（D024 交互范式一）。
 * 纯文本输入仍用 [PennedDialog]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PennedSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = PennedDimens.sheetCorner, topEnd = PennedDimens.sheetCorner),
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = PennedSpacing.lg)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(
                        start = PennedSpacing.xl,
                        end = PennedSpacing.xl,
                        bottom = PennedSpacing.sm,
                    ),
                )
            }
            content()
        }
    }
}
