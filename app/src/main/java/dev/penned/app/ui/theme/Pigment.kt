package dev.penned.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 分类颜料盘 v3（D046，取代 D043/D045 的"降饱和"盘）。
 *
 * 前两轮的教训：AR 轮"高彩度深色 ink + 12% 淡底"，用户说**太艳太明亮**；
 * RA 轮把彩度压到 ≤50% 并加深，用户说**绕回了国画色**（深、灰、土 = 古董工具软件的味道）。
 * 结论：问题不在"彩度高低"，而在**明度与分工**——块面要亮、要干净，
 * 让深色只去承担"字与细件"。
 *
 * 新盘照用户给的参考（两张课表截图的课程格子）取色带，三档分工明确：
 *
 * - [fill] **亮档 = 块面**（头像/气泡的实心底）：S74 / L70，八个槽**等视觉重量**。
 *   在浅色卡片上只有 1.4~3.2:1 —— 这是有意的：块面是**装饰性**的，分类身份由
 *   压在上面的字形 + 旁边的名称文字承担（参考图自己的块面在画布上也只有 1.4~1.6:1）。
 * - [mark] **中档 = 细件**（占比环/图例点/占比条）：按对比度对齐（白卡上 3.2:1），
 *   所以明度随色相变——绿、金压得深，紫、靛亮到 L60。
 * - [ink] **深档 = 字与选中块**：块面上的字形（在自家亮档上 4.6:1）、选中态的实心底。
 *   深色主题里它只当字形（细件在深色下改用 [fill]，深档在暗底上看不见）。
 *
 * 两个主题**共用同一份 [fill]**：分类的颜色是身份，不随主题变（"食品餐饮"永远是玫红）。
 * 深色下亮档块面在 #181A1D 上 ≥5.4:1，看得见；字形用深档，仍是 4.6:1。
 *
 * 分配按名字稳定哈希落槽（同名同色、跨页稳定）；**同一张图里**出现的一组分类走 [assignSlots]
 * 保证不撞色。转账/未分类/统计页的合并桶「其他」**不是分类**，走中性三档（[neutralFill] 等）。
 */
object PennedPigment {

    /**
     * 亮档：块面色（头像/气泡实心底），浅深两个主题共用。
     * 八个槽同为 S74 / L70，块面之间没有"谁更重"。
     *
     * **槽位顺序不是色相顺序**——哈希落槽决定了"哪几个真实分类会同屏"，所以槽位与色相要错开配：
     * AR 轮实测发现，按色相排（…紫 267 / 洋红 295）会让「晚餐」与「工资」这两个最常用的分类
     * 只差 28°（旧盘是 46°），用户一眼就能看出"两个都是紫的"。按下面这个顺序，
     * 真实分类名落到的槽位两两最小色相差 46°（详见 `docs/界面规范.md` 第四节）。
     */
    val fill: List<Color> = listOf(
        Color(0xFFEB7A9E), // 0 玫红 341°（食品餐饮/出行交通/休闲娱乐/文化教育/午餐/其他 落这里）
        Color(0xFFEB9A7A), // 1 橙   17°
        Color(0xFF7AEBC0), // 2 翠  157°（休闲零食/转账）
        Color(0xFF7AD3EB), // 3 青  193°
        Color(0xFFAD7AEB), // 4 紫  267°
        Color(0xFF7A87EB), // 5 靛  233°（购物消费/居家生活）
        Color(0xFFE27AEB), // 6 洋红 295°（晚餐）
        Color(0xFFEBC57A), // 7 金   40°（工资/早餐）
    )

    /**
     * 深档：字与细件色（顺序同 [fill]）。明度按色相各自解出来，取"字形在自家亮档上 = 4.6:1"的
     * **最亮**解——再亮字就糊，再暗就成了"近黑"，色相看不出来。所以八槽的深档明度 23~25 各差一点，
     * 是解出来的、不是随手定的。
     */
    val ink: List<Color> = listOf(
        Color(0xFF690D2A), // 0 玫红
        Color(0xFF712A0E), // 1 橙
        Color(0xFF0D6946), // 2 翠
        Color(0xFF0E596E), // 3 青
        Color(0xFF390E6F), // 4 紫
        Color(0xFF0E1A72), // 5 靛
        Color(0xFF620D69), // 6 洋红
        Color(0xFF6F4E0E), // 7 金
    )

    /**
     * 中档：细件色（占比环、图例点、占比条）。八槽按**对比度**对齐而不是按明度——
     * 都解到"在白卡上 = 3.2:1"，所以玫红/紫/靛亮到 L60、翠/金压到 L33~38。
     * 这不是随手定的：绿、金的物理亮度高，压到同一个明度就会看不见（翠在 L48 只有 1.8:1），
     * 而亮度对比才是"这段数据看不看得见"的真正口径。
     * 深色主题不用这一档（暗底上改用 [fill]，见 [pigmentInkAt]）。
     */
    val mark: List<Color> = listOf(
        Color(0xFFEC3E75), // 0 玫红
        Color(0xFFE55117), // 1 橙
        Color(0xFF0F9562), // 2 翠
        Color(0xFF118EB0), // 3 青
        Color(0xFF9145ED), // 4 紫
        Color(0xFF4559ED), // 5 靛
        Color(0xFFDA2BEA), // 6 洋红
        Color(0xFFAD7911), // 7 金
    )

    /** 中性块面（转账/未分类/合并桶「其他」）：灰底白字，两个主题同一份。 */
    val neutralFill = Color(0xFF6B7280)

    /**
     * 块面上的白字：中性灰块（4.8:1）与选中态的深档块（≥7:1）都用它。
     * 彩色块面上的字不走这里——那是同色相的深档（见 [ink]），不是白。
     */
    val onFillWhite = Color(0xFFFFFFFF)

    /** 中性细件色：浅色主题用深一档的灰（白卡 7.6:1），深色主题用浅灰（暗卡 7.8:1）。 */
    val neutralInkLight = Color(0xFF4B5563)
    val neutralInkDark = Color(0xFFA8AEBA)

    const val TRANSFER = "转账"
    const val UNKNOWN = "未分类"

    /** 合并桶不是分类，配色与转账/未分类同待遇（AI 轮）。 */
    const val OTHERS = dev.penned.app.domain.StatsMath.OTHERS

    /** 落在八色里的返回 0..7；转账/未分类/「其他」返回 -1（中性）。 */
    fun slotOf(name: String): Int = when (name) {
        TRANSFER, UNKNOWN, OTHERS, "?" -> -1
        else -> (name.hashCode().toLong() and 0xFFFFFFFFL).rem(fill.size.toLong()).toInt()
    }

    /**
     * 每槽的色相角（度）：判"撞色"用，**顺序与 [fill] 一一对应**（所以看起来不是递增的）。
     */
    private val hues = listOf(341f, 17f, 157f, 193f, 267f, 233f, 295f, 40f)

    /**
     * 同一张图里两段的最小色相差：低于它就算撞色（用户反馈「连续三种一样的颜色」）。
     * 45° 是"一眼能分开"的经验线。
     *
     * **这套盘的极限（诚实记录）**：k 个分类能拿到的最优最小色相差是 2→176°、3→110°、4→62°、
     * 5→46°、6→36°、7→28°、8→23°。旧盘同样是"5 个的极限"（D040 记过），所以一屏 6 个彩色分类时
     * 第 6 色与邻居约 36°，靠"块面色 + 桶名 + 占比数字"一起区分。
     *
     * 另外：**真实数据里会同屏的那几个分类**（食品餐饮/购物消费/休闲娱乐/文化教育/晚餐/工资…）
     * 落到的槽位两两 ≥46°——这靠"槽位与色相错开配"实现，见 [fill] 的注释。
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
        val used = BooleanArray(fill.size)
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
            for (slot in fill.indices) {
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

/** 块面色：头像/气泡的实心底。两个主题同色（分类身份色不随主题变）。 */
@Composable
fun pigmentFill(name: String): Color = pigmentFillAt(PennedPigment.slotOf(name))

/** 按槽位取块面色（账户按类型固定上色，不走哈希）。slot 为负＝中性灰。 */
@Composable
fun pigmentFillAt(slot: Int): Color =
    if (slot < 0) PennedPigment.neutralFill else PennedPigment.fill[slot % PennedPigment.fill.size]

/** 块面上的字形色：彩色块配深档（4.6:1），中性块配白字（4.8:1）；两个主题同一份。 */
@Composable
fun pigmentOnFillAt(slot: Int): Color =
    if (slot < 0) PennedPigment.onFillWhite else PennedPigment.ink[slot % PennedPigment.ink.size]

/** 选中态的实心底：深档（中性用深灰），配白字——和亮档块面差一整个明度档，一眼看得出选中。 */
@Composable
fun pigmentSelectedAt(slot: Int): Color =
    if (slot < 0) PennedPigment.neutralInkLight else PennedPigment.ink[slot % PennedPigment.ink.size]

/**
 * 细件色（占比环、图例点、占比条）：浅色主题用中档（白卡与浅轨道上 3.0~3.7:1），
 * 深色主题改用亮档（中/深档在暗底上都看不见，亮档在 #181A1D 上有 5.4:1）。
 */
@Composable
fun pigmentInkAt(slot: Int): Color = when {
    slot < 0 -> if (isDarkTheme()) PennedPigment.neutralInkDark else PennedPigment.neutralInkLight
    isDarkTheme() -> PennedPigment.fill[slot % PennedPigment.fill.size]
    else -> PennedPigment.mark[slot % PennedPigment.mark.size]
}

/** 按名字取细件色。 */
@Composable
fun pigmentInk(name: String): Color = pigmentInkAt(PennedPigment.slotOf(name))

/**
 * 按"一张图里分配好的槽位"取细件色（[PennedPigment.assignSlots] 的产物）：
 * 没分配到的名字退回名字哈希，转账/未分类/「其他」照旧走中性。
 */
@Composable
fun pigmentInkFor(slots: Map<String, Int>, name: String): Color =
    pigmentInkAt(slots[name] ?: PennedPigment.slotOf(name))

/** [pigmentInkFor] 的槽位版本：给 `CategoryAvatar(slot = …)` 这类接口用。 */
fun pigmentSlotFor(slots: Map<String, Int>, name: String): Int =
    slots[name] ?: PennedPigment.slotOf(name)
