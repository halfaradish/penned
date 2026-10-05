package dev.penned.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import dev.penned.app.PennedApplication

/** 全局提供 Application 实例，供各页 viewModel 初始化使用。 */
val LocalApp = staticCompositionLocalOf<PennedApplication> { error("PennedApplication not provided") }
