package dev.penned.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字阶（docs/界面重构方案.md 第四节）：系统字族，不引字体文件。
 * 层级靠字号/字重/字距区分；所有样式带 tnum——金额与数字在列表里右对齐成列（原则一）。
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
    displayLarge = style(40, 48, 600, -0.75),
    displayMedium = style(36, 42, 600, -0.5),
    displaySmall = style(32, 38, 600, -0.5),
    headlineLarge = style(28, 34, 600, -0.4),
    headlineMedium = style(26, 32, 600, -0.3),
    headlineSmall = style(24, 30, 600, -0.25),
    titleLarge = style(20, 26, 600, -0.2),
    titleMedium = style(16, 22, 600, 0.0),
    titleSmall = style(14, 20, 600, 0.0),
    bodyLarge = style(15, 22, 400, 0.1),
    bodyMedium = style(14, 20, 400, 0.1),
    bodySmall = style(13, 18, 400, 0.2),
    labelLarge = style(14, 18, 600, 0.1),
    labelMedium = style(12, 16, 500, 0.2),
    labelSmall = style(11, 14, 500, 0.3),
)
