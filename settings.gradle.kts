pluginManagement {
    repositories {
        // dl.google.com 在本机不可达，镜像顺序不得调整（见 AGENTS.md / decisions D010）
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS：本机全局 init 脚本会注入项目级仓库，忽略之，以本文件声明为准
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        mavenCentral()
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "penned"
include(":app")
