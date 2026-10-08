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
import dev.penned.app.ui.theme.pigmentFillAt
import dev.penned.app.ui.theme.pigmentInkAt

/**
 * 分类头像：**极淡色相底 + 墨色字形**（D047 起；日头条那一栏的口径）。
 *
 * 大面积只给 12%（浅）/ 22%（深）的淡底——与白卡只有 1.16~1.19:1，跟日头条底一个量级，
 * 是"纸"不是"色块"；色相给满的地方只有细件与选中态（见 `PennedPigment`）。
 * 字形一律 `onSurface` 墨色，不染颜料：前几轮把字形染成彩色，正是"艳"与"国画"两种观感的来源。
 *
 * [selected] 走全强度实心 + 反白（浅色深档配白字、深色亮档配深字）——和淡底拉开一整个档。
 * [slot] 给账户这类"按类型固定上色"的场景用；不传则按名字哈希取色。
 */
@Composable
fun CategoryAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = PennedDimens.avatar,
    selected: Boolean = false,
    glyph: String? = null,
    slot: Int? = null,
) {
    val s = slot ?: PennedPigment.slotOf(name)
    val background = if (selected) pigmentInkAt(s) else pigmentFillAt(s)
    val foreground = when {
        !selected -> MaterialTheme.colorScheme.onSurface
        // 选中态是实心底：浅色主题是深档色，配白字；深色主题是亮档色，配深字
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
