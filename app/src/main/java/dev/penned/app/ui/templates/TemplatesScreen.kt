package dev.penned.app.ui.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import dev.penned.app.data.db.dao.TemplateWithTags
import dev.penned.app.data.db.entity.TxTemplateEntity
import dev.penned.app.domain.Templates
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedSnackbarHost
import dev.penned.app.ui.theme.PennedTextStyles
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 模板记账（M3-5）：列表 + 上移（sortOrder 手动排序，不做拖拽）+ 左滑删除（可撤销）。
 * 点行进编辑页；点右下 FAB 新建。
 */
class TemplatesViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    @OptIn(ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<TemplateWithTags>> = app.settings.currentBookId
        .flatMapLatest { bookId ->
            if (bookId == null) flowOf(emptyList()) else db.templateDao().observeByBook(bookId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 待撤销的软删 id（左滑删除后进 Snackbar「撤销」）。 */
    private val _deletedIds = MutableStateFlow<List<String>>(emptyList())
    val deletedIds: StateFlow<List<String>> = _deletedIds

    /**
     * 上移一位：与上一条**交换 sortOrder**（两条一起写，不会出现并列名次）。
     * 规格明确不做拖拽，所以排序入口只有这一个（下移 = 上移它下面那条）。
     */
    fun moveUp(id: String) {
        viewModelScope.launch {
            val current = templates.value
            val index = current.indexOfFirst { it.id == id }
            if (index <= 0) return@launch
            val moved = current[index]
            val above = current[index - 1]
            val now = System.currentTimeMillis()
            db.templateDao().get(moved.id)?.let { db.templateDao().upsert(it.copy(sortOrder = above.sortOrder, updatedAt = now)) }
            db.templateDao().get(above.id)?.let { db.templateDao().upsert(it.copy(sortOrder = moved.sortOrder, updatedAt = now)) }
        }
    }

    fun delete(row: TemplateWithTags) {
        viewModelScope.launch {
            db.templateDao().deleteTemplate(row.id, System.currentTimeMillis())
            _deletedIds.value = _deletedIds.value + row.id
        }
    }

    /** 撤销删除：软删的模板原样恢复（软删**不摘标签关系**，所以标签跟着一起回来）。 */
    fun undoDelete() {
        val ids = _deletedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            ids.forEach { id ->
                db.templateDao().get(id)?.let { db.templateDao().upsert(it.copy(deleted = false, updatedAt = now)) }
            }
            _deletedIds.value = emptyList()
        }
    }

    fun clearUndo() {
        _deletedIds.value = emptyList()
    }
}

@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onOpenTemplate: (String) -> Unit,
    onNewTemplate: () -> Unit,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: TemplatesViewModel = viewModel(initializer = { TemplatesViewModel(app) })
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.deletedIds.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var revealedRowId: String? by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingUndo.size) {
        if (pendingUndo.isNotEmpty()) {
            val result = snackbarHostState.showSnackbar(
                message = "已删除 ${pendingUndo.size} 个模板",
                actionLabel = "撤销",
                // 带动作的 Snackbar 按 M3 给足 10s（与首页/资产页同范式）
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearUndo()
        }
    }

    TemplatesContent(
        templates = templates,
        onBack = onBack,
        onOpenTemplate = onOpenTemplate,
        onNewTemplate = onNewTemplate,
        onMoveUp = viewModel::moveUp,
        onDelete = viewModel::delete,
        revealedRowId = revealedRowId,
        onRevealedRowChange = { revealedRowId = it },
        snackbarHostState = snackbarHostState,
    )
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@Composable
fun TemplatesContent(
    templates: List<TemplateWithTags>,
    onBack: () -> Unit,
    onOpenTemplate: (String) -> Unit,
    onNewTemplate: () -> Unit,
    onMoveUp: (String) -> Unit = {},
    onDelete: (TemplateWithTags) -> Unit = {},
    revealedRowId: String? = null,
    onRevealedRowChange: (String?) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "模板记账", onBack = onBack) },
        snackbarHost = { if (snackbarHostState != null) PennedSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewTemplate,
                modifier = Modifier.size(PennedDimens.fab),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                // 零阴影：浮层靠填充色分层（D043）
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = PennedElevation.floating,
                    pressedElevation = PennedElevation.floating,
                    focusedElevation = PennedElevation.floating,
                    hoveredElevation = PennedElevation.floating,
                ),
            ) {
                Icon(PennedIcons.Plus, contentDescription = "新建模板")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = PennedDimens.listBottomClearance),
        ) {
            if (templates.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "还没有模板",
                        hint = "把常记的那几笔存成模板：用模板 = 进记一笔预填，确认后才入库",
                        icon = PennedIcons.Receipt,
                        // D044 六态：空态给出路（与债务列表同一个做法）
                        action = { PennedButton(text = "新建模板", onClick = onNewTemplate) },
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
                        templates.forEachIndexed { index, row ->
                            if (index > 0) PennedDivider()
                            SwipeActionRow(
                                isOpen = revealedRowId == row.id,
                                onOpenChange = { open -> onRevealedRowChange(if (open) row.id else null) },
                                // 排序用手动「上移」（规格：不做拖拽）；第一条没有上移
                                actions = listOfNotNull(
                                    if (index > 0) {
                                        RowAction(
                                            label = "上移",
                                            container = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                            onClick = {
                                                onRevealedRowChange(null)
                                                onMoveUp(row.id)
                                            },
                                        )
                                    } else {
                                        null
                                    },
                                    rowDeleteAction {
                                        onRevealedRowChange(null)
                                        onDelete(row)
                                    },
                                ),
                            ) {
                                TemplateRow(
                                    row = row,
                                    onClick = {
                                        // 展开态点行 = 收起动作区，不进编辑页（QQ 口径）
                                        if (revealedRowId == row.id) {
                                            onRevealedRowChange(null)
                                        } else {
                                            onOpenTemplate(row.id)
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

/** 模板行：头像（名字首字）+ 名称 + 摘要（类型 · 金额 · 分类 · 账户 [+ 标签]）+ 箭头。 */
@Composable
private fun TemplateRow(row: TemplateWithTags, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(row.name, size = PennedDimens.avatar)
        Spacer(Modifier.width(PennedSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(row.name, style = PennedTextStyles.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(Templates.summaryText(row), row.tagNames).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(PennedSpacing.sm))
        Icon(
            PennedIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(PennedDimens.iconChevron),
        )
    }
}

@Preview(showBackground = true, name = "模板记账", widthDp = 400, heightDp = 800)
@Composable
private fun TemplatesPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        TemplatesContent(
            templates = listOf(
                TemplateWithTags(
                    id = "t1", name = "午饭", type = TxType.EXPENSE, amountCents = 1650,
                    categoryId = null, accountId = null, targetAccountId = null, note = null,
                    feeCents = 0, sortOrder = 0, categoryName = "食品餐饮", accountName = "微信",
                    targetAccountName = null, tagNames = "报销中",
                ),
                TemplateWithTags(
                    id = "t2", name = "还信用卡", type = TxType.TRANSFER, amountCents = 100_000,
                    categoryId = null, accountId = null, targetAccountId = null, note = null,
                    feeCents = 0, sortOrder = 1, categoryName = null, accountName = "招行信用卡",
                    targetAccountName = "储蓄卡", tagNames = null,
                ),
            ),
            onBack = {}, onOpenTemplate = {}, onNewTemplate = {},
        )
    }
}

/**
 * 模板抽屉（M3-5，记一笔的「模板」chip 与列表页共用一份数据形态）：
 * 点一行 = 把字段预填进记一笔（**不直接入库**）；末尾给「管理模板」出口。
 */
@Composable
fun TemplatePickerSheet(
    templates: List<TemplateWithTags>,
    onPick: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "选择模板") {
        if (templates.isEmpty()) {
            Text(
                "还没有模板——下面「管理模板」去建一个",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PennedSpacing.xl, vertical = PennedSpacing.sm),
            )
        }
        templates.forEach { row ->
            PennedListRow(
                title = row.name,
                subtitle = listOfNotNull(Templates.summaryText(row), row.tagNames).joinToString(" · "),
                showChevron = false,
                onClick = { onPick(row.id) },
            )
        }
        PennedListRow(
            title = "管理模板",
            subtitle = "新建 / 编辑 / 排序 / 删除",
            leadingIcon = PennedIcons.Edit,
            onClick = onManage,
        )
    }
}
