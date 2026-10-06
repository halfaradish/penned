package dev.penned.app.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.data.db.entity.ImportBatchEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 数据管理页：一木 xls 导入 / CSV 导入导出 / 导入批次管理。
 * 文件读写全部走 SAF（OpenDocument / CreateDocument），无需存储权限。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(
    viewModel: DataViewModel,
    onBack: () -> Unit,
    onOpenBatches: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val pickXls = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importYimuXls(it, resolver) }
    }
    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importCsv(it, resolver) }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportCsv(it, resolver) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据管理") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ 返回") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            SectionCard("导入") {
                MenuRow(
                    title = "一木记账 xls 导入",
                    subtitle = "选择一木导出的 .xls 账单，自动按名匹配分类/账本/账户",
                    enabled = !busy,
                ) { pickXls.launch(arrayOf("application/vnd.ms-excel", "application/octet-stream")) }
                HorizontalDivider()
                MenuRow(
                    title = "CSV 备份导入",
                    subtitle = "导入 penned 导出的 CSV（恢复到当前账本）",
                    enabled = !busy,
                ) { pickCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) }
            }
            SectionCard("导出") {
                MenuRow(
                    title = "导出 CSV（当前账本）",
                    subtitle = "带 id 的流水级备份，可用于再次导入",
                    enabled = !busy,
                ) {
                    val name = "penned_" +
                        DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").format(java.time.LocalDateTime.now()) +
                        ".csv"
                    createCsv.launch(name)
                }
            }
            SectionCard("导入批次") {
                MenuRow(
                    title = "导入批次管理",
                    subtitle = "查看历史导入，按批次整体删除",
                    enabled = true,
                ) { onOpenBatches() }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Card {
            Column { content() }
        }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 导入批次管理页：查看 + 按批次整体删除（软删）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImportBatchesScreen(
    viewModel: DataViewModel,
    onBack: () -> Unit,
    onOpenBatch: (String) -> Unit = {},
) {
    val batches by viewModel.batches.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    var confirmTarget by remember { mutableStateOf<ImportBatchEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入批次") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ 返回") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (batches.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("还没有导入记录", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    "在数据管理页导入一木 xls 或 CSV 后，会在这里按批次记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(batches.size) { i ->
                    val batch = batches[i]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenBatch(batch.id) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(batch.fileName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${batch.count} 条流水 · ${formatBatchTime(batch.importedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { confirmTarget = batch }) { Text("删除") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    confirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text("删除这批导入？") },
            text = { Text("「${target.fileName}」的 ${target.count} 条流水将被删除（可恢复的软删除）。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBatch(target.id)
                    confirmTarget = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmTarget = null }) { Text("取消") } },
        )
    }
}

private fun formatBatchTime(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** 批次明细页：某一批导入的流水清单（只读，M1 收尾补"查看"）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImportBatchDetailScreen(
    viewModel: DataViewModel,
    batchId: String,
    onBack: () -> Unit,
) {
    val batch by viewModel.batch(batchId).collectAsStateWithLifecycle(initialValue = null)
    val rows by viewModel.batchTransactions(batchId).collectAsStateWithLifecycle(initialValue = emptyList())
    ImportBatchDetailContent(batch = batch, rows = rows, onBack = onBack)
}

/** 无状态入口：截图测试/预览用。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImportBatchDetailContent(
    batch: ImportBatchEntity?,
    rows: List<TxWithCategory>,
    onBack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("批次明细") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ 返回") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            batch?.let {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(it.fileName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${it.count} 条流水 · 导入于 ${formatBatchTime(it.importedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
            if (rows.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("这批流水已全部删除", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows.size) { i ->
                        val row = rows[i]
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.note ?: row.categoryName ?: "未分类",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    batchRowDate(row.dateEpochMinute) + " · " +
                                        (row.categoryName ?: "未分类"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                (if (row.type == dev.penned.app.domain.TxType.EXPENSE) "-" else if (row.type == dev.penned.app.domain.TxType.INCOME) "+" else "") +
                                    dev.penned.app.domain.Money.format(row.amountCents),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (row.type == dev.penned.app.domain.TxType.EXPENSE) dev.penned.app.ui.theme.expenseColor()
                                else if (row.type == dev.penned.app.domain.TxType.INCOME) dev.penned.app.ui.theme.incomeColor()
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun batchRowDate(epochMinute: Long): String =
    Instant.ofEpochSecond(epochMinute * 60).atZone(ZoneId.systemDefault())
        .let { "%d月%d日".format(it.monthValue, it.dayOfMonth) }
