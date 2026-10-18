package dev.penned.app.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * `update.json` v1（规格 M2 S1）——纯静态清单，客户端只读。
 *
 * 解析走平台自带 `org.json`（不引序列化库，D058）；未知字段容忍，**必填字段缺失/类型不符 = 清单无效**
 * （[parse] 返回 null，调用方当"检查失败"处理，不崩）。
 *
 * v1.1（D114）只追加可选字段：[channel]（这次发布本身是正式还是预发布）、[publishedAt]、[tag]。
 * 老客户端忽略它们；老清单缺 `channel` 也照旧有效。
 */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val minVersionCode: Int,
    val changelog: List<String>,
    val sha256: String,
    val size: Long,
    val urls: List<String>,
    val channel: UpdateChannel? = null,
    val publishedAt: String? = null,
    val tag: String? = null,
) {

    /**
     * 与本机版本比较（规格 M2 S2）：`versionCode` 更大才算有新版。
     *
     * [UpdateStatus.Available.stronglyRecommended] 只是文案分级——低于 `minVersionCode` 时提示语写
     * 「强烈建议更新」，**从不强制**（D059）。
     */
    fun statusFor(currentVersionCode: Int): UpdateStatus =
        if (versionCode <= currentVersionCode) {
            UpdateStatus.UpToDate
        } else {
            UpdateStatus.Available(
                manifest = this,
                stronglyRecommended = currentVersionCode < minVersionCode,
            )
        }

    companion object {

        /** 清单必填：`versionCode` / `sha256` / `size` / `urls`；其余可选（`channel` 认不出 = 无效）。 */
        fun parse(json: String): UpdateManifest? = try {
            val obj = JSONObject(json)
            val versionCode = obj.getInt("versionCode")
            val sha256 = obj.getString("sha256")
            val size = obj.getLong("size")
            val urls = obj.getJSONArray("urls").toStringList()
            val channelText = obj.optString("channel").trim()
            val channel = if (channelText.isEmpty()) null else UpdateChannel.ofKey(channelText)
            if (versionCode <= 0 ||
                sha256.isBlank() ||
                size <= 0 ||
                urls.isEmpty() ||
                (channelText.isNotEmpty() && channel == null)
            ) {
                null
            } else {
                UpdateManifest(
                    versionCode = versionCode,
                    versionName = obj.optString("versionName").ifBlank { versionCode.toString() },
                    minVersionCode = obj.optInt("minVersionCode", 0),
                    changelog = obj.optJSONArray("changelog").toStringList(),
                    sha256 = sha256.lowercase(),
                    size = size,
                    urls = urls,
                    channel = channel,
                    publishedAt = obj.optString("publishedAt").takeIf { it.isNotBlank() },
                    tag = obj.optString("tag").takeIf { it.isNotBlank() },
                )
            }
        } catch (_: Exception) {
            null // JSON 坏 / 类型不符 / 必填字段缺失
        }

        /** 数组里的元素只认非空字符串（JSON null 与数字一律跳过）。 */
        private fun JSONArray?.toStringList(): List<String> =
            if (this == null) {
                emptyList()
            } else {
                (0 until length()).mapNotNull { i -> (opt(i) as? String)?.takeIf { it.isNotBlank() } }
            }
    }
}

/** 一次检查的结果（规格 M2 S2）。 */
sealed interface UpdateStatus {

    /** 已是最新（含本机比清单更新——开发中装的高版本号也算）。 */
    data object UpToDate : UpdateStatus

    data class Available(
        val manifest: UpdateManifest,
        val stronglyRecommended: Boolean,
    ) : UpdateStatus
}
