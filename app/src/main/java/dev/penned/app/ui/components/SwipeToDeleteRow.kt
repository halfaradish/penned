package dev.penned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.expenseColor
import kotlinx.coroutines.launch

/**
 * 左滑删除（D024 交互范式二）：滑到阈值即软删并触发一次触感。
 *
 * 两个坑：
 * - 行要自带 [rowBackground] 底色，不盖住会在没滑动时透出红底；
 * - 状态用 `remember` 自建而不是 `rememberSwipeToDismissBoxState`（后者是 rememberSaveable）：
 *   删除后行被移出列表，撤销时同一 id 重新入列，被复原的"已滑出"状态会让它立刻再删一次。
 *   滑出后再显式 snapTo 归位，双重保险。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    rowBackground: Color = MaterialTheme.colorScheme.background,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val state = remember(threshold) {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            positionalThreshold = threshold,
        )
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
                scope.launch { state.snapTo(SwipeToDismissBoxValue.Settled) }
            }
        },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(expenseColor()),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    PennedIcons.Trash,
                    contentDescription = "删除",
                    tint = Color.White,
                    modifier = Modifier.padding(end = PennedSpacing.xl),
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(rowBackground)) { content() }
    }
}
