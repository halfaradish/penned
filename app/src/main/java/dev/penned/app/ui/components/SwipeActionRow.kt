package dev.penned.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedMotion
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.rememberAnimationsEnabled
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 落位判定用的最小甩速：超过才按"甩"算。比 M3 默认的 125dp/s 高一档，防的是"轻轻一蹭就展开"。 */
private val ActionSwipeFling = 500.dp

/**
 * QQ 式左滑动作区（UI-12 / D053）：内容层 1:1 跟手左移，露出右侧固定的动作块（编辑 / 删除）。
 *
 * 与 [SwipeToDeleteRow]（滑过阈值即触发动作，资产页仍在用）的关键区别是**没有"滑到某个位置就执行"**：
 * 滑只负责把按钮露出来，动作必须再点一下按钮——首页的误触正出在"滑过 56dp 或甩速 125dp/s 就弹确认"。
 *
 * 口径：
 * - 露满（= 面板实测宽）后夹住，继续滑不再移动；松手时超过半宽或甩速够 → 定格为展开，否则收回；
 * - 展开是"定格"状态，不会自己消失：点按钮才动作；收起由调用方改 [isOpen]（点行内容 / 滚列表 / 开了另一行）
 *   或往回拖松手驱动；
 * - 面板宽按内容实测（字号放大时按钮变宽，不裁字），最小 [PennedDimens.rowActionWidth]。
 *
 * 三个坑：
 * - 动作面板用 `matchParentSize` 才拿得到"与行同高"：在 LazyColumn 的无穷高约束里 `fillMaxHeight`
 *   会塌成内容高，按钮就只剩一行字那么高；
 * - 面板压在内容层底下，读屏会把两个按钮当成"可见控件"——关闭态补 `invisibleToUser` 挡掉焦点；
 * - 偏移量用 `remember`（不 saveable）：删除后行被移出列表、撤销时同一 id 重新入列，
 *   被复原的"展开态"会立刻再删一次（旧组件踩过同型坑）。
 */
@Composable
fun SwipeActionRow(
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    rowBackground: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val animationsEnabled = rememberAnimationsEnabled()
    val offset = remember { Animatable(0f) }
    var panelWidth by remember { mutableIntStateOf(0) }
    val flingPx = with(LocalDensity.current) { ActionSwipeFling.toPx() }

    // 落位：手势松手与"外部改 isOpen"共用同一条路径；动画缩放 0 时直接落位不播放
    suspend fun settleTo(target: Float) {
        if (abs(offset.value - target) < 1f) return
        if (animationsEnabled) {
            offset.animateTo(target, tween(durationMillis = PennedMotion.base, easing = PennedMotion.ease))
        } else {
            offset.snapTo(target)
        }
    }

    LaunchedEffect(isOpen, panelWidth) {
        if (panelWidth <= 0) return@LaunchedEffect
        settleTo(if (isOpen) -panelWidth.toFloat() else 0f)
    }

    val dragState = rememberDraggableState { delta ->
        val max = panelWidth.toFloat()
        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-max, 0f)) }
    }

    Box(
        modifier
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    val max = panelWidth.toFloat()
                    if (max > 0f) {
                        val open = -offset.value > max / 2f || velocity < -flingPx
                        if (open && !isOpen) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (open != isOpen) onOpenChange(open)
                        // 状态没变时（开→开 / 关→关）只有这里能落位，所以本行不省；
                        // 状态变了时下一次 LaunchedEffect 也会收口到同一目标，重复调用无副作用
                        settleTo(if (open) -max else 0f)
                    }
                },
            )
            .semantics {
                // 左滑对读屏用户完全不可见：两个动作都补成无障碍动作（D044）
                customActions = listOf(
                    CustomAccessibilityAction("编辑") { onEdit(); true },
                    CustomAccessibilityAction("删除") { onDelete(); true },
                )
            },
    ) {
        // 动作面板：与行同框、内容贴右，压在内容层底下（关闭态被内容层完全盖住）
        Row(
            Modifier
                .matchParentSize()
                .semantics { if (!isOpen) invisibleToUser() },
            horizontalArrangement = Arrangement.End,
        ) {
            Row(Modifier.fillMaxHeight().onSizeChanged { panelWidth = it.width }) {
                RowAction("编辑", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface, onEdit)
                RowAction("删除", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError, onDelete)
            }
        }
        // 内容层：跟手平移；自带行底，不然没滑动时会透出按钮
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(rowBackground),
        ) {
            content()
        }
    }
}

/** 动作块：直角、与行同高（QQ 同款是两个实心块，不是圆角按钮）。 */
@Composable
private fun RowAction(label: String, container: Color, contentColor: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .widthIn(min = PennedDimens.rowActionWidth)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1)
    }
}
