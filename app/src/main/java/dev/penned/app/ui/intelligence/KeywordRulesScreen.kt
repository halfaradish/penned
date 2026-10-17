package dev.penned.app.ui.intelligence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.KeywordRuleEntity
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.domain.KeywordRules
import dev.penned.app.domain.RuleTargetType
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.add.CategoryPanel
import dev.penned.app.ui.add.CategoryUi
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedNameField
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedSnackbarHost
import dev.penned.app.ui.components.PennedSwitchRow
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.RowAction
import dev.penned.app.ui.components.SwipeActionRow
import dev.penned.app.ui.components.rowDeleteAction
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedElevation
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** 新增规则抽屉里关键词输入框的测试标记（页面上还有别的输入件时钉准这一个）。 */
const val KEYWORD_FIELD_TAG = "keyword-field"

/** 规则列表容器的测试标记（滑动/滚动用）。 */
const val KEYWORD_RULES_LIST_TAG = "keyword-rules-list"

/** 规则列表里一行的展示投影：关键词 + 目标（分类给父级路径 / 标签给名字）。 */
data class KeywordRuleRow(
    val id: String,
    val keyword: String,
    val enabled: Boolean,
    val targetType: RuleTargetType,
    /** 目标名；null = 目标被删了（页面显示「目标已删除」，让用户自己重选）。 */
    val targetLabel: String?,
)

/** 规则页的整页状态（一次 combine 出来，UI 不自己拼）。 */
data class KeywordRulesUiState(
    val rows: List<KeywordRuleRow> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
)

/**
 * 分类关键词规则（M4-1）：关键词 → 分类 / 标签（双目标同一张表，D100）。
 *
 * 规则是**建议值**的源头：一句话（M4-3）、账单导入（M4-4）、截图（M4-7）、通知（M4-8）
 * 命中后都是"填进草稿"，用户随时可改；本页只管规则本身的增删启停与排序。
 */
class KeywordRulesViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<KeywordRulesUiState> = app.settings.currentBookId
        .flatMapLatest { bookId ->
            if (bookId == null) {
                flowOf(KeywordRulesUiState())
            } else {
                combine(
                    db.keywordRuleDao().observeByBook(bookId),
                    db.categoryDao().observeByBook(bookId),
                    db.tagDao().observeByBook(bookId),
                ) { rules, categories, tags ->
                    KeywordRulesUiState(
                        rows = rules.map { rule ->
                            KeywordRuleRow(
                                id = rule.id,
                                keyword = rule.keyword,
                                enabled = rule.enabled,
                                targetType = rule.targetType,
                                targetLabel = KeywordRules.targetName(rule.targetType, rule.targetId, categories, tags),
                            )
                        },
                        // 隐藏的分类/标签不作为规则目标（口径与记一笔的宫格一致：隐藏 = 不再用）
                        categories = categories.filter { !it.hidden },
                        tags = tags.filter { !it.hidden },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KeywordRulesUiState())

    /** 待撤销的软删 id（左滑删除后进 Snackbar「撤销」）。 */
    private val _deletedIds = MutableStateFlow<List<String>>(emptyList())
    val deletedIds: StateFlow<List<String>> = _deletedIds

    fun addRule(keyword: String, targetType: RuleTargetType, targetId: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return // 空关键词拒绝入库（规格 §三 M4-0）
        viewModelScope.launch {
            val bookId = app.settings.currentBookId.first() ?: return@launch
            val now = System.currentTimeMillis()
            db.keywordRuleDao().insertAtEnd(
                KeywordRuleEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    keyword = trimmed,
                    targetType = targetType,
                    targetId = targetId,
                    priority = 0, // insertAtEnd 在同一个事务里改成 MAX+1
                    enabled = true,
                    createdAt = now,
                    updatedAt = now,
                    deviceId = app.deviceId,
                )
            )
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { db.keywordRuleDao().setEnabled(id, enabled, System.currentTimeMillis()) }
    }

    /**
     * 上移一位：与上一条**交换优先级**（两条一起写，不会出现并列名次）。
     * 排序入口只有滑动动作区的「上移」（下移 = 上移它下面那条），与模板页同范式。
     */
    fun moveUp(id: String) {
        viewModelScope.launch {
            val rows = uiState.value.rows
            val index = rows.indexOfFirst { it.id == id }
            if (index <= 0) return@launch
            db.keywordRuleDao().swapPriority(rows[index].id, rows[index - 1].id, System.currentTimeMillis())
        }
    }

    fun delete(row: KeywordRuleRow) {
        viewModelScope.launch {
            db.keywordRuleDao().softDelete(row.id, System.currentTimeMillis())
            _deletedIds.value = _deletedIds.value + row.id
        }
    }

    fun undoDelete() {
        val ids = _deletedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            ids.forEach { id ->
                db.keywordRuleDao().get(id)?.let { db.keywordRuleDao().upsert(it.copy(deleted = false, updatedAt = now)) }
            }
            _deletedIds.value = emptyList()
        }
    }

    fun clearUndo() {
        _deletedIds.value = emptyList()
    }
}

@Composable
fun KeywordRulesScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val viewModel: KeywordRulesViewModel = viewModel(initializer = { KeywordRulesViewModel(app) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.deletedIds.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var revealedRowId: String? by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pendingUndo.size) {
        if (pendingUndo.isNotEmpty()) {
            val result = snackbarHostState.showSnackbar(
                message = "已删除 ${pendingUndo.size} 条规则",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearUndo()
        }
    }

    KeywordRulesContent(
        state = state,
        onBack = onBack,
        onAdd = { showAdd = true },
        onToggle = viewModel::setEnabled,
        onMoveUp = viewModel::moveUp,
        onDelete = viewModel::delete,
        revealedRowId = revealedRowId,
        onRevealedRowChange = { revealedRowId = it },
        snackbarHostState = snackbarHostState,
    )

    if (showAdd) {
        AddKeywordRuleSheet(
            categories = state.categories,
            tags = state.tags,
            onSave = { keyword, targetType, targetId ->
                viewModel.addRule(keyword, targetType, targetId)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

/** 无状态入口：截图测试 / 预览用（不碰 DB 与 DataStore）。 */
@Composable
fun KeywordRulesContent(
    state: KeywordRulesUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onToggle: (String, Boolean) -> Unit = { _, _ -> },
    onMoveUp: (String) -> Unit = {},
    onDelete: (KeywordRuleRow) -> Unit = {},
    revealedRowId: String? = null,
    onRevealedRowChange: (String?) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "分类关键词", onBack = onBack) },
        snackbarHost = { if (snackbarHostState != null) PennedSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
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
                Icon(PennedIcons.Plus, contentDescription = "新增规则")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(KEYWORD_RULES_LIST_TAG),
            contentPadding = PaddingValues(bottom = PennedDimens.listBottomClearance),
        ) {
            if (state.rows.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "还没有关键词规则",
                        hint = "写个关键词（如「肯打鸡」）指向分类或标签：一句话、账单导入、截图与通知" +
                            "识别到它时，会先把这笔账的分类填好——你确认后才入账",
                        icon = PennedIcons.Tag,
                        action = { PennedButton(text = "新增规则", onClick = onAdd) },
                        modifier = Modifier.padding(top = PennedSpacing.xxl),
                    )
                }
            } else {
                item(key = "hint") {
                    Text(
                        "从上往下最先命中：命中只填建议值，入账前你还能改",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = PennedSpacing.lg,
                            end = PennedSpacing.lg,
                            top = PennedSpacing.sm,
                            bottom = PennedSpacing.xs,
                        ),
                    )
                }
                item(key = "list") {
                    PennedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        state.rows.forEachIndexed { index, row ->
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
                                PennedSwitchRow(
                                    title = row.keyword,
                                    subtitle = subtitleOf(row),
                                    checked = row.enabled,
                                    onCheckedChange = { onToggle(row.id, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 规则行的副标题：「→ 分类：食品餐饮 / 午餐」/「→ 标签：出差」/「→ 目标已删除」。 */
internal fun subtitleOf(row: KeywordRuleRow): String = when {
    row.targetLabel == null -> "→ 目标已删除，删了重加一条吧"
    row.targetType == RuleTargetType.CATEGORY -> "→ 分类：${row.targetLabel}"
    else -> "→ 标签：${row.targetLabel}"
}

/**
 * 新增规则抽屉：关键词输入 + 目标二选一（分类 / 标签）+ 目标选择器。
 *
 * 目标选择器**复用记一笔的分类宫格**（`CategoryPanel`，去掉尾部「新增」格——这里只选已有分类）
 * 与标签 chip 组（同 `TagPickerSheet` 的形态）；两样都是单选。
 * 输入用 `PennedNameField`（BasicTextField）：文本输入弹窗在 Robolectric 里不 idle（看板已知坑）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddKeywordRuleSheet(
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    onSave: (keyword: String, targetType: RuleTargetType, targetId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    var targetType by remember { mutableStateOf(RuleTargetType.CATEGORY) }
    var targetId by remember { mutableStateOf<String?>(null) }
    var expandedParentId by remember { mutableStateOf<String?>(null) }

    val parents = remember(categories) {
        categories.filter { it.parentId == null }.sortedBy { it.sortOrder }
            .map { CategoryUi(it.id, it.name, categories.any { c -> c.parentId == it.id }) }
    }
    val subsOf = { parentId: String ->
        categories.filter { it.parentId == parentId }.sortedBy { it.sortOrder }
            .map { CategoryUi(it.id, it.name, false) }
    }

    PennedSheet(onDismiss = onDismiss, title = "新增关键词规则") {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PennedSpacing.xl),
        ) {
            PennedNameField(
                name = keyword,
                placeholder = "关键词（如：肯打鸡）",
                onNameChange = { keyword = it },
                modifier = Modifier.testTag(KEYWORD_FIELD_TAG),
            )
            Spacer(Modifier.height(PennedSpacing.md))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm)) {
                PennedChip(
                    label = "分类",
                    selected = targetType == RuleTargetType.CATEGORY,
                    onClick = {
                        targetType = RuleTargetType.CATEGORY
                        targetId = null
                    },
                )
                PennedChip(
                    label = "标签",
                    selected = targetType == RuleTargetType.TAG,
                    onClick = {
                        targetType = RuleTargetType.TAG
                        targetId = null
                    },
                )
            }
            Spacer(Modifier.height(PennedSpacing.sm))

            if (targetType == RuleTargetType.CATEGORY) {
                if (parents.isEmpty()) {
                    Text(
                        "还没有分类——先去「分类管理」建一个",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    CategoryPanel(
                        parents = parents,
                        selectedId = targetId,
                        expandedId = expandedParentId,
                        expandedSubs = expandedParentId?.let(subsOf) ?: emptyList(),
                        // 一级分类：有子分类就顺带展开（与记一笔同一手感），点二级 = 选二级
                        onSelectParent = { cat ->
                            targetId = cat.id
                            expandedParentId = if (cat.hasSubs) cat.id else null
                        },
                        onSelectSub = { sub -> targetId = sub.id },
                        showAddCell = false,
                    )
                }
            } else {
                if (tags.isEmpty()) {
                    Text(
                        "还没有标签——先去「标签管理」建一个",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val tagParents = tags.filter { it.parentId == null }.sortedBy { it.sortOrder }
                    Column(verticalArrangement = Arrangement.spacedBy(PennedSpacing.xs)) {
                        tagParents.forEach { parent ->
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(PennedSpacing.xs),
                            ) {
                                PennedChip(
                                    label = parent.name,
                                    selected = targetId == parent.id,
                                    onClick = { targetId = parent.id },
                                )
                                tags.filter { it.parentId == parent.id }.sortedBy { it.sortOrder }.forEach { child ->
                                    PennedChip(
                                        label = child.name,
                                        selected = targetId == child.id,
                                        onClick = { targetId = child.id },
                                    )
                                }
                            }
                        }
                        // 父标签被隐藏/删掉、只剩二级的：单独一行照样能选（不能让规则目标悬空）
                        val orphans = tags.filter { it.parentId != null && tags.none { p -> p.id == it.parentId } }
                        if (orphans.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm)) {
                                orphans.forEach { child ->
                                    PennedChip(
                                        label = child.name,
                                        selected = targetId == child.id,
                                        onClick = { targetId = child.id },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(PennedSpacing.lg))
            PennedButton(
                text = "保存规则",
                enabled = keyword.isNotBlank() && targetId != null,
                onClick = { targetId?.let { onSave(keyword, targetType, it) } },
            )
        }
    }
}

@Preview(showBackground = true, name = "分类关键词", widthDp = 400, heightDp = 800)
@Composable
private fun KeywordRulesPreview() {
    PennedTheme {
        KeywordRulesContent(
            state = KeywordRulesUiState(
                rows = listOf(
                    KeywordRuleRow("k1", "肯打鸡", true, RuleTargetType.CATEGORY, "食品餐饮"),
                    KeywordRuleRow("k2", "午饭", true, RuleTargetType.CATEGORY, "食品餐饮 / 午餐"),
                    KeywordRuleRow("k3", "租房", false, RuleTargetType.TAG, "出差"),
                ),
            ),
            onBack = {}, onAdd = {},
        )
    }
}
