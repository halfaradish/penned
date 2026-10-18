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
import dev.penned.app.domain.UpdateChannel
import dev.penned.app.domain.UpdateChannelPolicy
import dev.penned.app.domain.UpdateManifest
import dev.penned.app.domain.UpdateStatus
import dev.penned.app.domain.UpdateThrottle
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * 更新通道（D114）是**用户订阅**，存在 DataStore；安装包版本名只用于首次初始化和手动装 beta 的
 * 自动升舱（[UpdateChannelPolicy]）。所以 beta 用户升到正式版后仍继续读 `update-prerelease.json`，
 * 不会掉回正式通道。
 *
 * [http] 与 [verifier] 可注入——单测用假 HTTP 与假校验，真机走 OkHttp 与 `PackageSignatures`。
 */
class AboutViewModel(
    private val app: PennedApplication,
    private val http: HttpFetcher = OkHttpFetcher(),
    private val verifier: (File) -> Boolean = { PackageSignatures.matchesInstalled(app, it) },
    private val stableManifestUrls: List<String> = defaultStableManifestUrls(),
    private val prereleaseManifestUrls: List<String> = defaultPrereleaseManifestUrls(),
) : ViewModel() {

    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE

    private val userAgent = "penned-update/$versionName"
    private val downloader = UpdateDownloader(http, userAgent)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** 关于页「接收预发布版本」开关（D114）。 */
    private val _prereleaseEnabled = MutableStateFlow(UpdateChannel.isPrerelease(versionName))
    val prereleaseEnabled: StateFlow<Boolean> = _prereleaseEnabled.asStateFlow()

    /** 「安装未知应用」引导弹窗（可取消，D059）。 */
    val needsUnknownSources = MutableStateFlow(false)

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var available: UpdateUiState.Available? = null

    /** 通道的读-判定-写要串行：首次初始化与用户立刻点开关可能同时发生（D114）。 */
    private val channelLock = Mutex()

    init {
        // 先把订阅通道从 DataStore 解出来（beta 用户装正式版时开关应显示为开）
        viewModelScope.launch { currentChannel() }
    }

    /** 手动检查（关于页按钮）：任何结果都要有反馈（规格 M2 S2）。 */
    fun checkNow() {
        if (checkJob?.isActive == true) return
        if (_state.value is UpdateUiState.Checking) return
        // 下载/校验在跑时不重开检查：那会把下载中的界面顶掉，残包白下
        if (downloadJob?.isActive == true) return
        checkJob = viewModelScope.launch { runCheck(currentChannel()) }
    }

    /**
     * 启动时的自动检查（规格 M2 S2）：每天至多一次、无新版静默；有新版且**这个版本还没提示过**时
     * 返回它，交给调用方弹一次可取消的 changelog（D059）。同一版本不重复弹。
     *
     * 自动检查**恒为开**（D064）；「接收预发布版本」是通道开关，不是自动检查开关（D114）。
     */
    suspend fun autoCheckOnLaunch(): UpdateUiState.Available? {
        val lastCheckAt = app.settings.lastUpdateCheckAt.first()
        if (!UpdateThrottle.shouldAutoCheck(System.currentTimeMillis(), lastCheckAt, autoCheckEnabled = true)) {
            return null
        }
        runCheck(currentChannel())
        val status = _state.value as? UpdateUiState.Available ?: return null
        if (!UpdateThrottle.shouldNotify(status.manifest.versionCode, app.settings.notifiedUpdateVersionCode.first())) {
            return null
        }
        app.settings.setNotifiedUpdateVersionCode(status.manifest.versionCode)
        return status
    }

    /**
     * 关于页「接收预发布版本」开关（D114）：显式选择写进 DataStore，之后自动判定不再覆盖。
     *
     * 切通道会取消进行中的检查/下载并清掉旧清单——旧清单属于旧通道，不能拿它去安装。
     */
    fun setPrereleaseEnabled(enabled: Boolean) {
        checkJob?.cancel()
        checkJob = null
        downloadJob?.cancel()
        downloadJob = null
        available = null
        _state.value = UpdateUiState.Idle
        viewModelScope.launch {
            // 放 IO：DataStore 的写不依赖主线程，避免和测试里的 runBlocking 互相等（M2 旧坑同族）
            withContext(Dispatchers.IO) {
                channelLock.withLock {
                    val channel = if (enabled) UpdateChannel.PRERELEASE else UpdateChannel.STABLE
                    app.settings.setUpdateChannel(channel.key, explicit = true)
                    _prereleaseEnabled.value = enabled
                }
            }
        }
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

    private suspend fun runCheck(channel: UpdateChannel) {
        available = null
        _state.value = UpdateUiState.Checking
        val checker = UpdateChecker(
            http = http,
            manifestUrls = UpdateChannel.urlsFor(channel, stableManifestUrls, prereleaseManifestUrls),
            userAgent = userAgent,
            currentVersionCode = versionCode,
            acceptsPrerelease = channel == UpdateChannel.PRERELEASE,
        )
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

    /**
     * 当前订阅通道：读 DataStore → 需要时按安装包初始化/升舱 → 回写（D114）。
     *
     * 整段放 IO：`currentChannel()` 会被 `autoCheckOnLaunch()` 在 `runBlocking` 里调用（测试与启动路径），
     * 若锁/读依赖主线程，会和"主线程被 runBlocking 占着"互相等死。
     */
    private suspend fun currentChannel(): UpdateChannel = withContext(Dispatchers.IO) {
        channelLock.withLock {
            val choice = UpdateChannelPolicy.decide(
                storedKey = app.settings.updateChannel.first(),
                explicit = app.settings.updateChannelExplicit.first(),
                installedVersionName = versionName,
            )
            if (choice.changed) app.settings.setUpdateChannel(choice.channel.key, choice.explicit)
            _prereleaseEnabled.value = choice.channel == UpdateChannel.PRERELEASE
            choice.channel
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
        /** 正式通道清单地址（release 双地址按序回退，规格 M2 S1）。 */
        fun defaultStableManifestUrls(): List<String> =
            BuildConfig.UPDATE_MANIFEST_URLS.toUrlList()

        /** 预发布通道清单地址（D114）：同样双地址回退，正式地址在 `urlsFor` 里再兜底。 */
        fun defaultPrereleaseManifestUrls(): List<String> =
            BuildConfig.UPDATE_MANIFEST_URLS_PRERELEASE.toUrlList()

        private fun String.toUrlList(): List<String> =
            split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
