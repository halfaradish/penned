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
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedNameField
import dev.penned.app.ui.components.PennedSwitchRow
import dev.penned.app.ui.components.PennedTopBar
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
 */
class AiSettingsViewModel(private val app: PennedApplication) : ViewModel() {

    private val client = LlmClient()

    private val _test = MutableStateFlow<TestState>(TestState.Idle)
    val test: StateFlow<TestState> = _test

    /** 从设置里读一次（页面进入时调；之后由页面自己管输入态）。 */
    suspend fun load(): AiSettingsUiState {
        val loaded = AiSettingsUiState(
            enabled = app.settings.llmEnabled.first(),
            baseUrl = app.settings.llmBaseUrl.first().orEmpty(),
            apiKey = app.settings.llmApiKey.first().orEmpty(),
            model = app.settings.llmModel.first().orEmpty(),
            visionModel = app.settings.llmVisionModel.first().orEmpty(),
        )
        return loaded
    }

    fun setEnabled(enabled: Boolean) = persist { app.settings.setLlmEnabled(enabled) }

    fun saveBaseUrl(url: String) = persist { app.settings.setLlmBaseUrl(url.trim().ifEmpty { null }) }

    fun saveApiKey(key: String) = persist { app.settings.setLlmApiKey(key.trim().ifEmpty { null }) }

    fun saveModel(model: String) = persist { app.settings.setLlmModel(model.trim().ifEmpty { null }) }

    fun saveVisionModel(model: String) =
        persist { app.settings.setLlmVisionModel(model.trim().ifEmpty { null }) }

    fun clearKey() {
        _test.value = TestState.Idle
        persist { app.settings.setLlmApiKey(null) }
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
            val config = LlmEndpoints.config(snapshot.baseUrl, snapshot.apiKey, snapshot.model, snapshot.visionModel)
            _test.value = when (val result = client.extractText(config, LlmClient.TEST_PROMPT)) {
                is LlmResult.Ok -> TestState.Ok(result.model)
                is LlmResult.Err -> TestState.Failed(testMessage(result.reason))
            }
        }
    }

    /** 把每个失败原因落到**不同的一句话**（页面直接显示）。 */
    private fun testMessage(reason: LlmError): String = when (reason) {
        LlmError.NOT_CONFIGURED -> "还没配好：服务地址、API Key、模型都要填"
        LlmError.INSECURE_URL -> "只支持 https 地址（正式版禁明文）"
        LlmError.MALFORMED_URL -> "地址格式不对，形如 https://open.bigmodel.cn/api/paas/v4"
        LlmError.NETWORK -> "连不上：检查网络或地址"
        LlmError.TIMEOUT -> "超时：服务没在 60 秒内回话"
        LlmError.UNAUTHORIZED -> "Key 无效（401）"
        LlmError.FORBIDDEN -> "没有权限（403）"
        LlmError.RATE_LIMITED -> "被限流了（429），等会儿再试"
        LlmError.SERVER -> "服务端出错（5xx）"
        LlmError.BAD_RESPONSE -> "通了但回的不是账单 JSON（模型或提示词不对）"
    }

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val viewModel: AiSettingsViewModel = viewModel(initializer = { AiSettingsViewModel(app) })
    val testState by viewModel.test.collectAsStateWithLifecycle()

    // 表单**只有一处真相**：进来读一次（null = 还没读到，先不画，免得闪一帧空表单），
    // 之后每次改动立刻写回设置——不再给 remember 挂 stored 做 key（那会在异步回读时把刚敲的字冲掉）。
    var form by remember { mutableStateOf<AiSettingsUiState?>(null) }
    var showKey by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { form = viewModel.load() }

    val current = form ?: return

    AiSettingsContent(
        enabled = current.enabled,
        baseUrl = current.baseUrl,
        apiKey = current.apiKey,
        model = current.model,
        visionModel = current.visionModel,
        showKey = showKey,
        testState = testState,
        onBack = onBack,
        onToggleEnabled = { enabled ->
            form = current.copy(enabled = enabled)
            viewModel.setEnabled(enabled)
        },
        onBaseUrlChange = { value ->
            form = current.copy(baseUrl = value)
            viewModel.saveBaseUrl(value)
        },
        onApiKeyChange = { value ->
            form = current.copy(apiKey = value)
            viewModel.saveApiKey(value)
        },
        onModelChange = { value ->
            form = current.copy(model = value)
            viewModel.saveModel(value)
        },
        onVisionModelChange = { value ->
            form = current.copy(visionModel = value)
            viewModel.saveVisionModel(value)
        },
        onToggleShowKey = { showKey = !showKey },
        onTest = { viewModel.testConnection(current) },
        onClearKey = {
            form = current.copy(apiKey = "")
            viewModel.clearKey()
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
    onToggleEnabled: (Boolean) -> Unit = {},
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
                    FieldLabel("服务地址")
                    PennedNameField(
                        name = baseUrl,
                        placeholder = "https://open.bigmodel.cn/api/paas/v4",
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
                        placeholder = "glm-4.6",
                        onNameChange = onModelChange,
                        modifier = Modifier.testTag(AI_MODEL_FIELD_TAG),
                    )
                    Spacer(Modifier.height(PennedSpacing.md))

                    FieldLabel("视觉模型（留空 = 跟文本模型）")
                    PennedNameField(
                        name = visionModel,
                        placeholder = "glm-4v",
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
