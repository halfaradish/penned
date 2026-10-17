package dev.penned.app.ui.intelligence

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.domain.NotifyParse
import dev.penned.app.domain.PendingStatus
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 「自动记账」页与「待确认」页的测试标记。 */
const val AUTO_ENTRY_STATUS_TAG = "auto-entry-status"
const val PENDING_EMPTY_TAG = "pending-empty"

/** 自动记账页状态。 */
data class AutoEntryUiState(
    val enabled: Boolean = false,
    val whitelist: Set<String> = emptySet(),
    val installedApps: List<AppEntry> = emptyList(),
    val pendingCount: Int = 0,
)

/** 可选 App（白名单里显示"应用名 + 包名"）。 */
data class AppEntry(val packageName: String, val label: String)

/** 自动记账页的 VM（M4-8）：总开关 + 白名单 + 待确认条数。授权与否由页面直接问系统。 */
class AutoEntryViewModel(private val app: PennedApplication) : ViewModel() {

    private val _state = MutableStateFlow(AutoEntryUiState())
    val state: StateFlow<AutoEntryUiState> = _state

    init {
        // 进来就把开关/白名单/可勾 App 全算好：**别依赖页面组合时机**——
        // 页面多组合一次（转场/双实例）就会拿不到列表（真机走查实拍：白名单一直空着）
        val packageManager = app.packageManager
        viewModelScope.launch {
            _state.update {
                it.copy(
                    enabled = app.settings.autoEntryEnabled.first(),
                    whitelist = NotifyParse.whitelist(app.settings.autoEntryPackages.first()),
                    pendingCount = app.database.pendingEntryDao().countPending(),
                )
            }
        }
        viewModelScope.launch { loadApps(packageManager) }
        viewModelScope.launch { observePendingCount() }
    }

    /** 待确认条数跟着候选池走（记完一笔就少一条）。 */
    private suspend fun observePendingCount() {
        app.database.pendingEntryDao().observeCount(dev.penned.app.domain.PendingStatus.PENDING)
            .collect { count -> _state.update { it.copy(pendingCount = count) } }
    }

    /** 可勾的 App 名单：装了通知监听的 App 太多，这里给"常见支付/出行/购物"几个 + 已勾的。 */
    private fun loadApps(pm: PackageManager) {
        runCatching {
            val known = COMMON_APPS.mapNotNull { packageName ->
                runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()?.let {
                    AppEntry(packageName, pm.getApplicationLabel(it).toString())
                }
            }
            val extra = _state.value.whitelist.filter { name -> known.none { it.packageName == name } }
                .map { AppEntry(it, it) }
            // debug：shell 不是"装了的包"（`com.android.shell` 查不到 ApplicationInfo），但 adb 造的通知
            // 就是它发的——单独补一行，release 不显示（规格的 adb 验收配方要用）
            val debugShell = if (dev.penned.app.BuildConfig.DEBUG &&
                known.none { it.packageName == "com.android.shell" }
            ) {
                listOf(AppEntry("com.android.shell", "Shell（adb 走查）"))
            } else {
                emptyList()
            }
            _state.update { it.copy(installedApps = (known + debugShell + extra).distinctBy { a -> a.packageName }) }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
        viewModelScope.launch { app.settings.setAutoEntryEnabled(enabled) }
    }

    fun toggleApp(packageName: String, on: Boolean) {
        val updated = if (on) _state.value.whitelist + packageName else _state.value.whitelist - packageName
        _state.update { it.copy(whitelist = updated) }
        viewModelScope.launch { app.settings.setAutoEntryPackages(updated.sorted().joinToString(",")) }
    }

    private companion object {
        /** 起步给这几个（装了哪个显示哪个）；其余 App 走"手动填包名/后续提案"。 */
        val COMMON_APPS = buildList {
            addAll(
                listOf(
                    "com.tencent.mm",
                    "com.eg.android.AlipayGphone",
                    "com.unionpay",
                    "com.icbc",
                    "com.chinamworld.main",
                    "com.jingdong.app.mall",
                    "com.taobao.taobao",
                )
            )
            // debug：adb 造通知的包名是 shell（规格 §三 M4-8 的验收配方要用它）；release 不显示
            if (dev.penned.app.BuildConfig.DEBUG) add("com.android.shell")
        }
    }
}

/**
 * 「自动记账」页（M4-8）：状态卡（已授权 / 未授权 +「去开启」）+ 总开关（默认**关**）
 * + **App 白名单**（默认空 = 什么都不处理）+「N 条待确认」入口 + 隐私说明。
 */
@Composable
fun AutoEntryScreen(
    onBack: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    notificationAccessGranted: Boolean,
) {
    val app = LocalApp.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: AutoEntryViewModel = viewModel(initializer = { AutoEntryViewModel(app) })
    // 用普通 collectAsState：这一页是推入的详情页，lifecycle 门控会让白名单/条数停在旧值（真机踩到）
    val state by viewModel.state.collectAsState()
    // 33+ 的通知权限只服务"候选提醒"：开自动记账时顺手申请（拒绝也不拦——候选照落库，只是没提醒）
    val askNotificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* 结果不回读：状态卡只讲通知使用权，运行时权限不影响落库 */ }
    AutoEntryContent(
        state = state,
        granted = notificationAccessGranted,
        onBack = onBack,
        onToggleEnabled = { enabled ->
            viewModel.setEnabled(enabled)
            if (shouldAskNotificationPermission(
                    android.os.Build.VERSION.SDK_INT,
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.POST_NOTIFICATIONS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                )
            ) {
                askNotificationPermission.launch(postNotificationsPermission())
            }
        },
        onToggleApp = viewModel::toggleApp,
        onOpenPending = onOpenPending,
        onOpenNotificationSettings = onOpenNotificationSettings,
    )
}

/**
 * 开自动记账时要不要顺手申请 `POST_NOTIFICATIONS`（33+）：**只申请一次口径**，拒绝不拦落库。
 * 单独抽出来是为了能表驱动测（真机上这条请求很难断言）。
 */
internal fun shouldAskNotificationPermission(sdkInt: Int, granted: Boolean): Boolean =
    sdkInt >= 33 && !granted

/**
 * 33+ 的通知权限名。**字符串常量会被内联**，26~32 上根本走不到申请那一支，
 * 所以 lint 的 `InlinedApi`（"Field requires API level 33"）在这里是误报——就地说明并抑制。
 */
@Suppress("InlinedApi")
private fun postNotificationsPermission(): String = android.Manifest.permission.POST_NOTIFICATIONS

/** 无状态入口：截图与测试用。 */
@Composable
fun AutoEntryContent(
    state: AutoEntryUiState,
    granted: Boolean,
    onBack: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit = {},
    onToggleApp: (String, Boolean) -> Unit = { _, _ -> },
    onOpenPending: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "自动记账", onBack = onBack) },
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
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm)
                    .testTag(AUTO_ENTRY_STATUS_TAG),
            ) {
                Text(
                    if (granted) "已获得通知使用权" else "还没有通知使用权",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (granted) {
                        "只读你勾选那几个 App 的通知，其余一概不看"
                    } else {
                        "去系统设置里给「一笔一记」开通知使用权，才能读到支付通知"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!granted) {
                    Spacer(Modifier.height(PennedSpacing.sm))
                    PennedButton(
                        text = "去开启",
                        fillWidth = false,
                        onClick = onOpenNotificationSettings,
                    )
                }
            }

            PennedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                PennedListRow(
                    title = "启用自动记账",
                    subtitle = "识别到一笔先落「待确认」，你点确认才入账（默认关）",
                    showChevron = false,
                    content = {
                        Switch(
                            checked = state.enabled,
                            onCheckedChange = onToggleEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                    },
                )
                PennedDivider()
                PennedListRow(
                    title = "待确认",
                    subtitle = if (state.pendingCount > 0) "${state.pendingCount} 条等你确认" else "暂时没有待确认的",
                    leadingIcon = PennedIcons.Receipt,
                    onClick = onOpenPending,
                )
            }

            PennedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs),
            ) {
                Text(
                    "监视哪些 App（默认一个都不勾）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(PennedSpacing.sm))
                state.installedApps.forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = PennedDimens.touchTarget),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                entry.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = entry.packageName in state.whitelist,
                            onCheckedChange = { on -> onToggleApp(entry.packageName, on) },
                        )
                    }
                }
                if (state.installedApps.isEmpty()) {
                    Text(
                        "没检测到常见的支付类 App——装哪个勾哪个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                androidx.compose.material3.Icon(
                    PennedIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(PennedDimens.iconInline),
                )
                Spacer(Modifier.width(PennedSpacing.sm))
                Text(
                    privacyLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 隐私说明那一行：「不进 AI」加重（别把 `**` 直接印在页面上）。 */
internal fun privacyLine(): AnnotatedString = buildAnnotatedString {
    append("通知原文只存在本机、")
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("不进 AI") }
    append("（不读短信、不装无障碍、不回扫历史通知）；白名单外的 App 一律不看；候选可一键清空。")
}

@Preview(showBackground = true, name = "自动记账", widthDp = 400, heightDp = 800)
@Composable
private fun AutoEntryPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        AutoEntryContent(
            state = AutoEntryUiState(
                enabled = false,
                installedApps = listOf(AppEntry("com.tencent.mm", "微信"), AppEntry("com.eg.android.AlipayGphone", "支付宝")),
            ),
            granted = false,
            onBack = {},
        )
    }
}

/** 候选行的来源名（纯映射；不是 Composable）。 */
internal fun pendingSourceLabel(packageName: String?): String = when (packageName) {
    "com.tencent.mm" -> "微信"
    "com.eg.android.AlipayGphone" -> "支付宝"
    null -> "截图"
    else -> packageName
}

/** 待确认条数（给「我的」页那行副标题用）。 */
internal fun pendingSubtitle(count: Int): String =
    if (count > 0) "$count 条待确认" else "没有待确认"

/** 白名单徽标（第三方 App 名）。 */
@Composable
internal fun WhitelistBadges(packages: Set<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PennedSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(PennedSpacing.xs),
    ) {
        packages.sorted().forEach { packageName ->
            Text(
                packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 未读候选的状态（页面顶部提示用）。 */
internal fun hasPending(statuses: List<PendingStatus>): Boolean = statuses.any { it == PendingStatus.PENDING }
