package dev.penned.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

private val HourLabels: List<String> = List(24) { "%02d".format(it) }

private val MinuteLabels: List<String> = List(60) { "%02d".format(it) }

/** 测试用：两列滚轮的 tag（`performScrollToNode` 得落在滚动列上）。 */
internal const val TIME_WHEEL_HOUR_TAG = "time-wheel-hour"

internal const val TIME_WHEEL_MINUTE_TAG = "time-wheel-minute"

/**
 * 「时 | 分」双列滚轮：与「年 | 月」同一枚共用滚轮（[WheelColumn]）。
 *
 * 记账时刻与日期共用**同一个弹窗**（用户反馈：不要再单独加一枚「时间」chip，点「日期」一起调），
 * 所以这里通常是嵌在日期弹窗里、给 [rows] = 3 的紧凑形态；从弹窗里抽出来是为了能单独截图与测试
 * （Robolectric 里弹窗窗口不便截图，同 [YearMonthWheel]）。
 */
@Composable
fun TimeWheel(
    selectedHour: Int,
    selectedMinute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    rows: Int = 5,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        WheelColumn(
            items = HourLabels,
            selectedIndex = selectedHour,
            onSelectIndex = { index -> onHourChange(index.coerceIn(0, 23)) },
            modifier = Modifier.weight(1f),
            rows = rows,
            scrollTag = TIME_WHEEL_HOUR_TAG,
        )
        UnitLabel("时")
        WheelColumn(
            items = MinuteLabels,
            selectedIndex = selectedMinute,
            onSelectIndex = { index -> onMinuteChange(index.coerceIn(0, 59)) },
            modifier = Modifier.weight(1f),
            rows = rows,
            scrollTag = TIME_WHEEL_MINUTE_TAG,
        )
        UnitLabel("分")
    }
}
