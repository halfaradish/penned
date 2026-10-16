package dev.penned.app.ui.calendar

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 日历的版式常量（M3-17 优化轮）。
 *
 * 两条都是"一处定义、多处使用"的共享值——网格与收起态用的是同一枚格子，
 * 任何一处都不许自己再写一个比例（否则两处慢慢就漂开了）。
 */
internal object CalendarStyle {

    /**
     * 格子高宽比（`aspectRatio` 是"宽/高"）：**小于 1 才是竖着更长**。
     * 日期一行 + 支/收两行小字要放得下——按 0.92 试过，两行小字会被裁掉半截；0.74 刚好。
     */
    const val cellAspect = 0.74f

    /** 选中态描边：热力已经占了格子的"底"，选中改用品牌色**描边**，两者不抢同一层。 */
    val selectStroke = 2.dp

    /** 格子之间的细缝（每格四周各 2dp，7 列摊在屏边距内）。 */
    val cellGap = 2.dp

    /**
     * 一行格子的实际高度：格子外宽 → 去掉四周细缝 → 按高宽比算内容高度 → 加回细缝。
     *
     * 收起/展开的几何与 pager 的容器高度都要这个数，而它是"格子里写了什么"的推论——
     * 所以公式只写在这里一处（改格子几何时两边一起对）。
     */
    fun rowHeight(cellOuterWidth: Dp): Dp {
        val inner = cellOuterWidth - cellGap * 2
        return inner / cellAspect + cellGap * 2
    }
}

