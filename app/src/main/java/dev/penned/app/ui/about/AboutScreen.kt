package dev.penned.app.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.domain.UpdateManifest
import dev.penned.app.domain.UpdateText
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedDialog
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedProgressBar
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.SectionCard
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles

/** 卡内分隔线与行内文字左对齐（图标 32 + 屏边距 16 + 图标后间距 16，与「我的」页同款）。 */
private val ROW_DIVIDER_INDENT = 64.dp

/** App 标记边长与其中笔形的大小（笔占方块约 1/3，与启动图标同一比例）。 */
private val MARK_SIZE = 72.dp
private val MARK_GLYPH = 60.dp

/**
 * 「关于 / 更新」页（规格 M2 S6，UI-16 改版）：
 * 品牌头（App 标记 + 名字）→ 版本 / 更新卡 → 页脚隐私一句话。
 *
 * 入口在「我的」页的「关于」行；启动自动检查的提示由 [UpdateAvailableDialog] 承担（PennedApp 层弹）。
 * 自动检查**恒为开**（D064，开关留后续里程碑），所以本页没有开关行。
 */
@Composable
fun AboutScreen(
    viewModel: AboutViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val needsUnknownSources by viewModel.needsUnknownSources.collectAsStateWithLifecycle()
    val context = LocalContext.current

    AboutContent(
        state = state,
        versionName = viewModel.versionName,
        versionCode = viewModel.versionCode,
        needsUnknownSources = needsUnknownSources,
        onBack = onBack,
        onCheck = viewModel::checkNow,
        onDownload = viewModel::download,
        onCancelDownload = viewModel::cancelDownload,
        onRetry = viewModel::retry,
        onInstall = { viewModel.install(context) },
        onOpenUnknownSourcesSettings = { viewModel.openUnknownSourcesSettings(context) },
        onDismissUnknownSources = viewModel::dismissUnknownSources,
        modifier = modifier,
    )
}

/** 无状态入口：截图测试与预览用（不碰 DB / DataStore / 网络）。 */
@Composable
fun AboutContent(
    state: UpdateUiState,
    versionName: String,
    versionCode: Int,
    needsUnknownSources: Boolean,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onDismissUnknownSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 检查 / 下载 / 校验进行中时这一行不再是入口（不然点一下会把下载中的界面顶掉）
    val busy = state is UpdateUiState.Checking ||
        state is UpdateUiState.Downloading ||
        state is UpdateUiState.Verifying

    Scaffold(
        topBar = { PennedTopBar(title = "关于", onBack = onBack) },
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                BrandHeader()

                Spacer(Modifier.height(PennedSpacing.xxl))

                SectionCard {
                    PennedListRow(
                        title = "当前版本",
                        leadingIcon = PennedIcons.Info,
                        trailing = "$versionName（$versionCode）",
                    )
                    PennedDivider(startIndent = ROW_DIVIDER_INDENT)
                    PennedListRow(
                        title = "检查更新",
                        subtitle = statusSubtitle(state),
                        leadingIcon = PennedIcons.Refresh,
                        trailing = statusTrailing(state),
                        onClick = if (busy) null else onCheck,
                    )
                    if (hasUpdateDetail(state)) {
                        PennedDivider(startIndent = 0.dp)
                        UpdateDetail(
                            state = state,
                            onDownload = onDownload,
                            onCancelDownload = onCancelDownload,
                            onRetry = onRetry,
                            onInstall = onInstall,
                        )
                    }
                }
            }

            Footer()
        }
    }

    if (needsUnknownSources) {
        PennedDialog(
            onDismiss = onDismissUnknownSources,
            title = "需要允许安装应用",
            confirmLabel = "去设置",
            onConfirm = onOpenUnknownSourcesSettings,
            dismissLabel = "取消",
            content = {
                Text(
                    "系统默认不允许应用自己装更新包。请在接下来的设置页里允许「一笔一记」安装应用，" +
                        "再回来点「去安装」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

/**
 * 品牌头：标记 + App 名 + 一句话定位。
 *
 * 底是一条**极淡的单色渐变**（白 → 日头条那档淡青灰 → 页面底）——QQ 式品牌区的克制版：
 * 不引渐变图片、不加光斑，色相只靠 `surfaceContainerLow` 那一档（彩度 6%，与日头条同值）。
 */
@Composable
private fun BrandHeader() {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.00f to MaterialTheme.colorScheme.surface,
                    0.34f to MaterialTheme.colorScheme.surfaceContainerLow,
                    0.66f to MaterialTheme.colorScheme.surfaceContainerLow,
                    1.00f to MaterialTheme.colorScheme.background,
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = PennedSpacing.xl, bottom = PennedSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(MARK_SIZE)
                    .clip(RoundedCornerShape(PennedSpacing.xl))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PennedIcons.Pen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(MARK_GLYPH),
                )
            }
            Spacer(Modifier.height(PennedSpacing.md))
            Text(
                "一笔一记",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(PennedSpacing.xs))
            Text(
                "本地优先 · 无服务器",
                style = PennedTextStyles.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 页脚：隐私口径（规格 M2 S2「请求不带任何标识」）——居中、小字，贴页面底。 */
@Composable
private fun Footer() {
    Text(
        "更新检查只访问静态文件，不携带任何标识。",
        style = PennedTextStyles.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PennedSpacing.huge, vertical = PennedSpacing.xl),
    )
}

/** 「检查更新」行右侧的状态摘要（状态机一屏只在一处说，细节留给详情块）。 */
private fun statusTrailing(state: UpdateUiState): String = when (state) {
    UpdateUiState.Idle -> "未检查"
    UpdateUiState.Checking -> "检查中…"
    is UpdateUiState.UpToDate -> "已是最新"
    is UpdateUiState.Available -> "有新版本"
    is UpdateUiState.Downloading -> "下载中…"
    UpdateUiState.Verifying -> "校验中…"
    is UpdateUiState.Ready -> "待安装"
    is UpdateUiState.Failed -> "未成功"
}

/** 行副标题：只在"没检查过"（说清自动检查的节奏）与"已最新"（上次检查时间）时有话说。 */
private fun statusSubtitle(state: UpdateUiState): String? = when (state) {
    UpdateUiState.Idle -> "每天自动检查一次"
    is UpdateUiState.UpToDate -> "上次检查 ${UpdateText.checkTimeLabel(state.checkedAtMillis)}"
    else -> null
}

/** 需要展开详情块的状态（Idle / Checking / Verifying / UpToDate 的信息在行上就说完了）。 */
private fun hasUpdateDetail(state: UpdateUiState): Boolean = when (state) {
    is UpdateUiState.Available,
    is UpdateUiState.Downloading,
    is UpdateUiState.Ready,
    is UpdateUiState.Failed,
    -> true

    UpdateUiState.Idle,
    UpdateUiState.Checking,
    UpdateUiState.Verifying,
    is UpdateUiState.UpToDate,
    -> false
}

/** 更新详情块：状态机里"有事要做"的那几档（规格 M2 S6），按钮永远铺满卡片内宽。 */
@Composable
private fun UpdateDetail(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
    ) {
        when (state) {
            is UpdateUiState.Available -> {
                Text(
                    "新版本 ${state.manifest.versionName}",
                    style = PennedTextStyles.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.stronglyRecommended) {
                    Spacer(Modifier.height(PennedSpacing.xs))
                    // 只是文案分级：写得重一点，但从不拦着用户（D059）
                    Text(
                        "强烈建议更新",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(PennedSpacing.xs))
                Text(
                    "安装包 ${UpdateText.sizeLabel(state.manifest.size)}",
                    style = PennedTextStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.manifest.changelog.isNotEmpty()) {
                    Spacer(Modifier.height(PennedSpacing.sm))
                    Changelog(state.manifest.changelog)
                }
                Spacer(Modifier.height(PennedSpacing.md))
                PennedButton(text = "下载更新", onClick = onDownload)
            }

            is UpdateUiState.Downloading -> {
                Text(
                    "正在下载 ${UpdateText.progressPercent(state.downloaded, state.total)}%",
                    style = PennedTextStyles.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(PennedSpacing.md))
                PennedProgressBar(
                    progress = if (state.total > 0L) state.downloaded.toFloat() / state.total else 0f,
                )
                Spacer(Modifier.height(PennedSpacing.md))
                PennedButton(text = "取消下载", onClick = onCancelDownload, variant = PennedButtonVariant.Secondary)
            }

            UpdateUiState.Verifying -> {
                Text(
                    "正在校验安装包…",
                    style = PennedTextStyles.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            is UpdateUiState.Ready -> {
                Text(
                    "已下载并校验通过",
                    style = PennedTextStyles.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(PennedSpacing.xs))
                Text(
                    "点下面的按钮交给系统安装，装完就是新版本。",
                    style = PennedTextStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(PennedSpacing.md))
                PennedButton(text = "去安装", onClick = onInstall)
            }

            is UpdateUiState.Failed -> {
                // 不写"更新没成功"这类标题：行上的状态已经是"未成功"，这块只补原因（同一信息只留一处）
                Text(
                    state.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(PennedSpacing.md))
                // 只有一个动作：`retry()` 自己分路——手上有清单就续传下载，没清单就重新检查
                // （M2-8 那条死路的出路）。「重新检查」不再单列：重新拉清单的入口就是上面那行
                // 「检查更新」，块里再放一个只是把同一个动作写两遍。
                PennedButton(text = "重试", onClick = onRetry)
            }

            // Idle / Checking / Verifying / UpToDate 不渲染详情块（调用方已用 hasUpdateDetail 挡住）
            else -> Unit
        }
    }
}

@Composable
private fun Changelog(items: List<String>) {
    Column {
        items.forEach { item ->
            Text(
                "· $item",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 更新提示弹窗（启动自动检查发现新版时弹一次）。
 *
 * **一律可取消**（点遮罩 / 返回键 / 「稍后」都行）——本 App 从不强制更新（D059）。
 */
@Composable
fun UpdateAvailableDialog(
    manifest: UpdateManifest,
    stronglyRecommended: Boolean,
    onDismiss: () -> Unit,
    onViewDetails: () -> Unit,
) {
    PennedDialog(
        onDismiss = onDismiss,
        title = "发现新版本 ${manifest.versionName}",
        confirmLabel = "查看更新",
        onConfirm = onViewDetails,
        dismissLabel = "稍后",
        content = {
            if (stronglyRecommended) {
                Text(
                    "强烈建议更新",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(PennedSpacing.xs))
            }
            Changelog(manifest.changelog)
        },
    )
}
