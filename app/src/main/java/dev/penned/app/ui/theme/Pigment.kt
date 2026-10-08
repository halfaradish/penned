package dev.penned.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 分类颜料盘 v4（D047，取代 D043/D045/D046 三版）。
 *
 * **四轮的结论**：AR「高彩度深色 ink + 12% 淡底」→ 用户说**太艳、太明亮**；RA「压彩度 ≤50% 并加深」→
 * **绕回了国画色**（深、灰、土）；v3「实心亮档块 + 深档字」→ **"又艳起来了，与整体不协调"**。
 * 三次都栽在同一处：**把颜色铺成了面**。而用户点名的样板始终是首页**日头条那一栏**——
 * 极淡的青灰底 + 3dp 深青竖条 + 墨色日期。那就是这套盘该有的口径：
 *
 * > **大面积一律极淡；色相只在小面积上给满。**
 *
 * 于是每槽只剩**一个色相**（[hue]），按三种强度用：
 * - **块面**（[TINT_ALPHA_LIGHT] 12% / [TINT_ALPHA_DARK] 22%，叠在卡片上）= 头像/气泡的底。
 *   浅色下与白卡只有 1.16~1.21:1，跟日头条底（1.12:1）同一档——块面是"纸"，不是"色块"。
 * - **细件**（[MARK_ALPHA_LIGHT] 45% / [MARK_ALPHA_DARK] 65%）= 占比环、图例点、占比条。
 *   比块面浓一档、比状态淡：环/条是辅助视觉（占比数字与金额每行都写着），浓到 3:1 就成"色块"了。
 * - **全强度**（100%）= 只留给**状态**：选中态实心、宫格"有子分类"小圆点。
 *   就是日头条那根 3dp 竖条的角色：小面积、给满、白字压上去 4.6~5.4:1。
 *
 * 块面上的字形**不染颜料**，用 `onSurface` 墨色（浅色 14.2~14.6:1、深色 13.2~13.8:1）——
 * 与日头条的墨色日期同一口径。前几轮把字形染成彩色，正是"艳"和"国画"两种观感的直接来源。
 *
 * 分配按名字稳定哈希落槽（同名同色、跨页稳定）；**同一张图里**出现的一组分类走 [assignSlots]
 * 保证不撞色。转账/未分类**不是分类**，走中性灰（[neutralHue] 等）；真实分类「其他」是普通分类，
 * 照名字哈希取色（落 0 号玫红，见 [hue] 注释）——D049 前它被"合并桶同名"误判成中性。
 */
object PennedPigment {

    /**
     * 分类色相（每槽一个，**全强度**值）：细件、选中态实心、淡底源色都用它。
     *
     * 八槽按**对比度**对齐而不是按明度——都解到"**白字压上去 = 4.5:1**"（同一档色相既当细件、
     * 又当选中态实心，白字要读得清），所以玫红/紫/靛亮到 L50~60、翠/金压到 L29~34。
     * 这不是随手定的：绿、金的物理亮度高，压到同一明度白字就糊了（翠在 L48 上白字只有 1.8:1），
     * 而亮度对比才是"看不看得见"的真正口径。
     *
     * **槽位顺序不是色相顺序**——哈希落槽决定了"哪几个真实分类会同屏"，所以槽位与色相要错开配：
     * AR 轮实测发现，按色相排（…紫 267 / 洋红 295）会让「晚餐」与「工资」这两个最常用的分类
     * 只差 28°（旧盘是 46°），用户一眼就能看出"两个都是紫的"。按下面这个顺序，
     * 真实分类名落到的槽位两两最小色相差 46°（详见 `docs/界面规范.md` 第四节）。
     */
    val hue: List<Color> = listOf(
        Color(0xFFE31657), // 0 玫红 341°（食品餐饮/出行交通/休闲娱乐/文化教育/午餐/其他 落这里）
        Color(0xFFCD4814), // 1 橙   17°
        Color(0xFF0D8557), // 2 翠  157°（休闲零食/转账）
        Color(0xFF107F9D), // 3 青  193°
        Color(0xFF9145ED), // 4 紫  267°
        Color(0xFF4559ED), // 5 靛  233°（购物消费/居家生活）
        Color(0xFFC815D8), // 6 洋红 295°（晚餐）
        Color(0xFF9B6C0F), // 7 金   40°（工资/早餐）
    )

    /**
     * 深色主题的全强度色（顺序同 [hue]）：暗底上深档看不见（靛只有 2.2:1），所以换成同色相的亮档
     * （在 `#181A1D` 上 5.4~12.0:1）。淡底也用这一份当源色——深色下的"淡底"= 亮色叠一点点。
     */
    val hueOnDark: List<Color> = listOf(
        Color(0xFFEB7A9E),
        Color(0xFFEB9A7A),
        Color(0xFF7AEBC0),
        Color(0xFF7AD3EB),
        Color(0xFFAD7AEB),
        Color(0xFF7A87EB),
        Color(0xFFE27AEB),
        Color(0xFFEBC57A),
    )

    /**
     * 淡底透明度：浅色 12% / 深色 22%。
     * 12% 是"与日头条同一档偏淡、又还能分清八个色相"的那条线——再淡（≤8%）八个色相就开始糊成
     * 一片（用户上一轮抱怨过"连续三种一样的颜色"，那一头也是硬线）；再浓就又回到"色块"。
     */
    const val TINT_ALPHA_LIGHT = 0.12f
    const val TINT_ALPHA_DARK = 0.22f

    /**
     * 细件透明度：浅色 45% / 深色 65%（占比环、图例点、占比条）。
     *
     * 细件比块面浓一档、但**比全强度淡**——用户第二轮点名"支出占比的配色也需要改动"，而占比环是页面上
     * 最大的一块颜色。45% 是渲染比出来的那条线：≥45% 时 4dp 的占比条还看得见，≤25% 就真看不见了
     * （`build/chart_pale.py` 把 12/25/40/55/74/100% 六档按真机尺寸排在一起比过）。
     * 代价说清楚：这一档与卡片只有 **1.84~2.00:1**、与浅轨道 **1.52~1.72:1**，够不上 3:1 的非文字下限——
     * 这是有意的取舍：环/条只是**辅助**视觉，占比数字与金额每一行都写着，色相本身另有 ≥45° 的分离保证。
     */
    const val MARK_ALPHA_LIGHT = 0.45f
    const val MARK_ALPHA_DARK = 0.65f

    /** 中性（转账/未分类）：全强度灰 + 它的淡底，不参与哈希。 */
    val neutralHue = Color(0xFF4B5563)
    val neutralHueOnDark = Color(0xFFA8AEBA)

    const val TRANSFER = "转账"
    const val UNKNOWN = "未分类"

    /** 落在八色里的返回 0..7；转账/未分类返回 -1（中性）。 */
    fun slotOf(name: String): Int = when (name) {
        TRANSFER, UNKNOWN, "?" -> -1
        else -> (name.hashCode().toLong() and 0xFFFFFFFFL).rem(hue.size.toLong()).toInt()
    }

    /**
     * 每槽的色相角（度）：判"撞色"用，**顺序与 [hue] 一一对应**（所以看起来不是递增的）。
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
     * 落到的槽位两两 ≥46°——这靠"槽位与色相错开配"实现，见 [hue] 的注释。
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
        val used = BooleanArray(hue.size)
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
            for (slot in hue.indices) {
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

/**
 * 全强度色（细件、选中态实心、淡底源色）：浅色主题用深档、深色主题用亮档；
 * slot 为负＝中性灰。
 */
@Composable
fun pigmentInkAt(slot: Int): Color = when {
    slot < 0 -> if (isDarkTheme()) PennedPigment.neutralHueOnDark else PennedPigment.neutralHue
    isDarkTheme() -> PennedPigment.hueOnDark[slot % PennedPigment.hueOnDark.size]
    else -> PennedPigment.hue[slot % PennedPigment.hue.size]
}

/** 按名字取全强度色。 */
@Composable
fun pigmentInk(name: String): Color = pigmentInkAt(PennedPigment.slotOf(name))

/** 块面淡底：全强度色按 12%（浅）/ 22%（深）叠在卡片上——与日头条底同一档的"偏淡"。 */
@Composable
fun pigmentFillAt(slot: Int): Color = pigmentInkAt(slot).copy(
    alpha = if (isDarkTheme()) PennedPigment.TINT_ALPHA_DARK else PennedPigment.TINT_ALPHA_LIGHT,
)

/** 按名字取块面淡底。 */
@Composable
fun pigmentFill(name: String): Color = pigmentFillAt(PennedPigment.slotOf(name))

/**
 * 细件色（占比环、图例点、占比条）：全强度色再按 [PennedPigment.MARK_ALPHA_LIGHT] 稀释一档
 * （浅色 45% / 深色 65%）——比块面浓、比选中态淡，是"图表看得见"与"整体偏淡"之间的那档。
 */
@Composable
fun pigmentMarkAt(slot: Int): Color = pigmentInkAt(slot).copy(
    alpha = if (isDarkTheme()) PennedPigment.MARK_ALPHA_DARK else PennedPigment.MARK_ALPHA_LIGHT,
)

/** 按名字取细件色。 */
@Composable
fun pigmentMark(name: String): Color = pigmentMarkAt(PennedPigment.slotOf(name))

/**
 * 按"一张图里分配好的槽位"取细件色（[PennedPigment.assignSlots] 的产物）：
 * 环、图例、占比条共用同一份，段段对得上。没分配到的名字退回名字哈希。
 */
@Composable
fun pigmentMarkFor(slots: Map<String, Int>, name: String): Color =
    pigmentMarkAt(slots[name] ?: PennedPigment.slotOf(name))

/**
 * 按"一张图里分配好的槽位"取全强度色（[PennedPigment.assignSlots] 的产物）：
 * 没分配到的名字退回名字哈希，转账/未分类照旧走中性。
 */
@Composable
fun pigmentInkFor(slots: Map<String, Int>, name: String): Color =
    pigmentInkAt(slots[name] ?: PennedPigment.slotOf(name))

/** [pigmentInkFor] 的槽位版本：给 `CategoryAvatar(slot = …)` 这类接口用。 */
fun pigmentSlotFor(slots: Map<String, Int>, name: String): Int =
    slots[name] ?: PennedPigment.slotOf(name)
