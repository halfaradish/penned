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
import dev.penned.app.ui.theme.pigmentFill
import dev.penned.app.ui.theme.pigmentFillAt
import dev.penned.app.ui.theme.pigmentOnFillAt
import dev.penned.app.ui.theme.pigmentSelectedAt

/**
 * 分类头像：**实心颜料块 + 同色相深档字形**（D046 起）。
 * 取代 AR/RA 两轮的"淡底 + ink 字"——那两版一个太艳一个太灰，用户都不认。
 * 现在的口径是用户给的参考（课表课程格子）：块面亮而干净（S74/L70，浅深两个主题同一份），
 * 字形用同色相的深档（在自家块面上 4.6:1），所以块面本身就承担了"这是什么分类"。
 *
 * [selected] 换成深档实心 + 白字：和亮档块面差一整个明度档，选中一眼看得出（记一笔宫格用）。
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
    val background = if (selected) pigmentSelectedAt(s) else pigmentFillAt(s)
    val foreground = if (selected) PennedPigment.onFillWhite else pigmentOnFillAt(s)
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
