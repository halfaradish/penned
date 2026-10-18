package dev.penned.app.ui.intelligence

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.ImportBatchEntity
import dev.penned.app.data.importer.BillImportRepository
import dev.penned.app.data.importer.ImportRepository
import dev.penned.app.data.llm.ImagePrep
import dev.penned.app.data.llm.LlmClient
import dev.penned.app.data.llm.LlmContentResult
import dev.penned.app.data.llm.LlmError
import dev.penned.app.data.llm.LlmRowsResult
import dev.penned.app.domain.BillDedupe
import dev.penned.app.domain.BillRow
import dev.penned.app.domain.LlmConfig
import dev.penned.app.domain.LlmDraft
import dev.penned.app.domain.Money
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType
import dev.penned.app.domain.mergedNote
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedSnackbarHost
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.SwipeActionRow
import dev.penned.app.ui.components.rowDeleteAction
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.incomeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** 失败卡的测试标记（内容件单独渲染测）。 */
const val SCREENSHOT_FAILURE_TAG = "screenshot-failure"

/** 识别提示词的测试标记锚（"这张图将发送给 …"）。 */
const val SCREENSHOT_PRIVACY_TAG = "screenshot-privacy"

/** 预览页计数卡的测试标记（内容件单独渲染测）。 */
const val SCREENSHOT_COUNTS_TAG = "screenshot-counts"

/** 预览列表的测试标记。 */
const val SCREENSHOT_LIST_TAG = "screenshot-list"

/**
 * 截图识别的提示词（**一处常量**）。
 *
 * 口径（D111）：**账单列表截图一屏多笔**——按屏幕顺序逐行回，一行一笔；单笔支付页就是"一批里的一条"。
 * 时间要让模型换算成完整日期（两家列表里的时间都没有年份），状态文字不能丢（写进 note）。
 */
internal const val SCREENSHOT_PROMPT =
    "这是一张支付/账单截图。只回 JSON：" +
        "{\"rows\":[{\"type\":\"EXPENSE|INCOME\",\"amount\":12.34,\"merchant\":\"商户\"," +
        "\"time\":\"2026-10-17 12:30\",\"note\":\"说明\"}]}。" +
        "规则：①单笔支付页也按这个格式回（rows 里一条）；" +
        "②账单列表从上到下逐行回、一行一笔，不要漏也不要合并；" +
        "③读不出金额的那一行不要回；" +
        "④时间统一成 YYYY-MM-DD HH:mm——图里的「10月18日 09:23」「10-16 22:42」「昨天 23:38」都要" +
        "换算成完整日期（年份取列表分组标题里的年份，如「2026年10月」；没有就按最近一次过去推）；" +
        "⑤行里的状态文字（等待确认收货 / 自动扣款成功 / 交易关闭 / 退款 等）写进 note，不要丢；" +
        "⑥不是账单就回 {\"rows\":[]}。金额必须是数字。"

/**
 * 预览里的一行（**内存态，不落库**）：点「全部导入」才成为流水（D111）。
 *
 * [timeFromImage] = 时间是从图里读出来的（false = 读不出，兜底用了识别时刻，预览页要标出来）；
 * [duplicate] = 兜底键（时间+金额+方向+备注）与库内既有流水相同 → 预览里标「重复」、**不导入**。
 */
data class ScreenshotRow(
    val id: String,
    val type: TxType,
    val amountCents: Long,
    val note: String?,
    val minute: Long,
    val timeFromImage: Boolean,
    val duplicate: Boolean,
)

/** 页面状态。 */
data class ScreenshotUiState(
    val busy: Boolean = false,
    val progressText: String? = null,
    /** 这张图将发给谁（页面必须明示）。 */
    val endpointLabel: String? = null,
    val modelLabel: String? = null,
    /** 整体性失败（未配置 / 一张都没读出来）；逐张的小失败在 [warnings]。 */
    val error: String? = null,
    /** 逐张识别的非致命提示（"第 2 张没识别成：超时"）——已认出的行照常可用。 */
    val warnings: List<String> = emptyList(),
    val rows: List<ScreenshotRow> = emptyList(),
    /** 有行但读不出金额、被丢掉的行数（要显示出来，不静默丢）。 */
    val droppedRows: Int = 0,
    /** 超过上限被截断（列表图防疯跑）。 */
    val truncated: Boolean = false,
    /** 「这批记到」选中的账户（null = 不指定）。 */
    val accountId: String? = null,
    val accounts: List<AccountOption> = emptyList(),
    val importing: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    /** 刚导入的笔数（>0 = 显示"已导入 N 笔"并可撤销）。 */
    val imported: Int = 0,
) {
    val importable: Int get() = rows.count { !it.duplicate }
    val duplicateCount: Int get() = rows.count { it.duplicate }
    val expenseTotalCents: Long get() = rows.filter { !it.duplicate && it.type == TxType.EXPENSE }.sumOf { it.amountCents }
    val incomeTotalCents: Long get() = rows.filter { !it.duplicate && it.type == TxType.INCOME }.sumOf { it.amountCents }
    val accountName: String? get() = accounts.firstOrNull { it.id == accountId }?.name
}

/** 账户选择项（"这批记到"用；不指定 = null）。 */
data class AccountOption(val id: String, val name: String)

/**
 * 截图导入的 ViewModel（M4-7 → D111 改成**账单列表导入**）。
 *
 * 口径：
 * - 多张图**逐张顺序**识别（不并发，省流量避限流），结果**合并成一个预览**；某张失败**不整体作废**
 *   （已认出的行留着 + 一条"第 i 张…"的提示）
 * - 识别结果**只在内存里**：预览页就是"确认"这一步，点「全部导入」才写库（一条批次，可整批撤销）
 * - 去重走 `BillDedupe` 的兜底键（时间 + 金额 + 方向 + 备注；截图没有单号），**库内已有 + 批内自身**都算
 * - 原图只走 `cacheDir` 临时文件、识别完即丢（不留存）；**不做本地 OCR**
 */
class ScreenshotImportViewModel(
    private val app: PennedApplication,
    /** 网络层可替换（测试给假服务器；生产用默认客户端）。 */
    private val client: LlmClient = LlmClient(),
) : ViewModel() {

    private val _state = MutableStateFlow(ScreenshotUiState())
    val state: StateFlow<ScreenshotUiState> = _state

    private val importer = BillImportRepository(app.database, app.deviceId)
    private val legacyImport = ImportRepository(app.database, app.settings, app.deviceId)

    /** 刚导入那一批的批次号（「撤销」= 整批软删）。 */
    private var lastBatchId: String? = null

    init {
        viewModelScope.launch {
            val bookId = app.settings.currentBookId.first()
            val accounts = bookId?.let { app.database.accountDao().allInBook(it) } ?: emptyList()
            _state.update {
                it.copy(accounts = accounts.map { account -> AccountOption(account.id, account.name) })
            }
        }
    }

    /** 认一批图：结果进预览（成功返回本次认出的行数，全部失败返回 0）。 */
    suspend fun recognize(uris: List<Uri>): Int {
        val config = llmConfig()
        if (config == null) {
            _state.update {
                it.copy(
                    busy = false,
                    error = "先去「AI 设置」填 Key 与地址（AI 默认关）",
                    endpointLabel = null,
                )
            }
            return 0
        }
        val keepAccount = _state.value.accountId
        val keepAccounts = _state.value.accounts
        _state.value = ScreenshotUiState(
            busy = true,
            endpointLabel = config.host,
            modelLabel = config.effectiveVisionModel,
            accountId = keepAccount,
            accounts = keepAccounts,
        )

        val drafts = mutableListOf<LlmDraft>()
        val warnings = mutableListOf<String>()
        var dropped = 0
        var truncated = false

        uris.forEachIndexed { index, uri ->
            _state.update { it.copy(progressText = "正在识别第 ${index + 1}/${uris.size} 张…") }
            val bytes = withContext(Dispatchers.IO) {
                runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                warnings += "第 ${index + 1} 张读不到（跳过）"
                return@forEachIndexed
            }
            val prepared = withContext(Dispatchers.IO) { ImagePrep.prepare(bytes) }
            if (prepared == null) {
                warnings += "第 ${index + 1} 张解不开，不是图片？（跳过）"
                return@forEachIndexed
            }
            when (val result = client.extractImageRows(config, SCREENSHOT_PROMPT, prepared.base64)) {
                is LlmRowsResult.Ok -> {
                    drafts += result.rows.rows
                    dropped += result.rows.droppedRows
                    truncated = truncated || result.rows.truncated
                }

                is LlmRowsResult.Err -> warnings += "第 ${index + 1} 张没识别成：${errorText(result.reason)}"
            }
        }

        val rows = toRows(drafts)
        _state.update {
            it.copy(
                busy = false,
                progressText = null,
                rows = rows,
                warnings = warnings,
                droppedRows = dropped,
                truncated = truncated,
                error = if (rows.isEmpty() && warnings.isEmpty() && drafts.isEmpty()) {
                    "没看出这是账单——换一张试试"
                } else {
                    null
                },
            )
        }
        return rows.size
    }

    /** 照片那条路：先落 cacheDir 临时文件（识别完即丢）。 */
    suspend fun recognizeCaptured(bytes: ByteArray): Int = withContext(Dispatchers.IO) {
        val file = java.io.File(app.cacheDir, "screenshot_capture.png")
        runCatching { file.writeBytes(bytes) }.getOrNull()
        val uri = runCatching {
            androidx.core.content.FileProvider.getUriForFile(app, "dev.penned.app.fileprovider", file)
        }.getOrNull()
        val count = uri?.let { recognize(listOf(it)) } ?: 0
        runCatching { file.delete() }
        count
    }

    fun removeRow(id: String) = _state.update { it.copy(rows = it.rows.filterNot { row -> row.id == id }) }

    fun selectAccount(accountId: String?) = _state.update { it.copy(accountId = accountId) }

    /** 从头再选一批（丢掉当前预览）。 */
    fun reset() {
        _state.update {
            ScreenshotUiState(accountId = it.accountId, accounts = it.accounts)
        }
    }

    /** 一次导入：把"将导入"的行写库 + 落一条批次（来源 `SCREENSHOT`）。 */
    fun import() {
        val current = _state.value
        if (current.importing || current.importable == 0) return
        viewModelScope.launch {
            val bookId = app.settings.currentBookId.first()
            if (bookId == null) {
                _state.update { it.copy(importing = false, error = "还没有账本，先去建一个") }
                return@launch
            }
            val keep = current.rows.filterNot { it.duplicate }.toBillRows()
            val duplicates = current.rows.count { it.duplicate }
            _state.update { it.copy(importing = true, progressDone = 0, progressTotal = keep.size) }
            val result = runCatching {
                importer.import(
                    bookId = bookId,
                    fileName = "截图账单 · " + java.time.LocalDate.now(),
                    sourceKind = ImportBatchEntity.SOURCE_SCREENSHOT,
                    rows = keep,
                    mappings = emptyList(),
                    duplicateCount = duplicates,
                    skippedCount = current.droppedRows,
                    txSource = TxSource.SCREENSHOT,
                    fallbackAccountId = current.accountId,
                    onProgress = { done, total ->
                        _state.update { it.copy(progressDone = done, progressTotal = total) }
                    },
                )
            }.getOrNull()
            if (result == null) {
                _state.update { it.copy(importing = false, error = "写库失败，稍后再试") }
                return@launch
            }
            lastBatchId = result.batchId
            _state.update {
                ScreenshotUiState(
                    accountId = it.accountId,
                    accounts = it.accounts,
                    imported = result.imported,
                )
            }
        }
    }

    /** 「撤销」= 整批软删（批次 + 名下流水；复用 `ImportRepository.deleteBatch`）。 */
    fun undoImport(onDone: (Int) -> Unit) {
        val batchId = lastBatchId ?: return
        val count = _state.value.imported
        viewModelScope.launch {
            legacyImport.deleteBatch(batchId)
            lastBatchId = null
            _state.update { it.copy(imported = 0) }
            onDone(count)
        }
    }

    /** 识别结果 → 预览行：备注拼商户与状态、时间去宽松解析的结果（读不出就用识别时刻）、标出重复。 */
    private suspend fun toRows(drafts: List<LlmDraft>): List<ScreenshotRow> {
        if (drafts.isEmpty()) return emptyList()
        val nowMinute = System.currentTimeMillis() / 60_000
        val bookId = app.settings.currentBookId.first()
        // 窗口要覆盖"兜底成识别时刻"的行（它们的时间不在图里）
        val minutes = drafts.map { it.occurredAtEpochMinute ?: nowMinute }
        val existing = bookId?.let { book ->
            importer.existingFallbackKeys(book, minutes.min(), minutes.max())
        } ?: emptySet()

        val candidates = drafts.map { draft ->
            val minute = draft.occurredAtEpochMinute ?: nowMinute
            BillRow(
                dateEpochMinute = minute,
                amountCents = draft.amountCents,
                expense = draft.type != TxType.INCOME,
                note = draft.mergedNote(),
                externalId = null,
                accountClue = null,
                categoryHint = null,
            ) to ScreenshotRow(
                id = UUID.randomUUID().toString(),
                type = draft.type,
                amountCents = draft.amountCents,
                note = draft.mergedNote(),
                minute = minute,
                timeFromImage = draft.occurredAtEpochMinute != null,
                duplicate = false,
            )
        }
        // 逐行标重复（保留屏幕顺序：重复行也显示、只是不导入）
        val flags = BillDedupe.duplicateFlags(candidates.map { it.first }, emptySet(), existing)
        return candidates.mapIndexed { index, (_, row) -> row.copy(duplicate = flags[index]) }
    }

    /** 当前供应商的一份可用配置（总开关关着 / 没配齐 → null，一个包都不发；口径在 repo 里一处，D113）。 */
    private suspend fun llmConfig(): LlmConfig? = app.settings.activeLlmConfig()

    private fun errorText(reason: LlmError): String = when (reason) {
        LlmError.NETWORK -> "连不上：检查网络或地址"
        LlmError.TIMEOUT -> "超时了，等会儿再试"
        LlmError.UNAUTHORIZED -> "Key 无效（401）"
        LlmError.FORBIDDEN -> "没有权限（403）"
        LlmError.RATE_LIMITED -> "被限流了（429）"
        LlmError.REJECTED -> "请求被拒（4xx）：模型名不对，或这个模型不能看图"
        LlmError.SERVER -> "服务端出错（5xx）"
        LlmError.BAD_RESPONSE -> "没看出这是账单"
        else -> "AI 没配好"
    }
}

/** 预览行 → 落库行（截图没有单号与账户线索；分类在落库时按关键词规则匹配）。 */
internal fun List<ScreenshotRow>.toBillRows(): List<BillRow> = map { row ->
    BillRow(
        dateEpochMinute = row.minute,
        amountCents = row.amountCents,
        expense = row.type != TxType.INCOME,
        note = row.note,
        externalId = null,
        accountClue = null,
        categoryHint = null,
    )
}

/** 预览行的时间（`MM-dd HH:mm`，本地墙钟）。 */
internal fun screenshotRowTime(minute: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochSecond(minute * 60).atZone(zone)
    return "%02d-%02d %02d:%02d".format(t.monthValue, t.dayOfMonth, t.hour, t.minute)
}

/**
 * 截图导入页（M4-7 → D111）：相册（可多选）/ 拍照 / 系统分享收图 → 识别成**一批** → 预览 → 一次导入。
 * 识别与落库之间是"人看得到的一步"：预览页可删行、可切方向，重复行不导入。
 */
@Composable
fun ScreenshotImportScreen(
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val app = LocalApp.current
    val viewModel: ScreenshotImportViewModel = viewModel(initializer = { ScreenshotImportViewModel(app) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_SCREENSHOT_PICK)
    ) { uris ->
        if (uris.isNotEmpty()) scope.launch { viewModel.recognize(uris) }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                val bytes = java.io.ByteArrayOutputStream().also {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }.toByteArray()
                viewModel.recognizeCaptured(bytes)
            }
        }
    }

    // 导入完成：给一条可撤销的提示（撤销 = 整批软删）
    LaunchedEffect(state.imported) {
        val count = state.imported
        if (count <= 0) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "已导入 $count 笔",
            actionLabel = "撤销",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoImport { removed -> scope.launch { snackbarHostState.showSnackbar("已撤销，删掉 $removed 笔") } }
        }
    }

    ScreenshotImportContent(
        state = state,
        onBack = onBack,
        onPickImages = {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onTakePhoto = { takePhoto.launch(null) },
        onOpenAiSettings = onOpenAiSettings,
        onRemoveRow = viewModel::removeRow,
        onSelectAccount = viewModel::selectAccount,
        onImport = viewModel::import,
        onReset = viewModel::reset,
        snackbarHostState = snackbarHostState,
    )
}

/** 一次最多选几张（顺序识别；列表图一屏十几笔，8 张够用且不至于一次烧太多调用）。 */
internal const val MAX_SCREENSHOT_PICK = 8

/** 无状态入口：截图与测试用。 */
@Composable
fun ScreenshotImportContent(
    state: ScreenshotUiState,
    onBack: () -> Unit,
    onPickImages: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    onRemoveRow: (String) -> Unit = {},
    onSelectAccount: (String?) -> Unit = {},
    onImport: () -> Unit = {},
    onReset: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
) {
    var accountSheet by rememberSaveable { mutableStateOf(false) }
    var revealedRowId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "截图导入", onBack = onBack) },
        snackbarHost = { if (snackbarHostState != null) PennedSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = PennedSpacing.xxl),
        ) {
            if (state.endpointLabel != null) {
                // 出图前的明示（隐私口径收敛在一处）
                Text(
                    "这张图将发送给 ${state.endpointLabel} / ${state.modelLabel.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm)
                        .testTag(SCREENSHOT_PRIVACY_TAG),
                )
            }

            if (state.importing) {
                PennedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
                ) {
                    Text(
                        "正在导入 ${state.progressDone}/${state.progressTotal}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(PennedSpacing.sm))
                    LinearProgressIndicator(
                        progress = {
                            if (state.progressTotal <= 0) 0f
                            else state.progressDone.toFloat() / state.progressTotal
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (state.busy || state.progressText != null) {
                PennedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
                ) {
                    Text(
                        state.progressText ?: "正在识别…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "（一张一张来，不并发；识别完即丢原图）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.error?.let { message ->
                PennedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm)
                        .testTag(SCREENSHOT_FAILURE_TAG),
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(PennedSpacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm)) {
                        PennedButton(text = "重试", fillWidth = false, onClick = onReset)
                        PennedButton(
                            text = "去 AI 设置",
                            fillWidth = false,
                            variant = PennedButtonVariant.Secondary,
                            onClick = onOpenAiSettings,
                        )
                    }
                }
            }

            // 逐张的小失败：已经认出的行照常可用，所以只提示不挡路
            if (state.warnings.isNotEmpty()) {
                Text(
                    state.warnings.joinToString("；"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
                )
            }

            val empty = !state.busy && !state.importing && state.error == null &&
                state.rows.isEmpty() && state.imported == 0
            if (empty) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PennedSpacing.lg),
                ) {
                    Text(
                        entryHintLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(PennedSpacing.md))
                    PennedButton(text = "从相册选（可多选，最多 $MAX_SCREENSHOT_PICK 张）", onClick = onPickImages)
                    Spacer(Modifier.height(PennedSpacing.sm))
                    PennedButton(
                        text = "拍一张",
                        variant = PennedButtonVariant.Secondary,
                        onClick = onTakePhoto,
                    )
                    Spacer(Modifier.height(PennedSpacing.lg))
                    Text(
                        "不做本地 OCR；原图不保存——压缩后发给模型识一笔或一整屏，识别完即丢。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.imported > 0) {
                EmptyState(
                    title = "已导入 ${state.imported} 笔",
                    hint = "想整批撤回就在上面的提示条点「撤销」；也可以到「数据 → 导入批次」里删这一批",
                    icon = PennedIcons.Receipt,
                    action = { PennedButton(text = "再选一批", onClick = onReset) },
                    modifier = Modifier.padding(top = PennedSpacing.lg),
                )
            }

            if (state.rows.isNotEmpty() && !state.importing) {
                ScreenshotPreview(
                    state = state,
                    revealedRowId = revealedRowId,
                    onRevealedRowChange = { revealedRowId = it },
                    onRemoveRow = onRemoveRow,
                    onOpenAccountSheet = { accountSheet = true },
                    onImport = onImport,
                    onReset = onReset,
                )
            }
        }
    }

    if (accountSheet) {
        AccountPickerSheet(
            accounts = state.accounts,
            selectedId = state.accountId,
            onPick = {
                onSelectAccount(it)
                accountSheet = false
            },
            onDismiss = { accountSheet = false },
        )
    }
}

/** 入口说明一行：「确认后才入账」加重（**别把 `**` 直接印在页面上**——M4-8 踩过一次）。 */
internal fun entryHintLine(): AnnotatedString = buildAnnotatedString {
    append("选一张支付截图或账单列表截图（或点系统分享把图发进来）——识别出的每一笔都会先列出来，")
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("确认后才入账") }
}

/** 预览：计数 + 合计 + 这批记到哪 + 行列表 + 主按钮。 */
@Composable
private fun ScreenshotPreview(
    state: ScreenshotUiState,
    revealedRowId: String?,
    onRevealedRowChange: (String?) -> Unit,
    onRemoveRow: (String) -> Unit,
    onOpenAccountSheet: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit,
) {
    Text(
        "识别到 ${state.rows.size} 笔，确认后一起导入",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
    )

    PennedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs)
            .testTag(SCREENSHOT_COUNTS_TAG),
    ) {
        PreviewCountRow("识别", "${state.rows.size} 笔")
        PreviewCountRow("将导入", "${state.importable} 笔", emphasize = true)
        PreviewCountRow("重复", "${state.duplicateCount} 笔", detail = "已排除，不导入".takeIf { state.duplicateCount > 0 })
        PreviewCountRow(
            "忽略",
            "${state.droppedRows} 行",
            detail = "没读出金额，没进列表".takeIf { state.droppedRows > 0 },
        )
        if (state.truncated) {
            PreviewCountRow("截断", "只取前一部分", detail = "这张图太长，剩下的没读")
        }
        PennedDivider()
        PreviewCountRow("支出合计", Money.format(state.expenseTotalCents))
        PreviewCountRow("收入合计", Money.format(state.incomeTotalCents))
    }

    PennedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = PennedDimens.touchTarget)
                .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "这批记到",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .heightIn(min = PennedDimens.touchTarget)
                    .clickable(onClick = onOpenAccountSheet),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    state.accountName ?: "不指定",
                    style = MaterialTheme.typography.bodyMedium,
                    color = dev.penned.app.ui.theme.primaryInkColor(),
                )
            }
        }
    }

    PennedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs)
            .testTag(SCREENSHOT_LIST_TAG),
        contentPadding = PaddingValues(0.dp),
    ) {
        state.rows.forEachIndexed { index, row ->
            if (index > 0) PennedDivider()
            SwipeActionRow(
                isOpen = revealedRowId == row.id,
                onOpenChange = { open -> onRevealedRowChange(if (open) row.id else null) },
                actions = listOf(
                    rowDeleteAction {
                        onRevealedRowChange(null)
                        onRemoveRow(row.id)
                    }
                ),
            ) {
                ScreenshotRowItem(row)
            }
        }
    }

    Text(
        "读错的删掉手记，或先导入再进详情改；导入是一整批，可在提示条里撤销。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
    )

    Column(Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm)) {
        PennedButton(
            text = if (state.importable > 0) "全部导入 ${state.importable} 笔" else "没有可导入的行",
            enabled = state.importable > 0,
            onClick = onImport,
        )
        Spacer(Modifier.height(PennedSpacing.sm))
        PennedButton(
            text = "重选",
            variant = PennedButtonVariant.Secondary,
            onClick = onReset,
        )
    }
}

/** 预览行：时间 + 备注（商户与状态）+ 金额（方向色）；重复行标出来。 */
@Composable
private fun ScreenshotRowItem(row: ScreenshotRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (row.timeFromImage) screenshotRowTime(row.minute) else "时间没读出来",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.note ?: "（没读出商户）",
                    style = PennedTextStyles.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.duplicate) {
                    Spacer(Modifier.width(PennedSpacing.xs))
                    Text(
                        "重复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(PennedSpacing.sm))
        Text(
            (if (row.type == TxType.INCOME) "+" else "-") + Money.format(row.amountCents),
            style = PennedTextStyles.cardTitle,
            color = if (row.type == TxType.INCOME) {
                incomeColor()
            } else {
                expenseColor()
            },
        )
    }
}

/** 计数行（与账单导入预览页同一版式）。 */
@Composable
private fun PreviewCountRow(label: String, value: String, emphasize: Boolean = false, detail: String? = null) {
    Column(Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs)) {
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

/** 「这批记到」的账户抽屉（不指定 = 纯现金无账户记账）。 */
@Composable
private fun AccountPickerSheet(
    accounts: List<AccountOption>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "这批记到哪个账户") {
        Column(Modifier.fillMaxWidth().padding(bottom = PennedSpacing.xl)) {
            SheetPickRow(
                label = "不指定",
                selected = selectedId == null,
                onClick = { onPick(null) },
            )
            accounts.forEach { account ->
                SheetPickRow(
                    label = account.name,
                    selected = account.id == selectedId,
                    onClick = { onPick(account.id) },
                )
            }
            if (accounts.isEmpty()) {
                Text(
                    "还没有账户——先到「我的 → 资产」建一个，或这批先不指定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun SheetPickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.touchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) dev.penned.app.ui.theme.primaryInkColor() else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
            Text(
                "已选",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, name = "截图导入·空态", widthDp = 400, heightDp = 800)
@Composable
private fun ScreenshotImportPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        ScreenshotImportContent(state = ScreenshotUiState(), onBack = {})
    }
}

@Preview(showBackground = true, name = "截图导入·预览", widthDp = 400, heightDp = 900)
@Composable
private fun ScreenshotPreviewPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        ScreenshotImportContent(
            state = ScreenshotUiState(
                endpointLabel = "open.bigmodel.cn",
                modelLabel = "glm-4v",
                rows = listOf(
                    ScreenshotRow("1", TxType.EXPENSE, 6707, "拼多多平台商户", 29_870_000L, true, false),
                    ScreenshotRow("2", TxType.EXPENSE, 1200, "西大朴诚食堂", 29_870_000L, true, false),
                    ScreenshotRow("3", TxType.INCOME, 8800, "微信红包-来自姐姐", 29_870_000L, true, true),
                ),
                droppedRows = 1,
                accounts = listOf(AccountOption("a1", "微信零钱")),
                accountId = "a1",
            ),
            onBack = {},
        )
    }
}
