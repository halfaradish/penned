package dev.penned.app.ui.invest

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
import dev.penned.app.data.db.dao.AccountWithBalance
import dev.penned.app.data.db.entity.AssetSnapshotEntity
import dev.penned.app.data.db.dao.toInvestSnapshot
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.DebtText
import dev.penned.app.domain.InvestMath
import dev.penned.app.domain.InvestSummary
import dev.penned.app.domain.Money
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.MoneyText
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.SectionCard
import dev.penned.app.ui.components.SwipeActionRow
import dev.penned.app.ui.components.rowDeleteAction
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.expenseColor
import dev.penned.app.ui.theme.primaryInkColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** 理财详情（M3-10）：现值 / 成本 / 盈亏三数 + 快照列表 + 记/删快照。 */
data class InvestUi(
    val account: AccountWithBalance,
    val summary: InvestSummary,
    val snapshots: List<AssetSnapshotEntity>,
)

class InvestViewModel(app: PennedApplication, private val accountId: String) : ViewModel() {

    private val db = app.database

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<InvestUi?> = combine(
        db.accountDao().observeWithBalanceByAccount(accountId),
        db.snapshotDao().observeByAccount(accountId),
    ) { account, snapshots ->
        account?.let {
            InvestUi(
                account = it,
                summary = InvestMath.summary(
                    snapshots = snapshots.map { s -> s.toInvestSnapshot() },
                    balanceCents = it.balanceCents,
                    initialBalanceCents = it.initialBalanceCents,
                    netTransferCents = it.netTransferCents,
                ),
                snapshots = snapshots,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _deletedIds = MutableStateFlow<List<String>>(emptyList())
    val deletedIds: StateFlow<List<String>> = _deletedIds

    /** 记一条快照（同一日期再记就更新那一条：一天只留一个现值，口径更干净）。 */
    fun record(dateEpochDay: Long, valueCents: Long, note: String?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val sameDay = state.value?.snapshots?.firstOrNull { it.dateEpochDay == dateEpochDay }
            db.snapshotDao().upsert(
                (sameDay ?: AssetSnapshotEntity(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId,
                    dateEpochDay = dateEpochDay,
                    valueCents = valueCents,
                    createdAt = now,
                    updatedAt = now,
                    deviceId = "unused",
                )).copy(
                    valueCents = valueCents,
                    note = note?.trim()?.ifEmpty { null },
                    updatedAt = now,
                )
            )
        }
    }

    fun delete(snapshot: AssetSnapshotEntity) {
        viewModelScope.launch {
            db.snapshotDao().softDelete(snapshot.id, System.currentTimeMillis())
            _deletedIds.value = _deletedIds.value + snapshot.id
        }
    }

    fun undoDelete() {
        val ids = _deletedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            ids.forEach { id ->
                db.snapshotDao().get(id)?.let { db.snapshotDao().upsert(it.copy(deleted = false, updatedAt = now)) }
            }
            _deletedIds.value = emptyList()
        }
    }

    fun clearUndo() {
        _deletedIds.value = emptyList()
    }
}

@Composable
fun InvestDetailScreen(
    accountId: String,
    onBack: () -> Unit,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: InvestViewModel = viewModel(
        key = "invest-$accountId",
        initializer = { InvestViewModel(app, accountId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val d = state
    if (d == null) {
        Scaffold(
            topBar = { PennedTopBar(title = "理财详情", onBack = onBack) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding -> Spacer(Modifier.fillMaxSize().padding(padding)) }
        return
    }
    InvestDetailContent(
        detail = d,
        onBack = onBack,
        onRecord = viewModel::record,
        onDeleteSnapshot = viewModel::delete,
    )
}

/** 无状态入口：截图测试/预览用。 */
@Composable
fun InvestDetailContent(
    detail: InvestUi,
    onBack: () -> Unit,
    onRecord: (Long, Long, String?) -> Unit = { _, _, _ -> },
    onDeleteSnapshot: (AssetSnapshotEntity) -> Unit = {},
) {
    var showRecord by remember { mutableStateOf(false) }
    var revealedId by remember { mutableStateOf<String?>(null) }
    val s = detail.summary
    val profitColor = if (s.profitCents < 0) expenseColor() else primaryInkColor()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "理财详情", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(PennedSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PennedSpacing.md),
        ) {
            // Hero：账户 + 总额（现值）+ 成本 / 盈亏两行
            PennedCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(detail.account.name, size = PennedDimens.avatarLarge)
                    Spacer(Modifier.width(PennedSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(detail.account.name, style = PennedTextStyles.cardTitle)
                        Text(
                            if (s.latest != null) {
                                "最近快照 ${DebtText.fullDate(s.latest.dateEpochDay)}"
                            } else {
                                "还没有快照：总额按账户余额算"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(PennedSpacing.md))
                Text("总额", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    Money.format(s.totalCents),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(PennedSpacing.sm))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "成本 ${Money.format(s.costCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "盈亏 ${Money.format(s.profitCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = profitColor,
                    )
                }
            }

            SectionCard(title = "快照") {
                if (detail.snapshots.isEmpty()) {
                    Text(
                        "还没有快照——「记快照」把今天的现值记下来，盈亏就按它算",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                    )
                } else {
                    detail.snapshots.forEachIndexed { index, snapshot ->
                        if (index > 0) PennedDivider()
                        SwipeActionRow(
                            isOpen = revealedId == snapshot.id,
                            onOpenChange = { open -> revealedId = if (open) snapshot.id else null },
                            actions = listOf(
                                rowDeleteAction {
                                    revealedId = null
                                    onDeleteSnapshot(snapshot)
                                },
                            ),
                        ) {
                            PennedListRow(
                                title = DebtText.fullDate(snapshot.dateEpochDay),
                                subtitle = snapshot.note?.takeIf { it.isNotBlank() },
                                trailing = Money.format(snapshot.valueCents),
                                onClick = { if (revealedId == snapshot.id) revealedId = null },
                                showChevron = false,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(PennedSpacing.xs))
            PennedButton("记快照", onClick = { showRecord = true })
        }
    }

    if (showRecord) {
        RecordSnapshotSheet(
            onConfirm = { day, cents, note ->
                onRecord(day, cents, note)
                showRecord = false
            },
            onDismiss = { showRecord = false },
        )
    }
}

/** 记快照抽屉：日期 + 现值 + 备注（同一天再记就更新那一条）。 */
@Composable
private fun RecordSnapshotSheet(
    onConfirm: (Long, Long, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var day by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    var valueText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val cents = Money.parseToCents(valueText)

    PennedSheet(onDismiss = onDismiss, title = "记快照") {
        PennedListRow(
            title = "日期",
            trailing = DebtText.fullDate(day),
            showChevron = false,
            onClick = { showDatePicker = true },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.xl, vertical = PennedSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("现值", style = PennedTextStyles.rowTitle, modifier = Modifier.width(PennedDimens.formLabel))
            BasicTextField(
                value = valueText,
                onValueChange = { text -> valueText = text.filter { it.isDigit() || it == '.' } },
                singleLine = true,
                textStyle = PennedTextStyles.rowTitle.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (valueText.isEmpty()) {
                            Text("0.00", style = PennedTextStyles.rowTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.xl, vertical = PennedSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("备注", style = PennedTextStyles.rowTitle, modifier = Modifier.width(PennedDimens.formLabel))
            BasicTextField(
                value = note,
                onValueChange = { note = it },
                singleLine = true,
                textStyle = PennedTextStyles.rowTitle.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (note.isEmpty()) {
                            Text("选填", style = PennedTextStyles.rowTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.height(PennedSpacing.sm))
        Row(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.xl)) {
            TextButton(onClick = onDismiss) { Text("取消") }
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = cents != null && cents > 0,
                onClick = { onConfirm(day, cents ?: 0L, note) },
            ) { Text("保存") }
        }
    }

    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = dev.penned.app.domain.AddDate.dayToPickerMillis(day),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { day = dev.penned.app.domain.AddDate.pickerMillisToDay(it) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            androidx.compose.material3.DatePicker(
                state = pickerState,
                title = { Text("选择日期", modifier = Modifier.padding(PennedSpacing.lg)) },
            )
        }
    }
}

@Preview(showBackground = true, name = "理财详情", widthDp = 400, heightDp = 800)
@Composable
private fun InvestDetailPreview() {
    dev.penned.app.ui.theme.PennedTheme {
        InvestDetailContent(
            detail = InvestUi(
                account = AccountWithBalance(
                    id = "a1", name = "理财账户", type = AccountType.INVEST, groupName = null,
                    balanceCents = 12_500L, netTransferCents = 2_000L, initialBalanceCents = 10_000L,
                ),
                summary = InvestMath.summary(
                    snapshots = listOf(dev.penned.app.domain.InvestSnapshot(LocalDate.now().minusDays(3).toEpochDay(), 13_000L)),
                    balanceCents = 12_500L, initialBalanceCents = 10_000L, netTransferCents = 2_000L,
                ),
                snapshots = listOf(
                    AssetSnapshotEntity(
                        id = "s1", accountId = "a1", dateEpochDay = LocalDate.now().minusDays(3).toEpochDay(),
                        valueCents = 13_000L, note = "季度更新", createdAt = 0, updatedAt = 0, deviceId = "d",
                    ),
                ),
            ),
            onBack = {},
        )
    }
}
