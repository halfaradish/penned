package dev.penned.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距阶梯（docs/界面规范.md 第六节）：屏边距 16、卡片内 16（紧凑 12）、卡间 12、分组间 24。
 *
 * **屏边距恒 16dp**：首页悬浮入口的 368↔56dp 形变几何、以及一批测试都盯着这个数，别改。
 */
object PennedSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val huge = 32.dp
}

/** 组件尺寸：行高、热区、头像、键盘键。 */
object PennedDimens {
    /** 分隔线/描边线宽（发丝线；卡片本身不描边，见 D043 原则二）。 */
    val hairline = 1.dp

    /** 卡片圆角（与 shapes.medium 同值；分段卡片要按角拼，得拿得到数值）。 */
    val cardCorner = 16.dp

    /** 列表行最小高度。 */
    val rowMinHeight = 56.dp

    /**
     * 最小触控热区（WCAG 2.5.5 / Material 建议）：chip、分段、图标按钮、行内箭头
     * **视觉尺寸可以更小，热区一律撑到这一档**。见 docs/界面规范.md 第十一节第 3 条。
     */
    val touchTarget = 48.dp

    /** 图标按钮热区。 */
    val iconButton = 44.dp

    /** 图标尺寸四档（16 行内箭头 / 18 小 / 20 嵌入 / 24 默认）。 */
    val icon = 24.dp
    val iconInline = 20.dp
    val iconSmall = 18.dp
    val iconChevron = 16.dp

    /** 流水行头像（搜索结果/首页）。 */
    val avatar = 36.dp

    /** 大号头像（资产账户、统计排行）。 */
    val avatarLarge = 40.dp

    /** 设置类行的前置图标方块（圆角方块，不是圆——见界面规范第八节）。 */
    val iconTile = 32.dp

    /** 流水行里文字列的左缩进（头像 + 两道间距）——行分隔线跟它对齐，不压在头像下面。 */
    val rowTextIndent = PennedSpacing.lg + avatar + PennedSpacing.md

    /** 记一笔宫格图标圆。 */
    val categoryBubble = 48.dp

    /** 记账键盘键高。 */
    val keyHeight = 52.dp

    /** 统计环形图直径与描边（AO 轮：环放大、描边收细，圆心要放得下 20sp 的金额）。 */
    val donut = 144.dp
    val donutStroke = 16.dp

    /** 空态图标圈直径。 */
    val emptyIcon = 56.dp

    /** 顶栏高度。 */
    val topBar = 56.dp

    /** 悬浮件（FAB、悬浮记账入口收起态）直径。 */
    val fab = 56.dp

    /** 卡头里"支出|收入"这类两段切换器的定宽（放卡头右侧，铺满会喧宾夺主）。 */
    val segmentToggle = 132.dp

    /** 底部 Snackbar 与导航栏的间距。 */
    val fabMargin = 16.dp
}

/**
 * 层级（docs/界面规范.md 第六节）：**全局不用阴影**（D024 原则二 / D043）。
 * 层级 = 底（`background`）→ 卡（`surface`）→ 浮层（`surface`/`primary` 填充 + 发丝线）。
 *
 * 这条 token 留着是为了"要统一给浮层加厚度时只改这一个值"——当前值就是 0，
 * 引用它时请连同界面规范一起改，别就地写 elevation = 6.dp。
 */
object PennedElevation {
    val flat = 0.dp
    val floating = 0.dp
}
