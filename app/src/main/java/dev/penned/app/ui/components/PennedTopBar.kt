package dev.penned.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens

/**
 * 下钻页统一顶栏：真返回箭头 + 标题 + 动作槽。
 * 取代各页自己拼的 `TopAppBar(title, navigationIcon = TextButton("‹ 返回"))`。
 * inset 由外层 Scaffold 消费后不再重复计算（D025）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PennedTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(PennedDimens.iconButton)) {
                    Icon(PennedIcons.Back, contentDescription = "返回")
                }
            }
        },
        actions = { Row(content = actions) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
