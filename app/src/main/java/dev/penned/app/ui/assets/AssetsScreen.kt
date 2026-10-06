package dev.penned.app.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.AccountWithBalance
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.Money
import dev.penned.app.domain.MoneyInput
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.MoneyText
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.categoryColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import dev.penned.app.ui.theme.expenseColor

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

    fun addAccount(type: AccountType, name: String, initialCents: Long) {
        viewModelScope.launch {
            val bookId = app.settings.currentBookId.firstOrNull() ?: return@launch
            val now = System.currentTimeMillis()
            db.accountDao().upsert(
                AccountEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    name = name.trim().ifEmpty { type.name },
                    type = type,
                    initialBalanceCents = initialCents,
                    createdAt = now,
                    updatedAt = now,
                    deviceId = app.deviceId,
                )
            )
        }
    }
}

/** 账户类型中文名（列表副标题；此前直接显示 DEBIT/CASH 英文枚举）。 */
fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.CASH -> "现金"
    AccountType.DEBIT -> "储蓄卡"
    AccountType.CREDIT -> "信用卡"
    AccountType.INVEST -> "理财"
    AccountType.DEBT -> "负债"
}

/** 添加账户的类型预设（对标一木：分组模板 + 自定义）。 */
data class AccountPreset(val label: String, val type: AccountType)

val ACCOUNT_PRESETS: Map<String, List<AccountPreset>> = linkedMapOf(
    "资金账户" to listOf(
        AccountPreset("现金", AccountType.CASH),
        AccountPreset("储蓄卡", AccountType.DEBIT),
        AccountPreset("支付宝", AccountType.DEBIT),
        AccountPreset("微信钱包", AccountType.DEBIT),
    ),
    "信贷账户" to listOf(
        AccountPreset("信用卡", AccountType.CREDIT),
        AccountPreset("花呗", AccountType.CREDIT),
    ),
    "投资理财" to listOf(AccountPreset("理财账户", AccountType.INVEST)),
    "负债" to listOf(AccountPreset("欠款", AccountType.DEBT)),
)

@Composable
fun AssetsScreen(modifier: Modifier = Modifier) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: AssetsViewModel = viewModel(initializer = { AssetsViewModel(app) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    AssetsContent(state = state, onAddAccount = viewModel::addAccount, modifier = modifier)
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@Composable
fun AssetsContent(
    state: AssetsUiState,
    onAddAccount: (AccountType, String, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
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

    if (showAdd) {
        AddAccountDialog(
            onConfirm = { type, name, initial ->
                onAddAccount(type, name, initial)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

/** 账户类型的固定颜料槽：信用卡/负债红系、现金赭石、储蓄卡石青、理财石绿。 */
private fun accountPigmentSlot(type: AccountType): Int = when (type) {
    AccountType.DEBT -> 6
    AccountType.CREDIT -> 0
    AccountType.CASH -> 1
    AccountType.INVEST -> 3
    AccountType.DEBIT -> 4
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
        Text("$label1 ${value1}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(2.dp))
        Text("$label2 ${value2}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AddAccountDialog(
    onConfirm: (AccountType, String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedType by remember { mutableStateOf<AccountType>(AccountType.CASH) }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var initialText by remember { mutableStateOf("") }

    val presetName = ACCOUNT_PRESETS.values.flatten().firstOrNull { it.type == selectedType }?.label ?: "账户"
    val displayName = name.ifBlank { presetName }
    val initialCents = Money.parseToCents(initialText) ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加账户") },
        text = {
            // 内容可能超出对话框高度（13 个预设 chip + 输入框），必须可滚动，否则键盘弹出时按钮被盖住
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ACCOUNT_PRESETS.forEach { (group, presets) ->
                    Text(
                        group,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    presets.forEach { preset ->
                        PennedChip(
                            label = preset.label,
                            selected = selectedLabel == preset.label,
                            onClick = {
                                // 只记类型与预设：不再覆盖用户已输入的名称（预设名留作默认显示值）
                                selectedLabel = preset.label
                                selectedType = preset.type
                            },
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账户名称（显示：$displayName）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = initialText,
                    onValueChange = { input -> initialText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("初始余额（元）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedType, displayName, initialCents) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
