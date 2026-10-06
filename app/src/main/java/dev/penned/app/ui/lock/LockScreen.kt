package dev.penned.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.penned.app.domain.PinHasher
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Icon
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens

/**
 * PIN 数字键盘（4 位）：锁屏与设置页共用。纯 UI + 可测状态机 [PinState]。
 */
@Composable
fun PinPad(
    filledCount: Int,
    title: String,
    subtitle: String? = null,
    errorText: String? = null,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(PinHasher.PIN_LENGTH) { i ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < filledCount) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            errorText ?: " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))

        val keys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '·', '0', '⌫')
        keys.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { key ->
                    if (key == '·') {
                        // 占位键不做成"看着能点其实没反应"的死 UI
                        Spacer(Modifier.size(76.dp, 60.dp))
                        return@forEach
                    }
                    PinKey(
                        label = if (key == '⌫') null else key.toString(),
                        backspace = key == '⌫',
                        onClick = { if (key == '⌫') onBackspace() else onDigit(key) },
                    )
                }
            }
        }
    }
}

/** 单个 PIN 键：与记账键盘同款键面（白底 + 发丝线 + 按压态 + 触感）。 */
@Composable
private fun PinKey(label: String?, backspace: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)
    val background by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        label = "pinKey",
    )
    Box(
        modifier = Modifier
            .size(76.dp, 60.dp)
            .clip(shape)
            .background(background)
            .border(BorderStroke(PennedDimens.hairline, MaterialTheme.colorScheme.outlineVariant), shape)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (backspace) {
            Icon(
                PennedIcons.Backspace,
                contentDescription = "退格",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Text(label.orEmpty(), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/**
 * PIN 输入缓冲状态机：满 [PinHasher.PIN_LENGTH] 位时回调 [onFull]。
 * 失败用 [fail]（清空并提示）。可独立于 Compose 测试。
 */
class PinState(private val onFull: (PinState, String) -> Unit) {
    var pin by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun onDigit(digit: Char) {
        if (!digit.isDigit()) return
        if (pin.length >= PinHasher.PIN_LENGTH) return
        error = null
        pin += digit
        if (pin.length == PinHasher.PIN_LENGTH) onFull(this, pin)
    }

    fun onBackspace() {
        error = null
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    fun fail(message: String) {
        error = message
        pin = ""
    }

    /** 清空缓冲（步骤切换时用，如"再输入一次"前）。 */
    fun reset() {
        pin = ""
        error = null
    }
}

@Composable
fun rememberPinState(onFull: (PinState, String) -> Unit): PinState =
    remember { PinState(onFull) }

/** 锁定页（启动/后台恢复）。校验通过后 [onUnlock]。 */
@Composable
fun LockScreen(
    storedHash: String,
    onUnlock: () -> Unit,
    title: String = "一笔一记已锁定",
) {
    val state = rememberPinState { s, pin ->
        if (PinHasher.verify(pin, storedHash)) onUnlock() else s.fail("密码不正确，请重试")
    }
    PinPad(
        filledCount = state.pin.length,
        title = title,
        subtitle = "输入 ${PinHasher.PIN_LENGTH} 位密码解锁",
        errorText = state.error,
        onDigit = state::onDigit,
        onBackspace = state::onBackspace,
    )
}
