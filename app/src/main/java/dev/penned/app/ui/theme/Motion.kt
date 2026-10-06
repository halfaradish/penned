package dev.penned.app.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 是否允许播放动效：读系统的"动画时长缩放"（设置 → 开发者选项 → 动画程序时长缩放）。
 * 为 0 时所有转场/动效关闭（原则五：动效回答动作，不做装饰）。
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
}
