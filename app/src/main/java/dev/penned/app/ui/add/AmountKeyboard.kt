package dev.penned.app.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val KEY_AGAIN = "再记"
private const val KEY_DONE = "完成"
private const val KEY_EQUALS = "="

private val KEYS: List<List<String>> = listOf(
    listOf("1", "2", "3", AddViewModel.KEY_BACKSPACE),
    listOf("4", "5", "6", "+"),
    listOf("7", "8", "9", "-"),
    listOf(KEY_AGAIN, "0", ".", KEY_DONE),
)
// 求值引擎已支持 × ÷（MoneyExpression），键盘键位后续并入（一木把 +−×÷ 放右侧双宽格）

/**
 * 金额键盘（对标一木 4×4）：+ − 支持表达式（×÷ 由 + − 键长按扩展，M1 简化为加减退步），
 * 右下键三态：取消语义走顶栏返回；表达式待算显示 "="；否则 "完成"。
 */
@Composable
fun AmountKeyboard(
    canSave: Boolean,
    showEquals: Boolean,
    onKey: (String) -> Unit,
    onEvaluate: () -> Unit,
    onSaveAgain: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KEYS.forEach { rowKeys ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowKeys.forEach { key ->
                    KeyCell(
                        label = key,
                        canSave = canSave,
                        showEquals = showEquals,
                        onKey = onKey,
                        onSaveAgain = onSaveAgain,
                        onEvaluate = onEvaluate,
                        onSave = onSave,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyCell(
    label: String,
    canSave: Boolean,
    showEquals: Boolean,
    onKey: (String) -> Unit,
    onSaveAgain: () -> Unit,
    onEvaluate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDone = label == KEY_DONE
    val isAgain = label == KEY_AGAIN
    val displayLabel = when {
        isDone && showEquals -> KEY_EQUALS
        isDone -> KEY_DONE
        else -> label
    }
    val action = when {
        isDone && showEquals -> Action.Evaluate
        isDone -> Action.Save
        isAgain -> Action.SaveAgain
        else -> Action.Type
    }
    val background = when {
        isDone && !showEquals && canSave -> MaterialTheme.colorScheme.primary
        isDone && !showEquals -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isDone && !showEquals && canSave -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(enabled = action != Action.Save || canSave) {
                when (action) {
                    Action.Evaluate -> onEvaluate()
                    Action.Save -> onSave()
                    Action.SaveAgain -> onSaveAgain()
                    Action.Type -> onKey(displayLabel)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayLabel,
            color = contentColor,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
    }
}

private enum class Action { Type, Evaluate, Save, SaveAgain }
