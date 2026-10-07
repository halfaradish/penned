package dev.penned.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.domain.AddDate
import dev.penned.app.domain.Money
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.StatsPeriod
import dev.penned.app.domain.StatsPeriods
import dev.penned.app.domain.StatsView
import dev.penned.app.domain.StatsWindow
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.components.PennedSegmentedTabs
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTheme
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.incomeColor
import java.time.LocalDate
import java.time.YearMonth

/**
 * 统计页（specs/M1.md 第二批起；AI 轮加时间维度与切换式）：
 * 时间维度分段 + 期选择 + 汇总卡 + 「占比 + 数据」两张卡（卡头支出/收入切换）。
 * 图表配色与首页头像共用一套分类颜料（D024）。
 */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    modifier: Modifier = Modifier,
    onOpenCategory: (CategoryTarget, StatsWindow) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsContent(
        period = state.period,
        window = state.window,
        totals = state.totals,
        type = state.type,
        expenseCategories = state.expenseCategories,
        incomeCategories = state.incomeCategories,
        emptyTitle = state.emptyTitle,
        monthlyBars = state.monthlyBars,
        stripMonths = state.stripMonths,
        stripYears = state.stripYears,
        onSelectView = viewModel::selectView,
        onSelectType = viewModel::selectType,
        onSelectMonth = viewModel::selectMonth,
        onSelectYear = viewModel::selectYear,
        onPickRange = viewModel::setCustomRange,
        onOpenCategory = { target -> onOpenCategory(target, state.window) },
        modifier = modifier,
    )
}

/** 占比/数据卡头的切换项：只在这两个域里切（转账不属于收支统计）。 */
private val STATS_TYPES = listOf(TxType.EXPENSE, TxType.INCOME)

@Composable
fun StatsContent(
    period: StatsPeriod,
    window: StatsWindow,
    totals: StatsMath.PeriodTotals,
    expenseCategories: List<StatsMath.CategoryTotal>,
    incomeCategories: List<StatsMath.CategoryTotal>,
    emptyTitle: String = "本期没有收支记录",
    monthlyBars: List<Long> = emptyList(),
    stripMonths: List<YearMonth> = emptyList(),
    stripYears: List<Int> = emptyList(),
    type: TxType = TxType.EXPENSE,
    onSelectView: (StatsView) -> Unit = {},
    onSelectType: (TxType) -> Unit = {},
    onSelectMonth: (YearMonth) -> Unit = {},
    onSelectYear: (Int) -> Unit = {},
    onPickRange: (Long, Long) -> Unit = { _, _ -> },
    onOpenCategory: (CategoryTarget) -> Unit = {},
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    // 自定义区间的日期选择：记住点的是「开始」还是「结束」；快速定位弹窗单独一个状态
    var picking by remember { mutableStateOf<RangeEnd?>(null) }
    var locating by remember { mutableStateOf(false) }
    val todayMonth = YearMonth.from(today)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = PennedSpacing.xxl),
    ) {
        // 时间维度分段直接当首行（2026-10-07：去掉冗余的「统计」页头大标题——底栏 Tab 已写着"统计"）
        PennedSegmentedTabs(
            options = StatsView.entries.toList(),
            selected = period.view,
            onSelect = onSelectView,
            label = { it.label },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PennedSpacing.md, start = PennedSpacing.lg, end = PennedSpacing.lg),
        )
        PeriodSelector(
            period = period,
            window = window,
            today = today,
            stripMonths = stripMonths,
            stripYears = stripYears,
            onSelectMonth = onSelectMonth,
            onSelectYear = onSelectYear,
            onPickYearMonth = { locating = true },
            onPickStart = { picking = RangeEnd.START },
            onPickEnd = { picking = RangeEnd.END },
        )

        // 汇总卡
        PennedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryCell("支出", Money.format(totals.expenseCents), expenseColor(), Modifier.weight(1f))
                SummaryCell("收入", Money.format(totals.incomeCents), incomeColor(), Modifier.weight(1f))
                SummaryCell(
                    "结余",
                    Money.format(totals.incomeCents - totals.expenseCents),
                    MaterialTheme.colorScheme.onSurface,
                    Modifier.weight(1f),
                )
            }
        }

        if (expenseCategories.isEmpty() && incomeCategories.isEmpty()) {
            EmptyState(
                title = emptyTitle,
                hint = "记几笔之后回来看统计",
                icon = PennedIcons.Pie,
                modifier = Modifier.padding(top = PennedSpacing.xxl),
            )
            return@Column
        }

        // 占比 + 数据：一张卡头的「支出|收入」切换驱动两张卡（参考图口径）
        val isExpense = type == TxType.EXPENSE
        val unit = if (isExpense) "支出" else "收入"
        val unitColor = if (isExpense) expenseColor() else incomeColor()
        val shown = if (isExpense) expenseCategories else incomeCategories
        val total = if (isExpense) totals.expenseCents else totals.incomeCents
        val toggle: @Composable () -> Unit = {
            PennedSegmentedTabs(
                options = STATS_TYPES,
                selected = type,
                onSelect = onSelectType,
                label = { if (it == TxType.EXPENSE) "支出" else "收入" },
                modifier = Modifier.width(132.dp),
            )
        }
        if (shown.isEmpty()) {
            // 该侧本期没数据：卡内给句话，切换器留在原处（一点即可看另一侧）
            PennedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${unit}占比",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    toggle()
                }
                Text(
                    "本期没有${unit}记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            StatsDonutCard(
                title = "${unit}占比",
                centerLabel = "总$unit",
                categories = shown,
                totalCents = total,
                totalColor = unitColor,
                trailing = toggle,
            )
            StatsDataCard(
                title = "${unit}数据",
                categories = shown,
                type = type,
                // 「其他」合并桶与「未分类」不是分类，不可点；其余行点进分类详细页
                drillable = {
                    it.name != StatsMath.OTHERS && it.name != StatsMath.UNCATEGORIZED
                },
                onOpenCategory = { name -> onOpenCategory(CategoryTarget(type, name, parent = null)) },
            )
        }

        // 年统计多一张「月度趋势」：同一套自绘柱状图，跟着上面的支出/收入切换走
        if (period is StatsPeriod.Year) {
            StatsBarChartCard(
                title = "月度趋势",
                bars = monthlyBars,
                xLabels = monthAxisLabels(),
                color = unitColor,
            )
        }
    }

    val target = picking
    if (target != null) {
        val initialDay = when (target) {
            RangeEnd.START -> period.startDayOrNull() ?: window.startEpochDay
            RangeEnd.END -> period.endDayOrNull() ?: window.endEpochDay
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = AddDate.dayToPickerMillis(initialDay),
        )
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val day = AddDate.pickerMillisToDay(millis)
                        when (target) {
                            RangeEnd.START -> onPickRange(day, window.endEpochDay)
                            RangeEnd.END -> onPickRange(window.startEpochDay, day)
                        }
                    }
                    picking = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }) { Text("取消") }
            },
        ) {
            DatePicker(
                state = pickerState,
                title = {
                    Text(
                        if (target == RangeEnd.START) "选择开始日期" else "选择结束日期",
                        modifier = Modifier.padding(PennedSpacing.lg),
                    )
                },
            )
        }
    }

    // 快速定位：点切换条左边的年份（月统计视图）
    if (locating) {
        YearMonthPickerDialog(
            years = stripYears,
            today = todayMonth,
            initial = (period as? StatsPeriod.Month)?.yearMonth ?: todayMonth,
            onDismiss = { locating = false },
            onConfirm = { month ->
                onSelectMonth(month)
                locating = false
            },
        )
    }
}

private enum class RangeEnd { START, END }

/**
 * 期选择行：月/年出**时间切换条**（可横滑，AK 轮替代箭头翻页）、近七日只报区间文案、
 * 自定义出两个日期 chip。
 */
@Composable
private fun PeriodSelector(
    period: StatsPeriod,
    window: StatsWindow,
    today: LocalDate,
    stripMonths: List<YearMonth>,
    stripYears: List<Int>,
    onSelectMonth: (YearMonth) -> Unit,
    onSelectYear: (Int) -> Unit,
    onPickYearMonth: () -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
) {
    when (period) {
        is StatsPeriod.Month -> MonthStrip(
            months = stripMonths,
            selected = period.yearMonth,
            today = YearMonth.from(today),
            onSelect = onSelectMonth,
            onPickYearMonth = onPickYearMonth,
        )
        is StatsPeriod.Year -> YearStrip(
            years = stripYears,
            selected = period.year,
            onSelect = onSelectYear,
        )
        is StatsPeriod.Custom -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PennedDimens.iconButton + PennedSpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm)) {
                PennedChip(
                    label = StatsPeriods.dayLabel(period.startEpochDay, today),
                    leading = "开始",
                    onClick = onPickStart,
                )
                PennedChip(
                    label = StatsPeriods.dayLabel(period.endEpochDay, today),
                    leading = "结束",
                    onClick = onPickEnd,
                )
            }
        }
        is StatsPeriod.Last7Days -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PennedDimens.iconButton + PennedSpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(window.label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** 自定义区间在期状态里的起止日（近七日/月/年没有"用户选的日期"，返回 null 走窗口端点）。 */
private fun StatsPeriod.startDayOrNull(): Long? = (this as? StatsPeriod.Custom)?.startEpochDay

private fun StatsPeriod.endDayOrNull(): Long? = (this as? StatsPeriod.Custom)?.endEpochDay

@Composable
private fun SummaryCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}

@Preview(showBackground = true, name = "统计-月统计", widthDp = 400, heightDp = 780)
@Composable
private fun StatsMonthPreview() {
    val period = StatsPeriod.Month(YearMonth.of(2026, 10))
    PennedTheme {
        StatsContent(
            period = period,
            window = StatsPeriods.window(period),
            totals = StatsMath.PeriodTotals(incomeCents = 350000, expenseCents = 32300),
            expenseCategories = listOf(
                StatsMath.CategoryTotal("食品餐饮", 15600, 110, 0.483f),
                StatsMath.CategoryTotal("购物消费", 9500, 15, 0.294f),
                StatsMath.CategoryTotal("出行交通", 4200, 6, 0.13f),
                StatsMath.CategoryTotal("其他", 3000, 4, 0.093f),
            ),
            incomeCategories = listOf(StatsMath.CategoryTotal("工资", 350000, 1, 1f)),
        )
    }
}
