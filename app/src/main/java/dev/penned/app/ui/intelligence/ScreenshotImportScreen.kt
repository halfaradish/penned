package dev.penned.app.ui.intelligence

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.PendingEntryEntity
import dev.penned.app.data.llm.ImagePrep
import dev.penned.app.data.llm.LlmClient
import dev.penned.app.data.llm.LlmContentResult
import dev.penned.app.data.llm.LlmError
import dev.penned.app.domain.KeywordRules
import dev.penned.app.domain.LlmConfig
import dev.penned.app.domain.LlmDraftParser
import dev.penned.app.domain.LlmEndpoints
import dev.penned.app.domain.PendingKind
import dev.penned.app.domain.PendingStatus
import dev.penned.app.domain.PendingType
import dev.penned.app.domain.RuleTargetType
import dev.penned.app.domain.TxType
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** 失败卡的测试标记（内容件单独渲染测）。 */
const val SCREENSHOT_FAILURE_TAG = "screenshot-failure"

/** 识别提示词的测试标记锚（"这张图将发送给 …"）。 */
const val SCREENSHOT_PRIVACY_TAG = "screenshot-privacy"

/**
 * 截图导入的识别提示词（**一处常量**）：只许回 JSON，且明确"认不出就说不是账单"（规格 §四·纯函数清单）。
 */
internal const val SCREENSHOT_PROMPT =
    "这是一张支付截图。只回 JSON：{\"type\":\"EXPENSE|INCOME\",\"amount\":12.34," +
        "\"merchant\":\"商户\",\"time\":\"2026-10-17 12:30\",\"note\":\"说明\"}；" +
        "认不出是账单就回 {\"type\":\"UNKNOWN\"}。金额必须是数字。"

/** 页面状态。 */
data class ScreenshotUiState(
    val busy: Boolean = false,
    val progressText: String? = null,
    /** 这张图将发给谁（页面必须明示）。 */
    val endpointLabel: String? = null,
    val modelLabel: String? = null,
    val error: String? = null,
    /** 已入库的候选数（多选时逐张顺序识别）。 */
    val savedCount: Int = 0,
    val queuedCount: Int = 0,
)

/**
 * 截图导入的 ViewModel（M4-7）：逐张**顺序**识别（不并发，省流量、避限流），
 * 识别成功**先落 `pending_entries`**（杀进程不丢）再打开记一笔页预填；失败**不落库**、当场报错可重试。
 *
 * 原图只走 `cacheDir` 临时文件、识别完即丢（不留存）；**不做本地 OCR**（§五·1 裁决）。
 */
class ScreenshotImportViewModel(
    private val app: PennedApplication,
    /** 网络层可替换（测试给假服务器；生产用默认客户端）。 */
    private val client: LlmClient = LlmClient(),
) : ViewModel() {
    private val _state = MutableStateFlow(ScreenshotUiState())
    val state: StateFlow<ScreenshotUiState> = _state

    /** 认一张图：成功返回候选行（已落库），失败把原因写进状态并返回 null。 */
    suspend fun recognize(uris: List<Uri>): List<PendingEntryEntity> {
        val config = llmConfig()
        if (config == null) {
            _state.value = _state.value.copy(
                busy = false,
                error = "先去「AI 设置」填 Key 与地址（AI 默认关）",
                endpointLabel = null,
            )
            return emptyList()
        }
        _state.value = ScreenshotUiState(
            busy = true,
            endpointLabel = config.host,
            modelLabel = config.effectiveVisionModel,
            queuedCount = uris.size,
        )

        val saved = mutableListOf<PendingEntryEntity>()
        uris.forEachIndexed { index, uri ->
            _state.value = _state.value.copy(progressText = "正在识别第 ${index + 1}/${uris.size} 张…")
            val bytes = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes == null) {
                _state.value = _state.value.copy(busy = false, error = "读不到这张图")
                return saved
            }
            val prepared = withContext(Dispatchers.IO) { ImagePrep.prepare(bytes) }
            if (prepared == null) {
                _state.value = _state.value.copy(busy = false, error = "这张图解不开（不是图片？）")
                return saved
            }
            when (val result = client.extractImage(config, SCREENSHOT_PROMPT, prepared.base64)) {
                is dev.penned.app.data.llm.LlmResult.Ok -> {
                    val entry = savePending(result.draft, config)
                    saved += entry
                }

                is dev.penned.app.data.llm.LlmResult.Err -> {
                    _state.value = _state.value.copy(busy = false, error = errorText(result.reason))
                    return saved
                }
            }
        }
        _state.value = _state.value.copy(busy = false, progressText = null, savedCount = saved.size)
        return saved
    }

    /** 识别成功：落候选池（备注沿用识别出的商户/说明；分类走关键词规则的建议值）。 */
    private suspend fun savePending(
        draft: dev.penned.app.domain.LlmDraft,
        config: LlmConfig,
    ): PendingEntryEntity {
        val bookId = app.settings.currentBookId.first()
        val rules = bookId?.let { app.database.keywordRuleDao().allInBook(it) } ?: emptyList()
        val hit = KeywordRules.match(draft.merchant ?: draft.note, rules)
        val now = System.currentTimeMillis()
        val entry = PendingEntryEntity(
            id = UUID.randomUUID().toString(),
            kind = PendingKind.SCREENSHOT,
            rawText = SCREENSHOT_PROMPT.take(0).ifEmpty { null }, // 截图没有"通知原文"；原图不留存
            type = when (draft.type) {
                TxType.INCOME -> PendingType.INCOME
                TxType.EXPENSE -> PendingType.EXPENSE
                else -> PendingType.UNKNOWN
            },
            amountCents = draft.amountCents,
            occurredAtEpochMinute = draft.occurredAtEpochMinute,
            merchant = draft.merchant,
            note = draft.note,
            categoryId = hit?.takeIf { it.targetType == RuleTargetType.CATEGORY }?.targetId,
            status = PendingStatus.PENDING,
            createdAt = now,
            updatedAt = now,
            deviceId = app.deviceId,
        )
        app.database.pendingEntryDao().upsert(entry)
        return entry
    }

    /** 未配置 / 非 https / 总开关关着：都给同一句引导。 */
    private suspend fun llmConfig(): LlmConfig? {
        if (!app.settings.llmEnabled.first()) return null
        val baseUrl = app.settings.llmBaseUrl.first()
        val apiKey = app.settings.llmApiKey.first()
        val model = app.settings.llmModel.first()
        if (LlmEndpoints.validate(baseUrl, apiKey, model) != null) return null
        return LlmEndpoints.config(
            baseUrl.orEmpty(), apiKey.orEmpty(), model.orEmpty(), app.settings.llmVisionModel.first(),
        )
    }

    private fun errorText(reason: LlmError): String = when (reason) {
        LlmError.NETWORK -> "连不上：检查网络或地址"
        LlmError.TIMEOUT -> "超时了，等会儿再试"
        LlmError.UNAUTHORIZED -> "Key 无效（401）"
        LlmError.FORBIDDEN -> "没有权限（403）"
        LlmError.RATE_LIMITED -> "被限流了（429）"
        LlmError.SERVER -> "服务端出错（5xx）"
        LlmError.BAD_RESPONSE -> "没看出这是账单——换一张试试"
        else -> "先去「AI 设置」填 Key 与地址（AI 默认关）"
    }
}

/**
 * 截图导入页（M4-7）：相册（可多选）/ 拍照两条路 + **出图前的明示** + 失败卡（重试 / 去 AI 设置）。
 * 系统分享进来的图也走这一页（入口在 `MainActivity` 的 `onNewIntent`）。
 */
@Composable
fun ScreenshotImportScreen(
    onBack: () -> Unit,
    onPrefill: (pendingId: String, type: TxType, amountCents: Long, note: String?) -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val app = LocalApp.current
    val viewModel: ScreenshotImportViewModel = viewModel(initializer = { ScreenshotImportViewModel(app) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val saved = viewModel.recognize(uris)
                saved.firstOrNull()?.let { entry ->
                    onPrefill(
                        entry.id,
                        if (entry.type == PendingType.INCOME) TxType.INCOME else TxType.EXPENSE,
                        entry.amountCents ?: 0L,
                        entry.note ?: entry.merchant,
                    )
                }
            }
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                val bytes = java.io.ByteArrayOutputStream().also {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }.toByteArray()
                val file = java.io.File(context.cacheDir, "screenshot_capture.png")
                withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "dev.penned.app.fileprovider", file,
                )
                val saved = viewModel.recognize(listOf(uri))
                withContext(Dispatchers.IO) { file.delete() } // 识别完即丢
                saved.firstOrNull()?.let { entry ->
                    onPrefill(
                        entry.id,
                        if (entry.type == PendingType.INCOME) TxType.INCOME else TxType.EXPENSE,
                        entry.amountCents ?: 0L,
                        entry.note ?: entry.merchant,
                    )
                }
            }
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
        onRetry = { /* 重试 = 再选一次；识别失败不落库（规格 §三 M4-7） */ },
    )
}

/** 无状态入口：截图与测试用。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenshotImportContent(
    state: ScreenshotUiState,
    onBack: () -> Unit,
    onPickImages: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "截图导入", onBack = onBack) },
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

            if (state.busy || state.progressText != null) {
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
                        PennedButton(text = "重试", fillWidth = false, onClick = onRetry)
                        PennedButton(
                            text = "去 AI 设置",
                            fillWidth = false,
                            variant = PennedButtonVariant.Secondary,
                            onClick = onOpenAiSettings,
                        )
                    }
                }
            }

            if (!state.busy && state.error == null && state.savedCount == 0) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PennedSpacing.lg),
                ) {
                    Text(
                        "选一张支付截图（或点系统分享把图发进来），识别成一笔草稿后进记一笔页确认——" +
                            "**入账永远要你点保存**",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(PennedSpacing.md))
                    PennedButton(text = "从相册选（可多选）", onClick = onPickImages)
                    Spacer(Modifier.height(PennedSpacing.sm))
                    PennedButton(
                        text = "拍一张",
                        variant = PennedButtonVariant.Secondary,
                        onClick = onTakePhoto,
                    )
                    Spacer(Modifier.height(PennedSpacing.lg))
                    Text(
                        "不做本地 OCR；一张图只取一笔（图里有多笔时只认主笔）；原图不保存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.savedCount > 0) {
                EmptyState(
                    title = state.savedCount.takeIf { it > 1 }?.let { "已识别 $it 张，接着填这一张" } ?: "识别成功",
                    hint = "其余候选在「待确认」里，可随时再记或丢弃",
                    icon = PennedIcons.Receipt,
                    action = { PennedButton(text = "再选一张", onClick = onPickImages) },
                    modifier = Modifier.padding(top = PennedSpacing.lg),
                )
            }
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
