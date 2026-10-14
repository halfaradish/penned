package dev.penned.app.ui.debts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.DebtWithRepaid
import dev.penned.app.domain.DebtDirection
import dev.penned.app.domain.DebtMath
import dev.penned.app.domain.DebtText
import dev.penned.app.domain.Money
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedBadge
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedElevation
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.primaryInkColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** 债务列表（M3-4）：应付/应收两段，卡片 = 对方 + 本金/已还/剩余 + 到期日 + 结清标记。 */
class DebtListViewModel(app: PennedApplication) : ViewModel() {

    private val db = app.database

    @OptIn(ExperimentalCoroutinesApi::class)
    val debts: StateFlow<List<DebtWithRepaid>> = app.settings.currentBookId
        .flatMapLatest { bookId ->
            if (bookId == null) flowOf(emptyList()) else db.debtDao().observeByBook(bookId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun DebtListScreen(
    onBack: () -> Unit,
    onOpenDebt: (String) -> Unit,
    onNewDebt: () -> Unit,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: DebtListViewModel = viewModel(initializer = { DebtListViewModel(app) })
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    DebtListContent(debts = debts, onBack = onBack, onOpenDebt = onOpenDebt, onNewDebt = onNewDebt)
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@Composable
fun DebtListContent(
    debts: List<DebtWithRepaid>,
    onBack: () -> Unit,
    onOpenDebt: (String) -> Unit,
    onNewDebt: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "债务", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewDebt,
                modifier = Modifier.size(PennedDimens.fab),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = PennedElevation.floating,
                    pressedElevation = PennedElevation.floating,
                    focusedElevation = PennedElevation.floating,
                    hoveredElevation = PennedElevation.floating,
                ),
            ) {
                Icon(PennedIcons.Plus, contentDescription = "新建债务")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = PennedDimens.listBottomClearance),
        ) {
            if (debts.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "还没有债务",
                        // D044 六态：空态给出路，动作就在下面
                        hint = "借入与借出都记在这里；建一笔债务之后，用记一笔的「债务」Tab 记它的借入与还款",
                        icon = PennedIcons.Receipt,
                        action = { PennedButton(text = "新建债务", onClick = onNewDebt) },
                        modifier = Modifier.padding(top = PennedSpacing.xxl),
                    )
                }
            } else {
                val payable = debts.filter { it.direction == DebtDirection.BORROW }
                val receivable = debts.filter { it.direction == DebtDirection.LEND }
                // 空的那一段不画：一张"应付 0.00"的空卡只会占地方
                if (payable.isNotEmpty()) {
                    item(key = "payable") {
                        DebtSection(
                            title = "应付 · 我欠对方",
                            debts = payable,
                            onOpenDebt = onOpenDebt,
                        )
                    }
                }
                if (receivable.isNotEmpty()) {
                    item(key = "receivable") {
                        DebtSection(
                            title = "应收 · 对方欠我",
                            debts = receivable,
                            onOpenDebt = onOpenDebt,
                        )
                    }
                }
            }
        }
    }
}

/** 一段（应付或应收）：标题 + 段小计 + 行。 */
@Composable
private fun DebtSection(
    title: String,
    debts: List<DebtWithRepaid>,
    onOpenDebt: (String) -> Unit,
) {
    val direction = debts.firstOrNull()?.direction ?: DebtDirection.BORROW
    val color = if (direction == DebtDirection.BORROW) expenseColor() else primaryInkColor()
    PennedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val total = debts.filter { !it.closed }.sumOf { it.remainingCents }
            Text(
                Money.format(total),
                style = MaterialTheme.typography.titleMedium,
                color = color,
            )
        }
        debts.forEach { debt ->
            PennedDivider()
            DebtRow(debt = debt, onClick = { onOpenDebt(debt.id) })
        }
    }
}

/** 债务行：对方 + 结清标记 + 本金/已还/到期，右端剩余。 */
@Composable
private fun DebtRow(debt: DebtWithRepaid, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(debt.counterparty, size = PennedDimens.avatar)
        Spacer(Modifier.width(PennedSpacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    debt.counterparty,
                    style = PennedTextStyles.rowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (debt.closed) {
                    Spacer(Modifier.width(PennedSpacing.sm))
                    PennedBadge("已结清")
                }
            }
            Text(
                listOf(
                    DebtText.principalRepaidLabel(debt.principalCents, debt.repaidCents),
                    DebtText.dueLabel(debt.dueEpochDay, java.time.LocalDate.now()),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(PennedSpacing.md))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "剩余",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                Money.format(debt.remainingCents),
                style = MaterialTheme.typography.titleMedium,
                color = if (debt.direction == DebtDirection.BORROW) expenseColor() else primaryInkColor(),
            )
        }
    }
}

@Preview(showBackground = true, name = "债务列表", widthDp = 400, heightDp = 800)
@Composable
private fun DebtListPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        DebtListContent(
            debts = listOf(
                DebtWithRepaid("d1", DebtDirection.BORROW, "张三", 100_000L, 20_000L, 20_100L, null, false, 0, 40_000L),
                DebtWithRepaid("d2", DebtDirection.LEND, "李四", 30_000L, 20_000L, null, null, false, 1, 0L),
            ),
            onBack = {},
            onOpenDebt = {},
            onNewDebt = {},
        )
    }
}
