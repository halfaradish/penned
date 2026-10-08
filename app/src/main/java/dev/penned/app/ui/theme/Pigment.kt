package dev.penned.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 分类颜料盘 v2（D043，取代 D024 的国画八色）：
 * 旧盘的朱砂/赭石/藤黄三槽挤在棕-橄榄色域，正是"老式工具软件"观感的主要来源；
 * 新盘换成现代高彩度的八个色相，仍是"等视觉重量、互为 ink / 淡底"两档用法。
 *
 * 分配按名字稳定哈希落槽（同名同色、跨页稳定）；**同一张图里**出现的一组分类走 [assignSlots]
 * 保证不撞色。转账/未分类/统计页的合并桶「其他」**不是分类**，走 [neutralLight]/[neutralDark] 的中性灰。
 */
object PennedPigment {

    /**
     * 浅色：作实心 ink 或深色字都成立（白底 5.18~6.62:1，自家 12% 淡底上 ≥4.53:1）。
     *
     * **槽位顺序不是色相顺序**——哈希落槽决定了"哪几个真实分类会同屏"，所以槽位与色相要错开配：
     * AR 轮实测发现，按色相排（…紫 267 / 洋红 295）会让「晚餐」与「工资」这两个最常用的分类
     * 只差 28°（旧盘是 46°），用户一眼就能看出"两个都是紫的"。按下面这个顺序，
     * 真实分类名落到的槽位两两最小色相差 46°（详见 `docs/界面规范.md` 第四节）。
     */
    val light: List<Color> = listOf(
        Color(0xFFB0234F), // 0 玫红 341°（食品餐饮/出行交通/休闲娱乐/文化教育/午餐/其他 落这里）
        Color(0xFFB93C0B), // 1 橙   17°
        Color(0xFF0E7A50), // 2 翠  157°（休闲零食/转账）
        Color(0xFF0E7490), // 3 青  193°
        Color(0xFF7B3FC4), // 4 紫  267°
        Color(0xFF3F4FC9), // 5 靛  233°（购物消费/居家生活）
        Color(0xFFA21CAF), // 6 洋红 295°（晚餐）
        Color(0xFF8F6205), // 7 金   40°（工资/早餐）
    )

    /** 深色：提亮一档，作 ink 在暗底上均 ≥ 8:1、在 22% 淡底上 ≥5.3:1（顺序同 [light]）。 */
    val dark: List<Color> = listOf(
        Color(0xFFF2A0BE),
        Color(0xFFF5B183),
        Color(0xFF7EDCB0),
        Color(0xFF7FD4EC),
        Color(0xFFC9A8F0),
        Color(0xFFA3B0F5),
        Color(0xFFEFA0DC),
        Color(0xFFE5C878),
    )

    /** 转账 / 未分类 / 合并桶「其他」的中性色（不参与哈希）。 */
    val neutralLight = Color(0xFF5F6672)
    val neutralDark = Color(0xFFA8AEBA)

    /** 淡底透明度：浅色 12% / 深色 22%（深色给不足会糊成灰）。 */
    const val TINT_ALPHA_LIGHT = 0.12f
    const val TINT_ALPHA_DARK = 0.22f

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
     * 每槽的色相角（度）：判"撞色"用，**顺序与 [light] 一一对应**（所以看起来不是递增的）。
     */
    private val hues = listOf(341f, 17f, 157f, 193f, 267f, 233f, 295f, 40f)

    /**
     * 同一张图里两段的最小色相差：低于它就算撞色（用户反馈「连续三种一样的颜色」）。
     * 45° 是"一眼能分开"的经验线。
     *
     * **这套盘的极限（诚实记录）**：k 个分类能拿到的最优最小色相差是 2→176°、3→110°、4→62°、
     * 5→46°、6→36°、7→28°、8→23°。旧盘同样是"5 个的极限"（D040 记过），所以一屏 6 个彩色分类时
     * 第 6 色与邻居约 36°，靠"淡底 + 桶名 + 占比数字"一起区分。
     *
     * 另外：**真实数据里会同屏的那几个分类**（食品餐饮/购物消费/休闲娱乐/文化教育/晚餐/工资…）
     * 落到的槽位两两 ≥46°——这靠"槽位与色相错开配"实现，见 [light] 的注释。
     */
    internal const val MIN_HUE_GAP = 45f

    /**
     * 给**一张图里同时出现的一组分类**分配槽位（统计页占比环/图例/数据条、分类详细页子分类共用一份）。
     *
     * 规则：**不撞色就一个都不动**（同名同色、跨页稳定优先）；撞色才在"所有槽位排列"里搜
     * 最小色相差最大的那个组合（并列时优先让更多名字留在自己的哈希槽、前几个名字优先）。
     * 名字顺序即优先级（金额从大到小），结果确定可复现。
     */
    fun assignSlots(names: List<String>): Map<String, Int> {
        val ordered = names.distinct()
        val out = LinkedHashMap<String, Int>()
        ordered.filter { slotOf(it) < 0 }.forEach { out[it] = -1 }
        val colored = ordered.filter { slotOf(it) >= 0 }
        if (colored.isEmpty()) return out

        val preferred = IntArray(colored.size) { slotOf(colored[it]) }
        val assignment = if (minHueGap(preferred.toList()) >= MIN_HUE_GAP) {
            preferred // 哈希槽本来就分得开：保持稳定，不去"优化"得更好看
        } else {
            searchBest(colored.size, preferred)
        }
        colored.forEachIndexed { i, name -> out[name] = assignment[i] }
        return out
    }

    /**
     * 穷举 P(8, k) 个排列（k=6 → 20160、k=7/8 → 40320，只在撞色时才跑），
     * 取"最小色相差最大"者；并列时取"保持哈希槽的最长前缀"（前面几个名字优先，沿用"先到先用"）。
     * 剪枝：已放下的槽位里出现小于当前最优的色相差就回退。
     */
    private fun searchBest(k: Int, preferred: IntArray): IntArray {
        val current = IntArray(k)
        val used = BooleanArray(light.size)
        val best = preferred.copyOf()
        var bestGap = -1f
        var bestKeep = -1

        fun keepPrefix(): Int {
            var n = 0
            while (n < k && current[n] == preferred[n]) n++
            return n
        }

        fun place(depth: Int, partialGap: Float) {
            if (depth == k) {
                val keep = keepPrefix()
                if (partialGap > bestGap || (partialGap == bestGap && keep > bestKeep)) {
                    bestGap = partialGap
                    bestKeep = keep
                    for (i in 0 until k) best[i] = current[i]
                }
                return
            }
            for (slot in light.indices) {
                if (used[slot]) continue
                var gap = partialGap
                for (i in 0 until depth) {
                    val g = hueGap(slot, current[i])
                    if (g < gap) gap = g
                }
                // 再加槽位只会让最小色相差更小，已经追不上最优就不必往下走
                if (gap < bestGap) continue
                used[slot] = true
                current[depth] = slot
                place(depth + 1, gap)
                used[slot] = false
            }
        }

        place(0, 360f)
        return best
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

/** 分类颜料 ink：头像字、图表段、占比条、图标字都用它。 */
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
fun pigmentTintAt(slot: Int): Color = pigmentInkAt(slot).copy(
    alpha = if (isDarkTheme()) PennedPigment.TINT_ALPHA_DARK else PennedPigment.TINT_ALPHA_LIGHT,
)

/** 分类颜料淡底：ink 12%（浅）/ 22%（深）叠在卡片上，配 [pigmentInk] 字。 */
@Composable
fun pigmentTint(name: String): Color = pigmentInk(name).copy(
    alpha = if (isDarkTheme()) PennedPigment.TINT_ALPHA_DARK else PennedPigment.TINT_ALPHA_LIGHT,
)

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
