package dev.penned.app.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * 转场口径（原则五：动效回答动作）——
 * 一级界面之间淡入淡出**保持不动**；同一界面点开下钻页时，子页整幅从右推入、父页向左退 1/3
 * 让位（视差），返回时完全反向；进出下钻页时底栏跟着收起/展开。
 *
 * 口径是纯函数，NavHost 与转场测试共用同一套（测试不另抄一份数字）。
 */
internal object MotionSpec {
    /** Tab 之间：淡入淡出，不位移。 */
    const val TAB_FADE_IN = 180
    const val TAB_FADE_OUT = 160

    /** 下钻页推入/推回。 */
    const val PAGE = 300

    /** 底栏收起/展开，与 [PAGE] 同步——父页可用高度跟着一起变，点进子页不再跳一截。 */
    const val BAR = 300

    /** 父页让位位移 = 屏宽 / PARALLAX。 */
    const val PARALLAX = 3

    val ease = FastOutSlowInEasing
}

/** 进入侧播什么。 */
internal enum class Enter { NONE, FADE, PUSH_RIGHT, POP_LEFT }

/** 退出侧播什么。 */
internal enum class Exit { NONE, FADE, PUSH_LEFT, POP_RIGHT }

internal data class ScreenMotion(val enter: Enter, val exit: Exit)

/**
 * 一次导航改动该播的转场（纯口径，只看是不是 Tab、是不是返回）：
 * - 两侧都是 Tab → 淡入淡出（底栏 navigate 带 `popUpTo`，框架可能把这次切换算 pop，两向都覆盖）；
 * - 只要牵扯下钻页 → 推入 / 推回（满幅 + 父页 1/3 视差）；
 * - 系统"动画时长缩放"为 0 → 全关。
 */
internal fun screenMotion(
    initialIsTab: Boolean,
    targetIsTab: Boolean,
    isPop: Boolean,
    motion: Boolean,
): ScreenMotion = when {
    !motion -> ScreenMotion(Enter.NONE, Exit.NONE)
    initialIsTab && targetIsTab -> ScreenMotion(Enter.FADE, Exit.FADE)
    isPop -> ScreenMotion(Enter.POP_LEFT, Exit.POP_RIGHT)
    else -> ScreenMotion(Enter.PUSH_RIGHT, Exit.PUSH_LEFT)
}

/** NavHost 四个 lambda 的公共入口：拿到的是同一个 (initialState, targetState)，只有 isPop 不同。 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.screenMotion(
    tabRoutes: Set<String>,
    isPop: Boolean,
    motion: Boolean,
): ScreenMotion = screenMotion(
    initialIsTab = initialState.destination.route in tabRoutes,
    targetIsTab = targetState.destination.route in tabRoutes,
    isPop = isPop,
    motion = motion,
)

internal fun Enter.toTransition(): EnterTransition = when (this) {
    Enter.NONE -> EnterTransition.None
    Enter.FADE -> fadeIn(tween(MotionSpec.TAB_FADE_IN))
    Enter.PUSH_RIGHT ->
        slideInHorizontally(tween(MotionSpec.PAGE, easing = MotionSpec.ease)) { it }
    Enter.POP_LEFT ->
        slideInHorizontally(tween(MotionSpec.PAGE, easing = MotionSpec.ease)) { -it / MotionSpec.PARALLAX }
}

internal fun Exit.toTransition(): ExitTransition = when (this) {
    Exit.NONE -> ExitTransition.None
    Exit.FADE -> fadeOut(tween(MotionSpec.TAB_FADE_OUT))
    Exit.PUSH_LEFT ->
        slideOutHorizontally(tween(MotionSpec.PAGE, easing = MotionSpec.ease)) { -it / MotionSpec.PARALLAX }
    Exit.POP_RIGHT ->
        slideOutHorizontally(tween(MotionSpec.PAGE, easing = MotionSpec.ease)) { it }
}
