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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.DebtMath
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.MoneyText
import dev.penned.app.ui.components.PennedBadge
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedPigment
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles

/**
 * 流水标题：分类路径（父 · 子）、转账行的「转账 · 转入 X」（D007 单表多态），
 * 以及债务流水的「借入 / 还款 / 借出 / 收到还款 · 对方」（M3-4）——它没有分类，
 * 不写这一句就只能显示"未分类"，看不出这笔钱是在还债。
 */
internal fun txTitle(row: TxWithCategory): String {
    val debt = row.debtDirection
    if (debt != null && !row.debtCounterparty.isNullOrBlank()) {
        return listOfNotNull(DebtMath.flowLabel(debt, row.type), row.debtCounterparty).joinToString(" · ")
    }
    return when (row.type) {
        TxType.TRANSFER -> listOfNotNull("转账", row.targetAccountName?.let { "转入 $it" }).joinToString(" · ")
        else -> listOfNotNull(row.categoryParentName, row.categoryName).joinToString(" · ")
            .ifEmpty { PennedPigment.UNKNOWN }
    }
}

/**
 * 行上的退优惠标记（M3-2）：金额列显示的是净额，没标记的话"0.00"与变小的数都说不清来路。
 * 全额退款单独给词（那是这一行最容易被当成记错的情况），优惠只在没有退款时才顶上（一行一个，不堆）。
 */
internal fun refundMarker(row: TxWithCategory): String? = when {
    row.refundCents <= 0L -> if (row.discountCents > 0L) "优惠" else null
    row.amountCents == 0L -> "全额退款"
    else -> "退款"
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
    val avatarName = when {
        row.type == TxType.TRANSFER -> PennedPigment.TRANSFER
        // 债务流水：头像给对方名（与详情页同口径，同一个人在两处同色）
        row.debtDirection != null -> row.debtCounterparty ?: PennedPigment.UNKNOWN
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
                    .size(PennedDimens.selectionDot)
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
                        modifier = Modifier.size(PennedDimens.selectionCheck),
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
                style = PennedTextStyles.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    HomeGroups.clockLabel(row.dateEpochMinute),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 退款/优惠标记（M3-2）：金额列是净额，行上得有一句"为什么变小/是 0"
                refundMarker(row)?.let { marker ->
                    Spacer(Modifier.width(PennedSpacing.sm))
                    PennedBadge(marker)
                }
            }
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
        // 金额是主角：比行标题重一档（16/600 对 15/500），等宽右对齐
        MoneyText(
            row.amountCents,
            type = row.type,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}
