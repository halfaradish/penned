package dev.penned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.Money
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.PennedTheme
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.incomeColor
import dev.penned.app.ui.theme.primaryInkColor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 组件总览截图（浅/深各一张）：改共用组件后一眼过目——这是**人工评审用**的图，不是回归基线
 * （同 `IconGalleryTest` 的定位）。AR 轮起组件语言变了先看这两张。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentGalleryTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureGalleryLight() {
        composeRule.setContent { PennedTheme(darkTheme = false) { Gallery() } }
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/component_gallery.png")
    }

    @Test
    fun captureGalleryDark() {
        composeRule.setContent { PennedTheme(darkTheme = true) { Gallery() } }
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/component_gallery_dark.png")
    }

    @Composable
    private fun Gallery() {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = PennedSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PennedSpacing.md),
        ) {
            Text(
                "卡片 / 分段卡 / 分隔线",
                style = PennedTextStyles.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PennedSpacing.lg),
            )
            PennedCard(Modifier.padding(horizontal = PennedSpacing.lg)) {
                Text("无边框白卡 + 16dp 圆角", style = PennedTextStyles.rowTitle)
                Spacer(Modifier.height(PennedSpacing.sm))
                PennedDivider(startIndent = 0.dp)
                Spacer(Modifier.height(PennedSpacing.sm))
                Text("卡内分隔线 1dp outlineVariant", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.padding(horizontal = PennedSpacing.lg)) {
                PennedCardSection(PennedCardSectionPosition.Top) {
                    Text("分段卡顶段", style = PennedTextStyles.rowTitle, modifier = Modifier.padding(PennedSpacing.lg))
                }
                PennedCardSection(PennedCardSectionPosition.Middle) {
                    Text("中段", style = PennedTextStyles.rowTitle, modifier = Modifier.padding(PennedSpacing.lg))
                }
                PennedCardSection(PennedCardSectionPosition.Bottom) {
                    Text("底段（圆角落在这里）", style = PennedTextStyles.rowTitle, modifier = Modifier.padding(PennedSpacing.lg))
                }
            }

            Text(
                "按钮三级 / 文字动作",
                style = PennedTextStyles.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PennedSpacing.lg),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PennedButton("主按钮", {}, modifier = Modifier.weight(1f), fillWidth = false)
                PennedButton("次按钮", {}, variant = PennedButtonVariant.Secondary, modifier = Modifier.weight(1f), fillWidth = false)
                PennedButton("危险", {}, variant = PennedButtonVariant.Danger, modifier = Modifier.weight(1f), fillWidth = false)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PennedTextAction("文字动作", {})
                Spacer(Modifier.width(PennedSpacing.md))
                PennedBadge("部分退款")
                Spacer(Modifier.width(PennedSpacing.sm))
                PennedBadge("手续费 2.00")
            }

            Text(
                "分段 / chips",
                style = PennedTextStyles.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PennedSpacing.lg),
            )
            PennedSegmentedTabs(
                options = listOf("支出", "收入", "转账"),
                selected = "支出",
                onSelect = {},
                label = { it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PennedChip("未选 chip")
                PennedChip("选中 chip", selected = true)
                PennedChip("禁用", enabled = false)
                PennedChip("带图标", icon = PennedIcons.Calendar)
            }

            Text(
                "行 / 汇总 / 进度条",
                style = PennedTextStyles.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PennedSpacing.lg),
            )
            SectionCard(Modifier.fillMaxWidth()) {
                PennedListRow("数据管理", leadingIcon = PennedIcons.Database, onClick = {})
                PennedDivider(startIndent = 60.dp)
                PennedListRow("分类管理", subtitle = "新增 / 重命名 / 删除", leadingIcon = PennedIcons.Tag, onClick = {})
                PennedDivider(startIndent = 60.dp)
                PennedSwitchRow("不计入收支", checked = true, onCheckedChange = {})
            }
            PennedCard(Modifier.padding(horizontal = PennedSpacing.lg)) {
                PennedStatCells(
                    listOf(
                        StatCell("支出", Money.format(13300), expenseColor()),
                        StatCell("收入", Money.format(1050000), incomeColor()),
                        StatCell("结余", Money.format(1036700)),
                    ),
                )
                Spacer(Modifier.height(PennedSpacing.md))
                PennedProgressBar(progress = 0.73f)
                Spacer(Modifier.height(PennedSpacing.sm))
                Text(
                    "预算 2,000.00 · 已用 16.00（0.73% 的绿在左端）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "空态 / 头像",
                style = PennedTextStyles.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PennedSpacing.lg),
            )
            PennedCard(Modifier.padding(horizontal = PennedSpacing.lg), contentPadding = PaddingValues(0.dp)) {
                EmptyState(
                    title = "还没有记录",
                    hint = "记一笔就有数据了",
                    icon = PennedIcons.Pie,
                    action = { PennedButton("记一笔", {}, fillWidth = false) },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("食品餐饮", "工资", "晚餐", "购物消费", "休闲零食", "转账", "未分类").forEach { name ->
                    CategoryAvatar(name, size = 36.dp)
                }
                Text("颜料盘", style = MaterialTheme.typography.labelMedium, color = primaryInkColor())
            }
        }
    }
}
