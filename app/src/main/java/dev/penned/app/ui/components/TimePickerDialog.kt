package dev.penned.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.penned.app.ui.theme.PennedSpacing
import java.time.LocalTime

private val HourLabels: List<String> = List(24) { "%02d".format(it) }

private val MinuteLabels: List<String> = List(60) { "%02d".format(it) }

/** 测试用：两列滚轮的 tag（`performScrollToNode` 得落在滚动列上）。 */
internal const val TIME_WHEEL_HOUR_TAG = "time-wheel-hour"

internal const val TIME_WHEEL_MINUTE_TAG = "time-wheel-minute"

/**
 * 「时 | 分」双列滚轮弹窗（优化轮 3）：与「选年月」同一枚共用滚轮（[WheelColumn]），
 * 口径也一致——确定前不改值、取消原样退回。[initial] 是打开时的选中时刻（未选过时分就是当前时刻）。
 */
@Composable
fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    PennedDialog(
        onDismiss = onDismiss,
        title = "选择时间",
        confirmLabel = "确定",
        onConfirm = { onConfirm(LocalTime.of(hour, minute)) },
    ) {
        TimeWheel(
            selectedHour = hour,
            selectedMinute = minute,
            onHourChange = { hour = it },
            onMinuteChange = { minute = it },
            modifier = Modifier.padding(top = PennedSpacing.sm),
        )
    }
}

/**
 * 双列滚轮本体：`00…23 | 00…59` 两列自上而下。从弹窗里抽出来是为了能单独截图与测试
 * （Robolectric 里弹窗窗口不便截图，同 [YearMonthWheel]）。
 */
@Composable
fun TimeWheel(
    selectedHour: Int,
    selectedMinute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
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
            scrollTag = TIME_WHEEL_HOUR_TAG,
        )
        UnitLabel("时")
        WheelColumn(
            items = MinuteLabels,
            selectedIndex = selectedMinute,
            onSelectIndex = { index -> onMinuteChange(index.coerceIn(0, 59)) },
            modifier = Modifier.weight(1f),
            scrollTag = TIME_WHEEL_MINUTE_TAG,
        )
        UnitLabel("分")
    }
}
