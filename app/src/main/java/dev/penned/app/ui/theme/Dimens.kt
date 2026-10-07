package dev.penned.app.ui.theme

import androidx.compose.ui.unit.dp

/** 间距阶梯（docs/界面重构方案.md 第五节）：屏边距 16、卡片内 16、卡间 12、分组间 24。 */
object PennedSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

/** 组件尺寸：行高、热区、头像、键盘键。 */
object PennedDimens {
    /** 卡片/抽屉边界线宽（发丝线，不用阴影表达层级）。 */
    val hairline = 1.dp

    /** 卡片圆角（与 shapes.medium 同值；分段卡片要按角拼，得拿得到数值）。 */
    val cardCorner = 16.dp

    /** 列表行最小高度。 */
    val rowMinHeight = 60.dp

    /** 图标按钮热区。 */
    val iconButton = 44.dp

    /** 图标默认尺寸。 */
    val icon = 24.dp

    /** 流水行头像（搜索结果/首页）。 */
    val avatar = 36.dp

    /** 大号头像（资产账户、统计排行）。 */
    val avatarLarge = 40.dp

    /** 流水行里文字列的左缩进（头像 + 两道间距）——行分隔线跟它对齐，不压在头像下面。 */
    val rowTextIndent = PennedSpacing.lg + avatar + PennedSpacing.md

    /** 记一笔宫格图标圆。 */
    val categoryBubble = 48.dp

    /** 记账键盘键高。 */
    val keyHeight = 52.dp

    /** 统计环形图直径与描边。 */
    val donut = 132.dp
    val donutStroke = 18.dp

    /** 底部 Snackbar 与导航栏的间距。 */
    val fabMargin = 16.dp
}
