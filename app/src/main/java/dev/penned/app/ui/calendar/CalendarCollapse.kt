package dev.penned.app.ui.calendar

import androidx.compose.ui.unit.dp

/** 日历收起态的几何/阈值常量（具名常量，别散着写 dp）。 */
internal object CalendarCollapse {

    /** 累计上滑这么多才收起（与悬浮记账入口同档：太小会被惯性抖动反复触发）。 */
    val collapseAfter = 24.dp
}

/**
 * 收起/展开的状态机（纯逻辑，表驱动单测）——照 `AddEntryScrollGate` 的范式：
 * **往下看累计够了才收、只有回到顶才复原**（UI-10 的用户裁决口径：往回滚一像素就弹开是被否掉的）。
 *
 * 与悬浮入口的差别只有一处：日历的"顶"是**当日流水列表的顶**，而且展开态下上滑要先把日历收起来
 * （位移由调用方吃掉，收完再放行给列表滚动）——所以这里不吃"在顶就展开"，
 * 展开只发生在"往回滚 **且** 列表已经在顶"的时候。
 *
 * @return true = 展开、false = 收起、null = 保持原状
 */
internal class CalendarCollapseGate(private val collapseAfterPx: Float) {
    private var advance = 0f

    fun onScroll(deltaY: Float, atTop: Boolean): Boolean? = when {
        // deltaY < 0 = 内容往上走 = 继续往下看：累计够了才收起
        deltaY < 0f -> {
            advance += -deltaY
            if (advance >= collapseAfterPx) {
                advance = 0f
                false
            } else {
                null
            }
        }

        // 往回滚：只有列表已经在顶才复原；没到顶就只清掉"往下看"的累计，形态不动
        deltaY > 0f -> {
            advance = 0f
            if (atTop) true else null
        }

        else -> null
    }
}
