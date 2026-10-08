package dev.penned.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import kotlin.math.roundToInt

/** 悬浮记账入口的 tag：展开是底部胶囊、收起是右下角圆，始终是同一个节点。 */
internal const val HOME_ADD_TAG = "home-add"

/** 入口标签外框的 tag：标签按进度收窄，量宽度要量这一层（Text 自身的语义边界不随外框收窄）。 */
internal const val HOME_ADD_LABEL_TAG = "home-add-label"

/**
 * 悬浮记账入口（界面上就是原来那个「添加一条新记账」按钮，只是搬到了列表之上）：
 * 停在底部 Tab 栏上方；页面往下看时收成右下角的正圆「＋」，往回看复原。
 *
 * 形变由**一个进度值**驱动（宽度、标签收窄、标签淡出全从它算出来），所以几何连续、没有跳变；
 * 不自绘阴影（原则二：全局不用阴影，层级靠表面色）。系统「动画时长缩放 = 0」时
 * `animateFloatAsState` 会直接落位。
 */
@Composable
internal fun HomeAddEntry(
    visible: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        BoxWithConstraints(Modifier.padding(PennedSpacing.lg)) {
            val progress by animateFloatAsState(
                targetValue = if (expanded) 0f else 1f,
                animationSpec = tween(AddEntryMotion.DURATION, easing = FastOutSlowInEasing),
                label = "home-add-morph",
            )
            // maxWidth 已经扣掉左右 16dp 内边距：展开＝通栏，收起＝56dp 正圆
            val width = AddEntryMotion.width(maxWidth, collapsed = AddEntryMotion.collapsedSize, progress = progress)
            Box(
                modifier = Modifier
                    .size(width = width, height = AddEntryMotion.collapsedSize)
                    .clip(RoundedCornerShape(AddEntryMotion.collapsedSize / 2))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onClick)
                    .testTag(HOME_ADD_TAG),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 固定 56dp 图标槽：收完时 Row 只剩它，正好落在圆心
                    Box(Modifier.size(AddEntryMotion.collapsedSize), contentAlignment = Alignment.Center) {
                        Icon(
                            PennedIcons.Plus,
                            contentDescription = "记一笔",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(PennedDimens.icon),
                        )
                    }
                    Box(Modifier.testTag(HOME_ADD_LABEL_TAG)) {
                        AddEntryLabel(text = "添加一条新记账", progress = progress)
                    }
                }
            }
        }
    }
}

/** 入口标签：随形变进度收窄 + 淡出；Row 居中，所以收窄的同时图标一路滑向圆心。 */
@Composable
private fun AddEntryLabel(text: String, progress: Float, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
        maxLines = 1,
        modifier = modifier
            .alpha(AddEntryMotion.labelAlpha(progress))
            .layout { measurable, _ ->
                // 先按自然宽度量，再按进度收窄（不是让它换行/省略号）——宽度与容器同步收敛
                val placeable = measurable.measure(Constraints())
                layout((placeable.width * (1f - progress)).roundToInt(), placeable.height) {
                    placeable.place(0, 0)
                }
            },
    )
}

/** 悬浮入口的形变口径（纯函数，表驱动单测）。 */
internal object AddEntryMotion {
    /** 形变时长：回答的是一次滚动手势，比页面转场（300ms）略快。 */
    const val DURATION = 240

    /** 收起后是正圆：宽 = 高 = 56dp、圆角半径 28dp（与原来的右下角 FAB 同尺寸）。 */
    val collapsedSize = 56.dp

    /** 累计往下看多少才收：太小会被惯性抖动反复触发。 */
    val collapseAfter = 24.dp

    /** 标签在这一段进度里淡完；剩下的行程是"空胶囊继续收缩"，不会看到文字被圆角挤扁。 */
    const val LABEL_FADE = 0.55f

    /** 0 = 通栏胶囊，1 = 右下角正圆。 */
    fun width(full: Dp, collapsed: Dp, progress: Float): Dp =
        full + (collapsed - full) * progress.coerceIn(0f, 1f)

    fun labelAlpha(progress: Float): Float =
        (1f - progress.coerceIn(0f, 1f) / LABEL_FADE).coerceIn(0f, 1f)
}

/**
 * 滚动方向判定（纯逻辑）：**往回看立刻展开、继续往下看累计够了才收起**——
 * 走 Material 那条通用口径（内容往下走 = 回看 = 展开），配合"回到顶部恒展开"，
 * 与「添加一条新记账」滚出视野后 FAB 接管的老行为读起来是一致的。
 *
 * @return true = 展开、false = 收起、null = 保持原状
 */
internal class AddEntryScrollGate(private val collapseAfterPx: Float) {
    private var advance = 0f

    fun onScroll(deltaY: Float, atTop: Boolean): Boolean? = when {
        // 到顶（或列表根本滚不动）：永远是展开态
        atTop -> {
            advance = 0f
            true
        }

        // deltaY > 0 = 内容往下走 = 往回看
        deltaY > 0f -> {
            advance = 0f
            true
        }

        deltaY < 0f -> {
            advance += -deltaY
            if (advance >= collapseAfterPx) {
                advance = 0f
                false
            } else {
                null
            }
        }

        else -> null
    }
}
