package dev.penned.app.ui.intelligence

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.KeywordRuleEntity
import dev.penned.app.domain.AddDate
import dev.penned.app.domain.KeywordRules
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.RuleTargetType
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import dev.penned.app.ui.add.CategoryPanel
import dev.penned.app.ui.add.CategoryUi
import dev.penned.app.ui.add.TxEdit
import dev.penned.app.ui.components.AddDateTimeDialog
import dev.penned.app.ui.components.MoneyText
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedNameField
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** 一句话输入框与结果卡五行的测试标记。 */
const val QUICK_TEXT_FIELD_TAG = "quick-text-field"
const val QUICK_ROW_TYPE = "quick-row-type"
const val QUICK_ROW_AMOUNT = "quick-row-amount"
const val QUICK_ROW_CATEGORY = "quick-row-category"
const val QUICK_ROW_DATE = "quick-row-date"
const val QUICK_ROW_NOTE = "quick-row-note"

/** 一句话页里「选择分类」抽屉的容器标记（测试要能把它跟卡片上的分类行分开）。 */
const val QUICK_CATEGORY_SHEET_TAG = "quick-category-sheet"

/**
 * 一句话记账（M4-3）：本地解析器把一句话拆成草稿 → **结果卡五行每行可改** → 保存才入账。
 *
 * 口径（规格 §三 M4-3）：
 * - 分类交给关键词规则（M4-1）——命中给建议值并标「来自关键词规则」；**没命中就留空，不猜**
 * - 保存 = `source = TEXT`、**账户留空**（无账户记账是既有合法口径）；保存后回首页并给「已记账 + 撤销」
 * - **AI 兜底只在用户点「AI 解析」时调**（不自动花用户的钱）；本片先把本地链路做完，
 *   「AI 解析」按钮按规矩只给"未配置 → 去 AI 设置"的引导（真正的调用在规格里归 M4-3 的 LLM 兜底）
 */
class QuickEntryViewModel(private val app: PennedApplication) : ViewModel() {

    private val _outcome = MutableStateFlow<QuickParseOutcome?>(null)
    val outcome: StateFlow<QuickParseOutcome?> = _outcome

    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories

    /**
     * 解析 + 关键词规则映射。
     *
     * **一律在 `viewModelScope` 里跑**：Room 的挂起查询回来时，若续体跑在查询线程上，
     * 直接写 Compose 状态 / 调导航会炸（"must be called on the main thread"，流程测试实测踩到）；
     * `viewModelScope` = Main.immediate，`withContext` 会把续体带回主线程。
     */
    fun parse(text: String) {
        viewModelScope.launch {
            val result = dev.penned.app.domain.QuickParse.parse(text, LocalDate.now())
            if (result !is dev.penned.app.domain.QuickParseResult.Ok) {
                _outcome.value = QuickParseOutcome(result, null, false)
                return@launch
            }
            val bookId = app.settings.currentBookId.first()
            val rules: List<KeywordRuleEntity> =
                bookId?.let { app.database.keywordRuleDao().allInBook(it) } ?: emptyList()
            val hit = KeywordRules.match(result.draft.note ?: text, rules)
            val categoryId = hit?.takeIf { it.targetType == RuleTargetType.CATEGORY }?.targetId
            _outcome.value = QuickParseOutcome(result, categoryId, categoryId != null)
            _categories.value = bookId?.let { id ->
                app.database.categoryDao().allInBook(id).filter { !it.hidden }
            } ?: emptyList()
        }
    }

    /** 保存一句话记的这笔账：`source = TEXT`、账户留空；[onSaved] 在主线程上带着新流水 id 回来。 */
    fun save(
        type: TxType,
        amountCents: Long,
        categoryId: String?,
        day: Long?,
        time: LocalTime?,
        note: String?,
        onSaved: (String) -> Unit,
    ) {
        viewModelScope.launch {
        val bookId = app.settings.currentBookId.first() ?: return@launch
        val now = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault()
        val today = LocalDate.now()
        val targetDay = day ?: today.toEpochDay()
        val targetTime = time ?: LocalTime.now()
        val minute = LocalDate.ofEpochDay(targetDay).atTime(targetTime)
            .atZone(zone).toEpochSecond() / 60
        val entity = TxEdit.newTransaction(
            bookId = bookId,
            editable = TxEdit.Editable(
                type = type,
                amountCents = amountCents,
                accountId = null, // 一句话记账不指定账户（D005：纯现金无账户是合法口径）
                targetAccountId = null,
                categoryId = categoryId,
                dateEpochMinute = minute,
                note = note?.trim()?.ifEmpty { null },
                feeCents = 0,
                refundCents = 0,
                discountCents = 0,
                reimburseState = ReimburseState.NONE,
                reimburseAccountId = null,
                reimburseCents = 0,
                debtId = null,
            ),
            now = now,
            deviceId = app.deviceId,
            source = TxSource.TEXT,
        )
        app.database.transactionDao().upsert(entity)
        onSaved(entity.id)
        }
    }
}

/** 解析结果 + 关键词规则的建议分类。 */
data class QuickParseOutcome(
    val result: dev.penned.app.domain.QuickParseResult,
    val categoryId: String?,
    val fromRule: Boolean,
)

@Composable
fun QuickEntryScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: QuickEntryViewModel = viewModel(initializer = { QuickEntryViewModel(app) })
    val clipboard = LocalClipboardManager.current
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var text by rememberSaveable { mutableStateOf("") }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var amountCents by remember { mutableStateOf(0L) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var categoryFromRule by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf<Long?>(null) }
    var dayWord by remember { mutableStateOf<String?>(null) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // 解析结果一到，把可编辑草稿按它初始化（之后各行由用户改）
    LaunchedEffect(outcome) {
        val parsed = outcome ?: return@LaunchedEffect
        val draft = (parsed.result as? dev.penned.app.domain.QuickParseResult.Ok)?.draft ?: return@LaunchedEffect
        type = draft.type
        amountCents = draft.amountCents
        categoryId = parsed.categoryId
        categoryFromRule = parsed.fromRule
        day = draft.epochDay
        dayWord = draft.dayWord
        time = null
        note = draft.note
    }

    QuickEntryContent(
        text = text,
        outcome = outcome,
        type = type,
        amountCents = amountCents,
        categoryName = categories.firstOrNull { it.id == categoryId }?.name,
        categoryFromRule = categoryFromRule,
        // 与记一笔的「日期」chip 同一写法（D097）：今天带「今天」前缀、调过时分才带 HH:mm
        dayLabel = AddDate.chipLabel(day, LocalDate.now(), time),
        note = note,
        saving = saving,
        onTextChange = { text = it },
        onPaste = { clipboard.getText()?.text?.let { text = it } },
        onParse = { viewModel.parse(text) },
        onToggleType = { type = if (type == TxType.EXPENSE) TxType.INCOME else TxType.EXPENSE },
        onPickCategory = { showCategoryPicker = true },
        onPickDate = { showDatePicker = true },
        onEditNote = { showNoteEditor = true },
        onSave = {
            saving = true
            viewModel.save(type, amountCents, categoryId, day, time, note) { id ->
                saving = false
                onSaved(id)
            }
        },
        onBack = onBack,
    )

    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = categories,
            selectedId = categoryId,
            onPick = { picked ->
                categoryId = picked
                categoryFromRule = false // 用户自己选的，不再是规则建议
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }
    if (showNoteEditor) {
        NoteEditorSheet(
            note = note.orEmpty(),
            onConfirm = { value ->
                note = value.trim().ifEmpty { null }
                showNoteEditor = false
            },
            onDismiss = { showNoteEditor = false },
        )
    }
    if (showDatePicker) {
        AddDateTimeDialog(
            selectedDay = day,
            selectedTime = time,
            onConfirm = { pickedDay, pickedTime ->
                day = pickedDay
                time = pickedTime
                dayWord = null // 用户自己挑的日子，不再回显解析词
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/**
 * 无状态入口：截图 / 流程测试用。
 * AI 兜底（规格里的「AI 解析」）在 M4-6/M4-7 接上真正调用；这里**不出现死按钮**。
 */
@Composable
fun QuickEntryContent(
    text: String,
    outcome: QuickParseOutcome?,
    type: TxType,
    amountCents: Long,
    categoryName: String?,
    categoryFromRule: Boolean,
    dayLabel: String,
    note: String?,
    saving: Boolean,
    onTextChange: (String) -> Unit,
    onPaste: () -> Unit,
    onParse: () -> Unit,
    onToggleType: () -> Unit,
    onPickCategory: () -> Unit,
    onPickDate: () -> Unit,
    onEditNote: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val parsedOk = outcome?.result is dev.penned.app.domain.QuickParseResult.Ok
    val noAmount = outcome?.result is dev.penned.app.domain.QuickParseResult.NoAmount

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "一句话记账", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = PennedSpacing.xxl),
        ) {
            // 输入区：多行、自动聚焦；右侧「粘贴」
            PennedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f).heightIn(min = PennedSpacing.xxl * 3)) { // token-ok：多行输入区最小高度（24×3）
                        if (text.isEmpty()) {
                            Text(
                                "说说这笔账，比如「昨天打车 32.5」",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChange,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(QUICK_TEXT_FIELD_TAG),
                        )
                    }
                    Spacer(Modifier.width(PennedSpacing.sm))
                    Text(
                        "粘贴",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .heightIn(min = PennedDimens.touchTarget)
                            .padding(horizontal = PennedSpacing.sm)
                            .testTag("quick-paste")
                            .androidxClickable(onPaste),
                    )
                }
            }
            Column(Modifier.padding(horizontal = PennedSpacing.lg)) {
                PennedButton(text = "解析", enabled = text.isNotBlank(), onClick = onParse)
            }

            if (noAmount) {
                Text(
                    "没找到金额——把数字也说出来，比如「午饭 12」",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(
                        start = PennedSpacing.lg,
                        end = PennedSpacing.lg,
                        top = PennedSpacing.md,
                    ),
                )
            }

            if (parsedOk) {
                Spacer(Modifier.height(PennedSpacing.md))
                PennedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PennedSpacing.lg),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    ResultRow(label = "类型", value = if (type == TxType.EXPENSE) "支出" else "收入", onClick = onToggleType, tag = QUICK_ROW_TYPE)
                    PennedDivider()
                    ResultRow(
                        label = "金额",
                        value = dev.penned.app.domain.Money.format(amountCents),
                        valueColor = if (type == TxType.INCOME) {
                            dev.penned.app.ui.theme.incomeColor()
                        } else {
                            dev.penned.app.ui.theme.expenseColor()
                        },
                        onClick = { /* 金额在结果卡上直接改：点行打开金额键盘（M4-3 收尾接） */ },
                        tag = QUICK_ROW_AMOUNT,
                    )
                    PennedDivider()
                    ResultRow(
                        label = "分类",
                        value = categoryName ?: "未分类",
                        muted = categoryName == null,
                        badge = if (categoryFromRule) "来自关键词规则" else null,
                        onClick = onPickCategory,
                        tag = QUICK_ROW_CATEGORY,
                    )
                    PennedDivider()
                    ResultRow(label = "日期", value = dayLabel, onClick = onPickDate, tag = QUICK_ROW_DATE)
                    PennedDivider()
                    ResultRow(
                        label = "备注",
                        value = note ?: "无",
                        muted = note == null,
                        onClick = onEditNote,
                        tag = QUICK_ROW_NOTE,
                    )
                }
                Column(Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md)) {
                    PennedButton(text = "保存", enabled = !saving, onClick = onSave)
                    Spacer(Modifier.height(PennedSpacing.xs))
                    Text(
                        "保存后账户留空（可在详情页补）；分类只是建议，入账前你还能改",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 结果卡一行：标签 + 值（+ 可选小标记），整行可点改。 */
@Composable
private fun ResultRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    tag: String,
    valueColor: androidx.compose.ui.graphics.Color? = null,
    muted: Boolean = false,
    badge: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.touchTarget + PennedSpacing.sm)
            .testTag(tag)
            .androidxClickable(onClick)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(PennedDimens.rowMinHeight),
        )
        Text(
            value,
            style = PennedTextStyles.rowTitle,
            color = valueColor
                ?: if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = dev.penned.app.ui.theme.primaryInkColor(),
            )
            Spacer(Modifier.width(PennedSpacing.xs))
        }
        Text(
            "改",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 分类选择抽屉（复用记一笔的分类宫格；只选已有分类）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickerSheet(
    categories: List<CategoryEntity>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    val parents = remember(categories) {
        categories.filter { it.parentId == null }.sortedBy { it.sortOrder }
            .map { CategoryUi(it.id, it.name, categories.any { c -> c.parentId == it.id }) }
    }
    PennedSheet(onDismiss = onDismiss, title = "选择分类") {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag(QUICK_CATEGORY_SHEET_TAG)
                .padding(horizontal = PennedSpacing.xl),
        ) {
            CategoryPanel(
                parents = parents,
                selectedId = selectedId,
                expandedId = expanded,
                expandedSubs = categories.filter { it.parentId == expanded }.sortedBy { it.sortOrder }
                    .map { CategoryUi(it.id, it.name, false) },
                // 有子分类 → 先展开子分类带（与记一笔同一手感）；没有子分类 → 直接选中。
                // 早先写成"点一级就选中并关抽屉"，二级分类**永远选不到**（流程测试抓出来的）
                onSelectParent = { cat ->
                    if (cat.hasSubs) expanded = if (expanded == cat.id) null else cat.id else onPick(cat.id)
                },
                onSelectSub = { sub -> onPick(sub.id) },
                showAddCell = false,
            )
        }
    }
}

/** 备注编辑抽屉（文本输入走抽屉，弹窗在 Robolectric 里不 idle；M4-1 同一处置）。 */
@Composable
private fun NoteEditorSheet(note: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(note) }
    PennedSheet(onDismiss = onDismiss, title = "备注") {
        Column(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.xl)) {
            PennedNameField(
                name = value,
                placeholder = "这笔账的备注",
                onNameChange = { value = it },
                modifier = Modifier.testTag("quick-note-field"),
            )
            Spacer(Modifier.height(PennedSpacing.md))
            PennedButton(text = "确定", onClick = { onConfirm(value) })
        }
    }
}

/** 小工具：`clickable` 的简写（本文件多处用；避免每处都写一遍 modifier 链）。 */
private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

@Preview(showBackground = true, name = "一句话·结果卡", widthDp = 400, heightDp = 800)
@Composable
private fun QuickEntryPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        QuickEntryContent(
            text = "昨天打车 32.5",
            outcome = QuickParseOutcome(
                dev.penned.app.domain.QuickParseResult.Ok(
                    dev.penned.app.domain.QuickDraft(
                        type = TxType.EXPENSE, amountCents = 3250, note = "打车",
                        epochDay = LocalDate.now().toEpochDay() - 1, dayWord = "昨天",
                    )
                ),
                categoryId = "c1", fromRule = true,
            ),
            type = TxType.EXPENSE,
            amountCents = 3250,
            categoryName = "出行交通",
            categoryFromRule = true,
            dayLabel = "10月16日",
            note = "打车",
            saving = false,
            onTextChange = {}, onPaste = {}, onParse = {}, onToggleType = {}, onPickCategory = {},
            onPickDate = {}, onEditNote = {}, onSave = {}, onBack = {},
        )
    }
}
