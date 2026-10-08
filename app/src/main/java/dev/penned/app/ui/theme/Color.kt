package dev.penned.app.ui.theme

import androidx.compose.ui.graphics.Color

// 色板规范见 docs/界面规范.md 第三节（D043）。
// 原则：颜色只表达信息——唯一饱和色是支出红/收入青绿；品牌青绿只用于"可操作/已选中/进度"；
// 分类颜料只出现在分类头像、图表、占比条。
//
// 与上一代（D024）的差别：中性层去绿味（冷中性灰），品牌青绿色相不变、整体加深
// （旧值 primary #2F9E8F 上写白字只有 3.3:1，不达 WCAG 正文 4.5:1）。
// 每个值后面的括号是对比度实测（build/contrast_check.py 与单测 ContrastTest 都按这份表核）。

// —— Light ——
/** 品牌填充色：主按钮、FAB、悬浮记账入口、进度条、选中指示（白字 5.47:1）。 */
val PrimaryLight = Color(0xFF0F766E)

/** 品牌文字色：用作正文/图标时的深一档青绿（白底 6.46:1 / 灰底 5.93:1）。 */
val PrimaryInkLight = Color(0xFF0B6A61)

val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFD9F0EC)
val OnPrimaryContainerLight = Color(0xFF08423B)

/** 页面底：冷中性灰（旧值 #F2F5F4 带绿味，是"旧账本纸"观感的一半来源）。 */
val BackgroundLight = Color(0xFFF4F5F7)
val OnBackgroundLight = Color(0xFF14171B)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF14171B)

/** 输入框、未选 chip、分段轨道、次级填充。 */
val SurfaceVariantLight = Color(0xFFEFF1F4)
val OnSurfaceVariantLight = Color(0xFF5C636E)
val SurfaceContainerLight = Color(0xFFEFF1F4)

/** 按压态、键盘运算键、进度条轨道。 */
val SurfaceContainerHighLight = Color(0xFFE7EAEF)

/** 强边界（开关轨道、禁用态）。**装饰性**——按 1.4.11 不承担识别职责，别拿它画有含义的图标。 */
val OutlineLight = Color(0xFFD5D8DE)

/** 卡片内的行分隔线（卡片本身不描边，D043 原则二）。 */
val OutlineVariantLight = Color(0xFFEEEFF2)

/** 承担识别职责的**非文字**控件（右箭头、占位图标）：白底 3.30:1 / 灰底 3.03:1。 */
val IconMutedLight = Color(0xFF878E99)

val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFBE4E2)
val OnErrorContainerLight = Color(0xFF5E1510)

// —— Dark ——
val PrimaryDark = Color(0xFF57CDBA)
val PrimaryInkDark = Color(0xFF55C9B6)
val OnPrimaryDark = Color(0xFF00332C)
val PrimaryContainerDark = Color(0xFF134E48)
val OnPrimaryContainerDark = Color(0xFFA8E6DA)

/** 深色不玩纯黑，靠"表面提亮"表达层级。 */
val BackgroundDark = Color(0xFF0F1113)
val OnBackgroundDark = Color(0xFFE7E9EC)
val SurfaceDark = Color(0xFF181A1D)
val OnSurfaceDark = Color(0xFFE7E9EC)
val SurfaceVariantDark = Color(0xFF212429)
val OnSurfaceVariantDark = Color(0xFF9BA1AC)
val SurfaceContainerDark = Color(0xFF212429)
val SurfaceContainerHighDark = Color(0xFF282C31)
val OutlineDark = Color(0xFF4A4F57)
val OutlineVariantDark = Color(0xFF262A2F)
val IconMutedDark = Color(0xFF6B727C)
val ErrorDark = Color(0xFFF2B8B5)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF7A2A24)
val OnErrorContainerDark = Color(0xFFF7DAD7)

// —— 金额语义色（信息架构分析：支红/收绿）——
/** 支出（白底 5.20:1 / 灰底 4.76:1）。 */
val ExpenseLight = Color(0xFFC43C33)
val ExpenseDark = Color(0xFFF28B82)

/** 收入青绿（= primaryInk，与 D024 一致：钱进钱出用同一套语义）。 */
val IncomeLight = Color(0xFF0B6A61)
val IncomeDark = Color(0xFF55C9B6)
