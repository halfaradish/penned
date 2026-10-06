package dev.penned.app.ui.stub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedSpacing

/** 未实现页的占位：Tab 页形态（可滚走的页头 + 居中的说明），不再直接说"将在 Mn 实现"。 */
@Composable
fun StubScreen(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(
                start = PennedSpacing.lg,
                end = PennedSpacing.lg,
                top = PennedSpacing.md,
                bottom = PennedSpacing.sm,
            ),
        )
        EmptyState(
            title = "这个页面还没做",
            hint = hint,
            icon = PennedIcons.Calendar,
            modifier = Modifier.padding(top = PennedSpacing.xxl),
        )
    }
}
