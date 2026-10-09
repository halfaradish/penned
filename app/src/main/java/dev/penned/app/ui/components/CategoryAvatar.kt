package dev.penned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedPigment
import dev.penned.app.ui.theme.isDarkTheme
import dev.penned.app.ui.theme.pigmentIconFill
import dev.penned.app.ui.theme.primaryInkColor

/**
 * 圆形头像（分类/账户/账本/导入行）：**淡灰绿底 + 墨色字形**（AS 轮起；日头条那一栏的口径）。
 *
 * 底色**全 App 一个色**（[pigmentIconFill]，12%/22% 叠在卡片上，与白卡 1.17:1、跟日头条底
 * 1.12:1 同档，是"纸"不是"色块"）——**不按分类分槽**。分类色只留在统计页图表细件（占比环/
 * 图例点/占比条）与宫格"有子分类"小圆点上。字形一律 `onSurface` 墨色（浅 15.6:1 / 深 8.2:1），
 * 不染颜料：前几轮把字形染成彩色，正是"艳"与"国画"两种观感的来源。
 *
 * [selected] = **品牌青绿实心 + 反白**（与日头条那根 3dp 竖条、宫格下方选中的分类名同色）——
 * 和淡底拉开一整个档，是宫格里唯一的"状态色"。
 */
@Composable
fun CategoryAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = PennedDimens.avatar,
    selected: Boolean = false,
    glyph: String? = null,
) {
    val background = if (selected) primaryInkColor() else pigmentIconFill()
    val foreground = when {
        !selected -> MaterialTheme.colorScheme.onSurface
        // 选中态是实心青绿：浅色配白字；深色下青绿是亮档，配深字
        isDarkTheme() -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Box(
        modifier = modifier.size(size).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph ?: defaultGlyph(name),
            style = if (size >= PennedDimens.avatarLarge) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = foreground,
        )
    }
}

/** 转账显示「转」、未分类显示「?」，其余取首字。 */
fun defaultGlyph(name: String): String = when (name) {
    PennedPigment.TRANSFER -> "转"
    PennedPigment.UNKNOWN, "?" -> "?"
    else -> name.take(1)
}
