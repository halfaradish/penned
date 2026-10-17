package dev.penned.app.ui.intelligence

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.PendingEntryEntity
import dev.penned.app.domain.Money
import dev.penned.app.domain.PendingKind
import dev.penned.app.domain.PendingStatus
import dev.penned.app.domain.PendingType
import dev.penned.app.domain.TxType
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedSnackbarHost
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.RowAction
import dev.penned.app.ui.components.SwipeActionRow
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.incomeColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 待确认列表的测试标记。 */
const val PENDING_LIST_TAG = "pending-list"

/** 「待确认」页的 VM（M4-8）：候选池的列表 / 丢弃 / 全部丢弃 / 清空已处理（**硬删**，D104）。 */
class PendingViewModel(private val app: PennedApplication) : ViewModel() {

    val pending: StateFlow<List<PendingEntryEntity>> =
        app.database.pendingEntryDao().observeByStatus(PendingStatus.PENDING)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun discard(id: String) {
        viewModelScope.launch { app.database.pendingEntryDao().discard(id, System.currentTimeMillis()) }
    }

    fun discardAll() {
        viewModelScope.launch { app.database.pendingEntryDao().discardAllPending(System.currentTimeMillis()) }
    }

    /** 「清空已处理」= **硬删**（候选池不是账目；全项目唯一一处硬删例外，D104）。 */
    fun clearProcessed(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val removed = app.database.pendingEntryDao().clearProcessed()
            onDone(removed)
        }
    }
}

/**
 * 「待确认」页（M4-8）：列表行 = 来源 App + 金额 + 方向 + 时间 + 原文一行（`maxLines = 1`、灰字）
 * + 右滑「丢弃」；点行 = 打开记一笔页预填（`source = NOTIFICATION`）；顶部「全部丢弃」；
 * 空态给"去开自动记账"的出路；**页脚一处「清空已处理」**（硬删）。
 */
@Composable
fun PendingScreen(
    onBack: () -> Unit,
    onOpenAutoEntry: () -> Unit,
    onPrefill: (entry: PendingEntryEntity) -> Unit,
) {
    val app = LocalApp.current
    val viewModel: PendingViewModel = viewModel(initializer = { PendingViewModel(app) })
    val rows by viewModel.pending.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var revealedRowId by rememberSaveable { mutableStateOf<String?>(null) }

    PendingContent(
        rows = rows,
        onBack = onBack,
        onOpenAutoEntry = onOpenAutoEntry,
        onPickRow = onPrefill,
        onDiscard = { viewModel.discard(it) },
        onDiscardAll = { viewModel.discardAll() },
        onClearProcessed = {
            viewModel.clearProcessed { removed ->
                viewModel.viewModelScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        "已清空 $removed 条",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.Dismissed) Unit
                }
            }
        },
        revealedRowId = revealedRowId,
        onRevealedRowChange = { revealedRowId = it },
        snackbarHostState = snackbarHostState,
    )
}

/** 无状态入口：截图与测试用。 */
@Composable
fun PendingContent(
    rows: List<PendingEntryEntity>,
    onBack: () -> Unit,
    onOpenAutoEntry: () -> Unit,
    onPickRow: (PendingEntryEntity) -> Unit = {},
    onDiscard: (String) -> Unit = {},
    onDiscardAll: () -> Unit = {},
    onClearProcessed: () -> Unit = {},
    revealedRowId: String? = null,
    onRevealedRowChange: (String?) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "待确认", onBack = onBack) },
        snackbarHost = { if (snackbarHostState != null) PennedSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (rows.isNotEmpty()) {
                Text(
                    "这些都是机器识别的草稿：点一条去确认（会先填好金额/方向），右滑可丢弃",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).testTag(PENDING_LIST_TAG),
                contentPadding = PaddingValues(bottom = PennedSpacing.lg),
            ) {
                if (rows.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = "没有待确认的",
                            hint = "开了自动记账后，识别到的每笔都会先落到这里（也可以从截图导入生成）",
                            icon = PennedIcons.Receipt,
                            action = { PennedButton(text = "去开自动记账", onClick = onOpenAutoEntry) },
                            modifier = Modifier
                                .padding(top = PennedSpacing.xxl)
                                .testTag(PENDING_EMPTY_TAG),
                        )
                    }
                } else {
                    item(key = "list") {
                        PennedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            rows.forEachIndexed { index, entry ->
                                if (index > 0) PennedDivider()
                                SwipeActionRow(
                                    isOpen = revealedRowId == entry.id,
                                    onOpenChange = { open -> onRevealedRowChange(if (open) entry.id else null) },
                                    actions = listOf(
                                        RowAction(
                                            label = "丢弃",
                                            container = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError,
                                            onClick = {
                                                onRevealedRowChange(null)
                                                onDiscard(entry.id)
                                            },
                                        )
                                    ),
                                ) {
                                    PendingRow(
                                        entry = entry,
                                        onClick = {
                                            if (revealedRowId == entry.id) {
                                                onRevealedRowChange(null)
                                            } else {
                                                onPickRow(entry)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "actions") {
                        Column(Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md)) {
                            PennedButton(
                                text = "全部丢弃",
                                variant = dev.penned.app.ui.components.PennedButtonVariant.Secondary,
                                onClick = onDiscardAll,
                            )
                        }
                    }
                }
            }
            // 页脚一处「清空已处理」（硬删，D104）
            Text(
                "清空已处理",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PennedDimens.touchTarget)
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md)
                    .androidClickableForPending(onClearProcessed),
            )
        }
    }
}

private fun Modifier.androidClickableForPending(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

/** 候选行：来源 App + 金额 + 方向 + 时间 + 原文一行（灰字）。 */
@Composable
private fun PendingRow(entry: PendingEntryEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .androidClickableForPending(onClick)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pendingSourceLabel(entry.sourceApp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(PennedSpacing.xs))
                Text(
                    entry.type.toLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    pendingTimeLabel(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val amount = entry.amountCents
            Text(
                amount?.let { Money.format(it) } ?: "金额没认出来",
                style = PennedTextStyles.rowTitle,
                color = when (entry.type) {
                    PendingType.INCOME -> incomeColor()
                    PendingType.EXPENSE -> expenseColor()
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            entry.rawText?.let { raw ->
                Text(
                    raw,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 候选行的时间（规格 M4-8：行 = 来源 + 金额 + 方向 + **时间** + 原文）。
 * 优先"识别到的时间"（通知到达时刻 / 截图里读到的时间），没有才退到入池时刻——**不猜**。
 */
internal fun pendingTimeLabel(
    entry: PendingEntryEntity,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val minute = entry.occurredAtEpochMinute ?: (entry.createdAt / 60_000L)
    val t = Instant.ofEpochSecond(minute * 60).atZone(zone)
    return "%02d-%02d %02d:%02d".format(t.monthValue, t.dayOfMonth, t.hour, t.minute)
}

private fun PendingType.toLabel(): String = when (this) {
    PendingType.EXPENSE -> "支出"
    PendingType.INCOME -> "收入"
    PendingType.TRANSFER -> "转账"
    PendingType.UNKNOWN -> "方向没认出来"
}

/** 候选 → 记一笔页的类型（未知按支出，用户可改）。 */
internal fun PendingEntryEntity.prefillType(): TxType =
    if (type == PendingType.INCOME) TxType.INCOME else TxType.EXPENSE

@Preview(showBackground = true, name = "待确认", widthDp = 400, heightDp = 800)
@Composable
private fun PendingPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        PendingContent(
            rows = listOf(
                PendingEntryEntity(
                    id = "p1", kind = PendingKind.NOTIFICATION, sourceApp = "com.tencent.mm",
                    rawText = "微信支付 12.50", type = PendingType.EXPENSE, amountCents = 1250,
                    createdAt = 1L, updatedAt = 1L, deviceId = "d",
                ),
                PendingEntryEntity(
                    id = "p2", kind = PendingKind.NOTIFICATION, sourceApp = "com.eg.android.AlipayGphone",
                    rawText = "支付宝 收款 88.00", type = PendingType.INCOME, amountCents = 8800,
                    createdAt = 2L, updatedAt = 2L, deviceId = "d",
                ),
            ),
            onBack = {}, onOpenAutoEntry = {},
        )
    }
}
