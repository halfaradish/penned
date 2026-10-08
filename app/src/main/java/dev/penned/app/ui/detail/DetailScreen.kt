package dev.penned.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import dev.penned.app.domain.ReimburseState
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
import androidx.compose.ui.text.style.TextAlign
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.MoneyText
import dev.penned.app.ui.components.PennedButton
import dev.penned.app.ui.components.PennedButtonVariant
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedSwitchRow
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.components.SectionCard
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedSpacing
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
    val d = detail
    if (d == null) {
        // 载入中就画页面底色与顶栏：下钻推入时父子两页同屏，空白帧会让下层页透出来（AP-2）
        Scaffold(
            topBar = { PennedTopBar(title = "账单详情", onBack = onBack) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Spacer(Modifier.fillMaxSize().padding(padding))
        }
        return
    }
    DetailContent(
        detail = d,
        onEdit = { onEdit(d.tx.id) },
        onDelete = { viewModel.delete(onBack) },
        onToggleStats = viewModel::setExcludeStats,
        onToggleBudget = viewModel::setExcludeBudget,
        onBack = onBack,
    )
}

/** 无状态入口：截图测试/预览用（不碰 DB 与 DataStore）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DetailContent(
    detail: DetailUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleStats: (Boolean) -> Unit,
    onToggleBudget: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val d = detail
    Scaffold(
        topBar = { PennedTopBar(title = "账单详情", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PennedSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PennedSpacing.md),
        ) {
            // 金额 hero：分类头像 + 分类路径 + 日期 + 大金额
            PennedCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(categoryLabel(d), size = 44.dp)
                    Spacer(Modifier.width(PennedSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(categoryLabel(d), style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatMinute(d.tx.dateEpochMinute),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (d.tx.refundCents > 0) {
                        RefundBadge(fullRefund = d.tx.amountCents == 0L)
                    }
                }
                Spacer(Modifier.height(PennedSpacing.md))
                MoneyText(
                    cents = d.tx.amountCents,
                    type = d.tx.type,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "明细") {
                if (d.tx.type == TxType.TRANSFER) {
                    DetailRow("转出账户", d.accountName ?: "无")
                    PennedDivider()
                    DetailRow("转入账户", d.targetAccountName ?: "无")
                    PennedDivider()
                    if (d.tx.feeCents > 0) {
                        DetailRow("手续费", Money.format(d.tx.feeCents))
                        PennedDivider()
                    }
                }
                if (d.tx.refundCents > 0) {
                    DetailRow("退款", Money.format(d.tx.refundCents))
                    PennedDivider()
                }
                if (d.tx.discountCents > 0) {
                    DetailRow("优惠", Money.format(d.tx.discountCents))
                    PennedDivider()
                }
                DetailRow("账单日期", formatMinute(d.tx.dateEpochMinute))
                PennedDivider()
                DetailRow("备注", d.tx.note ?: "无")
                if (d.tx.type != TxType.TRANSFER) {
                    PennedDivider()
                    DetailRow("账户", d.accountName ?: "无账户")
                }
                if (d.tx.reimburseState != ReimburseState.NONE) {
                    PennedDivider()
                    DetailRow("报销", reimburseLabel(d.tx.reimburseState))
                }
            }

            SectionCard(title = "记录信息") {
                DetailRow("所属账本", d.bookName)
                PennedDivider()
                DetailRow("记录方式", sourceLabel(d.tx))
                PennedDivider()
                DetailRow("记录时间", formatMinute(d.tx.createdAt / 60_000))
                if (!d.tx.address.isNullOrBlank()) {
                    PennedDivider()
                    DetailRow("地点", d.tx.address!!)
                }
                val rate = d.tx.rate
                if (!d.tx.currencyCode.isNullOrBlank()) {
                    PennedDivider()
                    DetailRow("原币种", d.tx.currencyCode!! + (rate?.let { " · 汇率 $it" } ?: ""))
                }
            }

            SectionCard(title = "统计口径") {
                PennedSwitchRow("不计入收支", d.tx.excludeFromStats, onToggleStats)
                PennedDivider()
                PennedSwitchRow("不计入预算", d.tx.excludeFromBudget, onToggleBudget)
            }

            Spacer(Modifier.height(PennedSpacing.xs))
            PennedButton("编辑", onEdit, leadingIcon = PennedIcons.Edit)
            PennedButton("删除", onDelete, variant = PennedButtonVariant.Danger, leadingIcon = PennedIcons.Trash)
        }
    }
}

private fun categoryLabel(d: DetailUi): String =
    listOfNotNull(d.categoryParentName, d.categoryName).joinToString(" · ")
        .ifEmpty { if (d.tx.type == TxType.TRANSFER) "转账" else "未分类" }

/** 退款标记（M1 只读展示；退款交互留 M3）。 */
@Composable
private fun RefundBadge(fullRefund: Boolean) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = if (fullRefund) "已全额退款" else "部分退款",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun reimburseLabel(state: ReimburseState): String = when (state) {
    ReimburseState.PENDING -> "待报销"
    ReimburseState.DONE -> "已报销"
    ReimburseState.NONE -> "不报销"
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
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}


