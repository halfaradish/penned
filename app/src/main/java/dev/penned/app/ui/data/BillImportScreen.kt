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
import dev.penned.app.data.db.entity.CategoryEntity
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
import dev.penned.app.data.db.entity.KeywordRuleEntity
import dev.penned.app.data.llm.LlmContentResult
import dev.penned.app.data.llm.LlmError
import dev.penned.app.data.llm.LlmImportAssist
import dev.penned.app.domain.AccountGuess
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.BillDedupe
import dev.penned.app.domain.BillRowCounts
import dev.penned.app.domain.CategoryAssignment
import dev.penned.app.domain.CategorySource
import dev.penned.app.domain.ColumnMappingSuggestion
import dev.penned.app.domain.LlmConfig
import dev.penned.app.domain.LlmSuggestions
import dev.penned.app.domain.MerchantCategorySuggestion
import dev.penned.app.domain.MerchantNeed
import dev.penned.app.domain.RuleTargetType
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

/** 通用模式列映射卡 / AI 建议卡的测试标记。 */
const val BILL_COLUMN_MAP_TAG = "bill-column-map"
const val BILL_AI_TAG = "bill-ai"

/** AI 没配置/未开启时的统一引导（一处文案）。 */
const val OFFLINE_MESSAGE = "先去「AI 设置」填 Key 与地址（AI 默认关）"

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
    /** 分类来源说明：分类列命中 / 关键词规则命中 / AI 建议 / 待补。 */
    val categoryFromColumn: Int = 0,
    val categoryFromRule: Int = 0,
    val categoryFromAi: Int = 0,
    val categoryMissing: Int = 0,
    /** 通用模式的表头（认不出两家来源时才有；列映射 UI 用）。 */
    val genericHeader: List<String> = emptyList(),
    /** 通用模式当前生效的映射：我们的字段 → 表头列名（UI 显示与手改都用它）。 */
    val genericMapping: Map<String, String> = emptyMap(),
    /** AI 给的列映射建议（已在本地校验过；null = 还没问过）。 */
    val aiMapping: ColumnMappingSuggestion? = null,
    /** AI 的商户分类建议（只改预览页的建议值）。 */
    val aiMerchants: List<MerchantCategorySuggestion> = emptyList(),
    val aiBusy: Boolean = false,
    val aiMessage: String? = null,
    /** 「存为关键词规则」存过没有（存过就给一句"下次本地命中"）。 */
    val rulesSaved: Boolean = false,
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
    private var lastFile: BillFile? = null
    private val assist = LlmImportAssist()

    /** AI 贴过的分类：key = 方向 + 商户名（`MerchantNeed.key`），value = 本账本现有分类 id。 */
    private val aiCategoryByKey = HashMap<String, String>()

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
                lastFile = file
                val parsedBill = withContext(Dispatchers.IO) { BillRowParser.parse(file) }
                refreshFrom(parsedBill, file)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "读不了这个文件")
            }
        }
    }

    /** 解析结果 → 一页状态（查重、账户映射、分类来源统计都在这里收口；通用模式重解析也走它）。 */
    private suspend fun refreshFrom(parsedBill: ParsedBill, file: BillFile) {
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

        // AI 建议来自商户名；重解析 / 切编码后按同一份映射重新贴上（不丢）
        val annotated = applyAiCategory(keep)
        val categoryCounts = countCategorySources(annotated, categories, rules)

        _state.value = BillImportUiState(
            loading = false,
            fileName = fileName,
            source = parsedBill.source,
            // 通用模式（认不出两家来源）才给列映射 UI：第 1 行当表头
            genericHeader = if (parsedBill.source == BillSource.GENERIC) {
                file.rows.firstOrNull().orEmpty()
            } else {
                emptyList()
            },
            // 通用模式重解析时保留当前映射（首帧为空 = 还没认列）
            genericMapping = if (parsedBill.source == BillSource.GENERIC) {
                _state.value.genericMapping
            } else {
                emptyMap()
            },
            container = file.container,
            charsetLabel = file.charsetLabel,
            headerRowIndex = parsedBill.headerRowIndex,
            // 计数卡上的"将导入"要的是**去重后**的数（不是解析出的候选数），否则
            // "将导入 + 重复"会超过总行数——真机走查一眼看出来的
            counts = parsedBill.counts.copy(importable = keep.size, duplicate = dup.size),
            importable = annotated,
            mappings = mappings,
            accounts = accounts,
            books = books,
            targetBookId = bookId,
            categoryFromColumn = categoryCounts.column,
            categoryFromRule = categoryCounts.rule,
            categoryFromAi = categoryCounts.ai,
            categoryMissing = categoryCounts.missing,
            // 重解析不清掉已经问到的建议与"存为规则"的提示
            aiMapping = _state.value.aiMapping,
            aiMerchants = _state.value.aiMerchants,
            rulesSaved = _state.value.rulesSaved,
        )
    }

    /** 把 AI 建议按「方向 + 商户名」贴到行上（匹配不上 = 保持未分类）。 */
    private fun applyAiCategory(rows: List<dev.penned.app.domain.BillRow>): List<dev.penned.app.domain.BillRow> =
        rows.map { row ->
            val merchant = CategoryAssignment.merchantOf(row)
            val categoryId = merchant?.let { aiCategoryByKey[MerchantNeed.keyOf(it, row.expense)] }
            row.copy(suggestedCategoryId = categoryId)
        }

    private data class CategoryCounts(
        val column: Int = 0,
        val rule: Int = 0,
        val ai: Int = 0,
        val missing: Int = 0,
    )

    private fun countCategorySources(
        rows: List<dev.penned.app.domain.BillRow>,
        categories: List<CategoryEntity>,
        rules: List<KeywordRuleEntity>,
    ): CategoryCounts = rows.fold(CategoryCounts()) { acc, row ->
        when (CategoryAssignment.resolve(row, categories, rules).source) {
            CategorySource.COLUMN -> acc.copy(column = acc.column + 1)
            CategorySource.RULE -> acc.copy(rule = acc.rule + 1)
            CategorySource.AI -> acc.copy(ai = acc.ai + 1)
            CategorySource.NONE -> acc.copy(missing = acc.missing + 1)
        }
    }

    /** 手动切编码重解析（预览页那是两个 chip）。 */
    fun reparseWith(charset: Charset?) = load(charset)

    /** 通用模式：按"字段 → 列名"的手动映射重新解析（AI 建议"采用"后也走这里）。 */
    fun reparseGeneric(mapping: Map<String, String>) {
        val file = lastFile ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(genericMapping = mapping)
            try {
                val parsedBill = withContext(Dispatchers.IO) {
                    BillRowParser.parseGeneric(file, file.rows.firstOrNull().orEmpty(), mapping)
                }
                refreshFrom(parsedBill, file)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "这份映射解析不了")
            }
        }
    }

    /** 「让 AI 认列」：默认只发表头；[includeSamples] = 用户点过「再看 3 行样例」（数字已掩码）。 */
    fun requestAiMapping(includeSamples: Boolean) {
        val file = lastFile ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(aiBusy = true, aiMessage = null)
            val config = llmConfig()
            if (config == null) {
                _state.value = _state.value.copy(aiBusy = false, aiMessage = OFFLINE_MESSAGE)
                return@launch
            }
            val header = file.rows.firstOrNull().orEmpty()
            val samples = if (includeSamples) file.rows.drop(1).take(3) else emptyList()
            when (val result = assist.suggestColumnMapping(config, header, samples)) {
                is LlmContentResult.Ok -> {
                    val suggestion = LlmSuggestions.parseColumnMapping(result.content, header)
                    _state.value = _state.value.copy(
                        aiBusy = false,
                        aiMapping = suggestion,
                        aiMessage = if (suggestion == null) "AI 回的列名对不上表头，改用手动映射吧" else null,
                    )
                }

                is LlmContentResult.Err -> _state.value =
                    _state.value.copy(aiBusy = false, aiMessage = aiErrorText(result.reason))
            }
        }
    }

    /** 采用 AI 的列映射建议（本地校验过的那份）。 */
    fun applyAiMapping() {
        val suggestion = _state.value.aiMapping ?: return
        val mapping = buildMap {
            put("time", suggestion.timeColumn)
            put("amount", suggestion.amountColumn)
            suggestion.directionColumn?.let { put("direction", it) }
            suggestion.merchantColumn?.let { put("merchant", it) }
            suggestion.itemColumn?.let { put("item", it) }
            suggestion.statusColumn?.let { put("status", it) }
            suggestion.externalIdColumn?.let { put("externalId", it) }
            suggestion.paymentColumn?.let { put("payment", it) }
            suggestion.categoryColumn?.let { put("category", it) }
            suggestion.noteColumn?.let { put("note", it) }
        }
        _state.value = _state.value.copy(aiMapping = null)
        reparseGeneric(mapping)
    }

    /**
     * 「AI 自动贴分类」：只发商户名 + 分类名（按收支分域、≤100/批），结果**直接贴到将导入的行上**
     * （D117）——导入时不再需要第二次手动确认，落库就是这些分类。
     */
    fun requestAiCategories() {
        viewModelScope.launch {
            val config = llmConfig()
            if (config == null) {
                _state.value = _state.value.copy(aiMessage = OFFLINE_MESSAGE)
                return@launch
            }
            val current = _state.value
            val bookId = current.targetBookId ?: return@launch
            _state.value = current.copy(aiBusy = true, aiMessage = null)

            val categories = app.database.categoryDao().allInBook(bookId)
            val rules = app.database.keywordRuleDao().allInBook(bookId)

            val needs = current.importable.mapNotNull { row ->
                CategoryAssignment.merchantOf(row)?.let { MerchantNeed(it, row.expense) }
            }.distinctBy { it.key }
            if (needs.isEmpty()) {
                _state.value = current.copy(
                    aiBusy = false,
                    aiMessage = "这些行没有商户列（通用模式可手动映射一列），没法贴分类（会记为未分类）",
                )
                return@launch
            }

            val outcome = assist.suggestCategories(config, needs, categories)
            outcome.suggestions
                .filter { it.categoryId != null }
                .forEach { suggestion ->
                    aiCategoryByKey[MerchantNeed.keyOf(suggestion.merchant, suggestion.expense)] =
                        suggestion.categoryId!!
                }

            // 网络回来后以最新状态为准（期间可能切了编码 / 改了列映射）
            val latest = _state.value
            val annotated = applyAiCategory(latest.importable)
            val counts = countCategorySources(annotated, categories, rules)
            val usableCount = outcome.suggestions.count { it.categoryId != null }
            val message = when {
                outcome.error != null ->
                    "AI 贴了 ${counts.ai} 笔；后面失败了：${aiErrorText(outcome.error)}（没贴上的会记为未分类）"
                usableCount == 0 -> "AI 没认出可用的分类（这些会记为未分类）"
                else -> null
            }
            _state.value = latest.copy(
                importable = annotated,
                aiBusy = false,
                aiMerchants = outcome.suggestions.filter { it.categoryId != null },
                aiMessage = message,
                categoryFromColumn = counts.column,
                categoryFromRule = counts.rule,
                categoryFromAi = counts.ai,
                categoryMissing = counts.missing,
            )
        }
    }

    /** 「把这些商户 → 分类存为关键词规则」：写 `keyword_rules`，下次导入/一句话本地命中（不再花 LLM）。 */
    fun saveAsKeywordRules() {
        val bookId = _state.value.targetBookId ?: return
        // 同一个商户在收支两域给了不同分类时不存（规则表没有"方向"维度，存了会串域）
        val rows = _state.value.aiMerchants
            .filter { it.categoryId != null }
            .groupBy { it.merchant.trim().lowercase() }
            .values
            .filter { group -> group.map { it.categoryId }.distinct().size == 1 }
            .map { it.first() }
        if (rows.isEmpty()) return
        viewModelScope.launch {
            val dao = app.database.keywordRuleDao()
            val existing = dao.allInBook(bookId)
            val now = System.currentTimeMillis()
            rows.forEach { suggestion ->
                val merchant = suggestion.merchant.trim()
                val categoryId = suggestion.categoryId ?: return@forEach
                if (existing.any { it.keyword == merchant && it.targetId == categoryId }) return@forEach
                dao.insertAtEnd(
                    KeywordRuleEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        bookId = bookId,
                        keyword = merchant,
                        targetType = RuleTargetType.CATEGORY,
                        targetId = categoryId,
                        priority = 0,
                        enabled = true,
                        createdAt = now,
                        updatedAt = now,
                        deviceId = app.deviceId,
                    )
                )
            }
            _state.value = _state.value.copy(rulesSaved = true, aiMerchants = emptyList())
        }
    }

    /** 当前供应商的一份可用配置（口径在 repo 里一处，D113）。 */
    private suspend fun llmConfig(): LlmConfig? = app.settings.activeLlmConfig()

    private fun aiErrorText(reason: LlmError): String = when (reason) {
        LlmError.NOT_CONFIGURED, LlmError.INSECURE_URL, LlmError.MALFORMED_URL -> OFFLINE_MESSAGE
        LlmError.NETWORK -> "连不上：检查网络或地址"
        LlmError.TIMEOUT -> "超时了，等会儿再试"
        LlmError.UNAUTHORIZED -> "Key 无效（401）"
        LlmError.FORBIDDEN -> "没有权限（403）"
        LlmError.RATE_LIMITED -> "被限流了（429）"
        LlmError.REJECTED -> "请求被拒（4xx）：模型名不对，或这个模型不支持这种用法"
        LlmError.SERVER -> "服务端出错（5xx）"
        LlmError.BAD_RESPONSE -> "回的不是能用的 JSON"
    }

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
        onPickColumn = { field, column ->
            val updated = state.genericMapping.toMutableMap()
            if (column == null) updated.remove(field) else updated[field] = column
            viewModel.reparseGeneric(updated)
        },
        onAskAiMapping = { includeSamples -> viewModel.requestAiMapping(includeSamples) },
        onApplyAiMapping = viewModel::applyAiMapping,
        onAskAiCategories = viewModel::requestAiCategories,
        onSaveRules = viewModel::saveAsKeywordRules,
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
/** 列映射 UI 的字段顺序与中文标签（与 `LlmSuggestions.FIELDS` 对齐）。 */
val ColumnMappingRows = listOf(
    "time" to "时间",
    "amount" to "金额",
    "direction" to "收/支",
    "merchant" to "商户",
    "item" to "商品",
    "status" to "状态",
    "externalId" to "单号",
    "payment" to "支付方式",
    "category" to "分类",
    "note" to "备注",
)

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
    /** 通用模式：手改某一列（null = 取消这一列）。 */
    onPickColumn: (String, String?) -> Unit = { _, _ -> },
    onAskAiMapping: (Boolean) -> Unit = {},
    onApplyAiMapping: () -> Unit = {},
    onAskAiCategories: () -> Unit = {},
    onSaveRules: () -> Unit = {},
) {
    var expandedField by remember { mutableStateOf<String?>(null) }

    // 手改某列：抽屉里列这份文件的表头（+「不指定」）
    expandedField?.let { field ->
        ColumnPickerSheet(
            title = "「${ColumnMappingRows.firstOrNull { it.first == field }?.second ?: field}」对应哪一列",
            columns = state.genericHeader,
            current = state.genericMapping[field],
            onPick = { column ->
                onPickColumn(field, column)
                expandedField = null
            },
            onDismiss = { expandedField = null },
        )
    }

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

            // ③点五 通用模式：列映射（认不出两家来源时才出现；AI 建议落在这里由用户确认/手改）
            if (state.genericHeader.isNotEmpty()) {
                PennedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs)
                        .testTag(BILL_COLUMN_MAP_TAG),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        "列映射（这份表头没认出来，指一下哪列是什么）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = PennedSpacing.lg, top = PennedSpacing.sm),
                    )
                    ColumnMappingRows.forEach { (field, label) ->
                        PickRow(
                            label = label,
                            value = state.genericMapping[field] ?: "没指定",
                            onClick = { expandedField = field },
                        )
                    }
                    Row(
                        Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PennedButton(
                            text = if (state.aiBusy) "问 AI 中…" else "让 AI 认列",
                            enabled = !state.aiBusy,
                            fillWidth = false,
                            onClick = { onAskAiMapping(false) },
                        )
                        if (state.genericMapping["time"] == null || state.genericMapping["amount"] == null) {
                            Spacer(Modifier.width(PennedSpacing.sm))
                            PennedButton(
                                text = "让 AI 再看 3 行样例",
                                enabled = !state.aiBusy,
                                fillWidth = false,
                                variant = dev.penned.app.ui.components.PennedButtonVariant.Secondary,
                                onClick = { onAskAiMapping(true) },
                            )
                        }
                    }
                    Text(
                        "AI 只发表头（点「再看 3 行样例」时多发前 3 行的**数字已打码**版本）；" +
                            "列名对不上表头的建议会被丢掉，时间与金额两列必须指定",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
                    )
                }
            }

            // ③点六 AI 建议：列映射建议（通用模式）+ 商户分类建议（都可跳过）
            if (state.aiMapping != null || state.aiMerchants.isNotEmpty() || state.aiMessage != null ||
                state.rulesSaved || state.genericHeader.isNotEmpty() || state.counts.importable > 0
            ) {
                PennedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs)
                        .testTag(BILL_AI_TAG),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        "AI 自动贴分类（可选；只发商户名与分类名，不发金额/时间/单号）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = PennedSpacing.lg, top = PennedSpacing.sm),
                    )
                    Text(
                        "AI 只从「现有分类」里挑，认不出的行保持未分类；点一下就把建议贴到下面将导入的流水上",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
                    )
                    state.aiMapping?.let { suggestion ->
                        Text(
                            "AI 认列：时间=${suggestion.timeColumn} · 金额=${suggestion.amountColumn}" +
                                (suggestion.directionColumn?.let { " · 收支=$it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
                        )
                        Column(Modifier.padding(horizontal = PennedSpacing.lg)) {
                            PennedButton(text = "采用这套列映射", onClick = onApplyAiMapping)
                        }
                        Spacer(Modifier.height(PennedSpacing.sm))
                    }
                    state.aiMerchants.take(5).forEach { suggestion ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
                        ) {
                            Text(
                                suggestion.merchant,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "→ ${suggestion.categoryName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = dev.penned.app.ui.theme.primaryInkColor(),
                            )
                        }
                    }
                    if (state.aiMerchants.size > 5) {
                        Text(
                            "…共 ${state.aiMerchants.size} 条建议",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = PennedSpacing.lg),
                        )
                    }
                    state.aiMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
                        )
                    }
                    if (state.rulesSaved) {
                        Text(
                            "已存为关键词规则：下次导入与一句话记账本地直接命中，不再花 AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = dev.penned.app.ui.theme.incomeColor(),
                            modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
                        )
                    }
                    Row(
                        Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PennedButton(
                            text = if (state.aiBusy) "问 AI 中…" else "AI 自动贴分类",
                            enabled = !state.aiBusy && state.counts.importable > 0,
                            fillWidth = false,
                            onClick = onAskAiCategories,
                        )
                        if (state.aiMerchants.isNotEmpty()) {
                            Spacer(Modifier.width(PennedSpacing.sm))
                            PennedButton(
                                text = "存为关键词规则",
                                fillWidth = false,
                                variant = dev.penned.app.ui.components.PennedButtonVariant.Secondary,
                                onClick = onSaveRules,
                            )
                        }
                    }
                }
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
                "分类：按名命中 ${state.categoryFromColumn} · 关键词规则 ${state.categoryFromRule} · " +
                    "AI ${state.categoryFromAi} · 待补 ${state.categoryMissing}" +
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

/** 通用模式：选某一列（表头列表 + 「不指定」）。 */
@Composable
private fun ColumnPickerSheet(
    title: String,
    columns: List<String>,
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = title) {
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
            columns.forEachIndexed { index, column ->
                Text(
                    "${index + 1}. $column",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (current == column) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PennedDimens.touchTarget)
                        .androidClickable { onPick(column) }
                        .padding(vertical = PennedSpacing.sm),
                )
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
