package dev.penned.app.data.update

import dev.penned.app.domain.UpdateChannel
import dev.penned.app.domain.UpdateManifest
import dev.penned.app.domain.UpdateStatus

/** 一次检查的结果（规格 M2 S2）：要么拿到结论，要么给一句能显示给用户的原因。 */
sealed interface UpdateCheckResult {

    data class Checked(val status: UpdateStatus) : UpdateCheckResult

    data class Failed(val reason: String) : UpdateCheckResult
}

/**
 * 更新检查器（规格 M2 S2）：按序试候选清单地址 → 解析 → 与本机版本比较。
 *
 * 只管"拿清单"，不管节流与提示（那是调用方 + [dev.penned.app.domain.UpdateThrottle] 的事），
 * 也不碰 Android API——纯 Kotlin，可直接用 fake 单测。
 */
class UpdateChecker(
    private val http: HttpFetcher,
    private val manifestUrls: List<String>,
    private val userAgent: String,
    private val currentVersionCode: Int,
    /** 订阅的是不是预发布通道（D114）：false 时拒绝任何预发布清单，绝不把 beta 推给正式版。 */
    private val acceptsPrerelease: Boolean = false,
) {

    suspend fun check(): UpdateCheckResult {
        if (manifestUrls.isEmpty()) return UpdateCheckResult.Failed("没有配置更新地址")

        var failure = "网络不可用"
        for (url in manifestUrls) {
            val body = http.getText(url, userAgent)
            if (body == null) {
                failure = "更新清单下载失败"
                continue
            }
            val manifest = UpdateManifest.parse(body)
            if (manifest == null) {
                failure = "更新清单格式不对"
                continue
            }
            // 正式版客户端双重拒绝：清单标了 prerelease，或版本名带 "-"（老清单没 channel 时也拦得住）
            if (!acceptsPrerelease &&
                (manifest.channel == UpdateChannel.PRERELEASE || manifest.versionName.contains('-'))
            ) {
                failure = "更新清单通道不符"
                continue
            }
            return UpdateCheckResult.Checked(manifest.statusFor(currentVersionCode))
        }
        return UpdateCheckResult.Failed(failure)
    }
}
