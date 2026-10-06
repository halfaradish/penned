package dev.penned.app.ui.assets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.SwipeToDeleteRow
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.primaryInkColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 眼睛藏起来时金额显示成它（页面内状态，不落库）。 */
private const val MASK = "****"

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

/** 账户类型中文名（新增页名称兜底、导入等场景）。 */
fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.CASH -> "现金"
    AccountType.DEBIT -> "储蓄卡"
    AccountType.CREDIT -> "信用卡"
    AccountType.INVEST -> "理财"
    AccountType.DEBT -> "负债"
}

@Composable
fun AssetsScreen(
    onAddAccount: (AccountGroup, String?) -> Unit = { _, _ -> },
    onOpenAccount: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: AssetsViewModel = viewModel(initializer = { AssetsViewModel(app) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.deletedIds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // 一次只显示一条提示：被拦下的说明优先，其次是「已删除 + 撤销」。
    // key 变化会取消上一条（SnackbarHostState 在取消时自行 dismiss），连续删除时后一条顶掉前一条。
    LaunchedEffect(pendingUndo.size, message) {
        val blocked = message
        if (blocked != null) {
            snackbarHostState.showSnackbar(blocked, duration = SnackbarDuration.Short)
            viewModel.consumeMessage()
        } else if (pendingUndo.isNotEmpty()) {
            val result = snackbarHostState.showSnackbar(
                message = "已删除 ${pendingUndo.size} 个账户",
                actionLabel = "撤销",
                // 带动作的 Snackbar 按 M3 给足 10s：撤销窗口太短等于没有（与首页一致）
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearUndo()
        }
    }

    AssetsContent(
        state = state,
        onAddAccount = onAddAccount,
        onOpenAccount = onOpenAccount,
        onDeleteAccount = viewModel::deleteAccount,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@Composable
fun AssetsContent(
    state: AssetsUiState,
    onAddAccount: (AccountGroup, String?) -> Unit,
    modifier: Modifier = Modifier,
    onOpenAccount: (String) -> Unit = {},
    onDeleteAccount: (AccountWithBalance) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
) {
    // 眼睛藏金额：切 Tab 回来还在（rememberSaveable），重启重置
    var masked by rememberSaveable { mutableStateOf(false) }
    var collapsedGroups by remember { mutableStateOf(emptySet<String>()) }
    var showTypeSheet by remember { mutableStateOf(false) }

    if (showTypeSheet) {
        AccountTypeSheet(
            onPick = { group, preset ->
                showTypeSheet = false
                onAddAccount(group, preset?.label)
            },
            onDismiss = { showTypeSheet = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showTypeSheet = true },
                modifier = Modifier.size(60.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(PennedIcons.Plus, contentDescription = "添加账户")
            }
        },
    ) { padding ->
        // 整页一起滚：页头/净资产卡/汇总卡/分组卡都随列表滚走，只留 FAB 与底栏
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "title") {
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
            }
            item(key = "net") {
                NetAssetCard(state = state, masked = masked, onToggleMask = { masked = !masked })
            }
            item(key = "summary") { SummaryCards(state = state, masked = masked) }

            if (state.accounts.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "你还没有任何账户",
                        hint = "点击右下角 + 添加你的第一个账户",
                        icon = PennedIcons.Wallet,
                        modifier = Modifier.padding(top = PennedSpacing.xxl),
                    )
                }
            } else {
                groupAccounts(state.accounts).forEach { (title, accounts) ->
                    item(key = "g-$title") {
                        AccountGroupCard(
                            title = title,
                            accounts = accounts,
                            masked = masked,
                            collapsed = title in collapsedGroups,
                            onToggle = {
                                collapsedGroups = if (title in collapsedGroups) {
                                    collapsedGroups - title
                                } else {
                                    collapsedGroups + title
                                }
                            },
                            onOpenAccount = onOpenAccount,
                            onDeleteAccount = onDeleteAccount,
                        )
                    }
                }
            }
        }
    }
}

/** 净资产卡：浅青底 + 大数字，右列总资产/负资产，标题旁一个眼睛。 */
@Composable
private fun NetAssetCard(state: AssetsUiState, masked: Boolean, onToggleMask: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PennedSpacing.lg)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(PennedSpacing.lg),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "净资产(元)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        Modifier.size(36.dp).clickable(onClick = onToggleMask),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (masked) PennedIcons.EyeOff else PennedIcons.Eye,
                            contentDescription = if (masked) "显示金额" else "隐藏金额",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    if (masked) MASK else Money.format(state.netCents),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (state.netCents < 0) expenseColor() else primaryInkColor(),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                AmountLine("总资产", state.assetCents, masked)
                Spacer(Modifier.height(PennedSpacing.sm))
                AmountLine("负资产", state.liabilityCents, masked)
            }
        }
    }
}

@Composable
private fun AmountLine(label: String, cents: Long, masked: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(PennedSpacing.sm))
        Text(
            if (masked) MASK else Money.format(cents),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class SummaryLine(val label: String, val cents: Long, val color: Color)

/** 报销 / 债务 / 理财：三张独立小卡（债务与理财是 M1 静态占位）。 */
@Composable
private fun SummaryCards(state: AssetsUiState, masked: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(PennedSpacing.md),
    ) {
        SummaryCard(
            title = "报销",
            lines = listOf(
                SummaryLine("可报", state.reimbursePendingCents, MaterialTheme.colorScheme.onSurface),
                SummaryLine("已报", state.reimburseDoneCents, MaterialTheme.colorScheme.onSurface),
            ),
            masked = masked,
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            title = "债务",
            lines = listOf(
                SummaryLine("应付", 0L, expenseColor()),
                SummaryLine("应收", 0L, primaryInkColor()),
            ),
            masked = masked,
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            title = "理财",
            lines = listOf(
                SummaryLine("总额", 0L, MaterialTheme.colorScheme.onSurface),
                SummaryLine("盈亏", 0L, primaryInkColor()),
            ),
            masked = masked,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    lines: List<SummaryLine>,
    masked: Boolean,
    modifier: Modifier = Modifier,
) {
    PennedCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = PennedSpacing.xs, vertical = PennedSpacing.md),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(PennedSpacing.sm))
        lines.forEach { line ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(line.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(PennedSpacing.xs))
                Text(
                    if (masked) MASK else Money.format(line.cents),
                    style = MaterialTheme.typography.bodySmall,
                    color = line.color,
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

/**
 * 分组卡：组名 + 组小计 + 折叠箭头，组内是账户行（左滑删除、点进编辑）。
 * 整组做成一个 LazyColumn item——分组天然是"一张卡"，行数少，不需要逐行虚拟化。
 */
@Composable
private fun AccountGroupCard(
    title: String,
    accounts: List<AccountWithBalance>,
    masked: Boolean,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onDeleteAccount: (AccountWithBalance) -> Unit,
) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "group-chevron")
    PennedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val groupTotal = accounts.sumOf { it.balanceCents }
            Text(
                if (masked) MASK else Money.format(groupTotal),
                style = MaterialTheme.typography.titleMedium,
                color = if (groupTotal < 0) expenseColor() else primaryInkColor(),
            )
            Spacer(Modifier.width(PennedSpacing.sm))
            Icon(
                PennedIcons.ChevronDown,
                contentDescription = if (collapsed) "展开$title" else "折叠$title",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(visible = !collapsed) {
            Column {
                accounts.forEachIndexed { index, account ->
                    SwipeToDeleteRow(
                        onDelete = { onDeleteAccount(account) },
                        rowBackground = MaterialTheme.colorScheme.surface,
                    ) {
                        AccountRow(
                            account = account,
                            masked = masked,
                            onClick = { onOpenAccount(account.id) },
                        )
                    }
                    if (index < accounts.lastIndex) PennedDivider()
                }
            }
        }
    }
}

/** 账户行：颜料头像（按类型固定色）+ 名称 + 等宽余额（正=品牌青绿、负=朱砂）。 */
@Composable
private fun AccountRow(account: AccountWithBalance, masked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(account.name, size = PennedDimens.avatarLarge, slot = accountPigmentSlot(account.type))
        Spacer(Modifier.width(PennedSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                account.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 备注只在写了的时候占一行（D027：个人备忘，不参与任何计算）
            if (!account.remark.isNullOrBlank()) {
                Text(
                    account.remark,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            if (masked) MASK else Money.format(account.balanceCents),
            style = MaterialTheme.typography.titleMedium,
            color = if (account.balanceCents < 0) expenseColor() else primaryInkColor(),
        )
    }
}

@Preview(showBackground = true, name = "资产", widthDp = 400, heightDp = 800)
@Composable
private fun AssetsPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        val accounts = listOf(
            AccountWithBalance("a1", "现金", AccountType.CASH, null, 12_850L),
            AccountWithBalance("a2", "支付宝", AccountType.DEBIT, null, 128_450L),
            AccountWithBalance("a3", "招行信用卡", AccountType.CREDIT, null, -58_900L),
        )
        AssetsContent(
            state = AssetsUiState(
                accounts = accounts,
                netCents = accounts.sumOf { it.balanceCents },
                assetCents = accounts.filter { it.balanceCents > 0 }.sumOf { it.balanceCents },
                liabilityCents = -accounts.filter { it.balanceCents < 0 }.sumOf { it.balanceCents },
            ),
            onAddAccount = { _, _ -> },
        )
    }
}
