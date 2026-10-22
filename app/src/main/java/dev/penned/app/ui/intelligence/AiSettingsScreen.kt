package dev.penned.app.ui.intelligence

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.llm.LlmClient
import dev.penned.app.data.llm.LlmError
import dev.penned.app.data.llm.LlmResult
import dev.penned.app.domain.LlmConfig
import dev.penned.app.domain.LlmConfigError
import dev.penned.app.domain.LlmEndpoints
import dev.penned.app.domain.LlmProvider
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedNameField
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedSwitchRow
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.pennedClickable
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 设置页里各输入框的测试标记（页面上一堆输入件，测试要能钉准）。 */
const val AI_BASE_URL_FIELD_TAG = "ai-base-url"
const val AI_KEY_FIELD_TAG = "ai-key"
const val AI_MODEL_FIELD_TAG = "ai-model"
const val AI_VISION_MODEL_FIELD_TAG = "ai-vision-model"

/** 「供应商」字段行（点开选择抽屉）。 */
const val AI_PROVIDER_ROW_TAG = "ai-provider"

/**
 * 隐私口径**只写一处**（规格 §二·9）：Key 怎么存、发给谁、不填会怎样。各页引用这一份。
 */
object AiPrivacyCopy {
    const val FOOTER: String =
        "Key 只存本机（DataStore 明文，系统备份可能带走，风险自行衡量）；调用直连你填的地址，" +
            "应用不做任何上报；不填不联网；随时可清除 Key。"
    const val POWERED_BY: String = "AI 默认关闭：只有你点「AI 解析 / 截图识别」时才会发请求"
}

/** 设置页的整页状态。 */
data class AiSettingsUiState(
    val enabled: Boolean = false,
    /** 当前供应商（D113）：四样配置都是**这一家**的。 */
    val provider: LlmProvider = LlmProvider.DEFAULT,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val visionModel: String = "",
) {
    /** 三样齐了没有（页面拿它决定「测试连接」是否可点）。 */
    val configured: Boolean
        get() = LlmEndpoints.validate(
            baseUrl.ifBlank { null }, apiKey.ifBlank { null }, model.ifBlank { null },
        ) == null

    /** 地址当场校验的结果（边填边提示）。 */
    val urlError: LlmConfigError?
        get() = baseUrl.ifBlank { null }?.let { LlmEndpoints.validateUrl(it) }
}

/** 「测试连接」的三种终态（还有个"测试中"由 [testing] 单独表达）。 */
sealed interface TestState {
    data object Idle : TestState
    data object Testing : TestState
    data class Ok(val model: String) : TestState
    data class Failed(val message: String) : TestState
}

/**
 * AI 设置（M4-2）：BYOK 的总开关与四样配置 + 测试连接 + 清除 Key。
 *
 * **AI 默认关**（`llm_enabled = false`）：关着时一切入口都不发请求（§二·19）。
 * 地址**只接受 https**（C2）：填 http 在页面当场报错——正式版禁明文，填了也连不上。
 *
 * **供应商（D113）**：四样配置**每家各存一份**，页面上的读写永远针对"当前这家"；
 * 切换供应商只换显示的值，别家的一个字不动。
 */
class AiSettingsViewModel(private val app: PennedApplication) : ViewModel() {

    private val client = LlmClient()

    private val _test = MutableStateFlow<TestState>(TestState.Idle)
    val test: StateFlow<TestState> = _test

    /**
     * 从设置里读一次（页面进入时调；之后由页面自己管输入态）。
     * 顺带把老用户那份扁平配置认领到对应的家（只做一次，D113）。
     */
    suspend fun load(): AiSettingsUiState {
        app.settings.adoptLegacyLlmConfigIfNeeded()
        // D116：D113 预置错的 Go 地址在这里一次修掉（只认旧预置的精确值）
        app.settings.repairLegacyLlmPresetIfNeeded()
        return currentState()
    }

    /**
     * 切换供应商（D113）：写入"当前是哪家"，把那一家的配置读回来；**空着的才补预置**
     * （地址、模型；视觉模型不预置），用户改过的值一个字不动。
     */
    suspend fun switchProvider(provider: LlmProvider): AiSettingsUiState {
        app.settings.setLlmProvider(provider)
        prefill(provider)
        return currentState()
    }

    /** 只补空：地址与模型空着才填预置（D113）。 */
    private suspend fun prefill(provider: LlmProvider) {
        val fields = app.settings.llmFields(provider)
        provider.presetBaseUrl?.takeIf { fields.baseUrl.isNullOrBlank() }?.let {
            app.settings.setLlmBaseUrl(provider, it)
        }
        provider.presetModel?.takeIf { fields.model.isNullOrBlank() }?.let {
            app.settings.setLlmModel(provider, it)
        }
    }

    /** 当前那家的整页状态（总开关 + 供应商 + 四样）。 */
    private suspend fun currentState(): AiSettingsUiState {
        val provider = LlmProvider.fromId(app.settings.llmProvider.first())
        val fields = app.settings.llmFields(provider)
        return AiSettingsUiState(
            enabled = app.settings.llmEnabled.first(),
            provider = provider,
            baseUrl = fields.baseUrl.orEmpty(),
            apiKey = fields.apiKey.orEmpty(),
            model = fields.model.orEmpty(),
            visionModel = fields.visionModel.orEmpty(),
        )
    }

    fun setEnabled(enabled: Boolean) = persist { app.settings.setLlmEnabled(enabled) }

    fun saveBaseUrl(provider: LlmProvider, url: String) =
        persist { app.settings.setLlmBaseUrl(provider, url.trim().ifEmpty { null }) }

    fun saveApiKey(provider: LlmProvider, key: String) =
        persist { app.settings.setLlmApiKey(provider, key.trim().ifEmpty { null }) }

    fun saveModel(provider: LlmProvider, model: String) =
        persist { app.settings.setLlmModel(provider, model.trim().ifEmpty { null }) }

    fun saveVisionModel(provider: LlmProvider, model: String) =
        persist { app.settings.setLlmVisionModel(provider, model.trim().ifEmpty { null }) }

    /** 只清**当前这家**的 Key（别家的不动）。 */
    fun clearKey(provider: LlmProvider) {
        _test.value = TestState.Idle
        persist { app.settings.setLlmApiKey(provider, null) }
    }

    /**
     * 「测试连接」：**通了就是通了**（发一句最小 prompt）；失败按原因给不同文案。
     * 未配置时按钮本来就不可点，这里再兜一层。
     */
    fun testConnection(snapshot: AiSettingsUiState) {
        val error = LlmEndpoints.validate(
            snapshot.baseUrl.ifBlank { null }, snapshot.apiKey.ifBlank { null }, snapshot.model.ifBlank { null },
        )
        if (error != null) {
            _test.value = TestState.Failed(LlmEndpoints.message(error))
            return
        }
        _test.value = TestState.Testing
        viewModelScope.launch {
            // 与两条导入路径同一份口径：Go 这类网关要求的会话头也一起带上（D116）
            val config = LlmEndpoints.config(
                snapshot.baseUrl, snapshot.apiKey, snapshot.model, snapshot.visionModel,
                extraHeaders = app.settings.llmExtraHeaders(snapshot.provider),
            )
            _test.value = when (val result = client.extractText(config, LlmClient.TEST_PROMPT)) {
                is LlmResult.Ok -> TestState.Ok(result.model)
                is LlmResult.Err -> TestState.Failed(testMessage(result.reason, snapshot.provider))
            }
        }
    }

    /** 把每个失败原因落到**不同的一句话**（页面直接显示）。 */
    private fun testMessage(reason: LlmError, provider: LlmProvider): String = when (reason) {
        LlmError.NOT_CONFIGURED -> "还没配好：服务地址、API Key、模型都要填"
        LlmError.INSECURE_URL -> "只支持 https 地址（正式版禁明文）"
        LlmError.MALFORMED_URL -> "地址格式不对，形如 https://open.bigmodel.cn/api/paas/v4"
        LlmError.NETWORK -> "连不上：检查网络或地址"
        LlmError.TIMEOUT -> "超时：服务没在 60 秒内回话"
        LlmError.UNAUTHORIZED -> if (provider == LlmProvider.OPENCODE_GO) {
            // Go 的 Key 绑 workspace：没订阅 Go / 建在别的 workspace 都会得到同一个 401（D116）
            "Key 无效或没开通 Go（401）：确认 Key 建在已订阅 Go / Go Plus 的 workspace 上"
        } else {
            "Key 无效（401）"
        }
        LlmError.FORBIDDEN -> "没有权限（403）"
        LlmError.RATE_LIMITED -> "被限流了（429），等会儿再试"
        LlmError.REJECTED -> "请求被拒（4xx）：地址或模型名不对，或这个模型不支持这种用法"
        LlmError.SERVER -> "服务端出错（5xx）"
        LlmError.BAD_RESPONSE -> "通了但回的不是账单 JSON（模型或提示词不对）"
    }

    private fun persist(block: suspend () -> Unit) {
        // 设置写入**不能绑页面生命周期**：粘贴完 Key 立刻返回时 viewModelScope 会被取消，
        // 最后一笔写入可能没落盘（D116 排查：App 里仍是旧 Key）。改走应用级协程。
        app.appScopeLaunch(block)
    }
}

@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val viewModel: AiSettingsViewModel = viewModel(initializer = { AiSettingsViewModel(app) })
    val testState by viewModel.test.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 表单**只有一处真相**：进来读一次（null = 还没读到，先不画，免得闪一帧空表单），
    // 之后每次改动立刻写回设置——不再给 remember 挂 stored 做 key（那会在异步回读时把刚敲的字冲掉）。
    var form by remember { mutableStateOf<AiSettingsUiState?>(null) }
    var showKey by remember { mutableStateOf(false) }
    var showProviders by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { form = viewModel.load() }

    val current = form ?: return

    AiSettingsContent(
        enabled = current.enabled,
        provider = current.provider,
        baseUrl = current.baseUrl,
        apiKey = current.apiKey,
        model = current.model,
        visionModel = current.visionModel,
        showKey = showKey,
        testState = testState,
        showProviders = showProviders,
        onBack = onBack,
        onToggleEnabled = { enabled ->
            form = current.copy(enabled = enabled)
            viewModel.setEnabled(enabled)
        },
        onOpenProviders = { showProviders = true },
        onDismissProviders = { showProviders = false },
        onPickProvider = { picked ->
            showProviders = false
            // 同一家 = 只关抽屉，不写任何东西（D113）
            if (picked != current.provider) {
                scope.launch { form = viewModel.switchProvider(picked) }
            }
        },
        onBaseUrlChange = { value ->
            form = current.copy(baseUrl = value)
            viewModel.saveBaseUrl(current.provider, value)
        },
        onApiKeyChange = { value ->
            form = current.copy(apiKey = value)
            viewModel.saveApiKey(current.provider, value)
        },
        onModelChange = { value ->
            form = current.copy(model = value)
            viewModel.saveModel(current.provider, value)
        },
        onVisionModelChange = { value ->
            form = current.copy(visionModel = value)
            viewModel.saveVisionModel(current.provider, value)
        },
        onToggleShowKey = { showKey = !showKey },
        onTest = { viewModel.testConnection(current) },
        onClearKey = {
            form = current.copy(apiKey = "")
            viewModel.clearKey(current.provider)
        },
    )
}

/** 无状态入口：预览 / 截图测试用（不碰 DataStore 与网络）。 */
@Composable
fun AiSettingsContent(
    enabled: Boolean,
    baseUrl: String,
    apiKey: String,
    model: String,
    visionModel: String,
    showKey: Boolean,
    testState: TestState,
    onBack: () -> Unit,
    provider: LlmProvider = LlmProvider.DEFAULT,
    showProviders: Boolean = false,
    onToggleEnabled: (Boolean) -> Unit = {},
    onOpenProviders: () -> Unit = {},
    onDismissProviders: () -> Unit = {},
    onPickProvider: (LlmProvider) -> Unit = {},
    onBaseUrlChange: (String) -> Unit = {},
    onApiKeyChange: (String) -> Unit = {},
    onModelChange: (String) -> Unit = {},
    onVisionModelChange: (String) -> Unit = {},
    onToggleShowKey: () -> Unit = {},
    onTest: () -> Unit = {},
    onClearKey: () -> Unit = {},
) {
    val urlError = baseUrl.ifBlank { null }?.let { LlmEndpoints.validateUrl(it) }
    val configured = LlmEndpoints.validate(
        baseUrl.ifBlank { null }, apiKey.ifBlank { null }, model.ifBlank { null },
    ) == null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "AI 设置", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = PennedSpacing.xxl),
        ) {
            PennedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                PennedSwitchRow(
                    title = "启用 AI 功能",
                    subtitle = AiPrivacyCopy.POWERED_BY,
                    checked = enabled,
                    onCheckedChange = onToggleEnabled,
                )
            }

            PennedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
            ) {
                Column(Modifier.padding(PennedSpacing.md)) {
                    ProviderRow(provider = provider, onClick = onOpenProviders)
                    Text(
                        provider.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = PennedSpacing.xs),
                    )
                    PennedDivider(
                        Modifier.padding(top = PennedSpacing.md, bottom = PennedSpacing.md),
                        startIndent = 0.dp,
                    )

                    FieldLabel("服务地址")
                    PennedNameField(
                        name = baseUrl,
                        placeholder = provider.presetBaseUrl ?: "https://…/v1",
                        onNameChange = onBaseUrlChange,
                        modifier = Modifier.testTag(AI_BASE_URL_FIELD_TAG),
                    )
                    if (urlError != null) {
                        Text(
                            LlmEndpoints.message(urlError),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = PennedSpacing.xs),
                        )
                    }
                    Spacer(Modifier.height(PennedSpacing.md))

                    FieldLabel("API Key")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PennedNameField(
                            name = apiKey,
                            placeholder = "sk-…",
                            onNameChange = onApiKeyChange,
                            visualTransformation = if (showKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(AI_KEY_FIELD_TAG),
                        )
                        Spacer(Modifier.size(PennedSpacing.sm))
                        // 显示/隐藏：小尺寸图标按钮（48dp 热区，D044）
                        Text(
                            if (showKey) "隐藏" else "显示",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .heightIn(min = PennedDimens.touchTarget)
                                .clickable(onClick = onToggleShowKey)
                                .padding(horizontal = PennedSpacing.sm)
                                .align(Alignment.CenterVertically),
                        )
                    }
                    Spacer(Modifier.height(PennedSpacing.md))

                    FieldLabel("模型")
                    PennedNameField(
                        name = model,
                        placeholder = provider.presetModel ?: "模型名",
                        onNameChange = onModelChange,
                        modifier = Modifier.testTag(AI_MODEL_FIELD_TAG),
                    )
                    Spacer(Modifier.height(PennedSpacing.md))

                    FieldLabel("视觉模型（留空 = 跟文本模型）")
                    PennedNameField(
                        name = visionModel,
                        placeholder = provider.presetModel ?: "视觉模型名",
                        onNameChange = onVisionModelChange,
                        modifier = Modifier.testTag(AI_VISION_MODEL_FIELD_TAG),
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
            ) {
                PennedButton(
                    text = if (testState is TestState.Testing) "测试中…" else "测试连接",
                    enabled = configured && testState !is TestState.Testing,
                    onClick = onTest,
                )
                TestResultLine(testState)
                Spacer(Modifier.height(PennedSpacing.sm))
                PennedButton(
                    text = "清除 Key",
                    variant = PennedButtonVariant.Secondary,
                    enabled = apiKey.isNotEmpty(),
                    onClick = onClearKey,
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    dev.penned.app.ui.icons.PennedIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(PennedDimens.iconInline),
                )
                Spacer(Modifier.size(PennedSpacing.sm))
                Text(
                    AiPrivacyCopy.FOOTER,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showProviders) {
        PennedSheet(onDismiss = onDismissProviders, title = "选择供应商") {
            AiProviderSheetContent(current = provider, onPick = onPickProvider)
        }
    }
}

/**
 * 「供应商」字段行（D113）：左标签与四组输入同缩进，右侧当前家名 + 箭头。
 *
 * 形态是**表单里的"选择"字段**而不是卡里的入口行——所以没走 `PennedListRow`
 * （它自带 16dp 内边距与 56dp 行高，塞进这里会与四组输入错位）。
 */
@Composable
private fun ProviderRow(provider: LlmProvider, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.touchTarget)
            .testTag(AI_PROVIDER_ROW_TAG)
            .pennedClickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "供应商",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            provider.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(PennedSpacing.xs))
        Icon(
            dev.penned.app.ui.icons.PennedIcons.ChevronRight,
            contentDescription = null,
            tint = dev.penned.app.ui.theme.iconMutedColor(),
            modifier = Modifier.size(PennedDimens.iconChevron),
        )
    }
}

/**
 * 选择供应商的抽屉内容（D113）：四档各一行，副标题 = 预置地址；当前那行尾部标「已选」。
 *
 * 单独抽出来是为了能**脱离 `ModalBottomSheet` 直接渲染断言**（既有先例：`PennedSheetTest`）。
 */
@Composable
fun AiProviderSheetContent(current: LlmProvider, onPick: (LlmProvider) -> Unit) {
    LlmProvider.entries.forEach { candidate ->
        PennedListRow(
            title = candidate.label,
            subtitle = candidate.presetBaseUrl ?: "地址、模型、Key 都自己填",
            trailing = if (candidate == current) "已选" else null,
            showChevron = false,
            onClick = { onPick(candidate) },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = PennedSpacing.xs),
    )
}

/** 测试结果一行：空 / 成功（绿）/ 失败（红）。 */
@Composable
private fun TestResultLine(state: TestState) {
    val (text, color) = when (state) {
        TestState.Idle -> return
        TestState.Testing -> "正在连…" to MaterialTheme.colorScheme.onSurfaceVariant
        is TestState.Ok -> "连上了：${state.model} 回话正常" to dev.penned.app.ui.theme.incomeColor()
        is TestState.Failed -> state.message to MaterialTheme.colorScheme.error
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(top = PennedSpacing.sm),
    )
}

@Preview(showBackground = true, name = "AI 设置·未配置", widthDp = 400, heightDp = 900)
@Composable
private fun AiSettingsEmptyPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        AiSettingsContent(
            enabled = false, baseUrl = "", apiKey = "", model = "", visionModel = "",
            showKey = false, testState = TestState.Idle, onBack = {},
        )
    }
}
