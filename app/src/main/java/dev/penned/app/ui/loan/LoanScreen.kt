package dev.penned.app.ui.loan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.penned.app.domain.AddDate
import dev.penned.app.domain.DebtText
import dev.penned.app.domain.LoanMath
import dev.penned.app.domain.Money
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedSegmentedTabs
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.SectionCard
import dev.penned.app.ui.components.StatCell
import dev.penned.app.ui.components.PennedStatCells
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.primaryInkColor
import java.time.LocalDate

/** 计划表最多列几期（360 期全列出来没人看，剩下交给周期记账去循环）。 */
private const val PLAN_ROWS = 12

/** 表单左标签列宽：`PennedDimens.formLabel`（72dp）放不下「首期还款日」五个字，按六字宽留。 */
private val LOAN_FORM_LABEL = 96.dp

/**
 * 房贷计算器（M3-11，我的 → 分期房贷）。
 *
 * 输入本金 / 年利率 / 期数 / 首期还款日 / 方式 → 结果卡（首末月供、总利息、总还款）+ 逐期计划；
 * 「按首期记一笔」只预填**首期**那一笔（金额 + 日期 + 备注），**不自动生成整个计划**——
 * 要每期循环请用周期记账（规格 M3-11 的明确边界）。
 */
@Composable
fun LoanScreen(
    onBack: () -> Unit,
    /** 「按首期记一笔」：带金额（分）/ 日期（epochDay）/ 备注跳记一笔预填，分类由用户选。 */
    onRecordFirst: (Long, Long, String) -> Unit,
) {
    var principalText by rememberSaveable { mutableStateOf(DEFAULT_PRINCIPAL) }
    var rateText by rememberSaveable { mutableStateOf(DEFAULT_RATE) }
    var periodsText by rememberSaveable { mutableStateOf(DEFAULT_PERIODS) }
    var firstDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var method by rememberSaveable { mutableStateOf(LoanMath.Method.EQUAL_INSTALLMENT) }

    LoanContent(
        principalText = principalText,
        rateText = rateText,
        periodsText = periodsText,
        firstEpochDay = firstDay,
        method = method,
        onPrincipalChange = { principalText = it },
        onRateChange = { rateText = it },
        onPeriodsChange = { periodsText = it },
        onFirstDayChange = { firstDay = it },
        onMethodChange = { method = it },
        onBack = onBack,
        onRecordFirst = onRecordFirst,
    )
}

/** 默认填一组常见值（100 万 / 4.9% / 30 年），打开就有结果可看；用户直接改自己的数。 */
private const val DEFAULT_PRINCIPAL = "1000000"
private const val DEFAULT_RATE = "4.9"
private const val DEFAULT_PERIODS = "360"

/** 无状态入口：截图测试/预览用。 */
@Composable
fun LoanContent(
    principalText: String,
    rateText: String,
    periodsText: String,
    firstEpochDay: Long,
    method: LoanMath.Method,
    onPrincipalChange: (String) -> Unit,
    onRateChange: (String) -> Unit,
    onPeriodsChange: (String) -> Unit,
    onFirstDayChange: (Long) -> Unit,
    onMethodChange: (LoanMath.Method) -> Unit,
    onBack: () -> Unit,
    onRecordFirst: (Long, Long, String) -> Unit = { _, _, _ -> },
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val principalCents = Money.parseToCents(principalText)
    val rate = rateText.toDoubleOrNull()
    val periods = periodsText.toIntOrNull()
    val plan = if (principalCents != null && principalCents > 0L && rate != null && rate >= 0.0 && periods != null && periods > 0) {
        LoanMath.plan(principalCents, rate, periods, method)
    } else {
        null
    }
    // 还款日只给计划表与「按首期记一笔」用；口径在 LoanMath.periodDates（月对齐与周期记账同一套）
    val dates = plan?.let { LoanMath.periodDates(firstEpochDay, it.periods.size) }.orEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "分期房贷", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PennedSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PennedSpacing.md),
        ) {
            Spacer(Modifier.height(PennedSpacing.xs))

            // 表单卡：方式 + 三个数字 + 首期还款日
            PennedCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Column(Modifier.padding(PennedSpacing.lg)) {
                    PennedSegmentedTabs(
                        options = listOf(LoanMath.Method.EQUAL_INSTALLMENT, LoanMath.Method.EQUAL_PRINCIPAL),
                        selected = method,
                        onSelect = onMethodChange,
                        label = LoanMath::methodLabel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PennedDivider()
                LoanFieldRow("贷款金额", principalText, "1000000", onPrincipalChange, suffix = "元")
                PennedDivider()
                LoanFieldRow("年利率", rateText, "4.9", onRateChange, suffix = "%")
                PennedDivider()
                LoanFieldRow("期数", periodsText, "360", onPeriodsChange, suffix = "期")
                PennedDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = PennedDimens.rowMinHeight)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = PennedSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "首期还款日",
                        style = PennedTextStyles.rowTitle,
                        modifier = Modifier.width(LOAN_FORM_LABEL),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(DebtText.fullDate(firstEpochDay), style = PennedTextStyles.rowTitle)
                }
            }

            // 结果卡：首期月供大数 + 末月/总利息/总还款三格
            PennedCard(modifier = Modifier.fillMaxWidth()) {
                if (plan == null || plan.periods.isEmpty()) {
                    Text(
                        "填好金额、利率与期数就能算出月供",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "首期月供",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Money.format(plan.firstPaymentCents),
                        style = MaterialTheme.typography.displaySmall,
                        color = primaryInkColor(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        LoanMath.methodLabel(method),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(PennedSpacing.md))
                    PennedStatCells(
                        cells = listOf(
                            StatCell("末月月供", Money.format(plan.lastPaymentCents)),
                            StatCell("总利息", Money.format(plan.totalInterestCents)),
                            StatCell("总还款", Money.format(plan.totalPaymentCents)),
                        ),
                    )
                }
            }

            if (plan != null && plan.periods.isNotEmpty()) {
                SectionCard(title = "还款计划") {
                    plan.periods.take(PLAN_ROWS).forEachIndexed { index, period ->
                        if (index > 0) PennedDivider()
                        PlanRow(
                            period = period,
                            dateLabel = dates.getOrNull(index)?.let { DebtText.fullDate(it) }.orEmpty(),
                        )
                    }
                    if (plan.periods.size > PLAN_ROWS) {
                        PennedDivider()
                        Text(
                            "共 ${plan.periods.size} 期，这里列前 $PLAN_ROWS 期；" +
                                "想每期自动记一笔，用「周期记账」按月建规则",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                        )
                    }
                }
            }

            Spacer(Modifier.height(PennedSpacing.xs))
            PennedButton(
                text = "按首期记一笔",
                enabled = plan != null && plan.periods.isNotEmpty(),
                onClick = {
                    val first = plan?.periods?.firstOrNull() ?: return@PennedButton
                    onRecordFirst(first.paymentCents, firstEpochDay, "房贷首期 · ${LoanMath.methodLabel(method)}")
                },
            )
            Text(
                "只预填首期这一笔（金额与日期），分类在记一笔里选；不会自动生成整个计划",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(PennedSpacing.xl))
        }
    }

    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = AddDate.dayToPickerMillis(firstEpochDay),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onFirstDayChange(AddDate.pickerMillisToDay(it)) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            androidx.compose.material3.DatePicker(
                state = pickerState,
                title = { Text("首期还款日", modifier = Modifier.padding(PennedSpacing.lg)) },
            )
        }
    }
}

/** 表单行：左标签 + 右数值输入（+ 单位后缀）。 */
@Composable
private fun LoanFieldRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    suffix: String,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = PennedDimens.rowMinHeight).padding(horizontal = PennedSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = PennedTextStyles.rowTitle, modifier = Modifier.width(LOAN_FORM_LABEL))
        BasicTextField(
            value = value,
            onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == '.' }) },
            singleLine = true,
            textStyle = PennedTextStyles.rowTitle.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, style = PennedTextStyles.rowTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
        Text(suffix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 计划表一行：期号 + 还款日 + 月供（右端）；下一行是本金/利息/剩余。 */
@Composable
private fun PlanRow(period: LoanMath.Period, dateLabel: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("第 ${period.index} 期", style = MaterialTheme.typography.bodyLarge)
            if (dateLabel.isNotEmpty()) {
                Spacer(Modifier.width(PennedSpacing.sm))
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                Money.format(period.paymentCents),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            "本金 ${Money.format(period.principalCents)} · 利息 ${Money.format(period.interestCents)}" +
                " · 剩余 ${Money.format(period.remainingCents)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, name = "分期房贷", widthDp = 400, heightDp = 800)
@Composable
private fun LoanPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        LoanContent(
            principalText = DEFAULT_PRINCIPAL,
            rateText = DEFAULT_RATE,
            periodsText = DEFAULT_PERIODS,
            firstEpochDay = LocalDate.of(2026, 11, 15).toEpochDay(),
            method = LoanMath.Method.EQUAL_INSTALLMENT,
            onPrincipalChange = {},
            onRateChange = {},
            onPeriodsChange = {},
            onFirstDayChange = {},
            onMethodChange = {},
            onBack = {},
        )
    }
}
