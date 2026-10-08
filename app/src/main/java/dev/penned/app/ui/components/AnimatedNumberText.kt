package dev.penned.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import dev.penned.app.domain.Money
import dev.penned.app.ui.theme.PennedMotion
import dev.penned.app.ui.theme.rememberAnimationsEnabled

/**
 * 会滚动的金额：数值**变化**时用 220ms 从旧值滑到新值；**首次显示不播**
 * （`animateFloatAsState` 的初值就是首个目标值，冷启动与截图基线因此稳定，与 D040 的环口径一致）。
 *
 * 只用于 hero 级金额（净资产、月合计、环心合计）：列表行不滚——扫读列表时数字跳动是噪声。
 */
@Composable
fun AnimatedNumberText(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onSurface,
    prefix: String = "",
    suffix: String = "",
    textAlign: TextAlign? = null,
) {
    val animate = rememberAnimationsEnabled()
    val rolling by animateFloatAsState(
        targetValue = cents.toFloat(),
        animationSpec = tween(
            durationMillis = if (animate) PennedMotion.base else 0,
            easing = PennedMotion.ease,
        ),
        label = "animatedNumber",
    )
    Text(
        text = prefix + Money.format(rolling.toLong()) + suffix,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        modifier = modifier,
    )
}
