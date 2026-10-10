package dev.penned.app.ui.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.penned.app.BuildConfig
import dev.penned.app.PennedApplication
import dev.penned.app.data.update.DownloadResult
import dev.penned.app.data.update.HttpFetcher
import dev.penned.app.data.update.OkHttpFetcher
import dev.penned.app.data.update.PackageSignatures
import dev.penned.app.data.update.UpdateCheckResult
import dev.penned.app.data.update.UpdateChecker
import dev.penned.app.data.update.UpdateDownloader
import dev.penned.app.data.update.UpdateInstaller
import dev.penned.app.domain.UpdateManifest
import dev.penned.app.domain.UpdateStatus
import dev.penned.app.domain.UpdateThrottle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** 更新区的状态机（规格 M2 S6）。 */
sealed interface UpdateUiState {

    /** 还没检查过（显示「检查更新」）。 */
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    /** 已是最新；[checkedAtMillis] 用来显示"上次检查 …"。 */
    data class UpToDate(val checkedAtMillis: Long) : UpdateUiState

    /** 有新版：展示版本号与 changelog；[stronglyRecommended] 只影响文案（从不强制，D059）。 */
    data class Available(val manifest: UpdateManifest, val stronglyRecommended: Boolean) : UpdateUiState

    data class Downloading(val downloaded: Long, val total: Long) : UpdateUiState

    data object Verifying : UpdateUiState

    /** 下完且摘要 + 签名都过，可以进安装流程。 */
    data class Ready(val file: File) : UpdateUiState

    data class Failed(val reason: String) : UpdateUiState
}

/**
 * 「关于 / 更新」页的状态机（规格 M2 S2~S6）。
 *
 * 检查/下载/校验的实活都在 `data/update/` 里（各有自己的测试）；这里只负责：串流程、把结果翻译成
 * [UpdateUiState]、写 DataStore 节流键、以及"要不要弹提示"。
 *
 * [http] 与 [verifier] 可注入——单测用假 HTTP 与假校验，真机走 OkHttp 与 `PackageSignatures`。
 */
class AboutViewModel(
    private val app: PennedApplication,
    http: HttpFetcher = OkHttpFetcher(),
    private val verifier: (File) -> Boolean = { PackageSignatures.matchesInstalled(app, it) },
    manifestUrls: List<String> = defaultManifestUrls(),
) : ViewModel() {

    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE

    private val userAgent = "penned-update/$versionName"
    private val checker = UpdateChecker(http, manifestUrls, userAgent, versionCode)
    private val downloader = UpdateDownloader(http, userAgent)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** 「安装未知应用」引导弹窗（可取消，D059）。 */
    val needsUnknownSources = MutableStateFlow(false)

    private var downloadJob: Job? = null
    private var available: UpdateUiState.Available? = null

    /** 手动检查（关于页按钮）：任何结果都要有反馈（规格 M2 S2）。 */
    fun checkNow() {
        if (_state.value is UpdateUiState.Checking) return
        // 下载/校验在跑时不重开检查：那会把下载中的界面顶掉，残包白下
        if (downloadJob?.isActive == true) return
        viewModelScope.launch { runCheck() }
    }

    /**
     * 启动时的自动检查（规格 M2 S2）：每天至多一次、无新版静默；有新版且**这个版本还没提示过**时
     * 返回它，交给调用方弹一次可取消的 changelog（D059）。同一版本不重复弹。
     *
     * 自动检查**恒为开**（D064）：关于页的开关已下线、留后续里程碑，`SettingsRepository` 的
     * `autoUpdateCheck` 键保留待接回。
     */
    suspend fun autoCheckOnLaunch(): UpdateUiState.Available? {
        val lastCheckAt = app.settings.lastUpdateCheckAt.first()
        if (!UpdateThrottle.shouldAutoCheck(System.currentTimeMillis(), lastCheckAt, autoCheckEnabled = true)) {
            return null
        }
        runCheck()
        val status = _state.value as? UpdateUiState.Available ?: return null
        if (!UpdateThrottle.shouldNotify(status.manifest.versionCode, app.settings.notifiedUpdateVersionCode.first())) {
            return null
        }
        app.settings.setNotifiedUpdateVersionCode(status.manifest.versionCode)
        return status
    }

    /** 下载 + 校验（摘要在下完时校验、签名在这里校验，规格 M2 S3/S4）。 */
    fun download() {
        val target = available ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(0, target.manifest.size)
            val result = downloader.download(target.manifest, targetFile(target.manifest)) { done, total ->
                _state.value = UpdateUiState.Downloading(done, total)
            }
            _state.value = when (result) {
                is DownloadResult.Success -> {
                    _state.value = UpdateUiState.Verifying
                    if (verifier(result.file)) {
                        UpdateUiState.Ready(result.file)
                    } else {
                        result.file.delete()
                        UpdateUiState.Failed("安装包签名与当前版本不一致，已删除")
                    }
                }
                is DownloadResult.Incomplete -> UpdateUiState.Failed(result.reason)
                is DownloadResult.Rejected -> UpdateUiState.Failed(result.reason)
            }
        }
    }

    /** 取消下载：已下的部分留着，回到"可下载"状态（再点即续传，规格 M2 S3）。 */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        available?.let { _state.value = it }
    }

    /** 去安装：没授权"安装未知应用"就先弹引导（规格 M2 S5）。 */
    fun install(context: Context) {
        val ready = _state.value as? UpdateUiState.Ready ?: return
        if (!UpdateInstaller.canInstall(context)) {
            needsUnknownSources.value = true
            return
        }
        launchInstaller(context, ready.file)
    }

    /** 引导里点「去设置」：跳到系统的「安装未知应用」授权页（点了才跳，可取消）。 */
    fun openUnknownSourcesSettings(context: Context) {
        needsUnknownSources.value = false
        try {
            context.startActivity(UpdateInstaller.unknownSourcesSettingsIntent(context))
        } catch (_: Exception) {
            _state.value = UpdateUiState.Failed("打不开系统设置")
        }
    }

    fun dismissUnknownSources() {
        needsUnknownSources.value = false
    }

    /** 失败后重试：有清单就直接续传下载，否则重新检查（规格 M2 S3）。 */
    fun retry() {
        if (available != null) download() else checkNow()
    }

    /** 装完回来（或用户从安装器退回）：文件可能已不存在，回到可下载状态最稳。 */
    fun resetAfterInstallAttempt() {
        available?.let { _state.value = it }
    }

    private suspend fun runCheck() {
        _state.value = UpdateUiState.Checking
        _state.value = when (val result = checker.check()) {
            is UpdateCheckResult.Checked -> {
                // 只有成功拿到并解析出清单才记节流时间（规格 M2 S2）
                app.settings.setLastUpdateCheckAt(System.currentTimeMillis())
                when (val status = result.status) {
                    is UpdateStatus.UpToDate -> UpdateUiState.UpToDate(System.currentTimeMillis())
                    is UpdateStatus.Available -> {
                        available = UpdateUiState.Available(status.manifest, status.stronglyRecommended)
                        available!!
                    }
                }
            }
            is UpdateCheckResult.Failed -> UpdateUiState.Failed(result.reason)
        }
    }

    private fun launchInstaller(context: Context, file: File) {
        try {
            context.startActivity(UpdateInstaller.installIntent(context, file))
        } catch (_: Exception) {
            _state.value = UpdateUiState.Failed("打不开系统安装器")
        }
    }

    private fun targetFile(manifest: UpdateManifest): File =
        File(app.cacheDir, "updates/penned-${manifest.versionCode}.apk")

    companion object {
        /** 清单候选地址来自 BuildConfig（release 双地址按序回退，规格 M2 S1）。 */
        fun defaultManifestUrls(): List<String> =
            BuildConfig.UPDATE_MANIFEST_URLS.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
