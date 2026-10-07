package dev.penned.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.Money
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.MoneyText
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedPigment
import dev.penned.app.ui.theme.PennedSpacing

/** 流水标题：分类路径（父 · 子）或转账行的「转账 · 转入 X」（D007 单表多态）。列表行与删除确认共用。 */
internal fun txTitle(row: TxWithCategory): String = when (row.type) {
    TxType.TRANSFER -> listOfNotNull("转账", row.targetAccountName?.let { "转入 $it" }).joinToString(" · ")
    else -> listOfNotNull(row.categoryParentName, row.categoryName).joinToString(" · ")
        .ifEmpty { PennedPigment.UNKNOWN }
}

/** 删除确认里"删的是哪一条"：标题 + 带符号金额（符号口径与 [MoneyText] 一致）。 */
internal fun txSummary(row: TxWithCategory): String {
    val sign = when (row.type) {
        TxType.EXPENSE -> "-"
        TxType.INCOME -> "+"
        else -> ""
    }
    return "${txTitle(row)}  $sign${Money.format(row.amountCents)}"
}

/** 流水行（首页 / 搜索结果共用）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TransactionRow(
    row: TxWithCategory,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = txTitle(row)
    val avatarName = when (row.type) {
        TxType.TRANSFER -> PennedPigment.TRANSFER
        else -> row.categoryName ?: PennedPigment.UNKNOWN
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        // 顶端对齐：金额贴在标题那一行，不随行有多高而上下浮动（参考图口径）
        verticalAlignment = Alignment.Top,
    ) {
        if (selectionMode) {
            Box(
                Modifier
                    .align(Alignment.CenterVertically)
                    .size(22.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        CircleShape,
                    )
                    .border(
                        BorderStroke(
                            1.5.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        PennedIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.width(PennedSpacing.md))
        }
        CategoryAvatar(avatarName)
        Spacer(Modifier.width(PennedSpacing.md))
        Column(Modifier.weight(1f).padding(top = 1.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                HomeGroups.clockLabel(row.dateEpochMinute),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!row.note.isNullOrBlank()) {
                Text(
                    row.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(PennedSpacing.md))
        MoneyText(row.amountCents, type = row.type, modifier = Modifier.padding(top = 1.dp))
    }
}
