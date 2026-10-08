package dev.penned.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字阶（docs/界面规范.md 第五节，D043）：系统字族，不引字体文件。
 * 层级靠字号/字重/字距区分；所有样式带 tnum——金额与数字在列表里右对齐成列（原则一）。
 *
 * 与上一代的差别：金额类（display 系与 headline 系）字重 600→700（原则一"金额是主角"）；
 * titleMedium 600→500（让行主标题与卡片标题分层靠字重，不再堆字号）；labelSmall 11→12sp。
 */
private const val TABULAR = "tnum"

private fun style(
    size: Int,
    lineHeight: Int,
    weight: Int,
    tracking: Double,
) = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.sp,
    fontFeatureSettings = TABULAR,
)

val PennedTypography = Typography(
    displayLarge = style(40, 48, 700, -0.8),
    displayMedium = style(36, 42, 700, -0.7),
    displaySmall = style(32, 38, 700, -0.6),
    headlineLarge = style(28, 34, 700, -0.5),
    headlineMedium = style(26, 32, 700, -0.4),
    headlineSmall = style(24, 32, 700, -0.4),
    titleLarge = style(20, 26, 600, -0.2),
    titleMedium = style(16, 22, 500, 0.0),
    titleSmall = style(14, 20, 600, 0.0),
    bodyLarge = style(15, 22, 400, 0.1),
    bodyMedium = style(14, 20, 400, 0.1),
    bodySmall = style(13, 18, 400, 0.2),
    labelLarge = style(14, 18, 600, 0.1),
    labelMedium = style(12, 16, 500, 0.2),
    labelSmall = style(12, 16, 600, 0.4),
)

/**
 * M3 字阶之外的三条语义样式（列表行主标题 / 分区小标题 / 元信息）。
 * M3 槽位表达的是"层级"，这三条表达的是"用途"——界面里两套都登记在册，别就地写字面量。
 */
object PennedTextStyles {
    /** 列表行主标题：15sp/500（比正文重一档，比 titleMedium 轻一档）。 */
    val rowTitle = style(15, 21, 500, 0.0)

    /** 分区小标题（`SectionLabel`/`PennedSectionHeader` 用，= labelSmall 的语义别名）。 */
    val sectionLabel = style(12, 16, 600, 0.4)

    /** 元信息（笔数、时间、"找到 N 条"）。 */
    val caption = style(12, 16, 500, 0.2)
}
