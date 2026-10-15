package dev.penned.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.domain.StatsLayout
import dev.penned.app.domain.StatsLayoutCodec
import dev.penned.app.domain.StatsModule
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.SectionCard
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.iconMutedColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 统计设置（M3-14）：模块的显示开关 + 顺序，写 DataStore、**改动即生效**。 */
class StatsSettingsViewModel(private val app: PennedApplication) : ViewModel() {

    val layout: StateFlow<StatsLayout> = combine(
        app.settings.statsOrder,
        app.settings.statsEnabled,
    ) { order, enabled ->
        StatsLayoutCodec.decode(order, enabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsLayout())

    fun toggle(module: StatsModule, on: Boolean) {
        val next = layout.value.toggle(module, on)
        viewModelScope.launch { app.settings.setStatsEnabled(StatsLayoutCodec.encodeEnabled(next.enabled)) }
    }

    fun moveUp(module: StatsModule) = move(layout.value.moveUp(module))

    fun moveDown(module: StatsModule) = move(layout.value.moveDown(module))

    private fun move(next: StatsLayout) {
        viewModelScope.launch { app.settings.setStatsOrder(StatsLayoutCodec.encodeOrder(next.order)) }
    }
}

@Composable
fun StatsSettingsScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val viewModel: StatsSettingsViewModel = viewModel(initializer = { StatsSettingsViewModel(app) })
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    StatsSettingsContent(
        layout = layout,
        onToggle = viewModel::toggle,
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onBack = onBack,
    )
}

/** 无状态入口：截图测试/预览用。 */
@Composable
fun StatsSettingsContent(
    layout: StatsLayout,
    onToggle: (StatsModule, Boolean) -> Unit,
    onMoveUp: (StatsModule) -> Unit,
    onMoveDown: (StatsModule) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "统计设置", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = PennedSpacing.xxl),
        ) {
            SectionCard(title = "模块") {
                layout.order.forEachIndexed { index, module ->
                    if (index > 0) PennedDivider(startIndent = PennedSpacing.lg)
                    StatsModuleRow(
                        module = module,
                        enabled = layout.isEnabled(module),
                        canMoveUp = index > 0,
                        canMoveDown = index < layout.order.lastIndex,
                        onToggle = { on -> onToggle(module, on) },
                        onMoveUp = { onMoveUp(module) },
                        onMoveDown = { onMoveDown(module) },
                    )
                }
            }
            Text(
                "关掉的模块不渲染也不计算；全关时统计页只留一句空态提示（顺序用 ↑ ↓ 调）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = PennedSpacing.lg,
                    end = PennedSpacing.lg,
                    top = PennedSpacing.sm,
                ),
            )
        }
    }
}

/** 一行：模块名 + 说明 + 上移/下移 + 开关。 */
@Composable
private fun StatsModuleRow(
    module: StatsModule,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .padding(start = PennedSpacing.lg, end = PennedSpacing.sm, top = PennedSpacing.sm, bottom = PennedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(module.label, style = PennedTextStyles.rowTitle)
            Text(
                module.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(PennedSpacing.xs))
        MoveButton(up = true, enabled = canMoveUp, label = "上移${module.label}", onClick = onMoveUp)
        MoveButton(up = false, enabled = canMoveDown, label = "下移${module.label}", onClick = onMoveDown)
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

/** 上/下移按钮：同一个箭头图标转 180°（上移），不可用时只变淡不消失（位置稳定）。 */
@Composable
private fun MoveButton(up: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(PennedDimens.iconButton)) {
        Icon(
            PennedIcons.ChevronDown,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else iconMutedColor(),
            modifier = Modifier
                .size(PennedDimens.iconInline)
                .rotate(if (up) 180f else 0f),
        )
    }
}

@Preview(showBackground = true, name = "统计设置", widthDp = 400, heightDp = 800)
@Composable
private fun StatsSettingsPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        StatsSettingsContent(
            layout = StatsLayout(),
            onToggle = { _, _ -> },
            onMoveUp = {},
            onMoveDown = {},
            onBack = {},
        )
    }
}
