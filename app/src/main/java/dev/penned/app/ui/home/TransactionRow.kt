package dev.penned.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.Money
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.incomeColor

/** 流水行（首页 / 搜索结果共用）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TransactionRow(
    row: TxWithCategory,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // 转账行没有分类，显示"转账 · 转入 X"（D007 单表多态；此前一律显示"未分类？"）
    val title = when (row.type) {
        TxType.TRANSFER -> listOfNotNull("转账", row.targetAccountName?.let { "转入 $it" }).joinToString(" · ")
        else -> listOfNotNull(row.categoryParentName, row.categoryName).joinToString(" · ").ifEmpty { "未分类" }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text("✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(categoryColor(if (row.type == TxType.TRANSFER) "转账" else row.categoryName ?: "?"), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (row.type == TxType.TRANSFER) "转" else (row.categoryName ?: "?").take(1),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!row.note.isNullOrBlank()) {
                Text(
                    row.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val color = when (row.type) {
            TxType.EXPENSE -> expenseColor()
            TxType.INCOME -> incomeColor()
            TxType.TRANSFER -> MaterialTheme.colorScheme.onSurface
        }
        val prefix = when (row.type) {
            TxType.EXPENSE -> "-"
            TxType.INCOME -> "+"
            TxType.TRANSFER -> ""
        }
        Text(
            text = prefix + Money.format(row.amountCents),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            textAlign = TextAlign.End,
        )
    }
}
