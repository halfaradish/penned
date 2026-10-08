package dev.penned.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import dev.penned.app.ui.theme.PennedMotion
import dev.penned.app.ui.theme.rememberAnimationsEnabled

/**
 * 按压缩放反馈（D044）：卡片、键面、头像格这类**大面积可点件**用它，
 * 替掉 Material 涟漪——扁平语言里"按下去的那块颜色略深"比扩散的水波更干净。
 *
 * 按钮/chip 保留 ripple（它们的形状小、涟漪更像"按下了一个控件"）。
 *
 * 实现要点：`graphicsLayer` 必须挂在最外层（调用方要在它之后再 clip/background），
 * 否则缩放只作用于内层、卡片自己的底色不跟着动。
 */
@Composable
fun Modifier.pennedClickable(
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    pressedScale: Float = 1f,
    pressedAlpha: Float = 0.05f,
): Modifier {
    if (onClick == null || !enabled) return this
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animate = rememberAnimationsEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed && animate) pressedScale else 1f,
        animationSpec = tween(PennedMotion.instant, easing = PennedMotion.ease),
        label = "pressedScale",
    )
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (pressed) pressedAlpha else 0f)
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .background(tint)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** 按压底色用的中性色（需要自己画按压态时取它）。 */
@Composable
fun pressedTint(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
