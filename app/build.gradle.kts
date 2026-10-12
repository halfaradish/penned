import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 版本号单一来源（M2-7）：仓库根 version.properties，禁手改；发布前 ./gradlew bumpVersion
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
// release 签名参数（M2-7）：只从 local.properties 读（不入库），keystore 也在仓库外
val signingProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
// 本地闭环测试开关（M2-8，默认关，发布物不受影响）：
//   -PtestManifestUrls=http://10.0.2.2:8000/update.json  覆盖 release 的清单地址
//   -PtestCleartext=true                                  放行明文（只配本地 HTTP 假服务时用）
// 线上地址的连通性与真实托管在 M2-10 发布时验；这两个开关只为"release 配置的闭环"能在本机跑。
val testManifestUrls = (project.findProperty("testManifestUrls") as String?)?.trim()
val testCleartext = project.findProperty("testCleartext") == "true"

/** release 的清单候选地址（规格 M2 S1）：主地址等首发托管定稿后换，备地址保持 GitHub raw。 */
val RELEASE_MANIFEST_URLS =
    "https://raw.githubusercontent.com/halfaradish/penned/main/update.json," +
        "https://cdn.jsdelivr.net/gh/halfaradish/penned@main/update.json"

android {
    namespace = "dev.penned.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.penned.app"
        minSdk = 26
        targetSdk = 36
        versionCode = versionProps.getProperty("versionCode").trim().toInt()
        versionName = versionProps.getProperty("versionName").trim()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 项目主页（关于页「项目主页」行跳转用；github.com/{user}/{repo} 由 origin 派生，仓库不换就永远是它）
        buildConfigField(
            "String",
            "REPO_URL",
            "\"https://github.com/halfaradish/penned\"",
        )
        // 明文策略显式化：release 默认禁（targetSdk 28+ 的默认值，写出来是为了可查）；
        // debug 走 src/debug 的 networkSecurityConfig（只放行 10.0.2.2）
        manifestPlaceholders["pennedCleartext"] = if (testCleartext) "true" else "false"
    }

    signingConfigs {
        create("release") {
            val storePath = signingProps.getProperty("penned.storeFile")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = signingProps.getProperty("penned.storePassword")
                keyAlias = signingProps.getProperty("penned.keyAlias")
                keyPassword = signingProps.getProperty("penned.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // M2 闭环验证：模拟机经 10.0.2.2 连宿主的假清单服务（明文仅 debug 放行，见 src/debug）
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URLS",
                "\"http://10.0.2.2:8000/update.json\"",
            )
        }
        release {
            isMinifyEnabled = true // D012：M2 打开并做体积验收
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 清单候选地址按序回退（规格 M2 S1）；首发托管定稿后换主地址，备地址保持 GitHub raw。
            // -PtestManifestUrls 只为本地闭环覆盖（默认走下面的线上地址）
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URLS",
                "\"${testManifestUrls ?: RELEASE_MANIFEST_URLS}\"",
            )
            // 有签名配置就签（local.properties 里配了 penned.storeFile），没配就照旧出 unsigned
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 显式钉住编译工具链（与 compileOptions / jvmTarget 的 17 一致）：不钉的话 AGP 会拿
    // "当前 JVM"（= Gradle 守护进程所在 JVM）当工具链，而编辑器扩展自带的 JRE 没有 jlink，
    // JdkImageTransform 会直接失败（2026-10-10 实际踩到：VS Code 的 Gradle 扩展起了 JRE 守护进程）
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true // M2：UPDATE_MANIFEST_URLS / 版本号进 BuildConfig；REPO_URL 供关于页跳转
    }

    sourceSets {
        // Room 迁移测试读取本目录下的 schema JSON
        getByName("test") {
            assets.srcDir("schemas")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric 在 JDK17+ 上反射 FileDescriptor 需要 add-opens，否则
                // "Failed to interact with raw FileDescriptor internals"
                it.jvmArgs(
                    "-Xmx2g",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                )
                // Roborazzi：不加此属性时 captureRoboImage 静默跳过；M1 引入插件后改走 record/verify 任务
                it.systemProperty("roborazzi.test.record", "true")
                // release 变体单测与 debug 跑同一批代码，且 Robolectric compose 测试在 release
                // 变体下有 activity 解析问题（D017），只保留 debug 单测
                if (it.name.contains("Release")) it.enabled = false
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
