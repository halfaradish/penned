package dev.penned.app.ui.calendar

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 收起/展开的纯逻辑（表驱动单测在 `CalendarCollapseTest`）：拖动位移 ↔ 进度换算、松手吸附、
 * 以及格子侧几何（视口高 / 本周行上移 / 非本周行透明度）。
 *
 * 口径（优化轮 2，D093）：
 * - 收起进度 `p ∈ [0,1]`（1 = 收成一周）由拖动位移**实时 1:1**驱动（p = 累计位移 / 行程）
 * - **位移先归收起、再到列表滚动**（"先收起、再滚动"）；往回滚只在列表回顶后才展开（UI-10）
 * - 松手吸附：过半收满，否则弹回；速度方向明确时以速度为准（"甩一下"不该被位置拖住）
 */
internal object CalendarCollapse {

    /**
     * 松手吸附线：进度过半就收满成周，没过半弹回展开。
     * 速度超过 [flingAfter] 时**以速度方向为准**（向上收满 / 向下弹回），不看位置。
     */
    const val snapHalf = 0.5f

    /**
     * 速度阈值：按 **dp/s** 记（调用处 `with(density) { it.toPx() }` 得到 px/s）。
     * 800 是"明显甩了一下"的量级——慢速拖到一半松手仍按位置判（过半收满）。
     */
    val flingAfter = 800.dp

    /**
     * 上滑（收起）：吃掉 `min(位移, 剩余行程)`，进度按行程 1:1 前进。
     *
     * @return 新进度 + **实际吃掉的位移**（没吃完的归列表滚动——"p 到 1 后剩余位移才给列表"）
     */
    fun consumeUp(progress: Float, distance: Float, spanPx: Float): CollapseDrag {
        if (distance <= 0f || spanPx <= 0f) return CollapseDrag(progress.coerceIn(0f, 1f), 0f)
        val remain = (1f - progress.coerceIn(0f, 1f)) * spanPx
        val used = minOf(distance, remain)
        return CollapseDrag((progress + used / spanPx).coerceIn(0f, 1f), used)
    }

    /** 下滑（展开）：与 [consumeUp] 对称；"回顶才展开"的判定在调用方（列表没到顶就不该走这里）。 */
    fun consumeDown(progress: Float, distance: Float, spanPx: Float): CollapseDrag {
        if (distance <= 0f || spanPx <= 0f) return CollapseDrag(progress.coerceIn(0f, 1f), 0f)
        val remain = progress.coerceIn(0f, 1f) * spanPx
        val used = minOf(distance, remain)
        return CollapseDrag((progress - used / spanPx).coerceIn(0f, 1f), used)
    }

    /**
     * 松手吸附：0 = 弹回展开、1 = 收满成周。
     * 速度（Compose 口径：**正 = 内容往下走**，即手指下滑）过了 [flingPx] 就按方向定去留。
     */
    fun snapTarget(progress: Float, velocityY: Float, flingPx: Float): Float = when {
        velocityY <= -flingPx -> 1f
        velocityY >= flingPx -> 0f
        progress >= snapHalf -> 1f
        else -> 0f
    }

    /**
     * 网格视口高：`p = 0` 是整月（[expandedHeight]，5/6 行切换时它自己是补间的），
     * `p = 1` 只剩一行——白框面板在流式布局里跟着"顶"上来，p 越大约盖越多。
     */
    fun viewportHeight(expandedHeight: Dp, rowHeight: Dp, progress: Float): Dp =
        rowHeight + (expandedHeight - rowHeight) * (1f - progress.coerceIn(0f, 1f))

    /**
     * 内容上移距离：**本周（选中日所在那周）随面板逐渐上移，p = 1 时贴住视口顶部**。
     * `weekIndex` 是该周在本月网格里的行序号（0 起，[dev.penned.app.domain.CalendarMath.weekIndexOf]）：
     * p = 1 时上移 `weekIndex × 行高`，本周行正好落在视口那一行里。
     */
    fun weekShift(rowHeight: Dp, weekIndex: Int, progress: Float): Dp =
        rowHeight * (weekIndex.coerceAtLeast(0) * progress.coerceIn(0f, 1f))

    /** 非本周行的透明度：跟着本周做同一位移、随 p 渐隐到 0（本周行恒 1，不参与渐隐）。 */
    fun otherRowAlpha(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)

    /** 累计上滑这么多才收起（与悬浮记账入口同档：太小会被惯性抖动反复触发）。 */
    val collapseAfter = 24.dp

    /** 收起态横滑多少算一次"切月"（过 touchSlop 再走一段，别一蹭就翻）。 */
    val shiftAfter = 48.dp
}

/** [CalendarCollapse.consumeUp] / [CalendarCollapse.consumeDown] 的结果：新进度 + 实际吃掉的位移。 */
internal data class CollapseDrag(val progress: Float, val consumed: Float)

/**
 * 收起态的横滑方向（纯函数，表驱动）：**与展开态 pager 同一套语义**——往右拉 = 看更早的月、
 * 往左拉 = 看更新的月（未来月由 ViewModel 夹住）。收起态没有 pager 在树上，所以横滑由这里判定。
 *
 * @return -1 = 上一月、+1 = 下一月、null = 没到阈值（不算一次切月）
 */
internal fun shiftDirection(totalX: Float, thresholdPx: Float): Long? = when {
    totalX >= thresholdPx -> -1L
    totalX <= -thresholdPx -> 1L
    else -> null
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
