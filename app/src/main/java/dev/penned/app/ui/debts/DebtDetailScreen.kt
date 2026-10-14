package dev.penned.app.ui.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.DebtFlowRow
import dev.penned.app.data.db.dao.DebtWithRepaid
import dev.penned.app.domain.AddDate
import dev.penned.app.domain.DebtDirection
import dev.penned.app.domain.DebtMath
import dev.penned.app.domain.DebtText
import dev.penned.app.domain.Money
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.MoneyText
import dev.penned.app.ui.components.PennedBadge
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.SectionCard
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.primaryInkColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 债务详情（M3-4）：剩余三数 + 债务信息 + 还款记录 + 「记一笔还款」。 */
data class DebtDetailUi(
    val debt: DebtWithRepaid,
    /** 名下资金流水（借入与还款都在），倒序。 */
    val flows: List<DebtFlowRow>,
)

class DebtDetailViewModel(app: PennedApplication, debtId: String) : ViewModel() {

    private val db = app.database

    val state: StateFlow<DebtDetailUi?> = combine(
        db.debtDao().observeById(debtId),
        db.transactionDao().observeByDebt(debtId),
    ) { debt, flows ->
        debt?.let { DebtDetailUi(debt = it, flows = flows) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun DebtDetailScreen(
    debtId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onSettle: (String) -> Unit,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: DebtDetailViewModel = viewModel(
        key = "debt-detail-$debtId",
        initializer = { DebtDetailViewModel(app, debtId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val d = state
    if (d == null) {
        // 载入中：画底色与顶栏，别让下层页透出来（与账单详情同一做法）
        Scaffold(
            topBar = { PennedTopBar(title = "债务详情", onBack = onBack) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding -> Spacer(Modifier.fillMaxSize().padding(padding)) }
        return
    }
    DebtDetailContent(
        detail = d,
        onBack = onBack,
        onEdit = { onEdit(d.debt.id) },
        onSettle = { onSettle(d.debt.id) },
    )
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@Composable
fun DebtDetailContent(
    detail: DebtDetailUi,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSettle: () -> Unit,
) {
    val debt = detail.debt
    val amountColor = if (debt.direction == DebtDirection.BORROW) expenseColor() else primaryInkColor()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "债务详情", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PennedSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PennedSpacing.md),
        ) {
            // Hero：对方 + 立场 + 结清标记 + 剩余（主角）
            PennedCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(debt.counterparty, size = PennedDimens.avatarLarge)
                    Spacer(Modifier.width(PennedSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(debt.counterparty, style = PennedTextStyles.cardTitle)
                        Text(
                            DebtMath.directionLabel(debt.direction),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (debt.closed) PennedBadge("已结清")
                }
                Spacer(Modifier.height(PennedSpacing.md))
                Text(
                    "剩余",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 不带正负号（"剩余"两个字已经说明方向；"-600.00" 会被读成"还欠了负数"）；
                // 方向靠颜色——我欠人用支出色、人欠我用品牌青绿
                Text(
                    Money.format(debt.remainingCents),
                    style = MaterialTheme.typography.displaySmall,
                    color = amountColor,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    DebtText.principalRepaidLabel(debt.principalCents, debt.repaidCents),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "债务信息") {
                DetailLine("方向", DebtMath.directionLabel(debt.direction))
                PennedDivider()
                DetailLine("对方", debt.counterparty)
                PennedDivider()
                DetailLine("本金", Money.format(debt.principalCents))
                PennedDivider()
                DetailLine("已归还", Money.format(debt.repaidCents))
                PennedDivider()
                DetailLine("起始日", DebtText.fullDate(debt.startEpochDay))
                PennedDivider()
                DetailLine("到期日", debt.dueEpochDay?.let { DebtText.fullDate(it) } ?: "未设")
                if (!debt.note.isNullOrBlank()) {
                    PennedDivider()
                    DetailLine("备注", debt.note!!)
                }
            }

            SectionCard(title = "还款记录") {
                if (detail.flows.isEmpty()) {
                    Text(
                        "还没有资金流水——借入的钱到账、还了钱，都用记一笔的「债务」Tab 记",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                    )
                } else {
                    detail.flows.forEachIndexed { index, flow ->
                        if (index > 0) PennedDivider()
                        FlowLine(
                            flow = flow,
                            direction = debt.direction,
                        )
                    }
                }
            }

            Spacer(Modifier.height(PennedSpacing.xs))
            // 主按钮用词跟方向走：我欠人 → 还款；人欠我 → 收款
            PennedButton(DebtText.settleCtaLabel(debt.direction), onSettle, leadingIcon = PennedIcons.Plus)
            PennedButton("编辑", onEdit, leadingIcon = PennedIcons.Edit)
        }
    }
}

/** 一条资金流水：动作词（借入/还款…）+ 日期 + 账户与备注，右端金额。 */
@Composable
private fun FlowLine(flow: DebtFlowRow, direction: DebtDirection) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                DebtMath.flowLabel(direction, flow.type),
                style = PennedTextStyles.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOfNotNull(
                    DebtText.fullDate(AddDate.dayFromMinute(flow.dateEpochMinute)),
                    flow.accountName,
                    flow.note?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(PennedSpacing.md))
        MoneyText(
            flow.amountCents,
            type = flow.type,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** 明细行（只读；与账单详情的写法一致）。 */
@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true, name = "债务详情", widthDp = 400, heightDp = 800)
@Composable
private fun DebtDetailPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        DebtDetailContent(
            detail = DebtDetailUi(
                debt = DebtWithRepaid(
                    id = "d1", direction = DebtDirection.BORROW, counterparty = "张三",
                    principalCents = 100_000L, startEpochDay = java.time.LocalDate.now().minusDays(10).toEpochDay(),
                    dueEpochDay = java.time.LocalDate.now().plusDays(20).toEpochDay(),
                    note = "周转", closed = false, sortOrder = 0, repaidCents = 40_000L,
                ),
                flows = listOf(
                    DebtFlowRow("f2", TxType.EXPENSE, 40_000L, "a1", 30_000_000L, null, "现金"),
                    DebtFlowRow("f1", TxType.INCOME, 100_000L, "a1", 30_000_000L, null, "现金"),
                ),
            ),
            onBack = {}, onEdit = {}, onSettle = {},
        )
    }
}
