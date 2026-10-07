package dev.penned.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 分类颜料盘（D024，取代 D019 的哈希色相）：
 * 八色等彩度等明度，落在同一视觉重量上，不再互相打架。
 * 分配按名字稳定哈希落槽（同名同色）；统计页环形图/占比条复用同一套。
 * 转账/未分类/统计页的合并桶「其他」**不是分类**，走 [neutralLight]/[neutralDark] 的中性墨灰。
 */
object PennedPigment {

    /** 浅色：作为实心底或深色字都成立（白底均 ≥ 5:1）。 */
    val light: List<Color> = listOf(
        Color(0xFFA8433A), // 0 朱砂
        Color(0xFF97602F), // 1 赭石
        Color(0xFF866C1F), // 2 藤黄
        Color(0xFF2F7A50), // 3 石绿
        Color(0xFF2F6A92), // 4 石青
        Color(0xFF47578F), // 5 靛青
        Color(0xFF77518A), // 6 绛紫
        Color(0xFF7C4A66), // 7 紫檀
    )

    /** 深色：提亮一档，作 ink 在暗底上均 ≥ 4.5:1。 */
    val dark: List<Color> = listOf(
        Color(0xFFE89A90),
        Color(0xFFDBAE79),
        Color(0xFFD8C271),
        Color(0xFF86C9A2),
        Color(0xFF86BCDF),
        Color(0xFFA0AEE2),
        Color(0xFFC9A3DE),
        Color(0xFFD6A3C0),
    )

    /** 转账 / 未分类 / 合并桶「其他」的中性色（不参与哈希）。 */
    val neutralLight = Color(0xFF586360)
    val neutralDark = Color(0xFFAEBAB6)

    const val TRANSFER = "转账"
    const val UNKNOWN = "未分类"

    /** 合并桶不是分类，配色与转账/未分类同待遇（AI 轮）。 */
    const val OTHERS = dev.penned.app.domain.StatsMath.OTHERS

    /** 落在八色里的返回 0..7；转账/未分类/「其他」返回 -1（中性）。 */
    fun slotOf(name: String): Int = when (name) {
        TRANSFER, UNKNOWN, OTHERS, "?" -> -1
        else -> (name.hashCode().toLong() and 0xFFFFFFFFL).rem(light.size.toLong()).toInt()
    }

    /**
     * 每槽的色相角（度）：判"撞色"用。八色等彩度等明度，差异主要落在色相上，
     * 但暖色三槽（朱砂 5° / 赭石 28° / 藤黄 45°）与靛青·石青（227°/204°）本来就挨得近。
     */
    private val hues = listOf(5f, 28f, 45f, 146f, 204f, 227f, 280f, 326f)

    /**
     * 同一张图里两段的最小色相差：低于它就算撞色（用户反馈「连续三种一样的颜色」）。
     * 45° 是"一眼能分开"的经验线：朱砂 5°/藤黄 45° 只差 40°，屏幕上已经开始像同色，所以卡在 45。
     */
    internal const val MIN_HUE_GAP = 45f

    /**
     * 给**一张图里同时出现的一组分类**分配槽位（统计页占比环/图例/数据条、分类详细页子分类共用一份）：
     * 先按名字哈希各就各位（同名同色、跨页稳定），**撞色时**才让后来者改到"离已用色最疏远"的空槽。
     * 名字顺序即优先级（金额从大到小），结果确定可复现。
     */
    fun assignSlots(names: List<String>): Map<String, Int> {
        val ordered = names.distinct()
        val out = LinkedHashMap<String, Int>()
        ordered.filter { slotOf(it) < 0 }.forEach { out[it] = -1 }
        val colored = ordered.filter { slotOf(it) >= 0 }
        if (colored.isEmpty()) return out

        // 1) 贪心落位：先按名字哈希各就各位（同名同色、跨页稳定），撞色就换到"离已用色最疏远"的空槽
        val slot = LinkedHashMap<String, Int>()
        colored.forEach { name ->
            val preferred = slotOf(name)
            val used = slot.values.toList()
            slot[name] = when {
                used.none { hueGap(it, preferred) < MIN_HUE_GAP } -> preferred
                used.size == light.size -> preferred // 八色用满（多于八个分类）：退回哈希槽
                else -> light.indices.filter { it !in used }
                    .maxBy { candidate -> used.minOf { hueGap(candidate, it) } }
            }
        }

        // 2) 爬山（最多"名字数"轮）：单个名字换空槽、或两个名字对调，只要最小色相差变大就采纳。
        //    贪心只看"已用的颜色"，最后一两个分类可能仍挤在 40° 上下——这一轮把它们掰开。
        for (round in 0 until colored.size) {
            val current = minHueGap(slot.values)
            if (current >= MIN_HUE_GAP) break
            var bestGain = 0f
            var best: Map<String, Int>? = null
            val used = slot.values.toSet()
            colored.forEach { name ->
                light.indices.filter { it !in used }.forEach { candidate ->
                    val trial = LinkedHashMap(slot).also { it[name] = candidate }
                    val gain = minHueGap(trial.values) - current
                    if (gain > bestGain) {
                        bestGain = gain
                        best = trial
                    }
                }
            }
            for (i in colored.indices) {
                for (j in i + 1 until colored.size) {
                    val a = colored[i]
                    val b = colored[j]
                    val trial = LinkedHashMap(slot)
                    val sa = slot.getValue(a)
                    trial[a] = slot.getValue(b)
                    trial[b] = sa
                    val gain = minHueGap(trial.values) - current
                    if (gain > bestGain) {
                        bestGain = gain
                        best = trial
                    }
                }
            }
            val improved = best ?: break
            slot.clear()
            slot.putAll(improved)
        }

        out.putAll(slot)
        return out
    }

    /** 一组槽位里"最像的那一对"差多少度：单色或不配色返回 360（没人撞）。 */
    private fun minHueGap(slots: Collection<Int>): Float {
        val list = slots.toList()
        if (list.size < 2) return 360f
        var min = 360f
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                min = kotlin.math.min(min, hueGap(list[i], list[j]))
            }
        }
        return min
    }

    private fun hueGap(a: Int, b: Int): Float {
        val diff = kotlin.math.abs(hues[a] - hues[b])
        return kotlin.math.min(diff, 360f - diff)
    }
}

/** 分类颜料 ink：实心头像底、图表段、占比条、图标字都用它。 */
@Composable
fun pigmentInk(name: String): Color {
    val slot = PennedPigment.slotOf(name)
    if (slot < 0) return if (isDarkTheme()) PennedPigment.neutralDark else PennedPigment.neutralLight
    return if (isDarkTheme()) PennedPigment.dark[slot] else PennedPigment.light[slot]
}

/** 按槽位取颜料 ink（账户按类型固定上色，不走哈希）。slot 为负＝中性。 */
@Composable
fun pigmentInkAt(slot: Int): Color {
    if (slot < 0) return if (isDarkTheme()) PennedPigment.neutralDark else PennedPigment.neutralLight
    val palette = if (isDarkTheme()) PennedPigment.dark else PennedPigment.light
    return palette[slot % palette.size]
}

@Composable
fun pigmentTintAt(slot: Int): Color =
    pigmentInkAt(slot).copy(alpha = if (isDarkTheme()) 0.30f else 0.16f)

/** 分类颜料淡底：ink 16%（浅）/ 30%（深）叠在卡片上，配 [pigmentInk] 字。
 *  深色下透明度要给足，否则八色都糊成灰。 */
@Composable
fun pigmentTint(name: String): Color =
    pigmentInk(name).copy(alpha = if (isDarkTheme()) 0.30f else 0.16f)

/**
 * 兼容入口：给"实心圆 + 白字"的旧头像用（白字在八色上均 ≥ 5:1）。
 * 新代码请用 `CategoryAvatar`（tint 底 + ink 字）。
 */
@Composable
fun categoryColor(name: String): Color = pigmentInk(name)

/**
 * 按"一张图里分配好的槽位"取 ink（[PennedPigment.assignSlots] 的产物）：
 * 没分配到的名字退回名字哈希，转账/未分类/「其他」照旧走中性。
 */
@Composable
fun pigmentInkFor(slots: Map<String, Int>, name: String): Color =
    pigmentInkAt(slots[name] ?: PennedPigment.slotOf(name))

/** [pigmentInkFor] 的槽位版本：给 `CategoryAvatar(slot = …)` 这类接口用。 */
fun pigmentSlotFor(slots: Map<String, Int>, name: String): Int =
    slots[name] ?: PennedPigment.slotOf(name)
