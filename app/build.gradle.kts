plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.penned.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.penned.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            isMinifyEnabled = false // D012：M2 打开并做体积验收
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 清单候选地址按序回退（规格 M2 S1）；首发托管定稿后换主地址，备地址保持 GitHub raw
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URLS",
                "\"https://raw.githubusercontent.com/halfaradish/penned/main/update.json," +
                    "https://cdn.jsdelivr.net/gh/halfaradish/penned@main/update.json\"",
            )
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
        buildConfig = true // M2：UPDATE_MANIFEST_URLS / 版本号进 BuildConfig
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
