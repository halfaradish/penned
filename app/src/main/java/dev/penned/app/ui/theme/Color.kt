package dev.penned.app.ui.theme

import androidx.compose.ui.graphics.Color

// 主色取青绿色系（对标参考的观感，自选色值）。
// 两套完整色板（specs/M1.md 第二批：深色主题细化）——中性色统一带青灰偏，避免 M3 默认紫灰。

// —— Light ——
val PrimaryLight = Color(0xFF2F9E8F)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFB8E6DD)
val OnPrimaryContainerLight = Color(0xFF04382F)

val BackgroundLight = Color(0xFFF8FAF9)
val OnBackgroundLight = Color(0xFF191C1B)
val SurfaceLight = Color(0xFFFDFEFE)
val OnSurfaceLight = Color(0xFF191C1B)
val SurfaceVariantLight = Color(0xFFEDF0EF)
val OnSurfaceVariantLight = Color(0xFF5A6462)
val OutlineLight = Color(0xFF8A938F)
val SurfaceContainerLight = Color(0xFFF0F3F2)

// —— Dark ——
val PrimaryDark = Color(0xFF82D5C6)
val OnPrimaryDark = Color(0xFF00382F)
val PrimaryContainerDark = Color(0xFF0F5045)
val OnPrimaryContainerDark = Color(0xFFB8E6DD)

val BackgroundDark = Color(0xFF0F1312)
val OnBackgroundDark = Color(0xFFDEE4E2)
val SurfaceDark = Color(0xFF131817)
val OnSurfaceDark = Color(0xFFDEE4E2)
val SurfaceVariantDark = Color(0xFF232A28)
val OnSurfaceVariantDark = Color(0xFF9FA9A6)
val OutlineDark = Color(0xFF6F7977)
val SurfaceContainerDark = Color(0xFF1B2120)

/** 支出红 / 收入青绿（信息架构分析：一木用 支红/收绿 观感，色值自选）。
 *  暗色下整体提亮一档保证对比度（ui/expenseColor/incomeColor 按主题取用）。 */
val ExpenseRed = Color(0xFFE0524E)
val ExpenseRedDark = Color(0xFFF08A85)
val IncomeTeal = Color(0xFF2F9E8F)
val IncomeTealDark = Color(0xFF82D5C6)
