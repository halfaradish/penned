package dev.penned.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import dev.penned.app.domain.Money
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.incomeColor

/**
 * 金额文本：等宽数字（字阶已带 tnum）、按收/支着色、支出补 "-" 收入补 "+"。
 * 取代各屏自己拼的 `prefix + Money.format(...)`。
 */
@Composable
fun MoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    type: TxType? = null,
    signed: Boolean = type != null,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color? = null,
    textAlign: TextAlign = TextAlign.End,
) {
    val resolved = color ?: when (type) {
        TxType.EXPENSE -> expenseColor()
        TxType.INCOME -> incomeColor()
        else -> MaterialTheme.colorScheme.onSurface
    }
    val prefix = when {
        !signed -> ""
        type == TxType.EXPENSE -> "-"
        type == TxType.INCOME -> "+"
        else -> ""
    }
    Text(
        text = prefix + Money.format(cents),
        style = style,
        color = resolved,
        textAlign = textAlign,
        maxLines = 1,
        modifier = modifier,
    )
}
