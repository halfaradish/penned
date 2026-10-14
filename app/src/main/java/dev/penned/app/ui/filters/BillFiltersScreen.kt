package dev.penned.app.ui.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.penned.app.data.db.entity.BillFilterEntity
import dev.penned.app.domain.BillFilterCodec
import dev.penned.app.domain.TxFilters
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedSnackbarHost
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.RowAction
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 账单组合列表行：组合 + 条件摘要（名称映射由 VM 查好）。 */
data class BillFilterRow(val entity: BillFilterEntity, val summary: String)

/**
 * 账单组合（M3-8）：把筛选条件存成组合，下次一键应用。
 * 点行 = 进筛选页并应用（条件回填、结果即出）；左滑 = 重命名 / 删除（软删，不碰任何流水）。
 */
class BillFiltersViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentBookId = app.settings.currentBookId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val entities: StateFlow<List<BillFilterEntity>> = app.settings.currentBookId
        .flatMapLatest { bookId ->
            if (bookId == null) flowOf(emptyList()) else db.billFilterDao().observeByBook(bookId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 摘要要分类/账户名（组合里存的是 id）——名称映射从库里取。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val categories = currentBookId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else db.categoryDao().observeByBook(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val accounts = currentBookId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else db.accountDao().observeByBook(id)
    }

    val rows: StateFlow<List<BillFilterRow>> = combine(
        entities,
        categories,
        accounts,
    ) { list, categories, accounts ->
        val categoryNames = categories.associate { it.id to it.name }
        val accountNames = accounts.associate { it.id to it.name }
        list.map { entity ->
            BillFilterRow(
                entity = entity,
                summary = TxFilters.summary(BillFilterCodec.toFilter(entity), categoryNames, accountNames)
                    .ifEmpty { "没有任何条件" },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _deletedIds = MutableStateFlow<List<String>>(emptyList())
    val deletedIds: StateFlow<List<String>> = _deletedIds

    fun rename(row: BillFilterRow, name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                onResult("名字不能为空")
                return@launch
            }
            // 用行自己的 bookId（不依赖 settings 的异步读——重命名只关心这一行属于哪个账本）
            if (db.billFilterDao().nameTaken(row.entity.bookId, trimmed, row.entity.id) > 0) {
                onResult("已经有一个叫「$trimmed」的组合了")
                return@launch
            }
            db.billFilterDao().rename(row.entity.id, trimmed, System.currentTimeMillis())
            onResult(null)
        }
    }

    fun delete(row: BillFilterRow) {
        viewModelScope.launch {
            db.billFilterDao().softDelete(row.entity.id, System.currentTimeMillis())
            _deletedIds.value = _deletedIds.value + row.entity.id
        }
    }

    fun undoDelete() {
        val ids = _deletedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            ids.forEach { id ->
                db.billFilterDao().get(id)?.let { db.billFilterDao().upsert(it.copy(deleted = false, updatedAt = now)) }
            }
            _deletedIds.value = emptyList()
        }
    }

    fun clearUndo() {
        _deletedIds.value = emptyList()
    }
}

@Composable
fun BillFiltersScreen(
    onBack: () -> Unit,
    onApply: (String) -> Unit,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: BillFiltersViewModel = viewModel(initializer = { BillFiltersViewModel(app) })
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.deletedIds.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var revealedRowId: String? by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<BillFilterRow?>(null) }

    LaunchedEffect(pendingUndo.size) {
        if (pendingUndo.isNotEmpty()) {
            val result = snackbarHostState.showSnackbar(
                message = "已删除 ${pendingUndo.size} 个组合",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearUndo()
        }
    }

    BillFiltersContent(
        rows = rows,
        onBack = onBack,
        onApply = onApply,
        onRename = { renaming = it },
        onDelete = viewModel::delete,
        revealedRowId = revealedRowId,
        onRevealedRowChange = { revealedRowId = it },
        snackbarHostState = snackbarHostState,
    )

    renaming?.let { row ->
        RenameSheet(
            initial = row.entity.name,
            onConfirm = { name, report ->
                viewModel.rename(row, name) { error ->
                    if (error == null) renaming = null else report(error)
                }
            },
            onDismiss = { renaming = null },
        )
    }
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@Composable
fun BillFiltersContent(
    rows: List<BillFilterRow>,
    onBack: () -> Unit,
    onApply: (String) -> Unit,
    onRename: (BillFilterRow) -> Unit = {},
    onDelete: (BillFilterRow) -> Unit = {},
    revealedRowId: String? = null,
    onRevealedRowChange: (String?) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    /** 空态的出路：去筛选页建条件。 */
    onNewFilter: () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "账单组合", onBack = onBack) },
        snackbarHost = { if (snackbarHostState != null) PennedSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewFilter,
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
                Icon(PennedIcons.Plus, contentDescription = "新建组合")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = PennedDimens.listBottomClearance),
        ) {
            if (rows.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "还没有账单组合",
                        hint = "在筛选页选好条件后点右上「存为组合」，下次一键应用",
                        icon = PennedIcons.Search,
                        action = { PennedButton(text = "去筛选", onClick = onNewFilter) },
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
                        rows.forEachIndexed { index, row ->
                            if (index > 0) PennedDivider()
                            SwipeActionRow(
                                isOpen = revealedRowId == row.entity.id,
                                onOpenChange = { open -> onRevealedRowChange(if (open) row.entity.id else null) },
                                actions = listOf(
                                    RowAction(
                                        label = "重命名",
                                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        onClick = {
                                            onRevealedRowChange(null)
                                            onRename(row)
                                        },
                                    ),
                                    rowDeleteAction {
                                        onRevealedRowChange(null)
                                        onDelete(row)
                                    },
                                ),
                            ) {
                                PennedListRow(
                                    title = row.entity.name,
                                    subtitle = row.summary,
                                    onClick = {
                                        if (revealedRowId == row.entity.id) {
                                            onRevealedRowChange(null)
                                        } else {
                                            onApply(row.entity.id)
                                        }
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

@Preview(showBackground = true, name = "账单组合", widthDp = 400, heightDp = 800)
@Composable
private fun BillFiltersPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        BillFiltersContent(
            rows = listOf(
                BillFilterRow(
                    entity = BillFilterEntity(
                        id = "f1", bookId = "b1", name = "上月餐饮",
                        type = TxType.EXPENSE, categoryIds = "c1,c2",
                        createdAt = 0, updatedAt = 0, deviceId = "d",
                    ),
                    summary = "支出 · 食品餐饮、购物消费 · 近 30 天",
                ),
                BillFilterRow(
                    entity = BillFilterEntity(
                        id = "f2", bookId = "b1", name = "待报销",
                        tagIds = "g1", excludeFromStats = true,
                        createdAt = 0, updatedAt = 0, deviceId = "d",
                    ),
                    summary = "1 个标签 · 不计收支",
                ),
            ),
            onBack = {}, onApply = {},
        )
    }
}

/**
 * 重命名组合：输入名 + 重名/空名提示（错误由 VM 回传，就地显示）。
 * 与「存为组合」同一个形态（`PennedSheet`）——AlertDialog 里的输入在 Robolectric 下会卡 idle（M3-8 实测）。
 */
@Composable
private fun RenameSheet(
    initial: String,
    onConfirm: (String, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    dev.penned.app.ui.components.PennedSheet(onDismiss = onDismiss, title = "重命名组合") {
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PennedSpacing.xl)
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
                .padding(PennedSpacing.md),
        )
        error?.let {
            androidx.compose.material3.Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = PennedSpacing.xl, vertical = PennedSpacing.xs),
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(PennedSpacing.sm))
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.xl),
        ) {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("取消")
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            androidx.compose.material3.TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text) { message -> error = message } },
            ) {
                androidx.compose.material3.Text("保存")
            }
        }
    }
}
