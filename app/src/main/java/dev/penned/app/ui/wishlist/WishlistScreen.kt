package dev.penned.app.ui.wishlist

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.WishlistRow
import dev.penned.app.data.db.dao.toProgress
import dev.penned.app.domain.Money
import dev.penned.app.domain.WishlistMath
import dev.penned.app.domain.WishlistProgress
import dev.penned.app.domain.WishlistStatus
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.components.PennedProgressBar
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedSnackbarHost
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.SwipeActionRow
import dev.penned.app.ui.components.rowDeleteAction
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedElevation
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.primaryInkColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 列表行的进度条 tag（像素回归用）。 */
internal const val WISHLIST_BAR_TAG = "wishlist-bar"

/**
 * 愿望清单（M3-15，D072 = 攒钱目标）：卡片列表 + 新建/编辑 + 「存入一笔」。
 * **不生成流水**：它是规划工具，不进统计与净资产（规格的明确边界）。
 */
class WishlistViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<WishlistProgress>> = app.settings.currentBookId
        .flatMapLatest { bookId ->
            if (bookId == null) flowOf(emptyList()) else db.wishlistDao().observeByBook(bookId)
        }
        .map { list -> list.map { it.toProgress() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 左滑删除后待撤销的 id（10s 窗口，与全 App 一致）。 */
    private val _deletedIds = MutableStateFlow<List<String>>(emptyList())
    val deletedIds: StateFlow<List<String>> = _deletedIds

    fun delete(item: WishlistProgress) {
        viewModelScope.launch {
            db.wishlistDao().softDelete(item.id, System.currentTimeMillis())
            _deletedIds.value = _deletedIds.value + item.id
        }
    }

    fun undoDelete() {
        val ids = _deletedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            db.wishlistDao().restoreAll(ids, System.currentTimeMillis())
            _deletedIds.value = emptyList()
        }
    }

    fun clearUndo() {
        _deletedIds.value = emptyList()
    }

    /** 手动存入（正）/ 取出（负）：只动 `savedCents`，**不写流水**。 */
    fun addSaved(item: WishlistProgress, deltaCents: Long) {
        viewModelScope.launch {
            db.wishlistDao().addSaved(item.id, deltaCents, System.currentTimeMillis())
        }
    }
}

@Composable
fun WishlistScreen(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onNewItem: () -> Unit,
) {
    val app = LocalApp.current
    val viewModel: WishlistViewModel = viewModel(initializer = { WishlistViewModel(app) })
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.deletedIds.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var revealedRowId by remember { mutableStateOf<String?>(null) }
    var depositFor by remember { mutableStateOf<WishlistProgress?>(null) }

    LaunchedEffect(pendingUndo.size) {
        if (pendingUndo.isNotEmpty()) {
            val result = snackbarHostState.showSnackbar(
                message = "已删除 ${pendingUndo.size} 个目标",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearUndo()
        }
    }

    WishlistContent(
        rows = rows,
        onBack = onBack,
        onOpenItem = onOpenItem,
        onNewItem = onNewItem,
        onDelete = viewModel::delete,
        onDeposit = { depositFor = it },
        revealedRowId = revealedRowId,
        onRevealedRowChange = { revealedRowId = it },
        snackbarHostState = snackbarHostState,
    )

    depositFor?.let { item ->
        DepositSheet(
            item = item,
            onConfirm = { delta ->
                viewModel.addSaved(item, delta)
                depositFor = null
            },
            onDismiss = { depositFor = null },
        )
    }
}

/** 无状态入口：截图测试/预览用。 */
@Composable
fun WishlistContent(
    rows: List<WishlistProgress>,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit = {},
    onNewItem: () -> Unit = {},
    onDelete: (WishlistProgress) -> Unit = {},
    onDeposit: (WishlistProgress) -> Unit = {},
    revealedRowId: String? = null,
    onRevealedRowChange: (String?) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    today: LocalDate = LocalDate.now(),
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { if (snackbarHostState != null) PennedSnackbarHost(snackbarHostState) },
        topBar = { PennedTopBar(title = "愿望清单", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewItem,
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
                Icon(PennedIcons.Plus, contentDescription = "新建目标")
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "还没有攒钱目标",
                    hint = "点右下角 + 定一个（比如「换电脑」攒 8,000）",
                    icon = PennedIcons.Wallet,
                    modifier = Modifier.padding(top = PennedSpacing.xxl),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                top = PennedSpacing.md,
                bottom = PennedDimens.listBottomClearance,
            ),
        ) {
            items(rows, key = { it.id }) { row ->
                SwipeActionRow(
                    isOpen = revealedRowId == row.id,
                    onOpenChange = { open -> onRevealedRowChange(if (open) row.id else null) },
                    actions = listOf(
                        rowDeleteAction {
                            onRevealedRowChange(null)
                            onDelete(row)
                        },
                    ),
                ) {
                    WishlistCard(
                        row = row,
                        today = today,
                        onClick = {
                            if (revealedRowId == row.id) onRevealedRowChange(null) else onOpenItem(row.id)
                        },
                        onDeposit = { onDeposit(row) },
                    )
                }
            }
        }
    }
}

/** 一张目标卡：名称 + 达成标记 + 进度条 + 两数 + 状态文案 + 「存入一笔」。 */
@Composable
private fun WishlistCard(
    row: WishlistProgress,
    today: LocalDate,
    onClick: () -> Unit,
    onDeposit: () -> Unit,
) {
    PennedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm)
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.name,
                style = PennedTextStyles.cardTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (row.achieved) {
                PennedChip(label = "已达成", selected = true)
            }
        }
        if (row.accountName != null) {
            Text(
                "跟随「${row.accountName}」的余额",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(PennedSpacing.md))
        PennedProgressBar(
            progress = row.progress,
            color = if (row.achieved) primaryInkColor() else MaterialTheme.colorScheme.primary,
            testTag = WISHLIST_BAR_TAG,
        )
        Spacer(Modifier.height(PennedSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                WishlistMath.amountsLabel(row.savedCents, row.targetCents),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                WishlistMath.statusLabel(row.savedCents, row.targetCents, row.status, row.dueEpochDay, today),
                style = MaterialTheme.typography.bodySmall,
                color = if (row.achieved) primaryInkColor() else MaterialTheme.colorScheme.onSurface,
            )
        }
        // 跟随账户时"存入"由账户余额说了算，手动存一笔没有意义 → 按钮换成一句说明
        Spacer(Modifier.height(PennedSpacing.sm))
        if (row.accountName == null) {
            PennedButton(
                text = "存入一笔",
                onClick = onDeposit,
                variant = dev.penned.app.ui.components.PennedButtonVariant.Secondary,
            )
        } else {
            Text(
                "存入/取出记在账户上（转账或收支），这里跟着余额走",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 存入抽屉（M3-15）：金额 + 存入/取出两格。
 * **不写流水**——攒钱目标是规划工具（规格 M3-15 的明确边界），这里只动 `savedCents`。
 */
@Composable
internal fun DepositSheet(
    item: WishlistProgress,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var withdraw by remember { mutableStateOf(false) }
    val cents = Money.parseToCents(text) ?: 0L

    PennedSheet(onDismiss = onDismiss, title = "存入一笔 · ${item.name}") {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.xl, vertical = PennedSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
        ) {
            PennedChip(label = "存入", selected = !withdraw, onClick = { withdraw = false })
            PennedChip(label = "取出", selected = withdraw, onClick = { withdraw = true })
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.xl, vertical = PennedSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("金额", style = PennedTextStyles.rowTitle, modifier = Modifier.width(PennedDimens.formLabel))
            BasicTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() || it == '.' } },
                singleLine = true,
                textStyle = PennedTextStyles.rowTitle.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text("0.00", style = PennedTextStyles.rowTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
        }
        Text(
            "只更新这条目标的「已存」，不会生成流水（攒钱目标是规划，不进统计与净资产）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = PennedSpacing.xl),
        )
        Spacer(Modifier.height(PennedSpacing.sm))
        Row(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.xl)) {
            TextButton(onClick = onDismiss) { Text("取消") }
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = cents > 0L,
                onClick = { onConfirm(if (withdraw) -cents else cents) },
            ) { Text(if (withdraw) "取出" else "存入") }
        }
    }
}
