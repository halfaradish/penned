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
 * 动作块的内容：标签 + 配色 + 点击。用 [rowEditAction] / [rowDeleteAction] 造，
 * 别在调用点自己配颜色（配色是"删除红"这类 token 决定的事，收在本文件里）。
 */
data class RowAction(
    val label: String,
    val container: Color,
    val contentColor: Color,
    val onClick: () -> Unit,
)

/** 「编辑」动作块：次级填充 + 墨色字（可操作但非破坏性）。 */
@Composable
fun rowEditAction(onClick: () -> Unit): RowAction = RowAction(
    label = "编辑",
    container = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
    onClick = onClick,
)

/** 「删除」动作块：`error` 实心 + `onError` 字（浅 6.54:1 / 深 7.31:1）。 */
@Composable
fun rowDeleteAction(onClick: () -> Unit): RowAction = RowAction(
    label = "删除",
    container = MaterialTheme.colorScheme.error,
    contentColor = MaterialTheme.colorScheme.onError,
    onClick = onClick,
)

/**
 * QQ 式左滑动作区（UI-12 / D053，UI-13 起 [actions] 可配）：内容层 1:1 跟手左移，
 * 露出右侧固定的动作块——首页是「编辑 / 删除」，资产页账户行只有「删除」（点行进编辑，不再重复给入口）。
 *
 * 与"滑到某处就执行"的关键区别是**滑只负责把按钮露出来**，动作必须再点一下按钮——
 * 首页此前的误触正出在"滑过 56dp 或甩速 125dp/s 就弹确认"。旧的 `SwipeToDeleteRow` 已随
 * 两处都改用本组件而退场（UI-13）。
 *
 * 口径：
 * - 露满（= 面板实测宽）后夹住，继续滑不再移动；松手时超过半宽或甩速够 → 定格为展开，否则收回；
 * - 展开是"定格"状态，不会自己消失：点按钮才动作；收起由调用方改 [isOpen]（点行内容 / 滚列表 / 开了另一行）
 *   或往回拖松手驱动；
 * - [actions] 按**从左到右**排列，最后一个贴右边缘（删除习惯放最后）；面板宽按内容实测
 *   （字号放大时按钮变宽，不裁字），每块最小 [PennedDimens.rowActionWidth]。
 *
 * 三个坑：
 * - 动作面板用 `matchParentSize` 才拿得到"与行同高"：在 LazyColumn 的无穷高约束里 `fillMaxHeight`
 *   会塌成内容高，按钮就只剩一行字那么高；
 * - 面板压在内容层底下，读屏会把按钮当成"可见控件"——关闭态补 `invisibleToUser` 挡掉焦点；
 * - 偏移量用 `remember`（不 saveable）：删除后行被移出列表、撤销时同一 id 重新入列，
 *   被复原的"展开态"会立刻再删一次（旧组件踩过同型坑）。
 */
@Composable
fun SwipeActionRow(
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    actions: List<RowAction>,
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
                // 左滑对读屏用户完全不可见：每个动作都补一条无障碍动作（D044）
                customActions = actions.map { action ->
                    CustomAccessibilityAction(action.label) { action.onClick(); true }
                }
            },
    ) {
        // 动作面板：与行同框、内容贴右，压在内容层底下（关闭态被内容层完全盖住）
        // `enabled = isOpen` 不是装饰：内容层若**没有自己的点击处理**（如"开关行"），
        // 点击会穿过它落在底下这排按钮上——关闭态点行右侧就会**悄悄上移/删除**（M4-1 实踩）。
        Row(
            Modifier
                .matchParentSize()
                .semantics { if (!isOpen) invisibleToUser() },
            horizontalArrangement = Arrangement.End,
        ) {
            Row(Modifier.fillMaxHeight().onSizeChanged { panelWidth = it.width }) {
                actions.forEach { ActionBlock(it, enabled = isOpen) }
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

/**
 * 动作块：直角、与行同高（QQ 同款是实心块，不是圆角按钮）。
 *
 * [enabled] = 行是否已滑开：关闭态必须**不可点**，否则内容层没有点击处理的行（开关行）会把
 * 点击透到藏在底下的按钮上。
 */
@Composable
private fun ActionBlock(action: RowAction, enabled: Boolean) {
    Box(
        Modifier
            .fillMaxHeight()
            .widthIn(min = PennedDimens.rowActionWidth)
            .background(action.container)
            .clickable(enabled = enabled, onClick = action.onClick)
            .padding(horizontal = PennedSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(action.label, style = MaterialTheme.typography.labelLarge, color = action.contentColor, maxLines = 1)
    }
}
