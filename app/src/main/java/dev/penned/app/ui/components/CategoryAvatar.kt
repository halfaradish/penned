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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedPigment
import dev.penned.app.ui.theme.isDarkTheme
import dev.penned.app.ui.theme.pigmentInk
import dev.penned.app.ui.theme.pigmentTint

/**
 * 分类头像：颜料 tint 底 + 颜料 ink 首字。
 * 取代首页/统计/资产/分类管理四处各画一遍的"哈希实心圆 + 白字"。
 */
@Composable
fun CategoryAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = PennedDimens.avatar,
    selected: Boolean = false,
    glyph: String? = null,
) {
    val ink = pigmentInk(name)
    val bg = if (selected) ink else pigmentTint(name)
    val fg = when {
        !selected -> ink
        isDarkTheme() -> Color(0xFF0E1211)
        else -> Color.White
    }
    Box(
        modifier = modifier.size(size).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph ?: defaultGlyph(name),
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            color = fg,
        )
    }
}

/** 转账显示「转」、未分类显示「?」，其余取首字。 */
fun defaultGlyph(name: String): String = when (name) {
    PennedPigment.TRANSFER -> "转"
    PennedPigment.UNKNOWN, "?" -> "?"
    else -> name.take(1)
}
