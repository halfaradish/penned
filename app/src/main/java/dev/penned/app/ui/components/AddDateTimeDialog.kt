package dev.penned.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.penned.app.domain.AddDate
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.iconMutedColor
import java.time.LocalDate
import java.time.LocalTime

/**
 * 「选择记账日期」弹窗（D097）：**日历 + 时分滚轮同一个弹窗里调**，确定即"看到的就是记下的"。
 *
 * 记一笔（`AddTransactionScreen`）与一句话记账（`QuickEntryScreen`）共用——同一件事只有一套交互
 * （用户反馈里明确否掉过"再单独加一枚时间 chip"）。
 *
 * - [selectedDay] 为 null = 没选过（今天）；[selectedTime] 为 null = 没调过时分（打开时取此刻，确定即落成显式时分）
 * - 不用 M3 `DatePickerDialog`：它会把额外内容插在自己的标题与日历**中间**，且平台默认宽度
 *   280~320dp 装不下 336dp 的日历网格（真机会把周六那列裁掉）
 */
@Composable
fun AddDateTimeDialog(
    selectedDay: Long?,
    selectedTime: LocalTime?,
    onConfirm: (day: Long, time: LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = AddDate.dayToPickerMillis(selectedDay ?: LocalDate.now().toEpochDay()),
    )
    val initialTime = remember { selectedTime ?: LocalTime.now() }
    var hour by remember { mutableIntStateOf(initialTime.hour) }
    var minute by remember { mutableIntStateOf(initialTime.minute) }

    PennedDialog(
        onDismiss = onDismiss,
        title = "选择记账日期",
        confirmLabel = "确定",
        // M3 日历网格最窄 336dp（7 × 48dp）——满宽 + 限 400dp（与测试视口同宽，D016）
        modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(), // token-ok：M3 网格 336 + 内边距 48 = 384，400 是一次性上限
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onConfirm = {
            pickerState.selectedDateMillis?.let { millis ->
                onConfirm(AddDate.pickerMillisToDay(millis), LocalTime.of(hour, minute))
            }
            onDismiss()
        },
    ) {
        // 日历只要网格：标题由弹窗给、大号日期与"切键盘输入"开关都不要；底色去掉
        DatePicker(
            state = pickerState,
            title = null,
            headline = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(Modifier.fillMaxWidth()) {
            PennedDivider(startIndent = PennedSpacing.lg)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = PennedSpacing.lg, top = PennedSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    PennedIcons.Clock,
                    contentDescription = null,
                    tint = iconMutedColor(),
                    modifier = Modifier.size(PennedDimens.iconChevron),
                )
                Spacer(Modifier.width(PennedSpacing.xs))
                Text(
                    "时间",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TimeWheel(
                selectedHour = hour,
                selectedMinute = minute,
                onHourChange = { hour = it },
                onMinuteChange = { minute = it },
                rows = 3,
                modifier = Modifier.padding(horizontal = PennedSpacing.sm),
            )
        }
    }
}
