package dev.penned.app.ui.recurring

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.RuleWithSummary
import dev.penned.app.domain.DebtText
import dev.penned.app.domain.Money
import dev.penned.app.domain.RecurringMath
import dev.penned.app.domain.RecurringPeriod
import dev.penned.app.domain.Templates
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedSnackbarHost
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.SwipeActionRow
import dev.penned.app.ui.components.rowDeleteAction
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedElevation
import dev.penned.app.ui.theme.PennedSpacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 周期记账（M3-6）：列表行 = 名称 + 摘要 + 「下次 M月D日」+ 启停开关；
 * 左滑删除（软删规则，**已生成的流水保留**，提示条写明）；点行进编辑页。
 */
class RecurringViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    @OptIn(ExperimentalCoroutinesApi::class)
    val rules: StateFlow<List<RuleWithSummary>> = app.settings.currentBookId
        .flatMapLatest { bookId ->
            if (bookId == null) flowOf(emptyList()) else db.recurringDao().observeByBook(bookId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _deletedIds = MutableStateFlow<List<String>>(emptyList())
    val deletedIds: StateFlow<List<String>> = _deletedIds

    fun toggle(rule: RuleWithSummary, enabled: Boolean) {
        viewModelScope.launch {
            db.recurringDao().setEnabled(rule.id, enabled, System.currentTimeMillis())
        }
    }

    fun delete(rule: RuleWithSummary) {
        viewModelScope.launch {
            db.recurringDao().softDelete(rule.id, System.currentTimeMillis())
            _deletedIds.value = _deletedIds.value + rule.id
        }
    }

    /** 撤销删除：规则原样恢复（已生成的流水本来就没动）。 */
    fun undoDelete() {
        val ids = _deletedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            ids.forEach { id ->
                db.recurringDao().get(id)?.let { db.recurringDao().upsert(it.copy(deleted = false, updatedAt = now)) }
            }
            _deletedIds.value = emptyList()
        }
    }

    fun clearUndo() {
        _deletedIds.value = emptyList()
    }
}

@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    onOpenRule: (String) -> Unit,
    onNewRule: () -> Unit,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: RecurringViewModel = viewModel(initializer = { RecurringViewModel(app) })
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.deletedIds.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var revealedRowId: String? by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingUndo.size) {
        if (pendingUndo.isNotEmpty()) {
            val result = snackbarHostState.showSnackbar(
                // 软删规则不动流水：这句话必须写出来（否则用户以为连账一起删了）
                message = "已删除 ${pendingUndo.size} 条规则，已生成的流水保留",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearUndo()
        }
    }

    RecurringContent(
        rules = rules,
        onBack = onBack,
        onOpenRule = onOpenRule,
        onNewRule = onNewRule,
        onToggle = viewModel::toggle,
        onDelete = viewModel::delete,
        revealedRowId = revealedRowId,
        onRevealedRowChange = { revealedRowId = it },
        snackbarHostState = snackbarHostState,
    )
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@Composable
fun RecurringContent(
    rules: List<RuleWithSummary>,
    onBack: () -> Unit,
    onOpenRule: (String) -> Unit,
    onNewRule: () -> Unit,
    onToggle: (RuleWithSummary, Boolean) -> Unit = { _, _ -> },
    onDelete: (RuleWithSummary) -> Unit = {},
    revealedRowId: String? = null,
    onRevealedRowChange: (String?) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "周期记账", onBack = onBack) },
        snackbarHost = { if (snackbarHostState != null) PennedSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewRule,
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
                Icon(PennedIcons.Plus, contentDescription = "新建周期规则")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = PennedDimens.listBottomClearance),
        ) {
            if (rules.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "还没有周期记账",
                        hint = "房租、话费这类固定支出：建一条规则，打开 App 时自动补记到期的期数（不预生成未来的）",
                        icon = PennedIcons.Refresh,
                        action = { PennedButton(text = "新建规则", onClick = onNewRule) },
                        modifier = Modifier.padding(top = PennedSpacing.xxl),
                    )
                }
            } else {
                item(key = "list") {
                    PennedCard(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        rules.forEachIndexed { index, rule ->
                            if (index > 0) PennedDivider()
                            SwipeActionRow(
                                isOpen = revealedRowId == rule.id,
                                onOpenChange = { open -> onRevealedRowChange(if (open) rule.id else null) },
                                actions = listOf(
                                    rowDeleteAction {
                                        onRevealedRowChange(null)
                                        onDelete(rule)
                                    },
                                ),
                            ) {
                                PennedListRow(
                                    title = rule.name,
                                    subtitle = subtitleOf(rule),
                                    onClick = {
                                        if (revealedRowId == rule.id) onRevealedRowChange(null) else onOpenRule(rule.id)
                                    },
                                    // 开关在行尾（不堆图标）：改启停不丢已生成的流水
                                    content = {
                                        Switch(
                                            checked = rule.enabled,
                                            onCheckedChange = { onToggle(rule, it) },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 副标题：第一行「每月 · 下次 10月16日」（最要紧的两个信息），第二行摘要（长了走省略号）。 */
internal fun subtitleOf(rule: RuleWithSummary): String {
    val amount = Money.format(rule.amountCents)
    val body = if (rule.type == TxType.TRANSFER) {
        listOfNotNull(
            Templates.typeLabel(rule.type),
            amount,
            rule.accountName?.let { from -> rule.targetAccountName?.let { "$from → $it" } ?: from },
        ).joinToString(" · ")
    } else {
        listOfNotNull(
            Templates.typeLabel(rule.type),
            amount,
            rule.categoryName ?: "未分类",
            rule.accountName,
        ).joinToString(" · ")
    }
    val next = if (rule.enabled) {
        "下次 ${DebtText.dueLabel(rule.nextEpochDay, LocalDate.now())}"
    } else {
        "已停用"
    }
    // 周期与「下次」放第一行：摘要可能很长（分类名 + 账户名），被省略号截掉的必须是它
    return listOf(
        "${RecurringMath.periodLabel(rule.periodType, rule.interval)} · $next",
        body,
    ).joinToString("\n")
}

@Preview(showBackground = true, name = "周期记账", widthDp = 400, heightDp = 800)
@Composable
private fun RecurringPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        RecurringContent(
            rules = listOf(
                RuleWithSummary(
                    id = "r1", name = "房租", type = TxType.EXPENSE, amountCents = 200_000,
                    categoryId = null, accountId = null, targetAccountId = null, note = null, feeCents = 0,
                    periodType = RecurringPeriod.MONTHLY, interval = 1,
                    startEpochDay = 20_000L, nextEpochDay = LocalDate.now().plusDays(2).toEpochDay(),
                    endEpochDay = null, enabled = true,
                    categoryName = "居家生活", accountName = "储蓄卡TestCard", targetAccountName = null,
                ),
                RuleWithSummary(
                    id = "r2", name = "话费", type = TxType.EXPENSE, amountCents = 3_900,
                    categoryId = null, accountId = null, targetAccountId = null, note = null, feeCents = 0,
                    periodType = RecurringPeriod.MONTHLY, interval = 3,
                    startEpochDay = 20_000L, nextEpochDay = 20_010L,
                    endEpochDay = null, enabled = false,
                    categoryName = "通讯", accountName = "微信", targetAccountName = null,
                ),
            ),
            onBack = {}, onOpenRule = {}, onNewRule = {},
        )
    }
}
