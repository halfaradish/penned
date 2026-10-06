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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val KEY_AGAIN = "再记"
private const val KEY_DONE = "完成"
private const val KEY_EQUALS = "="

/** 键位：span = 占几格；operatorBlock = 右侧运算块（⌫ 与 +−×÷ 用灰字形，与数字区分）。 */
private data class Key(val label: String, val span: Float = 1f, val operatorBlock: Boolean = false)

/**
 * 5 列布局（对标一木）：数字占 3 列，右侧 2 格是运算块——
 * 第 1 行 ⌫ 占两格，第 2 行 `+ −`，第 3 行 `× ÷`，第 4 行动作键占两格。
 * 表达式由 [dev.penned.app.domain.MoneyExpression] 求值（乘除优先、除零保护）。
 */
private val KEYS: List<List<Key>> = listOf(
    listOf(Key("1"), Key("2"), Key("3"), Key(AddViewModel.KEY_BACKSPACE, span = 2f, operatorBlock = true)),
    listOf(Key("4"), Key("5"), Key("6"), Key("+", operatorBlock = true), Key("-", operatorBlock = true)),
    listOf(Key("7"), Key("8"), Key("9"), Key("×", operatorBlock = true), Key("÷", operatorBlock = true)),
    listOf(Key(KEY_AGAIN), Key("0"), Key("."), Key(KEY_DONE, span = 2f)),
)

/**
 * 金额键盘：加减乘除表达式 + 右下动作键三态——表达式待算显示 "="（求值），
 * 否则 "完成"（保存）；取消语义走顶栏返回。
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
                        key = key,
                        canSave = canSave,
                        showEquals = showEquals,
                        onKey = onKey,
                        onSaveAgain = onSaveAgain,
                        onEvaluate = onEvaluate,
                        onSave = onSave,
                        modifier = Modifier
                            .weight(key.span)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyCell(
    key: Key,
    canSave: Boolean,
    showEquals: Boolean,
    onKey: (String) -> Unit,
    onSaveAgain: () -> Unit,
    onEvaluate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = key.label
    val isDone = label == KEY_DONE
    val isAgain = label == KEY_AGAIN
    val displayLabel = if (isDone && showEquals) KEY_EQUALS else label
    val action = when {
        isDone && showEquals -> Action.Evaluate
        isDone -> Action.Save
        isAgain -> Action.SaveAgain
        else -> Action.Type
    }
    val primary = isDone && (showEquals || canSave)
    // 页面底色就是 surfaceVariant，键不能再用它（否则看不出是键）；运算键靠灰字形区分
    val background = when {
        primary -> MaterialTheme.colorScheme.primary
        isDone -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        primary -> MaterialTheme.colorScheme.onPrimary
        key.operatorBlock -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            // 键盘键位是语义单元：测试按 tag 定位（"+"/"-" 会与分类宫格「＋ 新增」文本撞车）
            .testTag("amount-key-$label")
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
