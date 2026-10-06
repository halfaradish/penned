package dev.penned.app.ui.theme

import androidx.compose.ui.graphics.Color

// 色板规范见 docs/界面重构方案.md 第三节（D024）。
// 原则：颜色只表达信息——唯一饱和色是支出朱砂/收入青绿；分类颜料只用于头像与图表。

// —— Light ——
/** 品牌填充色：主按钮、FAB、选中胶囊、进度条。App 身份色，保持不变。 */
val PrimaryLight = Color(0xFF2F9E8F)

/** 品牌文字色：用作正文/图标时的深一档青绿（白底 5.5:1；直接用 primary 只有 3.3:1）。 */
val PrimaryInkLight = Color(0xFF16756A)

val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFD8F0EA)
val OnPrimaryContainerLight = Color(0xFF0B4A41)

val BackgroundLight = Color(0xFFF2F5F4)
val OnBackgroundLight = Color(0xFF101715)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF101715)
val SurfaceVariantLight = Color(0xFFEAF0EE)
val OnSurfaceVariantLight = Color(0xFF5D6866)
val SurfaceContainerLight = Color(0xFFEAF0EE)
val SurfaceContainerHighLight = Color(0xFFE2E9E6)
val OutlineLight = Color(0xFFC4CEC9)
val OutlineVariantLight = Color(0xFFE3E9E6)
val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFF7DAD7)
val OnErrorContainerLight = Color(0xFF5E1510)

// —— Dark ——
val PrimaryDark = Color(0xFF6FD3C1)
val PrimaryInkDark = Color(0xFF6FD3C1)
val OnPrimaryDark = Color(0xFF00382F)
val PrimaryContainerDark = Color(0xFF0F5045)
val OnPrimaryContainerDark = Color(0xFFB8E6DD)

val BackgroundDark = Color(0xFF0E1211)
val OnBackgroundDark = Color(0xFFE3E9E7)
val SurfaceDark = Color(0xFF171C1B)
val OnSurfaceDark = Color(0xFFE3E9E7)
val SurfaceVariantDark = Color(0xFF202725)
val OnSurfaceVariantDark = Color(0xFF9BA6A3)
val SurfaceContainerDark = Color(0xFF202725)
val SurfaceContainerHighDark = Color(0xFF272E2C)
val OutlineDark = Color(0xFF47514E)
val OutlineVariantDark = Color(0xFF2A3230)
val ErrorDark = Color(0xFFF2B8B5)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF7A2A24)
val OnErrorContainerDark = Color(0xFFF7DAD7)

// —— 金额语义色（信息架构分析：支红/收绿）——
/** 支出朱砂（白底 5.1:1）。 */
val ExpenseLight = Color(0xFFC0453C)
val ExpenseDark = Color(0xFFF0908A)

/** 收入青绿（= primaryInk，白底 5.5:1）。 */
val IncomeLight = Color(0xFF16756A)
val IncomeDark = Color(0xFF6FD3C1)
