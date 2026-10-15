package dev.penned.app.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.domain.Money
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedSegmentedTabs
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.SectionCard
import dev.penned.app.ui.theme.BrandPreview
import dev.penned.app.ui.theme.DarkMode
import dev.penned.app.ui.theme.PennedBrands
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.ThemeAccent
import kotlinx.coroutines.launch

/** 日头条小样的竖条宽（与首页那根 3dp 竖条同值）。 */
private val SAMPLE_BAR_WIDTH = 3.dp

/** 一套色相预览行的最小高度：小样卡（日头条 + 按钮 + 胶囊）那一行得放得下。 */
private val ACCENT_ROW_MIN_HEIGHT = 96.dp

/**
 * 主题外观（M3-12，我的 → 主题外观）：品牌色相 4 套 + 深浅模式。
 *
 * **只换品牌一族**——中性灰、收支红绿、分类颜料盘一律不动（分类身份与统计可读性优先）；
 * 选中即生效（写 DataStore，`MainActivity` 收到后整棵树换色），不需要"保存"。
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val accentKey by app.settings.themeAccentKey.collectAsStateWithLifecycle(initialValue = null)
    val modeKey by app.settings.darkModeKey.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val darkMode = PennedBrands.darkModeFromKey(modeKey)

    AppearanceContent(
        accent = PennedBrands.fromKey(accentKey),
        darkMode = darkMode,
        // 小样按"实际会看到的深浅"画：跟随系统时看系统
        darkTheme = darkMode.isDark(androidx.compose.foundation.isSystemInDarkTheme()),
        onPickAccent = { accent -> scope.launch { app.settings.setThemeAccent(accent.name) } },
        onPickDarkMode = { mode -> scope.launch { app.settings.setDarkMode(mode.name) } },
        onBack = onBack,
    )
}

/** 无状态入口：截图测试/预览用（不碰 DataStore）。 */
@Composable
fun AppearanceContent(
    accent: ThemeAccent,
    darkMode: DarkMode,
    darkTheme: Boolean,
    onPickAccent: (ThemeAccent) -> Unit,
    onPickDarkMode: (DarkMode) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "主题外观", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = PennedSpacing.xxl),
        ) {
            SectionCard(title = "品牌色相") {
                ThemeAccent.entries.forEachIndexed { index, item ->
                    if (index > 0) PennedDivider(startIndent = PennedSpacing.lg)
                    AccentRow(
                        accent = item,
                        preview = PennedBrands.preview(item, darkTheme),
                        selected = item == accent,
                        onClick = { onPickAccent(item) },
                    )
                }
            }
            Text(
                "只换品牌色相：中性灰、收支红绿与分类颜料盘不随主题变" +
                    "（分类身份与统计可读性优先）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = PennedSpacing.lg,
                    end = PennedSpacing.lg,
                    top = PennedSpacing.sm,
                ),
            )

            Spacer(Modifier.height(PennedSpacing.md))
            SectionCard(title = "深浅模式") {
                Column(Modifier.padding(PennedSpacing.lg)) {
                    PennedSegmentedTabs(
                        options = DarkMode.entries.toList(),
                        selected = darkMode,
                        onSelect = onPickDarkMode,
                        label = { it.label },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "「跟随系统」= 跟手机的深色开关走；选浅色/深色则固定不动",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = PennedSpacing.lg,
                        end = PennedSpacing.lg,
                        bottom = PennedSpacing.md,
                    ),
                )
            }
        }
    }
}

/**
 * 一套色相的预览行：名称 + 「已选」标记 + 一张小样卡。
 * 小样卡里画三件东西——日头条（淡底 + 品牌竖条 + 墨色日期）、主按钮、选中胶囊；
 * 底用**当前主题的卡片底**（中性色不随主题变，正好当"展示台"）。
 */
@Composable
private fun AccentRow(
    accent: ThemeAccent,
    preview: BrandPreview,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ACCENT_ROW_MIN_HEIGHT)
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(accent.label, style = PennedTextStyles.rowTitle)
            Spacer(Modifier.weight(1f))
            if (selected) {
                Text(
                    "已选",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(PennedSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 日头条小样：淡品牌底 + 3dp 竖条 + 墨色日期 + 朱砂支出
            Row(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(preview.brand.dayHeader)
                    .padding(horizontal = PennedSpacing.sm, vertical = PennedSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(SAMPLE_BAR_WIDTH)
                        .height(PennedSpacing.lg)
                        .background(preview.brand.primaryInk),
                )
                Spacer(Modifier.width(PennedSpacing.sm))
                Text("10月15日 周三", style = MaterialTheme.typography.labelMedium, color = preview.onSurface)
                Spacer(Modifier.weight(1f))
                Text(
                    "支 ${Money.format(600L)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = preview.expense,
                )
            }
            Spacer(Modifier.width(PennedSpacing.sm))
            // 主按钮小样（primary 实心 + onPrimary 字）
            Box(
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(preview.brand.primary)
                    .padding(horizontal = PennedSpacing.md, vertical = PennedSpacing.xs),
            ) {
                Text(
                    "记一笔",
                    style = MaterialTheme.typography.labelMedium,
                    color = preview.brand.onPrimary,
                )
            }
            Spacer(Modifier.width(PennedSpacing.sm))
            // 选中胶囊小样（primaryContainer + onPrimaryContainer）
            Box(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(preview.brand.primaryContainer)
                    .padding(horizontal = PennedSpacing.md, vertical = PennedSpacing.xs),
            ) {
                Text(
                    "餐饮",
                    style = MaterialTheme.typography.labelMedium,
                    color = preview.brand.onPrimaryContainer,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "主题外观", widthDp = 400, heightDp = 800)
@Composable
private fun AppearancePreview() {
    dev.penned.app.ui.theme.PennedTheme {
        AppearanceContent(
            accent = ThemeAccent.TEAL,
            darkMode = DarkMode.SYSTEM,
            darkTheme = false,
            onPickAccent = {},
            onPickDarkMode = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, name = "主题外观 · 深色", widthDp = 400, heightDp = 800)
@Composable
private fun AppearanceDarkPreview() {
    dev.penned.app.ui.theme.PennedTheme(darkTheme = true) {
        AppearanceContent(
            accent = ThemeAccent.OCHRE,
            darkMode = DarkMode.DARK,
            darkTheme = true,
            onPickAccent = {},
            onPickDarkMode = {},
            onBack = {},
        )
    }
}
