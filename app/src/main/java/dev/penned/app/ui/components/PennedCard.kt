package dev.penned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles

/**
 * 卡片：纯白 `surface` + 16dp 圆角，**无边框、无阴影**（D043 原则二）。
 * 层级靠"底（background 中性灰）→ 卡（surface 白）"的底色差表达，发丝线只留给卡内的行分隔。
 */
@Composable
fun PennedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(PennedSpacing.lg),
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .pennedClickable(onClick)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** 分段卡片：一张卡在 LazyColumn 里横跨多个 item 时，每段带自己的位置。 */
enum class PennedCardSectionPosition { Top, Middle, Bottom, Single }

/**
 * 分段卡片容器：一张卡 = 顶段 + 若干中段 + 底段（首页月卡就是这种拼法）。
 * 画法：每段自己铺 `surface` 底、按位置裁圆角，拼起来仍读作一张卡（无边框，见 D043 原则二）。
 *
 * **圆角段必须够高**：Compose 会把圆角半径夹到短边的一半，4dp 高的封顶条上 16dp 圆角实际只画得出 2dp，
 * 整张卡看着就是直角（首页月卡踩过这个坑）。顶段/底段要交给有内容、够高的那一行，别拿细条凑。
 *
 * 注意：`modifier` 里的 padding 要放在调用方（圆角跟卡片走，间距留在卡片外）。
 */
@Composable
fun PennedCardSection(
    position: PennedCardSectionPosition,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val corner = PennedDimens.cardCorner
    val shape = when (position) {
        PennedCardSectionPosition.Top -> RoundedCornerShape(topStart = corner, topEnd = corner)
        PennedCardSectionPosition.Bottom -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
        PennedCardSectionPosition.Single -> RoundedCornerShape(corner)
        PennedCardSectionPosition.Middle -> RectangleShape
    }
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

/** 卡片内分隔线（默认与行内文字左对齐）。 */
@Composable
fun PennedDivider(modifier: Modifier = Modifier, startIndent: Dp = PennedSpacing.lg) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(PennedDimens.hairline)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 分组标题（卡片外的弱化小标题）。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = PennedTextStyles.sectionLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = PennedSpacing.lg,
            end = PennedSpacing.lg,
            top = PennedSpacing.md,
            bottom = PennedSpacing.sm,
        ),
    )
}

/** 分组卡：可选标题 + 一张卡包住若干行（设置类页面的标准形态）。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) SectionLabel(title)
        PennedCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
            contentPadding = PaddingValues(0.dp),
            content = content,
        )
    }
}
