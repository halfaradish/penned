package dev.penned.app.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.penned.app.domain.ReimburseState
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.reimburseStateLabel
import dev.penned.app.ui.theme.PennedSpacing

/** 三选顺序照语义、也照使用频率：不报销（默认）→ 待报销（要报还没报）→ 已报销。 */
internal val REIMBURSE_STATES = listOf(ReimburseState.NONE, ReimburseState.PENDING, ReimburseState.DONE)

/**
 * 报销抽屉（M3-3，D024 交互范式一：从名单里选走底部抽屉）。
 *
 * 三态 chip **就地改、抽屉不关**（还要接着选收到款的账户/金额）；「报销金额」行把金额键盘指过去
 * （M3-2 的同一套 `AmountField` 路由，不另做输入控件），点它时由调用方收起抽屉。
 * 金额留空 = 缺省 0 = 按流水**净额**全额（`ReimburseMath`，与资产页 SQL 同一口径）。
 */
@Composable
fun ReimburseSheet(
    state: ReimburseState,
    accountName: String?,
    claimLabel: String?,
    onPickState: (ReimburseState) -> Unit,
    onPickAccount: () -> Unit,
    onEditAmount: () -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "报销") {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PennedSpacing.xl),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm)) {
                REIMBURSE_STATES.forEach { option ->
                    PennedChip(
                        label = reimburseStateLabel(option),
                        selected = state == option,
                        onClick = { onPickState(option) },
                    )
                }
            }
            if (state != ReimburseState.NONE) {
                Spacer(Modifier.height(PennedSpacing.sm))
                Text(
                    "报销账户 = 收到报销款的地方；金额留空就按流水净额全额",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = PennedSpacing.xs),
                )
                PennedListRow(
                    title = "报销账户",
                    trailing = accountName ?: "未指定",
                    onClick = onPickAccount,
                )
                PennedListRow(
                    title = "报销金额",
                    trailing = claimLabel ?: "按全额",
                    onClick = onEditAmount,
                )
            }
            Spacer(Modifier.height(PennedSpacing.lg))
            // 三态 chip 是就地改的，抽屉不会自己关——给一个明确的收尾动作（与标签抽屉同一个范式）
            PennedButton(text = "选好了", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
