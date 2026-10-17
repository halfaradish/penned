package dev.penned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.primaryInkColor

/**
 * 撤销/提示条（优化轮 3 重设样式，D098）：**当前风格的浅色条**——`surfaceContainerHigh` 底、
 * 12dp 圆角、零阴影、墨色正文 + `primaryInk` 动作键；取代 D031 的深色反向层
 * （用户反馈"深色设计不符合现在的风格"）。
 *
 * 位置由调用方给：**首页在顶部**（底部是悬浮记账入口，提示条不许再挡它），
 * 其余页仍在底部（Scaffold 的默认槽）。时长由调用方给（撤销 / 存为模板一律 10s）。
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
    // 不用 M3 的 Snackbar：它写死 6dp 阴影且不给参数开关，与"全局零阴影"（D043）打架
    Row(
        modifier = Modifier
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .heightIn(min = PennedDimens.touchTarget)
            .padding(start = PennedSpacing.lg, end = PennedSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            data.visuals.message,
            style = PennedTextStyles.rowTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        val label = data.visuals.actionLabel
        if (label != null) {
            Spacer(Modifier.width(PennedSpacing.xs))
            TextButton(onClick = { data.performAction() }) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = primaryInkColor(),
                )
            }
        }
    }
}
