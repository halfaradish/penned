package dev.penned.app.ui.debts

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.domain.DebtDirection
import dev.penned.app.domain.DebtMath
import dev.penned.app.domain.DebtText
import dev.penned.app.domain.Money
import dev.penned.app.ui.components.ConfirmDialog
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedSegmentedTabs
import dev.penned.app.ui.components.PennedSwitchRow
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.iconMutedColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** 新建/编辑债务页状态（M3-4）。 */
data class DebtEditUiState(
    val debtId: String? = null,
    val direction: DebtDirection = DebtDirection.BORROW,
    val counterparty: String = "",
    val principalText: String = "",
    val startEpochDay: Long = LocalDate.now().toEpochDay(),
    val dueEpochDay: Long? = null,
    val note: String = "",
    /** 只对编辑有意义：已结清（结清后不再计入应付/应收）。 */
    val closed: Boolean = false,
) {
    val isEdit: Boolean get() = debtId != null

    val principalCents: Long get() = Money.parseToCents(principalText) ?: 0L

    val canSave: Boolean get() = counterparty.isNotBlank() && principalCents > 0L
}

class DebtEditViewModel(
    private val app: PennedApplication,
    private val editId: String?,
) : ViewModel() {

    private val db = app.database

    private val _state = MutableStateFlow(DebtEditUiState(debtId = editId))
    val state: StateFlow<DebtEditUiState> = _state

    init {
        if (editId != null) {
            viewModelScope.launch {
                val debt = db.debtDao().get(editId) ?: return@launch
                _state.value = DebtEditUiState(
                    debtId = debt.id,
                    direction = debt.direction,
                    counterparty = debt.counterparty,
                    principalText = Money.format(debt.principalCents, grouped = false),
                    startEpochDay = debt.startEpochDay,
                    dueEpochDay = debt.dueEpochDay,
                    note = debt.note.orEmpty(),
                    closed = debt.closed,
                )
            }
        }
    }

    fun pickDirection(direction: DebtDirection) = update { it.copy(direction = direction) }
    fun setCounterparty(text: String) = update { it.copy(counterparty = text) }
    fun setPrincipal(text: String) = update { it.copy(principalText = text.filter { ch -> ch.isDigit() || ch == '.' }) }
    fun setStartDay(day: Long) = update { it.copy(startEpochDay = day) }
    fun setDueDay(day: Long?) = update { it.copy(dueEpochDay = day) }
    fun setNote(text: String) = update { it.copy(note = text) }
    fun setClosed(closed: Boolean) = update { it.copy(closed = closed) }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            if (!s.canSave) return@launch
            val now = System.currentTimeMillis()
            val counterparty = s.counterparty.trim()
            val note = s.note.trim().ifEmpty { null }
            val existing = s.debtId?.let { db.debtDao().get(it) }
            val debt = existing?.copy(
                direction = s.direction,
                counterparty = counterparty,
                principalCents = s.principalCents,
                startEpochDay = s.startEpochDay,
                dueEpochDay = s.dueEpochDay,
                note = note,
                closed = s.closed,
                updatedAt = now,
            ) ?: run {
                val bookId = app.settings.currentBookId.firstOrNull() ?: return@launch
                dev.penned.app.data.db.entity.DebtEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    direction = s.direction,
                    counterparty = counterparty,
                    principalCents = s.principalCents,
                    startEpochDay = s.startEpochDay,
                    dueEpochDay = s.dueEpochDay,
                    note = note,
                    closed = false,
                    createdAt = now,
                    updatedAt = now,
                    deviceId = app.deviceId,
                )
            }
            db.debtDao().upsert(debt)
            onDone()
        }
    }

    /** 删除：软删债务，名下的资金流水保留（钱确实动过；列表页确认弹窗里已说明）。 */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val id = _state.value.debtId ?: return@launch
            db.debtDao().softDelete(id, System.currentTimeMillis())
            onDone()
        }
    }

    private fun update(block: (DebtEditUiState) -> DebtEditUiState) {
        _state.value = block(_state.value)
    }
}

@Composable
fun DebtEditScreen(
    editId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: DebtEditViewModel = viewModel(
        key = "debt-edit-${editId.orEmpty()}",
        initializer = { DebtEditViewModel(app, editId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    DebtEditContent(
        state = state,
        onBack = onBack,
        onPickDirection = viewModel::pickDirection,
        onCounterpartyChange = viewModel::setCounterparty,
        onPrincipalChange = viewModel::setPrincipal,
        onPickStartDay = viewModel::setStartDay,
        onPickDueDay = viewModel::setDueDay,
        onNoteChange = viewModel::setNote,
        onToggleClosed = viewModel::setClosed,
        onSave = { viewModel.save(onDone = onBack) },
        onDelete = if (state.isEdit) ({ confirmDelete = true }) else null,
        modifier = modifier,
    )

    if (confirmDelete) {
        ConfirmDialog(
            onDismiss = { confirmDelete = false },
            title = "删除债务？",
            text = "「${state.counterparty}」将从债务列表移除；名下已记的借入/还款流水**保留**在账单里（钱确实动过）。",
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onDone = onBack)
            },
        )
    }
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@Composable
fun DebtEditContent(
    state: DebtEditUiState,
    onBack: () -> Unit,
    onPickDirection: (DebtDirection) -> Unit,
    onCounterpartyChange: (String) -> Unit,
    onPrincipalChange: (String) -> Unit,
    onPickStartDay: (Long) -> Unit,
    onPickDueDay: (Long?) -> Unit,
    onNoteChange: (String) -> Unit,
    onToggleClosed: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    var datePickerFor by remember { mutableStateOf<DebtDateField?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PennedTopBar(title = if (state.isEdit) "编辑债务" else "新建债务", onBack = onBack)
        },
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // 卡片一：方向 / 对方 / 本金
                PennedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column(Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md)) {
                        PennedSegmentedTabs(
                            options = DebtDirection.entries,
                            selected = state.direction,
                            onSelect = onPickDirection,
                            label = { DebtMath.directionLabel(it) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    PennedDivider()
                    DebtFieldRow(
                        label = "对方",
                        value = state.counterparty,
                        placeholder = if (state.direction == DebtDirection.BORROW) "向谁借的" else "借给了谁",
                        onValueChange = onCounterpartyChange,
                    )
                    PennedDivider()
                    DebtFieldRow(
                        label = "本金",
                        value = state.principalText,
                        placeholder = "0.00",
                        money = true,
                        onValueChange = onPrincipalChange,
                    )
                }

                // 卡片二：日期与备注
                PennedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    DebtPickerRow(
                        label = "起始日",
                        value = DebtText.fullDate(state.startEpochDay),
                        onClick = { datePickerFor = DebtDateField.START },
                    )
                    PennedDivider()
                    DebtPickerRow(
                        label = "到期日",
                        value = state.dueEpochDay?.let { DebtText.fullDate(it) } ?: "未设",
                        onClick = { datePickerFor = DebtDateField.DUE },
                    )
                    PennedDivider()
                    DebtFieldRow(
                        label = "备注",
                        value = state.note,
                        placeholder = "选填",
                        onValueChange = onNoteChange,
                    )
                }

                // 卡片三（仅编辑）：结清开关——结清后不再计入应付/应收
                if (state.isEdit) {
                    PennedCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        PennedSwitchRow("标为结清", state.closed, onToggleClosed)
                        Text(
                            "结清后不再计入应付/应收；账面上的剩余数照旧显示（那是事实）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = PennedSpacing.lg,
                                end = PennedSpacing.lg,
                                bottom = PennedSpacing.md,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(PennedSpacing.xxl))
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md)) {
                PennedButton(text = "保存", onClick = onSave, enabled = state.canSave)
                if (onDelete != null) {
                    Spacer(Modifier.height(PennedSpacing.xs))
                    PennedButton(
                        text = "删除债务",
                        onClick = onDelete,
                        variant = PennedButtonVariant.Danger,
                    )
                }
            }
        }
    }

    val picking = datePickerFor
    if (picking != null) {
        val initial = when (picking) {
            DebtDateField.START -> state.startEpochDay
            DebtDateField.DUE -> state.dueEpochDay ?: LocalDate.now().toEpochDay()
        }
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = dev.penned.app.domain.AddDate.dayToPickerMillis(initial),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { datePickerFor = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val day = dev.penned.app.domain.AddDate.pickerMillisToDay(millis)
                        if (picking == DebtDateField.START) onPickStartDay(day) else onPickDueDay(day)
                    }
                    datePickerFor = null
                }) { Text("确定") }
            },
            dismissButton = {
                // 到期日可以清除；起始日必须有值，所以给的是「取消」
                if (picking == DebtDateField.DUE && state.dueEpochDay != null) {
                    TextButton(onClick = {
                        onPickDueDay(null)
                        datePickerFor = null
                    }) { Text("清除") }
                } else {
                    TextButton(onClick = { datePickerFor = null }) { Text("取消") }
                }
            },
        ) {
            androidx.compose.material3.DatePicker(
                state = pickerState,
                title = { Text(if (picking == DebtDateField.START) "选择起始日" else "选择到期日", modifier = Modifier.padding(PennedSpacing.lg)) },
            )
        }
    }
}

private enum class DebtDateField { START, DUE }

/** 表单行：左标签 + 输入（与账户编辑页同一套排版）。 */
@Composable
private fun DebtFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    money: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .padding(horizontal = PennedSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = PennedTextStyles.rowTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(PennedDimens.formLabel),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = PennedTextStyles.rowTitle.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = if (money) KeyboardType.Decimal else KeyboardType.Text),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = PennedTextStyles.rowTitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/** 日期行：左标签 + 右侧日期 + 箭头。 */
@Composable
private fun DebtPickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PennedDimens.rowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = PennedSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = PennedTextStyles.rowTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(PennedDimens.formLabel),
        )
        Spacer(Modifier.weight(1f))
        Text(value, style = PennedTextStyles.rowTitle)
        Spacer(Modifier.width(PennedSpacing.sm))
        Icon(
            PennedIcons.Calendar,
            contentDescription = null,
            tint = iconMutedColor(),
            modifier = Modifier.size(PennedDimens.iconChevron),
        )
    }
}

@Preview(showBackground = true, name = "新建债务", widthDp = 400, heightDp = 800)
@Composable
private fun DebtEditPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        DebtEditContent(
            state = DebtEditUiState(counterparty = "张三", principalText = "1000.00"),
            onBack = {},
            onPickDirection = {}, onCounterpartyChange = {}, onPrincipalChange = {},
            onPickStartDay = {}, onPickDueDay = {}, onNoteChange = {}, onToggleClosed = {}, onSave = {},
        )
    }
}

@Preview(showBackground = true, name = "编辑债务", widthDp = 400, heightDp = 800)
@Composable
private fun DebtEditExistingPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        DebtEditContent(
            state = DebtEditUiState(
                debtId = "d1",
                direction = DebtDirection.LEND,
                counterparty = "李四",
                principalText = "300.00",
                note = "垫的团建费",
                dueEpochDay = LocalDate.now().plusMonths(1).toEpochDay(),
            ),
            onBack = {},
            onPickDirection = {}, onCounterpartyChange = {}, onPrincipalChange = {},
            onPickStartDay = {}, onPickDueDay = {}, onNoteChange = {}, onToggleClosed = {}, onSave = {},
            onDelete = {},
        )
    }
}
