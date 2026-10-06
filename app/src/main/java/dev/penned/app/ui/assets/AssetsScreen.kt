package dev.penned.app.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.AccountWithBalance
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.Money
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.MoneyText
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.expenseColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssetsUiState(
    val accounts: List<AccountWithBalance> = emptyList(),
    val netCents: Long = 0,
    val assetCents: Long = 0,
    val liabilityCents: Long = 0,
    val reimbursePendingCents: Long = 0,
    val reimburseDoneCents: Long = 0,
)

class AssetsViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AssetsUiState> = app.settings.currentBookId
        .flatMapLatest { bookId ->
            val book = bookId ?: return@flatMapLatest flowOf(AssetsUiState())
            combine(
                db.accountDao().observeWithBalance(bookId),
                db.transactionDao().observeReimburseSums(bookId),
            ) { accounts, reimb ->
                val net = accounts.sumOf { it.balanceCents }
                AssetsUiState(
                    accounts = accounts,
                    netCents = net,
                    assetCents = accounts.filter { it.balanceCents > 0 }.sumOf { it.balanceCents },
                    liabilityCents = -accounts.filter { it.balanceCents < 0 }.sumOf { it.balanceCents },
                    reimbursePendingCents = reimb.pendingCents,
                    reimburseDoneCents = reimb.doneCents,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetsUiState())

    /** 待撤销的软删 id（左滑删除后进 Snackbar「撤销」，撤销条消失即失效）。 */
    private val _deletedIds = MutableStateFlow<List<String>>(emptyList())
    val deletedIds: StateFlow<List<String>> = _deletedIds

    /** 一次性提示：账户被拦下不让删时说明原因。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    /** 行左滑删除（单个账户）；有流水会被 [deleteAccountIfEmpty] 拦下并提示。 */
    fun deleteAccount(account: AccountWithBalance) {
        viewModelScope.launch {
            val blocked = deleteAccountIfEmpty(db, account.id, account.name)
            if (blocked != null) {
                _message.value = blocked
                return@launch
            }
            _deletedIds.value = _deletedIds.value + account.id
        }
    }

    /** 撤销删除：把累计待撤销的 id 一次恢复。 */
    fun undoDelete() {
        val ids = _deletedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            db.accountDao().restoreAll(ids, System.currentTimeMillis())
            _deletedIds.value = emptyList()
        }
    }

    /** 撤销条消失后调用：过期的撤销不能再生效。 */
    fun clearUndo() {
        _deletedIds.value = emptyList()
    }
}

/** 账户类型中文名（账户行副标题、新增页名称兜底）。 */
fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.CASH -> "现金"
    AccountType.DEBIT -> "储蓄卡"
    AccountType.CREDIT -> "信用卡"
    AccountType.INVEST -> "理财"
    AccountType.DEBT -> "负债"
}

@Composable
fun AssetsScreen(
    onAddAccount: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: AssetsViewModel = viewModel(initializer = { AssetsViewModel(app) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    AssetsContent(state = state, onAddAccount = onAddAccount, modifier = modifier)
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@Composable
fun AssetsContent(
    state: AssetsUiState,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddAccount,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(PennedIcons.Plus, contentDescription = "添加账户")
            }
        },
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding)) {
            Text(
                "资产",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(
                    start = PennedSpacing.lg,
                    end = PennedSpacing.lg,
                    top = PennedSpacing.md,
                    bottom = PennedSpacing.sm,
                ),
            )

            // 净资产 hero：一个主数字 + 两个分列
            PennedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg)) {
                Text(
                    "净资产(元)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(PennedSpacing.xs))
                Text(
                    Money.format(state.netCents),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (state.netCents < 0) expenseColor() else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(PennedSpacing.md))
                Row(modifier = Modifier.fillMaxWidth()) {
                    AmountColumn("总资产", Money.format(state.assetCents), Modifier.weight(1f))
                    AmountColumn("负资产", Money.format(state.liabilityCents), Modifier.weight(1f))
                }
            }

            // 报销 / 债务 / 理财：三列一张卡（原来三张卡各塞两行小字，读不动）
            PennedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SummaryCell(
                        "报销",
                        "可报",
                        Money.format(state.reimbursePendingCents),
                        "已报",
                        Money.format(state.reimburseDoneCents),
                        Modifier.weight(1f),
                    )
                    SummaryCell("债务", "应付", "0.00", "应收", "0.00", Modifier.weight(1f))
                    SummaryCell("理财", "总额", "0.00", "盈亏", "0.00", Modifier.weight(1f))
                }
            }

            if (state.accounts.isEmpty()) {
                EmptyState(
                    title = "你还没有任何账户",
                    hint = "点击右下角 + 添加你的第一个账户",
                    icon = PennedIcons.Wallet,
                    modifier = Modifier.padding(top = PennedSpacing.xxl),
                )
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    val groups = state.accounts.groupBy { it.groupName }
                    groups.forEach { (group, list) ->
                        item(key = "g-${group ?: "default"}") {
                            Text(
                                text = group ?: "默认分组",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = PennedSpacing.lg,
                                    end = PennedSpacing.lg,
                                    top = PennedSpacing.md,
                                    bottom = PennedSpacing.sm,
                                ),
                            )
                        }
                        items(list, key = { it.id }) { account ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 账户颜色按账户类型固定（信用卡一律朱砂、现金一律赭石…），比按名字哈希好认
                                CategoryAvatar(account.name, slot = accountPigmentSlot(account.type))
                                Spacer(Modifier.width(PennedSpacing.md))
                                Column(Modifier.weight(1f)) {
                                    Text(account.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        accountTypeLabel(account.type),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                MoneyText(
                                    cents = account.balanceCents,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (account.balanceCents < 0) {
                                        expenseColor()
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    signed = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SummaryCell(
    title: String,
    label1: String,
    value1: String,
    label2: String,
    value2: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(PennedSpacing.xs))
        Text("$label1 $value1", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(2.dp))
        Text("$label2 $value2", style = MaterialTheme.typography.bodySmall)
    }
}
