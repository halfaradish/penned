package dev.penned.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.SectionCard
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles

/** 设置类页面：图标 + 标题 + 副标题 + 右箭头的分组卡（取代原来的裸分隔线列表）。 */
private val ROW_DIVIDER_INDENT = 64.dp

/**
 * 「我的」页（M1 第二批）：数据管理入口 + 关于。对标一木的信息结构，仅保留规格内条目。
 */
@Composable
fun MineScreen(
    onOpenDataManagement: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenPinLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // 不透明页面：下钻推入时父子两页同屏，透明根会让下层页透出来（AP-2）
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = PennedSpacing.xxl),
    ) {
        Text(
            "一笔一记",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(
                start = PennedSpacing.lg,
                end = PennedSpacing.lg,
                top = PennedSpacing.xl,
            ),
        )
        Text(
            "本地优先 · 无服务器",
            style = PennedTextStyles.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = PennedSpacing.lg,
                end = PennedSpacing.lg,
                top = 2.dp,
            ),
        )

        SectionCard(title = "数据") {
            PennedListRow(
                title = "数据管理",
                subtitle = "导入一木 xls / CSV 备份 · 导出 · 导入批次",
                leadingIcon = PennedIcons.Database,
                onClick = onOpenDataManagement,
            )
            PennedDivider(startIndent = ROW_DIVIDER_INDENT)
            PennedListRow(
                title = "分类管理",
                subtitle = "新增 / 重命名 / 删除支出与收入分类",
                leadingIcon = PennedIcons.Tag,
                onClick = onOpenCategories,
            )
        }

        Spacer(Modifier.height(PennedSpacing.sm))
        SectionCard(title = "其他") {
            PennedListRow(
                title = "密码锁",
                subtitle = "启动与回到应用时需输入 4 位密码",
                leadingIcon = PennedIcons.Lock,
                onClick = onOpenPinLock,
            )
            PennedDivider(startIndent = ROW_DIVIDER_INDENT)
            // 纯信息行：不可点、无右箭头（关于页与更新检查随 M2）
            PennedListRow(
                title = "关于",
                subtitle = "版本 0.1.0（M1）",
                leadingIcon = PennedIcons.Info,
                showChevron = false,
            )
        }
    }
}
