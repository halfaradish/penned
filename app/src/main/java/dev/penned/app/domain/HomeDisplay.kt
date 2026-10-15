package dev.penned.app.domain

import androidx.compose.runtime.Immutable

/**
 * 首页月卡头部显示哪几项（M3-13，纯函数 + 表驱动测试在 `HomeDisplayTest`）。
 *
 * 四项对应月卡头能显示的东西：支出 / 收入 / 结余三格 + 预算进度块（预算·已用·剩余 + 4dp 进度条）。
 * 规格里还写了「「余额/预算」块整体开关」——本 App 的首页没有单独的「余额」块（余额在资产页），
 * 所以那一开关与「预算进度」这一项**合成了同一个开关**，不做两个互相打架的控件（D086 记录这处收窄）。
 */
enum class HomeStatItem(val label: String) {
    EXPENSE("支出"),
    INCOME("收入"),
    BALANCE("结余"),
    BUDGET_PROGRESS("预算进度"),
}

/** 首页显示设置。默认全开（与 M3-13 之前的样子一致）。 */
@Immutable
data class HomeDisplaySettings(
    val items: Set<HomeStatItem> = HomeStatItem.entries.toSet(),
) {
    /**
     * 实际渲染的项（固定按 支出 → 收入 → 结余 → 预算进度 的顺序）。
     * **全不勾时兜底保留「支出」**：月卡头不能空着——空着的话用户会以为卡坏了（规格 M3-13 的兜底口径）。
     */
    val visibleItems: List<HomeStatItem>
        get() = HomeStatItem.entries.filter { it in items }.ifEmpty { listOf(HomeStatItem.EXPENSE) }

    /** 某一项是否渲染（渲染口径一律走这里，别直接看 [items]——否则兜底会被绕过）。 */
    fun isVisible(item: HomeStatItem): Boolean = item in visibleItems

    /**
     * 勾/取消一项。**全关状态下再勾一项时，先把兜底渲染的「支出」落进集合**：
     * 否则用户看到「支出」的开关是开着的（兜底渲染），一勾「收入」支出却从卡上消失了——
     * 抽屉里的开关状态与卡上的渲染必须一致（模拟机走查抓到的）。
     */
    fun toggle(item: HomeStatItem, on: Boolean): HomeDisplaySettings {
        val base = if (items.isEmpty() && on) setOf(HomeStatItem.EXPENSE) else items
        return copy(items = if (on) base + item else base - item)
    }
}

/** 编解码：DataStore 里存成 `EXPENSE,INCOME` 这样的逗号串（认不出的段忽略）。 */
object HomeDisplay {

    /** 空集合 → 空串（**与"没存过"不同**：没存过 = 全开，空串 = 用户全关了 → 走兜底）。 */
    fun encode(items: Set<HomeStatItem>): String =
        HomeStatItem.entries.filter { it in items }.joinToString(",") { it.name }

    /** 没存过（null）→ 全开；认不出的段忽略（老版本写进去的值不会炸）。 */
    fun decode(stored: String?): HomeDisplaySettings {
        if (stored == null) return HomeDisplaySettings()
        val parsed = stored.split(',')
            .mapNotNull { name -> HomeStatItem.entries.firstOrNull { it.name == name.trim() } }
            .toSet()
        return HomeDisplaySettings(items = parsed)
    }
}
