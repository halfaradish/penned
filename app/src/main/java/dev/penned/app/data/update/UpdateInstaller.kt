package dev.penned.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 安装链路（规格 M2 S5）：FileProvider content URI + 系统安装器 Intent + 「安装未知应用」引导。
 *
 * 只负责"造 Intent / 查权限"，不负责启动——UI 层拿去 `startActivity`，测试也就能直接断言 Intent 组成。
 *
 * 注：Robolectric 读不到 provider 的 `paths` 元数据（所有根都匹配不上），所以 [apkUri] 这段在单测里
 * 只验"配置存在"（[authority]），真机上还要走一遍（M2-8 闭环验证里点名）。
 */
object UpdateInstaller {

    private const val APK_MIME = "application/vnd.android.package-archive"

    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /** 是否已允许"安装未知应用"（Android 8+；更低版本不需要这个权限，恒 true）。 */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** 系统设置里的「安装未知应用」授权页（按包名直达）。 */
    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * 安装 Intent 的组成（规格 M2 S5）——纯函数，可单测。
     *
     * `ACTION_VIEW` + package-archive MIME 是现在的正路（`ACTION_INSTALL_PACKAGE` 已废弃）；
     * content URI 必须带读权限授予，否则系统安装器打不开。
     */
    fun installIntent(apkUri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** 待装 APK 的 content URI：FileProvider 只暴露 `cacheDir/updates/`（见 `res/xml/file_paths.xml`）。 */
    fun apkUri(context: Context, apk: File): Uri =
        FileProvider.getUriForFile(context, authority(context), apk)

    fun installIntent(context: Context, apk: File): Intent = installIntent(apkUri(context, apk))
}
