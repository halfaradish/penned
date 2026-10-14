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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import dev.penned.app.data.db.dao.toInvestSnapshot
import dev.penned.app.domain.DebtMath
import dev.penned.app.domain.InvestMath
import dev.penned.app.domain.Money
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.AnimatedNumberText
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedSnackbarHost
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.SwipeActionRow
import dev.penned.app.ui.components.rowDeleteAction
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedElevation
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
    /** 债务（M3-4）：应付 = 我欠对方的剩余合计、应收 = 对方欠我的剩余合计。 */
    val payableCents: Long = 0,
    val receivableCents: Long = 0,
    /** 理财（M3-10）：总额 = Σ 各理财账户的总额（最近快照 / 无快照退回余额）；盈亏 = 总额 − 成本。 */
    val investTotalCents: Long = 0,
    val investProfitCents: Long = 0,
    /** 理财账户（点理财卡进详情用）。 */
    val investAccounts: List<AccountWithBalance> = emptyList(),
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
                db.debtDao().observeByBook(bookId),
                db.snapshotDao().observeForBook(bookId),
            ) { accounts, reimb, debts, snapshots ->
                // 债务不落库（D004/D068）：净资产 = Σ账户余额 − 应付 + 应收（DebtMath 的口径）
                val payable = DebtMath.payableCents(debts.map { it.toBalance() })
                val receivable = DebtMath.receivableCents(debts.map { it.toBalance() })
                val accountSum = accounts.sumOf { it.balanceCents }
                // 理财三数（M3-10）：每个理财账户各算一份再求和；快照按账户取最近一条
                val investAccounts = accounts.filter { it.type == AccountType.INVEST }
                val investSummaries = investAccounts.map { account ->
                    InvestMath.summary(
                        snapshots = snapshots.filter { it.accountId == account.id }.map { it.toInvestSnapshot() },
                        balanceCents = account.balanceCents,
                        initialBalanceCents = account.initialBalanceCents,
                        netTransferCents = account.netTransferCents,
                    )
                }
                AssetsUiState(
                    accounts = accounts,
                    netCents = DebtMath.netAssetCents(accountSum, payable, receivable),
                    assetCents = accounts.filter { it.balanceCents > 0 }.sumOf { it.balanceCents },
                    liabilityCents = -accounts.filter { it.balanceCents < 0 }.sumOf { it.balanceCents },
                    reimbursePendingCents = reimb.pendingCents,
                    reimburseDoneCents = reimb.doneCents,
                    payableCents = payable,
                    receivableCents = receivable,
                    investTotalCents = investSummaries.sumOf { it.totalCents },
                    investProfitCents = investSummaries.sumOf { it.profitCents },
                    investAccounts = investAccounts,
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
    onOpenDebts: () -> Unit = {},
    /** 点理财卡进理财详情（M3-10）。 */
    onOpenInvest: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: AssetsViewModel = viewModel(initializer = { AssetsViewModel(app) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.deletedIds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    // 左滑动作区：只留一行为开（UI-13）；纯 UI 状态，不进 ViewModel
    var revealedRowId: String? by rememberSaveable { mutableStateOf<String?>(null) }

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
        onOpenDebts = onOpenDebts,
        onOpenInvest = onOpenInvest,
        onDeleteAccount = viewModel::deleteAccount,
        revealedRowId = revealedRowId,
        onRevealedRowChange = { revealedRowId = it },
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
    /** 点「债务」卡进债务列表（M3-4）；点「理财」卡进理财详情（M3-10）。 */
    onOpenDebts: () -> Unit = {},
    onOpenInvest: (String) -> Unit = {},
    onDeleteAccount: (AccountWithBalance) -> Unit = {},
    /** 当前左滑展开动作区的账户 id（null = 都收起）；只留一行为开。 */
    revealedRowId: String? = null,
    onRevealedRowChange: (String?) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
) {
    // 眼睛藏金额：切 Tab 回来还在（rememberSaveable），重启重置
    var masked by rememberSaveable { mutableStateOf(false) }
    // 「已停用」段默认折叠（M3-9）：停用账户是"不常用"，不该占着首屏
    var collapsedGroups by remember { mutableStateOf(setOf(ARCHIVED_SECTION_TITLE)) }
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
        snackbarHost = { if (snackbarHostState != null) PennedSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showTypeSheet = true },
                modifier = Modifier.size(PennedDimens.fab),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                // 零阴影：浮层靠填充色分层（D043 原则二；此前吃的是 M3 默认 6dp）
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = PennedElevation.floating,
                    pressedElevation = PennedElevation.floating,
                    focusedElevation = PennedElevation.floating,
                    hoveredElevation = PennedElevation.floating,
                ),
            ) {
                Icon(PennedIcons.Plus, contentDescription = "添加账户")
            }
        },
    ) { padding ->
        // 整页一起滚：页头/净资产卡/汇总卡/分组卡都随列表滚走，只留 FAB 与底栏
        val listState = rememberLazyListState()
        // 动作区只留一行为开：滚列表就收起（与首页同一口径）
        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress) onRevealedRowChange(null)
        }
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = PennedDimens.listBottomClearance),
        ) {
            // 页头大标题已去掉：底栏 Tab 已经写着"资产"，同一句话不必出现两次（D035 推广）
            item(key = "net") {
                NetAssetCard(state = state, masked = masked, onToggleMask = { masked = !masked })
            }
            item(key = "summary") {
                SummaryCards(
                    state = state,
                    masked = masked,
                    onOpenDebts = onOpenDebts,
                    onOpenInvest = { id -> onOpenInvest(id) },
                )
            }

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
                val (active, archived) = partitionArchived(state.accounts)
                groupAccounts(active).forEach { (title, accounts) ->
                    item(key = "g-$title") {
                        AccountGroupCard(
                            title = title,
                            accounts = accounts,
                            masked = masked,
                            collapsed = title in collapsedGroups,
                            onToggle = {
                                onRevealedRowChange(null)   // 折叠/展开分组时收起动作区（行会跟着收起）
                                collapsedGroups = if (title in collapsedGroups) {
                                    collapsedGroups - title
                                } else {
                                    collapsedGroups + title
                                }
                            },
                            onOpenAccount = onOpenAccount,
                            onDeleteAccount = onDeleteAccount,
                            revealedRowId = revealedRowId,
                            onRevealedRowChange = onRevealedRowChange,
                        )
                    }
                }
                // 已停用段（M3-9）：单独一段、默认折叠、行尾「已停用」；净资产仍计入（上面 hero 的算法没动）
                if (archived.isNotEmpty()) {
                    item(key = "g-archived") {
                        AccountGroupCard(
                            title = ARCHIVED_SECTION_TITLE,
                            accounts = archived,
                            masked = masked,
                            collapsed = ARCHIVED_SECTION_TITLE in collapsedGroups,
                            onToggle = {
                                onRevealedRowChange(null)
                                collapsedGroups = if (ARCHIVED_SECTION_TITLE in collapsedGroups) {
                                    collapsedGroups - ARCHIVED_SECTION_TITLE
                                } else {
                                    collapsedGroups + ARCHIVED_SECTION_TITLE
                                }
                            },
                            onOpenAccount = onOpenAccount,
                            onDeleteAccount = onDeleteAccount,
                            revealedRowId = revealedRowId,
                            onRevealedRowChange = onRevealedRowChange,
                            rowTag = "已停用",
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
                        // 在浅青底上用容器自己的墨色（9.5:1），中性灰压在青底上会发脏
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Box(
                        Modifier.size(PennedDimens.iconButton).clickable(onClick = onToggleMask),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (masked) PennedIcons.EyeOff else PennedIcons.Eye,
                            contentDescription = if (masked) "显示金额" else "隐藏金额",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(PennedDimens.iconSmall),
                        )
                    }
                }
                if (masked) {
                    Text(
                        MASK,
                        style = MaterialTheme.typography.displaySmall,
                        color = if (state.netCents < 0) expenseColor() else primaryInkColor(),
                    )
                } else {
                    AnimatedNumberText(
                        cents = state.netCents,
                        style = MaterialTheme.typography.displaySmall,
                        color = if (state.netCents < 0) expenseColor() else primaryInkColor(),
                    )
                }
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

/** 报销 / 债务 / 理财：三张独立小卡（债务 M3-4 起接真数据并可点进列表；理财仍是占位）。 */
@Composable
private fun SummaryCards(
    state: AssetsUiState,
    masked: Boolean,
    onOpenDebts: () -> Unit,
    onOpenInvest: (String) -> Unit,
) {
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
                SummaryLine("应付", state.payableCents, expenseColor()),
                SummaryLine("应收", state.receivableCents, primaryInkColor()),
            ),
            masked = masked,
            onClick = onOpenDebts,
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            title = "理财",
            lines = listOf(
                SummaryLine("总额", state.investTotalCents, MaterialTheme.colorScheme.onSurface),
                // 盈亏：正=品牌青绿、负=朱砂（与净资产同一个颜色语言）
                SummaryLine(
                    "盈亏",
                    state.investProfitCents,
                    if (state.investProfitCents < 0) expenseColor() else primaryInkColor(),
                ),
            ),
            masked = masked,
            // 只有一个理财账户就直接进它；多个先让用户选（M3-10）
            onClick = if (state.investAccounts.isEmpty()) null else {
                { onOpenInvest(state.investAccounts.first().id) }
            },
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
    onClick: (() -> Unit)? = null,
) {
    PennedCard(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
 * 分组卡：组名 + 组小计 + 折叠箭头，组内是账户行（左滑动作区、点进编辑）。
 * 整组做成一个 LazyColumn item——分组天然是"一张卡"，行数少，不需要逐行虚拟化。
 *
 * 账户行的动作区只放「删除」（UI-13 用户裁决）：点行就是进编辑页，再给「编辑」是重复入口
 * （首页那行点的是详情，所以那边才需要补「编辑」）。
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
    revealedRowId: String? = null,
    onRevealedRowChange: (String?) -> Unit = {},
    /** 行尾标记（「已停用」段用，M3-9）。 */
    rowTag: String? = null,
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
                modifier = Modifier.size(PennedDimens.iconChevron).rotate(rotation),
            )
        }
        AnimatedVisibility(visible = !collapsed) {
            Column {
                accounts.forEachIndexed { index, account ->
                    SwipeActionRow(
                        isOpen = revealedRowId == account.id,
                        onOpenChange = { open ->
                            onRevealedRowChange(if (open) account.id else null)
                        },
                        actions = listOf(
                            // 删除直接做，拦下的说明走提示条（有流水时 deleteAccountIfEmpty 会返回原因）
                            rowDeleteAction {
                                onRevealedRowChange(null)
                                onDeleteAccount(account)
                            },
                        ),
                    ) {
                        AccountRow(
                            account = account,
                            masked = masked,
                            tag = rowTag,
                            // 展开态点行 = 收起动作区，不进编辑页（QQ 口径）
                            onClick = {
                                if (revealedRowId == account.id) {
                                    onRevealedRowChange(null)
                                } else {
                                    onOpenAccount(account.id)
                                }
                            },
                        )
                    }
                    if (index < accounts.lastIndex) PennedDivider()
                }
            }
        }
    }
}

/** 账户行：头像（统一淡灰绿）+ 名称 + 等宽余额（正=品牌青绿、负=朱砂）。 */
@Composable
private fun AccountRow(account: AccountWithBalance, masked: Boolean, tag: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(account.name, size = PennedDimens.avatarLarge)
        Spacer(Modifier.width(PennedSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                account.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tag != null) {
                Text(
                    tag,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
