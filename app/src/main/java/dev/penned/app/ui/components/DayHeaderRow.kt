package dev.penned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.penned.app.domain.Money
import dev.penned.app.ui.theme.PennedSpacing

/**
 * 日期分组头（首页月卡 / 搜索结果共用）：灰底 + 左侧青绿竖条，
 * 左边「今天 10月6日 周二」、右边「收 X 支 Y」——某侧为 0 就整段不显示（参考图口径）。
 * 底色不透明：吸顶（stickyHeader）时靠它盖住下面滚上来的行。
 */
@Composable
fun DayHeaderRow(
    label: String,
    incomeCents: Long,
    expenseCents: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = PennedSpacing.lg, end = PennedSpacing.lg, top = PennedSpacing.sm, bottom = PennedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 16.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(PennedSpacing.sm))
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        DayTotals(incomeCents, expenseCents)
    }
}

/** 日合计：`收`/`支` 前缀用更小更弱的一档，数字等宽；某侧为 0 就不出现。 */
@Composable
private fun DayTotals(incomeCents: Long, expenseCents: Long, modifier: Modifier = Modifier) {
    val segments = buildList {
        if (incomeCents != 0L) add("收" to Money.format(incomeCents))
        if (expenseCents != 0L) add("支" to Money.format(expenseCents))
    }
    if (segments.isEmpty()) return
    val weak = MaterialTheme.colorScheme.onSurfaceVariant
    val prefixStyle = SpanStyle(
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
        fontWeight = MaterialTheme.typography.labelSmall.fontWeight,
        color = weak,
    )
    val amountStyle = SpanStyle(
        fontSize = MaterialTheme.typography.labelMedium.fontSize,
        fontWeight = MaterialTheme.typography.labelMedium.fontWeight,
        color = weak,
    )
    Text(
        text = buildAnnotatedString {
            segments.forEachIndexed { index, (prefix, amount) ->
                if (index > 0) append("   ")
                withStyle(prefixStyle) { append(prefix) }
                append(" ")
                withStyle(amountStyle) { append(amount) }
            }
        },
        style = MaterialTheme.typography.labelMedium,
        color = weak,
        maxLines = 1,
        modifier = modifier,
    )
}
