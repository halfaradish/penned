package dev.penned.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 动效时长四档（docs/界面规范.md 第七节，D044）：曲线统一 [PennedMotion.ease]。
 * 转场口径（Tab 淡入淡出、下钻推入与 1/3 视差）仍由 `ui/nav/ScreenTransitions.kt` 的
 * `MotionSpec` 独占——那里的数字引用这里的档位，别在两处各写一遍。
 */
object PennedMotion {
    /** 按压反馈（缩放/按压底色）。 */
    const val instant = 120

    /** Tab 之间淡入淡出（沿用 D041 的 180/160）。 */
    const val fastIn = 180
    const val fastOut = 160

    /** 分段滑块位移、展开/收拢、chip 选中、数字滚动。 */
    const val base = 220

    /** 下钻页推入 + 父页 1/3 视差 + 底栏收展。 */
    const val page = 300

    val ease = FastOutSlowInEasing
}

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
