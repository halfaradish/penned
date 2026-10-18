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
    onOpenRecurring: () -> Unit,
    onOpenBillFilters: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenPinLock: () -> Unit,
    onOpenAbout: () -> Unit,
    /** 分期房贷计算器（M3-11）。 */
    onOpenLoan: () -> Unit = {},
    /** 主题外观（M3-12）。 */
    onOpenAppearance: () -> Unit = {},
    /** 愿望清单（M3-15）。 */
    onOpenWishlist: () -> Unit = {},
    /** 分类关键词规则（M4-1，「智能」分组卡的第一行）。 */
    onOpenKeywordRules: () -> Unit = {},
    /** AI 设置（M4-2，BYOK：自带 Key 直连模型）。 */
    onOpenAiSettings: () -> Unit = {},
    /** 一句话记账（M4-3）。 */
    onOpenQuickEntry: () -> Unit = {},
    /** 截图导入（M4-7）。 */
    onOpenScreenshotImport: () -> Unit = {},
    /** 自动记账（M4-8）。 */
    onOpenAutoEntry: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** 待确认条数（M4-8）：>0 时在「自动记账」那行副标题上显示。 */
    pendingCount: Int = 0,
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
            PennedDivider(startIndent = ROW_DIVIDER_INDENT)
            // M3-6：周期记账（打开即补 D070，不引后台任务）
            PennedListRow(
                title = "周期记账",
                subtitle = "房租话费这类固定支出：打开 App 时自动补记到期的期数",
                leadingIcon = PennedIcons.Refresh,
                onClick = onOpenRecurring,
            )
            PennedDivider(startIndent = ROW_DIVIDER_INDENT)
            // M3-8：账单组合（筛选条件的存档，一键应用）
            PennedListRow(
                title = "账单组合",
                subtitle = "把常用筛选条件存下来，下次一键应用",
                leadingIcon = PennedIcons.Search,
                onClick = onOpenBillFilters,
            )
        }

        Spacer(Modifier.height(PennedSpacing.sm))
        // M3-15：愿望清单（攒钱目标；规划工具，不生成流水）
        SectionCard(title = "规划") {
            PennedListRow(
                title = "愿望清单",
                subtitle = "攒钱目标：目标金额 + 进度 + 存入一笔（不生成流水）",
                leadingIcon = PennedIcons.Wallet,
                onClick = onOpenWishlist,
            )
        }

        Spacer(Modifier.height(PennedSpacing.sm))
        SectionCard(title = "外观") {
            // M3-12：品牌色相 4 套 + 深浅模式（只换品牌一族，中性层与语义色不动）
            PennedListRow(
                title = "主题外观",
                subtitle = "品牌色相 4 套 · 跟随系统 / 浅色 / 深色",
                leadingIcon = PennedIcons.Pen,
                onClick = onOpenAppearance,
            )
        }

        Spacer(Modifier.height(PennedSpacing.sm))
        // M4-1（C5 裁决）：智能分组卡——后续片各往这张卡里加自己的行
        // （M4-2「AI 设置」/ M4-3「一句话记账」/ M4-7「截图导入」/ M4-8「自动记账」），不另开二级页。
        SectionCard(title = "智能") {
            PennedListRow(
                title = "分类关键词",
                subtitle = "关键词指向分类或标签：识别到它时先把分类填好，确认后才入账",
                leadingIcon = PennedIcons.Tag,
                onClick = onOpenKeywordRules,
            )
            PennedDivider(startIndent = ROW_DIVIDER_INDENT)
            // M4-3：一句话记账（本地解析就够用，AI 兜底是可选的那一步）
            PennedListRow(
                title = "一句话记账",
                subtitle = "说一句「昨天打车 32.5」，解析成草稿再确认",
                leadingIcon = PennedIcons.Edit,
                onClick = onOpenQuickEntry,
            )
            PennedDivider(startIndent = ROW_DIVIDER_INDENT)
            // M4-7：截图导入（BYOK 多模态；不做本地 OCR）
            PennedListRow(
                title = "截图导入",
                subtitle = "截图里的一笔或一屏账单都认，确认后一起导入",
                leadingIcon = PennedIcons.Search,
                onClick = onOpenScreenshotImport,
            )
            PennedDivider(startIndent = ROW_DIVIDER_INDENT)
            // M4-8：自动记账（通知监听；识别到先落「待确认」，默认关）
            PennedListRow(
                title = "自动记账",
                subtitle = if (pendingCount > 0) {
                    "$pendingCount 条待确认"
                } else {
                    "读支付通知先落「待确认」，默认关 · 只勾你许可的 App"
                },
                leadingIcon = PennedIcons.Refresh,
                onClick = onOpenAutoEntry,
            )
            PennedDivider(startIndent = ROW_DIVIDER_INDENT)
            // M4-2：BYOK 设置（默认关；只接受 https；Key 只存本机）
            PennedListRow(
                title = "AI 设置",
                subtitle = "自带 Key 直连模型 · 默认关闭 · 不填不联网",
                leadingIcon = PennedIcons.Pen,
                onClick = onOpenAiSettings,
            )
        }

        Spacer(Modifier.height(PennedSpacing.sm))
        // M3-11：分期房贷计算器（等额本息/等额本金，只算不落库；「按首期记一笔」跳预填）
        SectionCard(title = "工具") {
            PennedListRow(
                title = "分期房贷",
                subtitle = "等额本息 / 等额本金：月供、总利息、逐期计划",
                leadingIcon = PennedIcons.Calculator,
                onClick = onOpenLoan,
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
