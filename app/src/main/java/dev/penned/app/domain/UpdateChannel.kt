package dev.penned.app.domain

/**
 * 更新通道（D114）：正式版只订 stable；预发布版订 prerelease（同时包含后续正式版）。
 *
 * 通道是**用户订阅**，不是"当前安装包是什么"——所以它存在 DataStore 里，安装包版本名只用于
 * 首次初始化和"手动装 beta"的自动升舱（见 [UpdateChannelPolicy]）。
 */
enum class UpdateChannel(val key: String) {
    STABLE("stable"),
    PRERELEASE("prerelease"),
    ;

    companion object {
        private val STABLE_VERSION = Regex("""^\d+\.\d+\.\d+$""")

        fun ofKey(key: String?): UpdateChannel? = entries.firstOrNull { it.key == key }

        /**
         * 安装包版本名是否预发布：纯 `x.y.z` 才是正式版，其余一律当预发布。
         *
         * fail-safe 方向：宁可让畸形版本去读 beta 清单，也不让正式版误读 beta。
         */
        fun isPrerelease(versionName: String): Boolean = !STABLE_VERSION.matches(versionName.trim())

        /**
         * 订阅通道 → 清单候选地址（规格 M2 S1 的按序回退）。
         *
         * 预发布先读自己的清单，再把正式地址作为兜底（beta 清单暂时拉不到时，正式版若更新仍能拿到）。
         */
        fun urlsFor(
            channel: UpdateChannel,
            stableUrls: List<String>,
            prereleaseUrls: List<String>,
        ): List<String> =
            if (channel == PRERELEASE) (prereleaseUrls + stableUrls).distinct() else stableUrls
    }
}

/** 通道判定结果（D114）：[changed] = 需要把决定写回 DataStore。 */
data class UpdateChannelChoice(
    val channel: UpdateChannel,
    val explicit: Boolean,
    val changed: Boolean,
)

/**
 * 通道判定（D114）。
 *
 * 四条例：
 * 1. 没存过 → 按安装包版本名初始化（全新装正式版 = stable，全新装 beta = prerelease）。
 * 2. 存过且用户显式选过 → 永远听用户的（关于页开关）。
 * 3. 存的是稳定版、装上的却是预发布包（手动装了 beta）→ 自动升舱到 prerelease。
 * 4. 存的是预发布、装上的却是正式版（beta 用户升级到正式版）→ **保持 prerelease**。
 *
 * 第 4 条是这套设计的核心：正式版安装不降级订阅，否则 `0.2.0-beta.1 → 0.2.0` 之后
 * 就再也收不到 `0.3.x-beta`。
 */
object UpdateChannelPolicy {

    fun decide(
        storedKey: String?,
        explicit: Boolean,
        installedVersionName: String,
    ): UpdateChannelChoice {
        val installed = if (UpdateChannel.isPrerelease(installedVersionName)) {
            UpdateChannel.PRERELEASE
        } else {
            UpdateChannel.STABLE
        }
        val stored = UpdateChannel.ofKey(storedKey)

        return when {
            // 没存过（或存了个认不出的值）：按安装包初始化
            stored == null ->
                UpdateChannelChoice(installed, explicit = false, changed = true)

            // 关于页开关写过：显式选择优先，自动规则不再覆盖
            explicit ->
                UpdateChannelChoice(stored, explicit = true, changed = false)

            // 正式版设备上手动装了 beta：自动加入预发布
            stored == UpdateChannel.STABLE && installed == UpdateChannel.PRERELEASE ->
                UpdateChannelChoice(UpdateChannel.PRERELEASE, explicit = false, changed = true)

            // 预发布用户升到正式版：保持订阅（不降级）
            else ->
                UpdateChannelChoice(stored, explicit = false, changed = false)
        }
    }
}
