package dev.penned.app.ui.stats

import android.net.Uri
import dev.penned.app.domain.StatsWindow
import dev.penned.app.domain.TxType

/**
 * 下钻目标：分类详细页的节点 = 类型 + 分类名 +（二级分类才有）父分类名。
 * 窗口（统计期）在钻取过程中不变，跟着路由参数走。
 */
data class CategoryTarget(
    val type: TxType,
    val name: String,
    val parent: String?,
)

/** 分类详细页路由：窗口用分钟区间 + 自然日数 + 期文案带过去（省得反推）。 */
fun categoryDetailRoute(
    target: CategoryTarget,
    fromMinute: Long,
    toMinute: Long,
    dayCount: Int,
    label: String,
): String {
    val type = Uri.encode(target.type.name)
    val name = Uri.encode(target.name)
    val parent = Uri.encode(target.parent.orEmpty())
    return "stats/category?type=$type&name=$name&parent=$parent" +
        "&from=$fromMinute&to=$toMinute&days=$dayCount&label=${Uri.encode(label)}"
}

/** 统计页用：直接把当前统计窗口带进路由。 */
fun categoryDetailRoute(target: CategoryTarget, window: StatsWindow): String =
    categoryDetailRoute(target, window.startMinute, window.lastMinute, window.dayCount, window.label)
