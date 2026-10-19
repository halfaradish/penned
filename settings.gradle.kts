pluginManagement {
    repositories {
        // 阿里云曾对 KSP 2.3.12 返回 502；Gradle 遇 502 会终止插件解析，不再尝试后续仓库。
        // KSP 组固定走 Maven Central（CI 实测 200），其余依赖仍保持原镜像顺序。
        exclusiveContent {
            forRepository {
                maven("https://repo.maven.apache.org/maven2")
            }
            filter {
                includeGroup("com.google.devtools.ksp")
            }
        }
        // dl.google.com 在本机不可达，镜像顺序不得调整（见 AGENTS.md / decisions D010）
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        mavenCentral()
        gradlePluginPortal()
        // CI 已验证可达；放在现有源之后兜底，避免单镜像瞬时解析失败（不改依赖版本）
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS：本机全局 init 脚本会注入项目级仓库，忽略之，以本文件声明为准
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        exclusiveContent {
            forRepository {
                maven("https://repo.maven.apache.org/maven2")
            }
            filter {
                includeGroup("com.google.devtools.ksp")
            }
        }
        maven("https://maven.aliyun.com/repository/google")
        mavenCentral()
        maven("https://maven.aliyun.com/repository/public")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    }
}

rootProject.name = "penned"
include(":app")
