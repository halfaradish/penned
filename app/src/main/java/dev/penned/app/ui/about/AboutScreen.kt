package dev.penned.app.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.domain.UpdateManifest
import dev.penned.app.domain.UpdateText
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedDialog
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedProgressBar
import dev.penned.app.ui.components.PennedSwitchRow
import dev.penned.app.ui.components.PennedTextAction
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.SectionCard
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles

/**
 * 「关于 / 更新」页（规格 M2 S6）：版本信息 + 更新状态机 + 自动检查开关 + 隐私一句话。
 *
 * 入口在「我的」页的「关于」行；启动自动检查的提示由 [UpdateAvailableDialog] 承担（PennedApp 层弹）。
 */
@Composable
fun AboutScreen(
    viewModel: AboutViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoCheck.collectAsStateWithLifecycle()
    val needsUnknownSources by viewModel.needsUnknownSources.collectAsStateWithLifecycle()
    val context = LocalContext.current

    AboutContent(
        state = state,
        versionName = viewModel.versionName,
        versionCode = viewModel.versionCode,
        autoCheck = autoCheck,
        needsUnknownSources = needsUnknownSources,
        onBack = onBack,
        onCheck = viewModel::checkNow,
        onDownload = viewModel::download,
        onCancelDownload = viewModel::cancelDownload,
        onRetry = viewModel::retry,
        onInstall = { viewModel.install(context) },
        onToggleAutoCheck = viewModel::setAutoCheck,
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
    autoCheck: Boolean,
    needsUnknownSources: Boolean,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
    onToggleAutoCheck: (Boolean) -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onDismissUnknownSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = { PennedTopBar(title = "关于", onBack = onBack) },
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PennedSpacing.lg)
                .padding(bottom = PennedSpacing.xxl),
        ) {
            Spacer(Modifier.height(PennedSpacing.md))

            SectionCard {
                Text(
                    "一笔一记",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(PennedSpacing.xs))
                Text(
                    "本地优先 · 无服务器",
                    style = PennedTextStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(PennedSpacing.sm))
                Text(
                    "版本 $versionName（$versionCode）",
                    style = PennedTextStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(PennedSpacing.md))

            SectionCard(title = "更新") {
                UpdateSection(
                    state = state,
                    onCheck = onCheck,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onRetry = onRetry,
                    onInstall = onInstall,
                )
                PennedDivider(startIndent = 0.dp)
                PennedSwitchRow(
                    title = "启动时自动检查更新",
                    subtitle = "每天至多一次，没有新版本不会打扰",
                    checked = autoCheck,
                    onCheckedChange = onToggleAutoCheck,
                )
                PennedDivider(startIndent = 0.dp)
                Text(
                    "更新检查只访问静态文件，不携带任何标识。",
                    style = PennedTextStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

@Composable
private fun ColumnScope.UpdateSection(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
) {
    when (state) {
        UpdateUiState.Idle -> {
            PennedButton(text = "检查更新", onClick = onCheck)
        }

        UpdateUiState.Checking -> {
            Text(
                "正在检查更新…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(PennedSpacing.md))
            PennedButton(text = "检查更新", onClick = onCheck, enabled = false)
        }

        is UpdateUiState.UpToDate -> {
            Text(
                "已是最新版本",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(PennedSpacing.xs))
            Text(
                "上次检查 ${UpdateText.checkTimeLabel(state.checkedAtMillis)}",
                style = PennedTextStyles.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(PennedSpacing.md))
            PennedButton(text = "再检查一次", onClick = onCheck, variant = PennedButtonVariant.Secondary)
        }

        is UpdateUiState.Available -> {
            Text(
                "发现新版本 ${state.manifest.versionName}",
                style = MaterialTheme.typography.bodyLarge,
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
                style = MaterialTheme.typography.bodyLarge,
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
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        is UpdateUiState.Ready -> {
            Text(
                "已下载并校验通过",
                style = MaterialTheme.typography.bodyLarge,
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
            Text(
                "更新没成功",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(PennedSpacing.xs))
            Text(
                state.reason,
                style = PennedTextStyles.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(PennedSpacing.md))
            // 「重试」沿用刚才那份清单（续传）；清单本身有问题（摘要/地址）时得能重新拉一份——
            // 否则这里只有一条死路（M2-8 真机验证时踩到）
            PennedButton(text = "重试", onClick = onRetry, variant = PennedButtonVariant.Secondary)
            Spacer(Modifier.height(PennedSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                PennedTextAction(text = "重新检查", onClick = onCheck)
            }
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
