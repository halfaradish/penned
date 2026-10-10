package dev.penned.app.domain

/**
 * APK 签名一致性判定（规格 M2 S4）——纯函数，表驱动单测。
 *
 * 防的是"镜像/CDN 被换包"：新包的签名证书必须与已装版本**逐一致**（顺序无关、多签名场景也要对齐）。
 * 读证书是 Android 侧的事（[dev.penned.app.data.update.PackageSignatures]），这里只管比。
 */
object ApkSignature {

    fun sameSigners(installed: List<ByteArray>, candidate: List<ByteArray>): Boolean {
        if (installed.isEmpty() || candidate.isEmpty()) return false
        if (installed.size != candidate.size) return false
        return installed.all { a -> candidate.any { b -> a.contentEquals(b) } }
    }
}
