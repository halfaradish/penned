package dev.penned.app.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.Money
import dev.penned.app.domain.TxType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import dev.penned.app.ui.theme.expenseColor

data class DetailUi(
    val tx: TransactionEntity,
    val categoryName: String?,
    val categoryParentName: String?,
    val accountName: String?,
    val targetAccountName: String?,
    val bookName: String,
)

class DetailViewModel(app: PennedApplication, txId: String) : ViewModel() {

    private val db = app.database

    val state: StateFlow<DetailUi?> = db.transactionDao().observeById(txId)
        .map { tx ->
            tx ?: return@map null
            val category = tx.categoryId?.let { db.categoryDao().get(it) }
            val account = tx.accountId?.let { db.accountDao().get(it) }
            val target = tx.targetAccountId?.let { db.accountDao().get(it) }
            val book = db.bookDao().get(tx.bookId)
            DetailUi(
                tx = tx,
                categoryName = category?.name,
                categoryParentName = category?.parentId?.let { pid -> db.categoryDao().get(pid)?.name },
                accountName = account?.name,
                targetAccountName = target?.name,
                bookName = book?.name ?: "",
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setExcludeStats(value: Boolean) = update { it.copy(excludeFromStats = value) }
    fun setExcludeBudget(value: Boolean) = update { it.copy(excludeFromBudget = value) }

    fun delete(onDone: () -> Unit) {
        val tx = state.value?.tx ?: return
        viewModelScope.launch {
            db.transactionDao().softDelete(tx.id, System.currentTimeMillis())
            onDone()
        }
    }

    private fun update(transform: (TransactionEntity) -> TransactionEntity) {
        val current = state.value?.tx ?: return
        viewModelScope.launch {
            db.transactionDao().upsert(transform(current).copy(updatedAt = System.currentTimeMillis()))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    txId: String,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: DetailViewModel = viewModel(
        key = txId,
        initializer = { DetailViewModel(app, txId) },
    )
    val detail by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账单详情") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ 返回") } },
            )
        },
    ) { padding ->
        val d = detail ?: return@Scaffold
        Surface(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        d.categoryName ?: if (d.tx.type == TxType.TRANSFER) "转账" else "未分类",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    val prefix = when (d.tx.type) {
                        TxType.EXPENSE -> "-"
                        TxType.INCOME -> "+"
                        TxType.TRANSFER -> ""
                    }
                    Text(
                        prefix + Money.format(d.tx.amountCents),
                        style = MaterialTheme.typography.headlineSmall,
                        color = when (d.tx.type) {
                            TxType.EXPENSE -> expenseColor()
                            TxType.INCOME -> MaterialTheme.colorScheme.primary
                            TxType.TRANSFER -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                if (d.tx.type == TxType.TRANSFER) {
                    DetailRow("转出账户", d.accountName ?: "无")
                    DetailRow("转入账户", d.targetAccountName ?: "无")
                    if (d.tx.feeCents > 0) DetailRow("手续费", Money.format(d.tx.feeCents))
                }
                DetailRow("账单日期", formatMinute(d.tx.dateEpochMinute))
                DetailRow("备注", d.tx.note ?: "无")
                DetailRow("账户", d.accountName ?: "无账户")
                DetailRow("所属账本", d.bookName)
                DetailRow("记录方式", sourceLabel(d.tx))
                DetailRow("记录时间", formatMinute(d.tx.createdAt / 60_000))
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ToggleRow("不计入收支", d.tx.excludeFromStats) { viewModel.setExcludeStats(it) }
                ToggleRow("不计入预算", d.tx.excludeFromBudget) { viewModel.setExcludeBudget(it) }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onEdit(d.tx.id) }, modifier = Modifier.weight(1f)) { Text("编辑") }
                    OutlinedButton(onClick = { viewModel.delete(onBack) }, modifier = Modifier.weight(1f)) {
                        Text("删除", color = expenseColor())
                    }
                }
            }
        }
    }
}

private fun sourceLabel(tx: TransactionEntity): String = when (tx.source.name) {
    "MANUAL" -> "手动记账"
    "VOICE" -> "语音记账"
    "TEXT" -> "一句话记账"
    "SCREENSHOT" -> "截图导入"
    "IMPORT" -> "账单导入"
    "AUTO" -> "自动记账"
    else -> tx.source.name
}

private fun formatMinute(epochMinute: Long): String =
    Instant.ofEpochSecond(epochMinute * 60).atZone(ZoneId.systemDefault())
        .let { "%d年%02d月%02d日 %02d:%02d".format(it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute) }

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
