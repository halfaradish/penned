package dev.penned.app.domain

import androidx.compose.runtime.Immutable

/**
 * 统计页的模块（M3-14）：显示开关 + 顺序都可由用户调（DataStore 存），**关掉的模块不渲染也不计算**。
 *
 * 前四个是统计页原有的四块（汇总 / 趋势 / 占比 / 数据）；后三个是 M3-14 新增
 * （标签占比 / 标签数据靠 M3-1 的标签、资产走势靠 M3-10 的资产）。
 */
enum class StatsModule(val label: String, val hint: String) {
    SUMMARY("汇总", "本期支出 / 收入 / 结余三格"),
    TREND("趋势", "按日（年统计按 12 个月）的柱状图"),
    DONUT("占比", "分类环形图 + 图例"),
    DATA("数据", "分类金额与笔数排行"),
    TAG_DONUT("标签占比", "按标签聚合的环形图（一笔挂多个标签会各算一次）"),
    TAG_DATA("标签数据", "标签金额与笔数排行"),
    ASSET_TREND("资产走势", "账户合计余额逐日走势（转账对冲、手续费计入；债务不在账户里，不计入）"),
}

/**
 * 统计页布局：模块顺序 + 显示开关。
 *
 * **没有兜底**（与首页显示不同）：统计模块**全关就是空态**（规格 M3-14 的验收里明写"含全关的空态"）——
 * 统计页本来就可以一屏不放东西，硬塞一块回去反而与用户的开关打架。
 */
@Immutable
data class StatsLayout(
    val order: List<StatsModule> = StatsModule.entries.toList(),
    val enabled: Set<StatsModule> = StatsModule.entries.toSet(),
) {
    /** 实际渲染的模块：按 [order] 排、只留 [enabled] 里的。 */
    val visibleModules: List<StatsModule> get() = order.filter { it in enabled }

    fun isEnabled(module: StatsModule): Boolean = module in enabled

    fun toggle(module: StatsModule, on: Boolean): StatsLayout =
        copy(enabled = if (on) enabled + module else enabled - module)

    /** 上移一位（已在第一位就原样返回）；列表行只在非第一位给「上移」。 */
    fun moveUp(module: StatsModule): StatsLayout {
        val index = order.indexOf(module)
        if (index <= 0) return this
        return copy(order = order.toMutableList().also { it.add(index - 1, it.removeAt(index)) })
    }

    /** 下移一位（已在最后一位就原样返回）。 */
    fun moveDown(module: StatsModule): StatsLayout {
        val index = order.indexOf(module)
        if (index < 0 || index >= order.size - 1) return this
        return copy(order = order.toMutableList().also { it.add(index + 1, it.removeAt(index)) })
    }
}

/** 编解码：DataStore 存两个串——顺序（逗号串）与开关（逗号串，空串 = 全关）。 */
object StatsLayoutCodec {

    /**
     * 顺序串：**认不出的段忽略、缺的模块按枚举顺序补在末尾**——
     * 老版本存的串（少了后加的模块）读出来仍是"完整的一份顺序"，不会丢模块。
     */
    fun decodeOrder(stored: String?): List<StatsModule> {
        if (stored == null) return StatsModule.entries.toList()
        val known = stored.split(',')
            .mapNotNull { name -> StatsModule.entries.firstOrNull { it.name == name.trim() } }
            .distinct()
        return known + StatsModule.entries.filterNot { it in known }
    }

    /** 开关串：**没存过 = 全开**；空串 = 用户全关（空态，不兜底）。 */
    fun decodeEnabled(stored: String?): Set<StatsModule> {
        if (stored == null) return StatsModule.entries.toSet()
        return stored.split(',')
            .mapNotNull { name -> StatsModule.entries.firstOrNull { it.name == name.trim() } }
            .toSet()
    }

    fun encodeOrder(order: List<StatsModule>): String = order.joinToString(",") { it.name }

    fun encodeEnabled(enabled: Set<StatsModule>): String =
        StatsModule.entries.filter { it in enabled }.joinToString(",") { it.name }

    fun decode(orderStored: String?, enabledStored: String?): StatsLayout =
        StatsLayout(order = decodeOrder(orderStored), enabled = decodeEnabled(enabledStored))
}
