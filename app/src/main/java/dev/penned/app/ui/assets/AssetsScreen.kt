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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import dev.penned.app.ui.home.categoryColor
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
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Text("+", style = MaterialTheme.typography.titleLarge) }
        },
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("净资产(元)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            Money.format(state.netCents),
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (state.netCents < 0) expenseColor() else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row {
                            Text("总资产 ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(state.assetCents), style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text("负资产 ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(state.liabilityCents), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryCell("报销", "可报", Money.format(state.reimbursePendingCents), "已报", Money.format(state.reimburseDoneCents), Modifier.weight(1f))
                SummaryCell("债务", "应付", "0.00", "应收", "0.00", Modifier.weight(1f))
                SummaryCell("理财", "总额", "0.00", "盈亏", "0.00", Modifier.weight(1f))
            }

            if (state.accounts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("你还没有任何账户", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点击右下角 + 添加你的第一个账户",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    val groups = state.accounts.groupBy { it.groupName }
                    groups.forEach { (group, list) ->
                        item(key = "g-${group ?: "default"}") {
                            Text(
                                text = group ?: "默认分组",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                        items(list, key = { it.id }) { account ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(categoryColor(account.name), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(account.name.take(1), style = MaterialTheme.typography.labelLarge, color = Color.White)
                                }
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(account.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(accountTypeLabel(account.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    Money.format(account.balanceCents),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (account.balanceCents < 0) expenseColor() else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End,
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

@Composable
private fun SummaryCell(
    title: String,
    label1: String,
    value1: String,
    label2: String,
    value2: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text("$label1: $value1", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$label2: $value2", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
                        FilterChip(
                            selected = selectedLabel == preset.label,
                            onClick = {
                                selectedLabel = preset.label
                                selectedType = preset.type
                                name = preset.label
                            },
                            label = { Text(preset.label) },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
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
