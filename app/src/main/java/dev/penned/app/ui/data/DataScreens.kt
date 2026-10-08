package dev.penned.app.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.data.db.entity.ImportBatchEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.PaddingValues
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.ConfirmDialog
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.components.MoneyText
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedDivider
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedSnackbarHost
import dev.penned.app.ui.components.SectionLabel
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import dev.penned.app.ui.theme.expenseColor

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
        topBar = { PennedTopBar(title = "数据管理", onBack = onBack) },
        snackbarHost = { PennedSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
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
    Column(Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.xs)) {
        SectionLabel(title)
        PennedCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            content()
        }
    }
}

@Composable
private fun MenuRow(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    PennedListRow(
        title = title,
        subtitle = subtitle,
        leadingIcon = leadingIcon,
        onClick = if (enabled) onClick else null,
        showChevron = enabled,
    )
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
        topBar = { PennedTopBar(title = "导入批次", onBack = onBack) },
        snackbarHost = { PennedSnackbarHost(snackbarHostState) },
    ) { padding ->
        if (batches.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "还没有导入记录",
                    hint = "在数据管理页导入一木 xls 或 CSV 后，会在这里按批次记录",
                    icon = PennedIcons.Database,
                )
            }
        } else {
            // 卡片语言：一张卡包住全部批次，行分隔线缩进到头像之后（AR 轮收编：此前是 20dp 裸行）
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = PennedSpacing.lg,
                    end = PennedSpacing.lg,
                    top = PennedSpacing.md,
                    bottom = PennedSpacing.xxl,
                ),
            ) {
                item(key = "batch-card") {
                    PennedCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                        batches.forEachIndexed { i, batch ->
                            if (i > 0) PennedDivider(startIndent = PennedDimens.rowTextIndent)
                            PennedListRow(
                                title = batch.fileName,
                                subtitle = "${batch.count} 条流水 · ${formatBatchTime(batch.importedAt)}",
                                leadingIcon = PennedIcons.Database,
                                onClick = { onOpenBatch(batch.id) },
                                content = {
                                    IconButton(onClick = { confirmTarget = batch }) {
                                        Icon(
                                            PennedIcons.Trash,
                                            contentDescription = "删除",
                                            tint = expenseColor(),
                                            modifier = Modifier.size(PennedDimens.iconInline),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    confirmTarget?.let { target ->
        ConfirmDialog(
            onDismiss = { confirmTarget = null },
            title = "删除这批导入？",
            text = "「${target.fileName}」的 ${target.count} 条流水将被删除（可恢复的软删除）。",
            onConfirm = {
                viewModel.deleteBatch(target.id)
                confirmTarget = null
            },
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
        topBar = { PennedTopBar(title = "批次明细", onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            batch?.let {
                PennedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.md),
                ) {
                    Text(it.fileName, style = PennedTextStyles.cardTitle)
                    Text(
                        "${it.count} 条流水 · 导入于 ${formatBatchTime(it.importedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(title = "这批流水已全部删除", icon = PennedIcons.Trash)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = PennedSpacing.lg,
                        end = PennedSpacing.lg,
                        bottom = PennedSpacing.xxl,
                    ),
                ) {
                    item(key = "rows-card") {
                        PennedCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                            rows.forEachIndexed { i, row ->
                                if (i > 0) PennedDivider(startIndent = PennedDimens.rowTextIndent)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = PennedDimens.rowMinHeight)
                                        .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CategoryAvatar(row.categoryName ?: "未分类")
                                    Spacer(Modifier.width(PennedSpacing.md))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            row.note ?: row.categoryName ?: "未分类",
                                            style = PennedTextStyles.rowTitle,
                                        )
                                        Text(
                                            batchRowDate(row.dateEpochMinute) + " · " +
                                                (row.categoryName ?: "未分类"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    MoneyText(
                                        cents = row.amountCents,
                                        type = row.type,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun batchRowDate(epochMinute: Long): String =
    Instant.ofEpochSecond(epochMinute * 60).atZone(ZoneId.systemDefault())
        .let { "%d月%d日".format(it.monthValue, it.dayOfMonth) }
