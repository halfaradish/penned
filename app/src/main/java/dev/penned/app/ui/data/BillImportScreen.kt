package dev.penned.app.ui.data

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.importer.AccountMapping
import dev.penned.app.data.importer.BillContainer
import dev.penned.app.data.importer.BillFile
import dev.penned.app.data.importer.BillFileReader
import dev.penned.app.data.importer.BillFormats
import dev.penned.app.data.importer.BillImportRepository
import dev.penned.app.data.importer.BillImportResult
import dev.penned.app.data.importer.BillRowParser
import dev.penned.app.data.importer.BillSource
import dev.penned.app.data.importer.ParsedBill
import dev.penned.app.domain.AccountGuess
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.BillDedupe
import dev.penned.app.domain.BillRowCounts
import dev.penned.app.domain.Money
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/** 预览页的测试标记（页面上同名文本太多，测试要钉准）。 */
const val BILL_PREVIEW_COUNTS_TAG = "bill-preview-counts"
const val BILL_PREVIEW_IMPORT_TAG = "bill-preview-import"

/** 预览页状态（一次把该显示的算好，UI 不自己拼）。 */
data class BillImportUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val fileName: String = "",
    val source: BillSource = BillSource.GENERIC,
    val container: BillContainer = BillContainer.CSV,
    val charsetLabel: String? = null,
    val headerRowIndex: Int = -1,
    val counts: BillRowCounts = BillRowCounts(),
    /** 将导入的行（已去重）；导入时原样写库。 */
    val importable: List<dev.penned.app.domain.BillRow> = emptyList(),
    val mappings: List<AccountMapping> = emptyList(),
    /** 表尾"其余统一记到"的兜底账户。 */
    val fallbackAccountId: String? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val books: List<BookEntity> = emptyList(),
    val targetBookId: String? = null,
    /** 分类来源说明：分类列命中 / 关键词规则命中 / 待补。 */
    val categoryFromColumn: Int = 0,
    val categoryFromRule: Int = 0,
    val categoryMissing: Int = 0,
    val importing: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val result: BillImportResult? = null,
) {
    val expenseTotalCents: Long get() = importable.filter { it.expense }.sumOf { it.amountCents }
    val incomeTotalCents: Long get() = importable.filter { !it.expense }.sumOf { it.amountCents }
    val willCreateCount: Int get() = mappings.count { it.willCreate }
}

/**
 * 账单导入预览页的 ViewModel（M4-5）：读文件 → 解析 → 查重 → 账户映射 → 落库。
 *
 * 口径（规格 §三 M4-5）：**分类按名匹配、绝不新建**（B1）；账户可新建、类型按名猜可改（B2）；
 * 跳过与重复**分列计数**且不写库（§二·16）；分块 500 写库并报进度。
 */
class BillImportViewModel(
    private val app: PennedApplication,
    private val uri: Uri,
    private val fileName: String,
) : ViewModel() {

    private val repo = BillImportRepository(app.database, app.deviceId)
    private val _state = MutableStateFlow(BillImportUiState(fileName = fileName))
    val state: StateFlow<BillImportUiState> = _state

    private var rawBytes: ByteArray? = null
    private var parsed: ParsedBill? = null

    fun load(charsetOverride: Charset? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val bytes = rawBytes ?: withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("读不到这个文件")
                }.also { rawBytes = it }

                val file: BillFile = withContext(Dispatchers.IO) {
                    BillFileReader.read(bytes, fileName, charsetOverride)
                }
                val parsedBill = withContext(Dispatchers.IO) { BillRowParser.parse(file) }
                parsed = parsedBill

                val bookId = app.settings.currentBookId.first()
                val importableRows = parsedBill.rows.filterNotNull()
                val existingIds = bookId?.let { id ->
                    repo.existingExternalIds(id, importableRows.mapNotNull { it.externalId })
                } ?: emptySet()
                val minutes = importableRows.map { it.dateEpochMinute }
                val existingKeys = bookId?.let { id ->
                    if (minutes.isEmpty()) emptySet() else {
                        repo.existingFallbackKeys(id, minutes.min(), minutes.max())
                    }
                } ?: emptySet()
                val (keep, dup) = BillDedupe.partition(importableRows, existingIds, existingKeys)

                val mappings = bookId?.let { repo.buildAccountMappings(it, keep) } ?: emptyList()
                val accounts = bookId?.let { app.database.accountDao().allInBook(it) } ?: emptyList()
                val books = app.database.bookDao().observeAll().first()
                val categories = bookId?.let { app.database.categoryDao().allInBook(it) } ?: emptyList()
                val rules = bookId?.let { app.database.keywordRuleDao().allInBook(it) } ?: emptyList()
                var fromColumn = 0
                var fromRule = 0
                var missing = 0
                keep.forEach { row ->
                    val hint = row.categoryHint
                    val byName = hint != null && categories.any { it.name == hint }
                    val resolved = repo.resolveCategoryId(bookId ?: "", row, categories, rules)
                    when {
                        resolved == null -> missing++
                        byName -> fromColumn++
                        else -> fromRule++
                    }
                }

                _state.value = BillImportUiState(
                    loading = false,
                    fileName = fileName,
                    source = parsedBill.source,
                    container = file.container,
                    charsetLabel = file.charsetLabel,
                    headerRowIndex = parsedBill.headerRowIndex,
                    // 计数卡上的"将导入"要的是**去重后**的数（不是解析出的候选数），否则
                    // "将导入 + 重复"会超过总行数——真机走查一眼看出来的
                    counts = parsedBill.counts.copy(importable = keep.size, duplicate = dup.size),
                    importable = keep,
                    mappings = mappings,
                    accounts = accounts,
                    books = books,
                    targetBookId = bookId,
                    categoryFromColumn = fromColumn,
                    categoryFromRule = fromRule,
                    categoryMissing = missing,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "读不了这个文件")
            }
        }
    }

    /** 手动切编码重解析（预览页那是两个 chip）。 */
    fun reparseWith(charset: Charset?) = load(charset)

    fun setMappingAccount(clue: String, accountId: String?) {
        _state.value = _state.value.copy(
            mappings = _state.value.mappings.map { mapping ->
                if (mapping.clue != clue) mapping else mapping.copy(
                    matchedAccountId = accountId,
                    matchedAccountName = _state.value.accounts.firstOrNull { it.id == accountId }?.name,
                    newAccountName = null,
                )
            }
        )
    }

    fun setMappingNewAccount(clue: String, name: String, type: AccountType) {
        _state.value = _state.value.copy(
            mappings = _state.value.mappings.map { mapping ->
                if (mapping.clue != clue) mapping else mapping.copy(
                    matchedAccountId = null,
                    matchedAccountName = null,
                    newAccountName = name,
                    newAccountType = type,
                )
            }
        )
    }

    fun skipMapping(clue: String) {
        _state.value = _state.value.copy(
            mappings = _state.value.mappings.map { mapping ->
                if (mapping.clue != clue) mapping else mapping.copy(
                    matchedAccountId = null, matchedAccountName = null, newAccountName = null,
                )
            }
        )
    }

    fun setFallbackAccount(accountId: String?) {
        _state.value = _state.value.copy(fallbackAccountId = accountId)
    }

    fun setTargetBook(bookId: String) {
        _state.value = _state.value.copy(targetBookId = bookId)
    }

    fun import() {
        val current = _state.value
        val bookId = current.targetBookId ?: return
        viewModelScope.launch {
            _state.value = current.copy(importing = true, progressDone = 0, progressTotal = current.importable.size)
            val result = repo.import(
                bookId = bookId,
                fileName = fileName,
                sourceKind = current.source.sourceKind,
                rows = current.importable,
                mappings = current.mappings,
                duplicateCount = current.counts.duplicate,
                skippedCount = current.counts.skipped,
                onProgress = { done, total ->
                    _state.value = _state.value.copy(progressDone = done, progressTotal = total)
                },
            )
            _state.value = _state.value.copy(importing = false, result = result)
        }
    }
}

/**
 * 账单导入预览页（M4-5）：识别结果 / 计数分列 / 收支合计 / 目标账本 / 账户映射表 / 分类来源 / 导入。
 * 编码可手动切换（UTF-8 / GB18030）并即时重解析。
 */
@Composable
fun BillImportScreen(onBack: () -> Unit, onImported: (String) -> Unit) {
    val app = dev.penned.app.ui.LocalApp.current
    val uri = BillImportRequest.uri ?: return
    val viewModel: BillImportViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        initializer = { BillImportViewModel(app, uri, BillImportRequest.fileName) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    var editingClue by remember { mutableStateOf<String?>(null) }
    var showFallbackPicker by remember { mutableStateOf(false) }
    var showBookPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.result) {
        state.result?.let { onImported(it.batchId) }
    }

    BillImportContent(
        state = state,
        onBack = onBack,
        onReparse = { charsetName ->
            viewModel.reparseWith(charsetName?.let { Charset.forName(it) })
        },
        onEditMapping = { clue -> editingClue = clue },
        onEditFallback = { showFallbackPicker = true },
        onEditBook = { showBookPicker = true },
        onImport = viewModel::import,
    )

    editingClue?.let { clue ->
        val mapping = state.mappings.firstOrNull { it.clue == clue }
        AccountMappingSheet(
            mapping = mapping,
            accounts = state.accounts,
            onPickAccount = { id -> viewModel.setMappingAccount(clue, id); editingClue = null },
            onCreate = { name, type -> viewModel.setMappingNewAccount(clue, name, type); editingClue = null },
            onSkip = { viewModel.skipMapping(clue); editingClue = null },
            onDismiss = { editingClue = null },
        )
    }
    if (showFallbackPicker) {
        FallbackAccountSheet(
            accounts = state.accounts,
            current = state.fallbackAccountId,
            onPick = { id -> viewModel.setFallbackAccount(id); showFallbackPicker = false },
            onDismiss = { showFallbackPicker = false },
        )
    }
    if (showBookPicker) {
        BookPickerSheet(
            books = state.books,
            current = state.targetBookId,
            onPick = { id -> viewModel.setTargetBook(id); showBookPicker = false },
            onDismiss = { showBookPicker = false },
        )
    }
}

/** 预览页打开时把"从哪个 uri 来的"放这里（SAF 的 uri 没法进导航参数）。 */
object BillImportRequest {
    var uri: Uri? = null
    var fileName: String = "账单文件"

    fun open(context: Context, uri: Uri, name: String) {
        this.uri = uri
        this.fileName = name.ifBlank { "账单文件" }
    }
}

/** 无状态入口：截图与 Compose 测试用。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillImportContent(
    state: BillImportUiState,
    onBack: () -> Unit,
    onReparse: (String?) -> Unit = {},
    onEditMapping: (String) -> Unit = {},
    onEditFallback: () -> Unit = {},
    onEditBook: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "导入账单", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = PennedSpacing.xxl),
        ) {
            if (state.loading) {
                Text(
                    "正在读文件…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(PennedSpacing.lg),
                )
                return@Column
            }
            state.error?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(PennedSpacing.lg),
                )
                return@Column
            }
            if (state.importing) {
                LinearProgressIndicator(
                    progress = {
                        if (state.progressTotal == 0) 0f
                        else state.progressDone.toFloat() / state.progressTotal
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                Text(
                    "正在导入 ${state.progressDone}/${state.progressTotal}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
                )
            }

            // ① 识别结果
            PennedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
            ) {
                Text(state.source.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    "容器 ${state.container.name.lowercase()} · 表头在第 ${state.headerRowIndex + 1} 行" +
                        (state.charsetLabel?.let { " · 编码 $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.charsetLabel != null) {
                    Spacer(Modifier.height(PennedSpacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "编码",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(PennedSpacing.sm))
                        PennedChip(
                            label = "UTF-8",
                            selected = state.charsetLabel == "UTF-8",
                            onClick = { onReparse("UTF-8") },
                        )
                        Spacer(Modifier.width(PennedSpacing.xs))
                        PennedChip(
                            label = "GB18030",
                            selected = state.charsetLabel == "GB18030",
                            onClick = { onReparse("GB18030") },
                        )
                    }
                }
            }

            // ② 分列计数（总 / 将导入 / 重复 / 跳过四类）
            PennedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs)
                    .testTag(BILL_PREVIEW_COUNTS_TAG),
            ) {
                CountRow("总行数", state.counts.total.toString())
                CountRow("将导入", state.counts.importable.toString(), emphasize = true)
                CountRow("重复", state.counts.duplicate.toString())
                CountRow(
                    "跳过",
                    state.counts.skipped.toString(),
                    detail = "关闭/退款 ${state.counts.statusSkipped} · 不计收支 ${state.counts.nonCashflowSkipped} · 坏行 ${state.counts.bad}",
                )
            }

            // ③ 收支合计（对账的位置）
            PennedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
            ) {
                CountRow("支出合计", Money.format(state.expenseTotalCents))
                CountRow("收入合计", Money.format(state.incomeTotalCents))
            }

            // ④ 目标账本 + 账户映射表
            PennedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
                contentPadding = PaddingValues(0.dp),
            ) {
                PickRow(
                    label = "目标账本",
                    value = state.books.firstOrNull { it.id == state.targetBookId }?.name ?: "当前账本",
                    onClick = onEditBook,
                )
                if (state.mappings.isNotEmpty()) {
                    PennedDivider()
                    Text(
                        "账户映射" + if (state.willCreateCount > 0) "（将新建 ${state.willCreateCount} 个）" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.willCreateCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(start = PennedSpacing.lg, top = PennedSpacing.sm),
                    )
                    state.mappings.forEach { mapping ->
                        PickRow(
                            label = mapping.clue,
                            value = when {
                                mapping.matchedAccountName != null -> mapping.matchedAccountName!!
                                mapping.newAccountName != null ->
                                    "新建「${mapping.newAccountName}」· ${AccountGuess.groupLabel(mapping.newAccountType)}"
                                else -> "不指定"
                            },
                            onClick = { onEditMapping(mapping.clue) },
                        )
                    }
                }
                PennedDivider()
                PickRow(
                    label = "其余统一记到",
                    value = state.accounts.firstOrNull { it.id == state.fallbackAccountId }?.name ?: "不指定",
                    onClick = onEditFallback,
                )
            }

            // ⑤ 分类来源说明
            Text(
                "分类：按名命中 ${state.categoryFromColumn} · 关键词规则 ${state.categoryFromRule} · 待补 ${state.categoryMissing}" +
                    "（导入时不会新建分类）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
            )

            Box(Modifier.padding(horizontal = PennedSpacing.lg)) {
                PennedButton(
                    text = if (state.importing) "导入中…" else "导入 ${state.counts.importable} 条",
                    enabled = !state.importing && state.counts.importable > 0,
                    modifier = Modifier.testTag(BILL_PREVIEW_IMPORT_TAG),
                    onClick = onImport,
                )
            }
        }
    }
}

@Composable
private fun CountRow(label: String, value: String, emphasize: Boolean = false, detail: String? = null) {
    Column(Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = if (emphasize) {
                    dev.penned.app.ui.theme.primaryInkColor()
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PickRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.touchTarget)
            .androidClickable(onClick)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(PennedSpacing.xs))
        Text("改", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

private fun Modifier.androidClickable(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

/** 账户映射一行：选已有账户 / 按名新建（改类型）/ 不指定。 */
@Composable
private fun AccountMappingSheet(
    mapping: AccountMapping?,
    accounts: List<AccountEntity>,
    onPickAccount: (String) -> Unit,
    onCreate: (String, AccountType) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val guessName = mapping?.newAccountName ?: mapping?.clue.orEmpty()
    var type by remember { mutableStateOf(AccountGuess.typeFor(guessName)) }
    PennedSheet(onDismiss = onDismiss, title = "「${mapping?.clue.orEmpty()}」记到哪个账户") {
        Column(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.xl)) {
            // 新建（默认按名猜类型，可改）
            Text(
                "新建「$guessName」· ${AccountGuess.groupLabel(type)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.androidClickable { onCreate(guessName, type) },
            )
            Spacer(Modifier.height(PennedSpacing.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm)) {
                listOf(
                    AccountType.DEBIT to "资金",
                    AccountType.CREDIT to "信贷",
                    AccountType.INVEST to "理财",
                    AccountType.DEBT to "负债",
                ).forEach { (t, label) ->
                    PennedChip(label = label, selected = type == t, onClick = { type = t })
                }
            }
            Spacer(Modifier.height(PennedSpacing.md))
            Text(
                "不指定（这笔不挂账户）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.androidClickable(onSkip),
            )
            if (accounts.isNotEmpty()) {
                Spacer(Modifier.height(PennedSpacing.md))
                Text(
                    "已有账户",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                accounts.forEach { account ->
                    Text(
                        "${account.name} · ${AccountGuess.groupLabel(account.type)}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = PennedDimens.touchTarget)
                            .androidClickable { onPickAccount(account.id) }
                            .padding(vertical = PennedSpacing.sm),
                    )
                }
            }
        }
    }
}

/** 表尾兜底账户。 */
@Composable
private fun FallbackAccountSheet(
    accounts: List<AccountEntity>,
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "其余统一记到") {
        Column(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.xl)) {
            Text(
                "不指定",
                style = MaterialTheme.typography.bodyLarge,
                color = if (current == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PennedDimens.touchTarget)
                    .androidClickable { onPick(null) }
                    .padding(vertical = PennedSpacing.sm),
            )
            accounts.forEach { account ->
                Text(
                    account.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (current == account.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PennedDimens.touchTarget)
                        .androidClickable { onPick(account.id) }
                        .padding(vertical = PennedSpacing.sm),
                )
            }
        }
    }
}

/** 目标账本选择。 */
@Composable
private fun BookPickerSheet(
    books: List<BookEntity>,
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "导入到哪个账本") {
        Column(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.xl)) {
            books.forEach { book ->
                Text(
                    book.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (current == book.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PennedDimens.touchTarget)
                        .androidClickable { onPick(book.id) }
                        .padding(vertical = PennedSpacing.sm),
                )
            }
        }
    }
}
