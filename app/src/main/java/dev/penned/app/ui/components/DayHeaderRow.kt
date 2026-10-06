package dev.penned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.penned.app.domain.Money
import dev.penned.app.ui.theme.PennedSpacing

/**
 * 日期分组头：左边「10月6日 今天」、右边「收 X 支 Y」（首页与搜索页共用）。
 * [background] 给吸顶（stickyHeader）用：吸顶时底色盖住下面滚上来的行。
 */
@Composable
fun DayHeaderRow(
    label: String,
    incomeCents: Long,
    expenseCents: Long,
    modifier: Modifier = Modifier,
    background: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (background) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "收 ${Money.format(incomeCents)}  支 ${Money.format(expenseCents)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
