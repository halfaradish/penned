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
 * 「我的」页（M1 第二批）：数据管理入口 + 关于（M2 起可点，进关于/更新页）。
 */
@Composable
fun MineScreen(
    onOpenDataManagement: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenPinLock: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    versionName: String = dev.penned.app.BuildConfig.VERSION_NAME,
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
                subtitle = "新增 / 重命名 / 隐藏 / 删除支出与收入分类",
                leadingIcon = PennedIcons.Tag,
                onClick = onOpenCategories,
            )
            PennedDivider(startIndent = ROW_DIVIDER_INDENT)
            PennedListRow(
                title = "标签管理",
                subtitle = "两级标签 · 隐藏不用的标签",
                leadingIcon = PennedIcons.Tag,
                onClick = onOpenTags,
            )
            PennedDivider(startIndent = ROW_DIVIDER_INDENT)
            // M3-5：模板记账（列表 + 新建/编辑 + 上移排序；用模板 = 记一笔预填）
            PennedListRow(
                title = "模板记账",
                subtitle = "把常记的那几笔存成模板，记一笔里一键预填",
                leadingIcon = PennedIcons.Receipt,
                onClick = onOpenTemplates,
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
            // M2 起可点：进「关于 / 更新」页（版本信息 + 检查更新，规格 M2 S6）
            PennedListRow(
                title = "关于",
                subtitle = "版本 $versionName · 检查更新",
                leadingIcon = PennedIcons.Info,
                onClick = onOpenAbout,
            )
        }
    }
}
