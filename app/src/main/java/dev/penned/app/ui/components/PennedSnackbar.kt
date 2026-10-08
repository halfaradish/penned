package dev.penned.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles

/**
 * 撤销/提示条（D031）：圆角 12dp、**反向色**（浅色主题＝深底浅字，深色主题＝浅底深字）、零阴影，
 * 动作键（撤销）用 `inversePrimary`——在反向底上对比度 9.4:1（浅）/ 4.5:1（深），
 * 见界面规范第三节。时长由调用方给（撤销一律 10s）。
 */
@Composable
fun PennedSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data -> PennedSnackbar(data) }
}

@Composable
private fun PennedSnackbar(data: SnackbarData) {
    Snackbar(
        modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        shape = MaterialTheme.shapes.small,
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        actionContentColor = MaterialTheme.colorScheme.inversePrimary,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                data.visuals.message,
                style = PennedTextStyles.rowTitle,
                modifier = Modifier.weight(1f),
            )
            val label = data.visuals.actionLabel
            if (label != null) {
                Spacer(Modifier.width(PennedSpacing.sm))
                TextButton(onClick = { data.performAction() }) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inversePrimary,
                    )
                }
            }
        }
    }
}
