package dev.penned.app.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dev.penned.app.domain.ApkSignature
import java.io.File

/**
 * 读"已装包"与"待装 APK"的签名证书（规格 M2 S4）。
 *
 * API 28+ 走 `GET_SIGNING_CERTIFICATES` / `apkContentsSigners`；API 26~27 退回 `GET_SIGNATURES` / `signatures`
 * （minSdk 26，必须留这条路）。证书读不到（文件不是 APK / 解析失败）一律当空集 → 判不一致 → 拒绝安装。
 */
object PackageSignatures {

    fun installedCerts(context: Context): List<ByteArray> = try {
        certs(context.packageManager.getPackageInfo(context.packageName, flags()))
    } catch (_: Exception) {
        emptyList()
    }

    fun archiveCerts(context: Context, apk: File): List<ByteArray> = try {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags())
        if (info == null) emptyList() else certs(info)
    } catch (_: Exception) {
        emptyList()
    }

    /** 新包签名是否与已装一致——不一致就不许进安装流程（规格 M2 S4）。 */
    fun matchesInstalled(context: Context, apk: File): Boolean =
        ApkSignature.sameSigners(installedCerts(context), archiveCerts(context, apk))

    private fun flags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun certs(info: PackageInfo): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toByteArray() } ?: emptyList()
        }
}
