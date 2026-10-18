import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

/**
 * 版本号自增（M2-7 / 开发工作流第五节 E：versionCode 由构建脚本生成，禁手改）。
 *
 * 用法：`./gradlew bumpVersion -PversionName=0.2.0` 或 `... -PversionName=0.2.0-beta.1`
 * （不带 versionName 就只自增 versionCode）。
 *
 * 版本名规则（D114）：纯 `x.y.z` = 正式版；`x.y.z-suffix`（dev / test / beta / beta.1 …）= 预发布版。
 * 其余写法直接失败——tag、文件名、清单 URL 都靠这个名字，不能在发版前才发现非法。
 */
tasks.register("bumpVersion") {
    group = "release"
    description = "versionCode +1（可选 -PversionName=x.y.z[-beta.1]），写回 version.properties"
    doLast {
        val file = file("version.properties")
        val props = Properties()
        file.inputStream().use { props.load(it) }
        val newCode = props.getProperty("versionCode").trim().toInt() + 1
        val newName = (project.findProperty("versionName") as String?)?.trim()
            ?: props.getProperty("versionName").trim()
        val versionPattern = Regex("""^\d+\.\d+\.\d+(-[0-9A-Za-z]+(\.[0-9A-Za-z]+)*)?$""")
        if (!versionPattern.matches(newName)) {
            throw GradleException(
                "versionName 不合法：$newName（正式版写 x.y.z，预发布写 x.y.z-beta.1；" +
                    "tag 会拼成 v + versionName）",
            )
        }
        val channel = if (newName.contains('-')) "prerelease" else "stable"
        file.writeText(
            """
            |# 版本号单一来源（M2-7）：app/build.gradle.kts 读这里，**禁手改**
            |# 发布前：./gradlew bumpVersion -PversionName=x.y.z（预发布：x.y.z-beta.1；versionCode 自增 1）
            |versionCode=$newCode
            |versionName=$newName
            """.trimMargin() + "\n",
        )
        println("versionCode -> $newCode，versionName -> $newName，通道 -> $channel（记得提交 version.properties）")
    }
}

/**
 * 生成通道清单（M2-7 / 开发工作流第五节 E / D114）：从**签名** release APK 算 sha256/size。
 *
 * 用法：
 * `./gradlew makeUpdateJson -Pchannel=stable -Purls="https://…/penned.apk" -Pchangelog="新增：…;修复：…"`
 * 可选：`-Pout=update.json`（产物路径，默认仓库根）、`-PminVersionCode=1`、`-PpublishedAt=<ISO>`、`-Ptag=v…`
 *
 * `-Pchannel` 必须与 versionName 一致（纯数字 = stable，带 `-` = prerelease）；CI 分别写
 * `update.json` / `update-prerelease.json`，预发布流水绝不碰正式清单。
 */
tasks.register("makeUpdateJson") {
    group = "release"
    description = "从签名 release APK 生成通道清单（-Pchannel / -Pout / -Purls / -Pchangelog / -PminVersionCode）"
    dependsOn(":app:assembleRelease")
    doLast {
        val apk = file("app/build/outputs/apk/release/app-release.apk")
        if (!apk.exists()) {
            throw GradleException("没找到签名 APK：${apk.path}（先配好 local.properties 的签名参数再 assembleRelease）")
        }
        val props = Properties()
        file("version.properties").inputStream().use { props.load(it) }
        val versionName = props.getProperty("versionName").trim()
        val versionCode = props.getProperty("versionCode").trim()

        // 通道与版本名必须自洽：防止 -Pchannel=stable 把一个 beta 包写进正式清单
        val channel = (project.findProperty("channel") as String?)?.trim() ?: "stable"
        if (channel != "stable" && channel != "prerelease") {
            throw GradleException("channel 只认 stable / prerelease，收到：$channel")
        }
        val nameIsPrerelease = versionName.contains('-')
        if (nameIsPrerelease != (channel == "prerelease")) {
            throw GradleException(
                "channel=$channel 与 versionName=$versionName 不一致" +
                    "（纯数字 = stable，带 - = prerelease）",
            )
        }

        val publishedAt = (project.findProperty("publishedAt") as String?)?.trim()?.takeIf { it.isNotEmpty() }
            ?: java.time.Instant.now().toString()
        try {
            java.time.Instant.parse(publishedAt)
        } catch (_: Exception) {
            throw GradleException("publishedAt 不是 ISO-8601 UTC 时间：$publishedAt（例 2026-10-20T12:34:56Z）")
        }
        val tag = (project.findProperty("tag") as String?)?.trim()?.takeIf { it.isNotEmpty() }
        val outFile = file((project.findProperty("out") as String?)?.trim()?.takeIf { it.isNotEmpty() } ?: "update.json")

        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }

        fun escape(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"")
        val urls = (project.findProperty("urls") as String?)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val changelog = (project.findProperty("changelog") as String?)
            ?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val minVersionCode = (project.findProperty("minVersionCode") as String?)?.trim()?.toIntOrNull() ?: 0

        if (urls.isEmpty()) throw GradleException("至少要给一个下载地址：-Purls=\"https://…/penned.apk\"")
        if (urls.size < 2) println("提醒：清单建议至少 2 个 url（主 + 备镜像），当前只有 ${urls.size} 个")

        val json = buildString {
            appendLine("{")
            appendLine("  \"versionCode\": $versionCode,")
            appendLine("  \"versionName\": \"${escape(versionName)}\",")
            appendLine("  \"channel\": \"$channel\",")
            appendLine("  \"publishedAt\": \"${escape(publishedAt)}\",")
            if (tag != null) appendLine("  \"tag\": \"${escape(tag)}\",")
            appendLine("  \"minVersionCode\": $minVersionCode,")
            appendLine("  \"changelog\": [${changelog.joinToString(", ") { "\"${escape(it)}\"" }}],")
            appendLine("  \"sha256\": \"$sha256\",")
            appendLine("  \"size\": ${apk.length()},")
            appendLine("  \"urls\": [${urls.joinToString(", ") { "\"${escape(it)}\"" }}]")
            appendLine("}")
        }
        outFile.parentFile?.mkdirs()
        outFile.writeText(json)
        println("清单已生成：${outFile.path}（channel=$channel version=$versionName sha256=$sha256 size=${apk.length()}）")
    }
}
