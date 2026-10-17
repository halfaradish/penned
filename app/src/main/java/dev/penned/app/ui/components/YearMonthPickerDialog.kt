package dev.penned.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.penned.app.domain.StatsStrip
import dev.penned.app.ui.theme.PennedSpacing
import java.time.YearMonth

/**
 * 「年 | 月」双列滚轮弹窗（AK 轮）：处于 `ui/components/` 的共用件——统计页的快速定位
 * （点切换条左边的年份）与日历的「点月份标题跳年月」用的是同一个（别各写一份滚轮）。
 *
 * 两列都能滚、居中项放大加墨色，底部「取消 / 确定」——确定前不改期，取消就原样退回。
 * 年月列由调用方给（统计页与日历都走 `StatsStrip.pickerMonths` / `years`，同一套口径：
 * "今年只到本月"）；[monthsForYear] 让调用方按"自己那边真能到的范围"再收窄一层
 * （日历只列"能翻到的月"，免得选完被夹回来）。
 *
 * [years] 与切换条同序（新→旧）；滚轮里翻成"自上而下递增"（照参考图，2023 在上、2027 在下），
 * 月份列同理 1…12 自上而下。
 */
@Composable
fun YearMonthPickerDialog(
    years: List<Int>,
    today: YearMonth,
    initial: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit,
    monthsForYear: (Int) -> List<Int> = { StatsStrip.pickerMonths(it, today) },
) {
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.monthValue) }
    val months = monthsForYear(year)
    // 换年时月份列会变（今年只到本月、日历还要砍掉早于最早流水的月）：把选中的月夹回可用范围
    LaunchedEffect(year, months) {
        if (months.isNotEmpty()) month = month.coerceIn(months.first(), months.last())
    }

    PennedDialog(
        onDismiss = onDismiss,
        title = "选择年月",
        confirmLabel = "确定",
        onConfirm = { onConfirm(YearMonth.of(year, month)) },
    ) {
        YearMonthWheel(
            years = remember(years) { years.reversed() },
            months = months,
            selectedYear = year,
            selectedMonth = month,
            onYearChange = { year = it },
            onMonthChange = { month = it },
            modifier = Modifier.padding(top = PennedSpacing.sm),
        )
    }
}

/**
 * 双列滚轮本体：两列都按传入顺序自上而下排。从弹窗里抽出来是为了能单独截图与测试
 * （Robolectric 里弹窗窗口不便截图）。选中项 = 视口正中那一格，滚动中回调 `onYearChange` / `onMonthChange`。
 */
@Composable
fun YearMonthWheel(
    years: List<Int>,
    months: List<Int>,
    selectedYear: Int,
    selectedMonth: Int,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        WheelColumn(
            items = years.map { "$it" },
            selectedIndex = years.indexOf(selectedYear),
            onSelectIndex = { index -> years.getOrNull(index)?.let(onYearChange) },
            modifier = Modifier.weight(1f),
        )
        UnitLabel("年")
        WheelColumn(
            items = months.map { "$it" },
            selectedIndex = months.indexOf(selectedMonth),
            onSelectIndex = { index -> months.getOrNull(index)?.let(onMonthChange) },
            modifier = Modifier.weight(1f),
        )
        UnitLabel("月")
    }
}
