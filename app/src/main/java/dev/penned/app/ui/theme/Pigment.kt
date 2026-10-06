package dev.penned.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 分类颜料盘（D024，取代 D019 的哈希色相）：
 * 八色等彩度等明度，落在同一视觉重量上，不再互相打架。
 * 分配按名字稳定哈希落槽（同名同色）；统计页环形图/占比条复用同一套。
 * 转账/未分类**不是分类**，走 [neutralLight]/[neutralDark] 的中性墨灰，与八色区分开。
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

    /** 转账 / 未分类的中性色（不参与哈希）。 */
    val neutralLight = Color(0xFF586360)
    val neutralDark = Color(0xFFAEBAB6)

    const val TRANSFER = "转账"
    const val UNKNOWN = "未分类"

    /** 落在八色里的返回 0..7；转账/未分类返回 -1（中性）。 */
    fun slotOf(name: String): Int = when (name) {
        TRANSFER, UNKNOWN, "?" -> -1
        else -> (name.hashCode().toLong() and 0xFFFFFFFFL).rem(light.size.toLong()).toInt()
    }
}

/** 分类颜料 ink：实心头像底、图表段、占比条、图标字都用它。 */
@Composable
fun pigmentInk(name: String): Color {
    val slot = PennedPigment.slotOf(name)
    if (slot < 0) return if (isDarkTheme()) PennedPigment.neutralDark else PennedPigment.neutralLight
    return if (isDarkTheme()) PennedPigment.dark[slot] else PennedPigment.light[slot]
}

/** 分类颜料淡底：ink 14%（浅）/ 20%（深）叠在卡片上，配 [pigmentInk] 字。 */
@Composable
fun pigmentTint(name: String): Color =
    pigmentInk(name).copy(alpha = if (isDarkTheme()) 0.20f else 0.14f)

/**
 * 兼容入口：给"实心圆 + 白字"的旧头像用（白字在八色上均 ≥ 5:1）。
 * 新代码请用 `CategoryAvatar`（tint 底 + ink 字）。
 */
@Composable
fun categoryColor(name: String): Color = pigmentInk(name)
