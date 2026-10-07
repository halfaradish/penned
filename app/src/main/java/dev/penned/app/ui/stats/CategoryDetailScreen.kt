package dev.penned.app.ui.stats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.Money
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.DayHeaderRow
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedCardSection
import dev.penned.app.ui.components.PennedCardSectionPosition
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.home.HomeGroups
import dev.penned.app.ui.home.TransactionRow
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTheme
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.incomeColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 分类详细页（AI 轮，对标参考图 3/4）：从统计页的「支出/收入数据」行点进来。
 * hero 三数（共支出/日均/账单数量）→ 有子分类时出「子分类占比 + 数据」（可继续下钻），
 * 叶子节点出该分类在本期内的流水（点行进账单详情）。窗口跟着跳转参数走，钻取过程中不变。
 */
class CategoryDetailViewModel(
    app: PennedApplication,
    private val target: CategoryTarget,
    private val fromMinute: Long,
    private val toMinute: Long,
    private val dayCount: Int,
) : ViewModel() {

    data class UiState(
        val summary: StatsMath.NodeSummary = StatsMath.NodeSummary(0, 0, 0),
        val children: List<StatsMath.CategoryTotal> = emptyList(),
        val days: List<HomeGroups.DayGroup> = emptyList(),
        /** 按日合计（柱状图）：长度 = 期自然日数，没记录的日子补 0。 */
        val bars: List<Long> = emptyList(),
        /** 叶子节点出流水列表；有子分类就先出「子分类占比 + 数据」。 */
        val isLeaf: Boolean = true,
    )

    /** 期首日的 epochDay：柱状图的 x 轴锚点（窗口都从当日 00:00 起算）。 */
    val startEpochDay: Long =
        Instant.ofEpochSecond(fromMinute * 60).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState> = app.settings.currentBookId
        .flatMapLatest { bookId ->
            if (bookId == null) {
                flowOf(emptyList())
            } else {
                app.database.transactionDao().observeRangeTxsWithCategory(bookId, fromMinute, toMinute)
            }
        }
        .map { rows ->
            val node = StatsMath.nodeRows(rows, target.type, target.name, target.parent)
            UiState(
                summary = StatsMath.summary(node, target.type, dayCount),
                children = StatsMath.childTotals(node, target.type),
                days = HomeGroups.groupByDay(node),
                bars = StatsMath.dailyTotals(node, target.type, startEpochDay, dayCount),
                isLeaf = !StatsMath.hasChildren(rows, target.type, target.name, target.parent),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
}

@Composable
fun CategoryDetailScreen(
    type: TxType,
    name: String,
    parent: String?,
    fromMinute: Long,
    toMinute: Long,
    days: Int,
    periodLabel: String,
    onBack: () -> Unit,
    onOpenCategory: (CategoryTarget) -> Unit,
    onOpenTx: (String) -> Unit,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val target = CategoryTarget(type, name, parent)
    val viewModel: CategoryDetailViewModel = viewModel(
        key = "$type|$name|$parent|$fromMinute|$toMinute",
        initializer = { CategoryDetailViewModel(app, target, fromMinute, toMinute, days) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CategoryDetailContent(
        target = target,
        periodLabel = periodLabel,
        summary = state.summary,
        children = state.children,
        days = state.days,
        bars = state.bars,
        isLeaf = state.isLeaf,
        startEpochDay = viewModel.startEpochDay,
        onOpenCategory = onOpenCategory,
        onOpenTx = onOpenTx,
        onBack = onBack,
    )
}

/** 无状态入口：截图测试/预览用。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryDetailContent(
    target: CategoryTarget,
    periodLabel: String,
    summary: StatsMath.NodeSummary,
    children: List<StatsMath.CategoryTotal>,
    days: List<HomeGroups.DayGroup>,
    bars: List<Long> = emptyList(),
    isLeaf: Boolean,
    startEpochDay: Long = 0L,
    onOpenCategory: (CategoryTarget) -> Unit = {},
    onOpenTx: (String) -> Unit = {},
    onBack: () -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val isExpense = target.type == TxType.EXPENSE
    val unit = if (isExpense) "支出" else "收入"
    Scaffold(
        topBar = { PennedTopBar(title = target.name, onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = PennedSpacing.xxl),
        ) {
            item(key = "hero") {
                NodeHero(
                    summary = summary,
                    unit = unit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = PennedSpacing.lg, end = PennedSpacing.lg, top = PennedSpacing.sm),
                )
            }
            item(key = "period") {
                Text(
                    "统计范围 $periodLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = PennedSpacing.lg,
                        end = PennedSpacing.lg,
                        top = PennedSpacing.sm,
                        bottom = PennedSpacing.xs,
                    ),
                )
            }

            // 按日柱状图（参考图的「支出统计」）：有记录才出这张卡
            if (bars.any { it > 0 }) {
                item(key = "chart") {
                    StatsBarChartCard(
                        title = "${unit}统计",
                        bars = bars,
                        xLabels = dayAxisLabels(startEpochDay, bars.size, LocalDate.now(zone)),
                        color = if (isExpense) expenseColor() else incomeColor(),
                        seriesLabel = unit,
                    )
                }
            }

            if (isLeaf && days.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "本期没有该分类的记录",
                        hint = "换个统计期，或去记一笔",
                        icon = PennedIcons.Pie,
                        modifier = Modifier.padding(top = PennedSpacing.xxl),
                    )
                }
            } else if (isLeaf) {
                // 流水列表：日头条 + 行（与首页同一套渲染），点行进账单详情
                days.forEachIndexed { dayIndex, day ->
                    val isLastDay = dayIndex == days.lastIndex
                    stickyHeader(key = "day-${day.epochDay}") {
                        PennedCardSection(
                            // 第一天的日头条接管卡片顶段圆角（圆角必须落在够高的段上，见 PennedCardSection 注释）
                            position = if (dayIndex == 0) {
                                PennedCardSectionPosition.Top
                            } else {
                                PennedCardSectionPosition.Middle
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                        ) {
                            DayHeaderRow(
                                label = HomeGroups.dayLabel(day.epochDay, zone),
                                incomeCents = day.incomeCents,
                                expenseCents = day.expenseCents,
                            )
                        }
                    }
                    itemsIndexed(day.items, key = { _, row -> row.id }) { rowIndex, row ->
                        val isLastOfDay = rowIndex == day.items.lastIndex
                        val isLastOfCard = isLastDay && isLastOfDay
                        PennedCardSection(
                            position = if (isLastOfCard) {
                                PennedCardSectionPosition.Bottom
                            } else {
                                PennedCardSectionPosition.Middle
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PennedSpacing.lg)
                                .then(
                                    if (isLastOfCard) {
                                        Modifier.padding(bottom = PennedSpacing.md)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            TransactionRow(
                                row = row,
                                selectionMode = false,
                                selected = false,
                                onClick = { onOpenTx(row.id) },
                                onLongClick = {},
                            )
                            // 日与日之间靠日头条分开，日的最后一行不画线
                            if (!isLastOfDay) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = PennedDimens.rowTextIndent),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "donut") {
                    StatsDonutCard(
                        title = "${unit}占比",
                        centerLabel = "总$unit",
                        categories = children,
                        totalCents = summary.totalCents,
                        totalColor = if (isExpense) expenseColor() else incomeColor(),
                    )
                }
                item(key = "data") {
                    StatsDataCard(
                        title = "${unit}数据",
                        categories = children,
                        type = target.type,
                        // 直挂在节点上的桶、合并桶「其他」与「未分类」都不是分类，不可再下钻
                        drillable = {
                            it.name != target.name &&
                                it.name != StatsMath.OTHERS &&
                                it.name != StatsMath.UNCATEGORIZED
                        },
                        onOpenCategory = { child -> onOpenCategory(target.copy(name = child, parent = target.name)) },
                    )
                }
            }
        }
    }
}

/** hero：浅青底三列（共支出/收入 · 日均 · 账单数量）。 */
@Composable
private fun NodeHero(summary: StatsMath.NodeSummary, unit: String, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(PennedSpacing.lg),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            HeroCell("共$unit", Money.format(summary.totalCents), ink, Modifier.weight(1f))
            HeroCell("日均$unit", Money.format(summary.avgPerDayCents), ink, Modifier.weight(1f))
            HeroCell("账单数量", "${summary.count}笔", ink, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroCell(label: String, value: String, ink: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.75f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = ink)
    }
}

private val OCT_START_DAY = java.time.LocalDate.of(2026, 10, 1).toEpochDay()

@Preview(showBackground = true, name = "分类详细-一级", widthDp = 400, heightDp = 780)
@Composable
private fun CategoryDetailParentPreview() {
    val rows = listOf(
        tx("1", "2026-10-04T19:30", 32746, "晚餐", "食品餐饮"),
        tx("2", "2026-10-03T12:00", 31260, "午餐", "食品餐饮"),
        tx("3", "2026-10-02T15:00", 14168, "休闲零食", "食品餐饮"),
        tx("4", "2026-10-01T08:10", 11860, "早餐", "食品餐饮"),
    )
    PennedTheme {
        CategoryDetailContent(
            target = CategoryTarget(TxType.EXPENSE, "食品餐饮", parent = null),
            periodLabel = "2026年10月",
            summary = StatsMath.summary(rows, TxType.EXPENSE, dayCount = 31),
            children = StatsMath.childTotals(rows, TxType.EXPENSE),
            days = HomeGroups.groupByDay(rows),
            bars = StatsMath.dailyTotals(rows, TxType.EXPENSE, OCT_START_DAY, 31),
            isLeaf = false,
            startEpochDay = OCT_START_DAY,
        )
    }
}

@Preview(showBackground = true, name = "分类详细-叶子（流水）", widthDp = 400, heightDp = 780)
@Composable
private fun CategoryDetailLeafPreview() {
    val rows = listOf(
        tx("1", "2026-10-04T19:30", 950, "晚餐", "食品餐饮").copy(note = "北苑"),
        tx("2", "2026-10-03T18:40", 1500, "晚餐", "食品餐饮").copy(note = "东27"),
        tx("3", "2026-10-02T19:05", 1300, "晚餐", "食品餐饮").copy(note = "北苑"),
    )
    PennedTheme {
        CategoryDetailContent(
            target = CategoryTarget(TxType.EXPENSE, "晚餐", parent = "食品餐饮"),
            periodLabel = "2026年10月",
            summary = StatsMath.summary(rows, TxType.EXPENSE, dayCount = 31),
            children = StatsMath.childTotals(rows, TxType.EXPENSE),
            days = HomeGroups.groupByDay(rows),
            bars = StatsMath.dailyTotals(rows, TxType.EXPENSE, OCT_START_DAY, 31),
            isLeaf = true,
            startEpochDay = OCT_START_DAY,
        )
    }
}

private fun tx(id: String, isoDateTime: String, cents: Long, name: String, parent: String) = TxWithCategory(
    id = id,
    type = TxType.EXPENSE,
    amountCents = cents,
    accountId = null,
    categoryId = null,
    dateEpochMinute = LocalDateTime.parse(isoDateTime).atZone(ZoneId.systemDefault()).toEpochSecond() / 60,
    note = null,
    categoryName = name,
    categoryParentName = parent,
    categoryIconKey = null,
    targetAccountName = null,
    excludeFromStats = false,
)
