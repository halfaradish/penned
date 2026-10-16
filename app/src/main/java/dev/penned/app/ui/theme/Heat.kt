package dev.penned.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * 日历热力的四档底色与配套字色（M3-17 优化轮 D091，界面规范 §13·补二十八）。
 *
 * 三条口径：
 * - **色相只来语义色**（走 [expenseColor] / [incomeColor]，即支出红 / 收入青绿）——
 *   品牌一族不许参与（它随 4 套主题色相变，热力必须随主题不变，D071/D085），
 *   分类颜料盘也不许（颜料只属于图表细件与宫格小圆点，D043 原则三）
 * - **底 = 语义色按档位稀释的淡底**：整体要淡（大面积饱和色只留给主按钮/进度这类"可操作"元素）
 * - **每一档都是成对的（底 + 该档上的字色）**：字色不是把底色调浅，而是**换一档**——
 *   浅色主题用**深一档**（expense 压在灰底上本来就只有 4.76:1，一旦加上同色淡底就必须加深），
 *   深色主题用**亮一档**。逐对进 `ContrastTest`，实测最低 4.68:1（浅色 4.80 / 深色 4.68）
 *
 * 档位透明度也按主题分：浅色底本来就是纸色，档位要很轻（8%~32%）；
 * 深色底近黑，档位要更"实"（14%~46%）才看得出四档。四档与页面底的对比度 1.12~2.86:1——
 * 淡底到不了 3:1（与图标底/图表细件同族的诚实记录），所以另有一条"档位可见 + 逐档递增"的下限断言。
 */
object PennedHeat {

    /** 浅色主题的档位透明度（叠在页面底 `background` 上）。 */
    val bandAlphaLight = listOf(0.08f, 0.16f, 0.25f, 0.32f)

    /** 深色主题的档位透明度。 */
    val bandAlphaDark = listOf(0.14f, 0.26f, 0.38f, 0.46f)

    /** 热力格上的支出字色（浅色主题：深一档红；灰底 7.68:1）。 */
    val ExpenseInkLight = Color(0xFF8E2A23)

    /** 热力格上的收入字色（浅色主题：深一档青绿）。 */
    val IncomeInkLight = Color(0xFF084F49)

    /** 热力格上的支出字色（深色主题：亮一档红，近白粉）。 */
    val ExpenseInkDark = Color(0xFFFFC0B9)

    /** 热力格上的收入字色（深色主题：亮一档青绿，薄荷）。 */
    val IncomeInkDark = Color(0xFF8FE9DA)

    /** 第 [level] 档（1..4）的透明度；档位越界夹到端点。 */
    fun bandAlpha(level: Int, dark: Boolean): Float {
        val ladder = if (dark) bandAlphaDark else bandAlphaLight
        return ladder[(level - 1).coerceIn(0, ladder.lastIndex)]
    }
}

/** 热力格底：语义色按档位稀释（`level` 1..4，越深 = 那天花/收得越多）。 */
@Composable
@ReadOnlyComposable
fun heatFillColor(expense: Boolean, level: Int): Color =
    (if (expense) expenseColor() else incomeColor()).copy(alpha = PennedHeat.bandAlpha(level, isDarkTheme()))

/** 热力格上的字色：与该档底色成对核算（见 [PennedHeat]），不是把底色调浅。 */
@Composable
@ReadOnlyComposable
fun heatInkColor(expense: Boolean): Color = when {
    expense && isDarkTheme() -> PennedHeat.ExpenseInkDark
    expense -> PennedHeat.ExpenseInkLight
    isDarkTheme() -> PennedHeat.IncomeInkDark
    else -> PennedHeat.IncomeInkLight
}
