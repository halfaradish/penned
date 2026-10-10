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
 * 用法：`./gradlew bumpVersion -PversionName=0.2.0`（不带 versionName 就只自增 versionCode）
 */
tasks.register("bumpVersion") {
    group = "release"
    description = "versionCode +1（可选 -PversionName=x.y.z），写回 version.properties"
    doLast {
        val file = file("version.properties")
        val props = Properties()
        file.inputStream().use { props.load(it) }
        val newCode = props.getProperty("versionCode").trim().toInt() + 1
        val newName = (project.findProperty("versionName") as String?)?.trim()
            ?: props.getProperty("versionName").trim()
        file.writeText(
            """
            |# 版本号单一来源（M2-7）：app/build.gradle.kts 读这里，**禁手改**
            |# 发布前：./gradlew bumpVersion -PversionName=x.y.z（versionCode 自增 1）
            |versionCode=$newCode
            |versionName=$newName
            """.trimMargin() + "\n",
        )
        println("versionCode -> $newCode，versionName -> $newName（记得提交 version.properties）")
    }
}

/**
 * 生成 update.json（M2-7 / 开发工作流第五节 E）：从**签名** release APK 算 sha256/size。
 *
 * 用法：`./gradlew makeUpdateJson -Purls="https://主/penned.apk,https://备/penned.apk" -Pchangelog="新增：…;修复：…" -PminVersionCode=1`
 * 产物落在仓库根 `update.json`（进仓库，配合托管方发布）。
 */
tasks.register("makeUpdateJson") {
    group = "release"
    description = "从签名 release APK 生成 update.json（-Purls / -Pchangelog / -PminVersionCode）"
    dependsOn(":app:assembleRelease")
    doLast {
        val apk = file("app/build/outputs/apk/release/app-release.apk")
        if (!apk.exists()) {
            throw GradleException("没找到签名 APK：${apk.path}（先配好 local.properties 的签名参数再 assembleRelease）")
        }
        val props = Properties()
        file("version.properties").inputStream().use { props.load(it) }

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
            appendLine("  \"versionCode\": ${props.getProperty("versionCode").trim()},")
            appendLine("  \"versionName\": \"${escape(props.getProperty("versionName").trim())}\",")
            appendLine("  \"minVersionCode\": $minVersionCode,")
            appendLine("  \"changelog\": [${changelog.joinToString(", ") { "\"${escape(it)}\"" }}],")
            appendLine("  \"sha256\": \"$sha256\",")
            appendLine("  \"size\": ${apk.length()},")
            appendLine("  \"urls\": [${urls.joinToString(", ") { "\"${escape(it)}\"" }}]")
            appendLine("}")
        }
        file("update.json").writeText(json)
        println("update.json 已生成：sha256=$sha256 size=${apk.length()} urls=${urls.size}")
    }
}
