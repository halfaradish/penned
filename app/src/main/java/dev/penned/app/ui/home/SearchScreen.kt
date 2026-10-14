package dev.penned.app.ui.home

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.data.importer.PennedCsv
import dev.penned.app.domain.Money
import dev.penned.app.domain.SearchQuery
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.Templates
import dev.penned.app.domain.TxFilter
import dev.penned.app.domain.TxFilters
import dev.penned.app.domain.TxType
import dev.penned.app.ui.components.DayHeaderRow
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedChip
import dev.penned.app.ui.components.PennedListRow
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.components.PennedSnackbarHost
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.PennedTextStyles
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 结果汇总：N 笔 · 收入 X · 支出 Y（口径走 `StatsMath`，与统计页同一处）。 */
data class FilterSummary(val count: Int, val incomeCents: Long, val expenseCents: Long)

/**
 * 组合筛选（M3-7）：搜索页升级——关键词之外还有类型/日期/分类（含二级）/账户/标签/金额区间/三个开关。
 * 结果复用首页行渲染 + 顶部汇总；「导出」把**当前结果集**写成 CSV（文件名带筛选摘要与日期）。
 */
class SearchViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    val filter = MutableStateFlow(TxFilter())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentBook = db.bookDao().observeAll()
        .flatMapLatest { books ->
            app.settings.currentBookId.map { id ->
                id?.let { wanted -> books.firstOrNull { it.id == wanted } } ?: books.firstOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<CategoryEntity>> = currentBook
        .flatMapLatest { book -> if (book == null) flowOf(emptyList()) else db.categoryDao().observeByBook(book.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val accounts: StateFlow<List<AccountEntity>> = currentBook
        .flatMapLatest { book -> if (book == null) flowOf(emptyList()) else db.accountDao().observeByBook(book.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val tags: StateFlow<List<TagEntity>> = currentBook
        .flatMapLatest { book -> if (book == null) flowOf(emptyList()) else db.tagDao().observeByBook(book.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<TxWithCategory>> = combine(currentBook, filter, categories) { book, f, cats ->
        Triple(book, f, cats)
    }
        .flatMapLatest { (book, f, cats) ->
            if (book == null || !f.isActive) {
                flowOf(emptyList())
            } else {
                val (from, to) = TxFilters.minuteRange(f.fromEpochDay, f.toEpochDay)
                val categoryIds = TxFilters.expandCategoryIds(cats, f.categoryIds)
                db.transactionDao().observeFiltered(
                    bookId = book.id,
                    pattern = f.keyword.trim().takeIf { it.isNotEmpty() }?.let { SearchQuery.likePattern(it) },
                    keywordCents = SearchQuery.amountCentsOrNull(f.keyword),
                    type = f.type,
                    fromMinute = from,
                    toMinute = to,
                    hasCategories = if (categoryIds.isEmpty()) 0 else 1,
                    // Room 的 IN (:list) 空列表会语法错：没用到的条件给个占位值
                    categoryIds = categoryIds.ifEmpty { listOf("") },
                    hasAccounts = if (f.accountIds.isEmpty()) 0 else 1,
                    accountIds = f.accountIds.ifEmpty { listOf("") },
                    hasTags = if (f.tagIds.isEmpty()) 0 else 1,
                    tagIds = f.tagIds.ifEmpty { listOf("") },
                    minCents = f.amountMinCents,
                    maxCents = f.amountMaxCents,
                    excludeFromStats = f.excludeFromStats,
                    excludeFromBudget = f.excludeFromBudget,
                    includeTransfer = f.includeTransfer?.let { if (it) 1 else 0 },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<FilterSummary> = results.map { rows ->
        val totals = StatsMath.totals(rows)
        FilterSummary(rows.size, totals.incomeCents, totals.expenseCents)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilterSummary(0, 0, 0))

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    fun update(transform: (TxFilter) -> TxFilter) {
        filter.value = transform(filter.value)
    }

    fun setKeyword(text: String) = update { it.copy(keyword = text) }

    fun clearAll() {
        filter.value = TxFilter()
    }

    fun toggleCategory(id: String) = update {
        it.copy(categoryIds = if (id in it.categoryIds) it.categoryIds - id else it.categoryIds + id)
    }

    fun toggleAccount(id: String) = update {
        it.copy(accountIds = if (id in it.accountIds) it.accountIds - id else it.accountIds + id)
    }

    fun toggleTag(id: String) = update {
        it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id)
    }

    /** 导出当前结果集为 CSV（文件名 = `penned-摘要-日期.csv`）。 */
    fun exportCsv(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val rows = results.value
                if (rows.isEmpty()) return@launch
                val csv = withContext(Dispatchers.IO) {
                    val entities = db.transactionDao().entitiesByIds(rows.map { it.id })
                    // 按结果顺序写（entitiesByIds 的 IN 查询不保证顺序）
                    val byId = entities.associateBy { it.id }
                    PennedCsv.export(rows.mapNotNull { byId[it.id] })
                }
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                        ?: throw IllegalStateException("打不开目标文件")
                }
                _message.value = "已导出 ${rows.size} 条"
            } catch (e: Exception) {
                _message.value = "导出失败：${e.message}"
            }
        }
    }

    /** 导出建议文件名（SAF 的 CreateDocument 要一个初始名）。 */
    suspend fun suggestedFileName(today: LocalDate = LocalDate.now()): String {
        val cats = categories.value.associate { it.id to it.name }
        val accs = accounts.value.associate { it.id to it.name }
        return TxFilters.exportFileName(filter.value, cats, accs, today)
    }

    /** 供导出前读一次文件名（UI 侧同步取不到时用）。 */
    fun fileNameNow(today: LocalDate = LocalDate.now()): String = TxFilters.exportFileName(
        filter = filter.value,
        categoryNames = categories.value.associate { it.id to it.name },
        accountNames = accounts.value.associate { it.id to it.name },
        today = today,
    )
}

@Composable
fun SearchScreen(
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: SearchViewModel = viewModel(initializer = { SearchViewModel(app) })
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(m, duration = SnackbarDuration.Short)
        viewModel.consumeMessage()
    }

    SearchContent(
        filter = filter,
        results = results,
        summary = summary,
        categories = categories,
        accounts = accounts,
        tags = tags,
        onFilterChange = viewModel::update,
        onKeywordChange = viewModel::setKeyword,
        onToggleCategory = viewModel::toggleCategory,
        onToggleAccount = viewModel::toggleAccount,
        onToggleTag = viewModel::toggleTag,
        onClearAll = viewModel::clearAll,
        onExport = { uri, resolver -> viewModel.exportCsv(uri, resolver) },
        resolver = app.contentResolver,
        suggestedFileName = { viewModel.fileNameNow() },
        onOpenDetail = onOpenDetail,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    )
}

/** 无状态入口：截图测试/预览用。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchContent(
    filter: TxFilter,
    results: List<TxWithCategory>,
    summary: FilterSummary,
    categories: List<CategoryEntity> = emptyList(),
    accounts: List<AccountEntity> = emptyList(),
    tags: List<TagEntity> = emptyList(),
    onFilterChange: ((TxFilter) -> TxFilter) -> Unit = {},
    onKeywordChange: (String) -> Unit = {},
    onToggleCategory: (String) -> Unit = {},
    onToggleAccount: (String) -> Unit = {},
    onToggleTag: (String) -> Unit = {},
    onClearAll: () -> Unit = {},
    onExport: (Uri, ContentResolver) -> Unit = { _, _ -> },
    /** 导出用的 resolver（`SearchScreen` 从 LocalApp 取；截图测试不需要真导出，留空即可）。 */
    resolver: ContentResolver? = null,
    suggestedFileName: () -> String = { "penned.csv" },
    onOpenDetail: (String) -> Unit = {},
    onBack: () -> Unit = {},
    autofocus: Boolean = true,
    snackbarHostState: SnackbarHostState? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var sheet by remember { mutableStateOf<FilterSheet?>(null) }
    // resolver 由调用方传入（回调里不是 @Composable 上下文，不能读 CompositionLocal）
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val target = resolver
        if (uri != null && target != null) onExport(uri, target)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PennedTopBar(title = "筛选", onBack = onBack) },
        snackbarHost = { if (snackbarHostState != null) PennedSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                query = filter.keyword,
                onQueryChange = onKeywordChange,
                autofocus = autofocus,
            )

            // 条件 chips：点开各自的抽屉；有条件的 chip 转选中态并回显值
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(PennedSpacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                PennedChip(
                    label = filter.type?.let { Templates.typeLabel(it) } ?: "类型",
                    selected = filter.type != null,
                    onClick = { sheet = FilterSheet.TYPE },
                )
                PennedChip(
                    label = TxFilters.dateLabel(filter.fromEpochDay, filter.toEpochDay, LocalDate.now())
                        .ifEmpty { "日期" },
                    icon = PennedIcons.Calendar,
                    selected = filter.fromEpochDay != null || filter.toEpochDay != null,
                    onClick = { sheet = FilterSheet.DATE },
                )
                PennedChip(
                    label = if (filter.categoryIds.isEmpty()) "分类" else "分类 · ${filter.categoryIds.size}",
                    selected = filter.categoryIds.isNotEmpty(),
                    onClick = { sheet = FilterSheet.CATEGORY },
                )
                PennedChip(
                    label = if (filter.accountIds.isEmpty()) "账户" else "账户 · ${filter.accountIds.size}",
                    selected = filter.accountIds.isNotEmpty(),
                    onClick = { sheet = FilterSheet.ACCOUNT },
                )
                PennedChip(
                    label = if (filter.tagIds.isEmpty()) "标签" else "标签 · ${filter.tagIds.size}",
                    selected = filter.tagIds.isNotEmpty(),
                    onClick = { sheet = FilterSheet.TAG },
                )
                PennedChip(
                    label = TxFilters.amountLabel(filter.amountMinCents, filter.amountMaxCents).ifEmpty { "金额" },
                    selected = filter.amountMinCents != null || filter.amountMaxCents != null,
                    onClick = { sheet = FilterSheet.AMOUNT },
                )
                PennedChip(
                    label = "更多",
                    selected = filter.excludeFromStats != null || filter.excludeFromBudget != null || filter.includeTransfer != null,
                    onClick = { sheet = FilterSheet.MORE },
                )
                if (filter.activeCount > 0) {
                    TextButton(onClick = onClearAll) { Text("清空") }
                }
            }

            val groups = HomeGroups.groupByDay(results, zone)
            when {
                !filter.isActive -> SearchHint("输入关键词，或点上面的条件开始筛选")
                groups.isEmpty() -> SearchHint("没有匹配的流水")
                else -> {
                    LazyColumn(Modifier.fillMaxSize().imePadding()) {
                        item(key = "summary") {
                            Row(
                                Modifier.fillMaxWidth().padding(
                                    horizontal = PennedSpacing.lg,
                                    vertical = PennedSpacing.sm,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${summary.count} 笔 · 收入 ${Money.format(summary.incomeCents)} · 支出 ${Money.format(summary.expenseCents)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { exportLauncher.launch(suggestedFileName()) }) {
                                    Text("导出")
                                }
                            }
                        }
                        groups.forEach { group ->
                            item(key = "day-${group.epochDay}") {
                                DayHeaderRow(
                                    label = HomeGroups.dayLabel(group.epochDay, zone),
                                    incomeCents = group.incomeCents,
                                    expenseCents = group.expenseCents,
                                )
                            }
                            items(group.items, key = { it.id }) { row ->
                                TransactionRow(
                                    row = row,
                                    selectionMode = false,
                                    selected = false,
                                    onClick = { onOpenDetail(row.id) },
                                    onLongClick = {},
                                    modifier = Modifier.animateItem(),
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = PennedDimens.rowTextIndent),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (sheet) {
        FilterSheet.TYPE -> TypeSheet(
            type = filter.type,
            includeTransfer = filter.includeTransfer,
            onPick = { type ->
                onFilterChange { it.copy(type = type, includeTransfer = null) }
                sheet = null
            },
            onDismiss = { sheet = null },
        )
        FilterSheet.DATE -> DateRangeSheet(
            from = filter.fromEpochDay,
            to = filter.toEpochDay,
            onPick = { from, to -> onFilterChange { it.copy(fromEpochDay = from, toEpochDay = to) } },
            onDismiss = { sheet = null },
        )
        FilterSheet.CATEGORY -> MultiPickerSheet(
            title = "选择分类",
            rows = categoryRows(categories),
            selectedIds = filter.categoryIds.toSet(),
            onToggle = onToggleCategory,
            onDismiss = { sheet = null },
        )
        FilterSheet.ACCOUNT -> MultiPickerSheet(
            title = "选择账户",
            rows = accounts.map { it.id to it.name },
            selectedIds = filter.accountIds.toSet(),
            onToggle = onToggleAccount,
            onDismiss = { sheet = null },
        )
        FilterSheet.TAG -> MultiPickerSheet(
            title = "选择标签",
            rows = tags.map { it.id to it.name },
            selectedIds = filter.tagIds.toSet(),
            onToggle = onToggleTag,
            onDismiss = { sheet = null },
        )
        FilterSheet.AMOUNT -> AmountRangeSheet(
            minCents = filter.amountMinCents,
            maxCents = filter.amountMaxCents,
            onPick = { min, max -> onFilterChange { it.copy(amountMinCents = min, amountMaxCents = max) } },
            onDismiss = { sheet = null },
        )
        FilterSheet.MORE -> MoreSheet(
            excludeFromStats = filter.excludeFromStats,
            excludeFromBudget = filter.excludeFromBudget,
            includeTransfer = filter.includeTransfer,
            onFilterChange = onFilterChange,
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

private enum class FilterSheet { TYPE, DATE, CATEGORY, ACCOUNT, TAG, AMOUNT, MORE }

/** 分类多选行：`父 · 子`（一级与二级都能选；选了父 = 连子树一起筛，口径在 `TxFilters`）。 */
private fun categoryRows(categories: List<CategoryEntity>): List<Pair<String, String>> =
    categories.map { c ->
        val parent = c.parentId?.let { pid -> categories.firstOrNull { it.id == pid }?.name }
        c.id to listOfNotNull(parent, c.name).joinToString(" · ")
    }

/** 类型抽屉：全部 / 支出 / 收入 / 转账。 */
@Composable
private fun TypeSheet(
    type: TxType?,
    includeTransfer: Boolean?,
    onPick: (TxType?) -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "类型") {
        PennedListRow(
            title = "全部",
            trailing = if (type == null && includeTransfer == null) "已选" else null,
            showChevron = false,
            onClick = { onPick(null) },
        )
        listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER).forEach { t ->
            PennedListRow(
                title = Templates.typeLabel(t),
                trailing = if (t == type) "已选" else null,
                showChevron = false,
                onClick = { onPick(t) },
            )
        }
    }
}

/** 日期抽屉：常用区间一键选 + 自定义两端（各自可清除）。 */
@Composable
private fun DateRangeSheet(
    from: Long?,
    to: Long?,
    onPick: (Long?, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var datePickerFor by remember { mutableStateOf<Int?>(null) }
    PennedSheet(onDismiss = onDismiss, title = "日期范围") {
        val quick = listOf(
            "今天" to (today.toEpochDay() to today.toEpochDay()),
            "近 7 天" to (today.minusDays(6).toEpochDay() to today.toEpochDay()),
            "近 30 天" to (today.minusDays(29).toEpochDay() to today.toEpochDay()),
            "本月" to (today.withDayOfMonth(1).toEpochDay() to today.toEpochDay()),
        )
        quick.forEach { (label, range) ->
            PennedListRow(
                title = label,
                trailing = if (from == range.first && to == range.second) "已选" else null,
                showChevron = false,
                onClick = { onPick(range.first, range.second) },
            )
        }
        PennedListRow(
            title = "起始日",
            trailing = from?.let { dev.penned.app.domain.DebtText.fullDate(it) } ?: "不限",
            showChevron = false,
            onClick = { datePickerFor = 0 },
        )
        PennedListRow(
            title = "结束日",
            trailing = to?.let { dev.penned.app.domain.DebtText.fullDate(it) } ?: "不限",
            showChevron = false,
            onClick = { datePickerFor = 1 },
        )
    }
    val which = datePickerFor
    if (which != null) {
        val initial = (if (which == 0) from else to) ?: today.toEpochDay()
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = dev.penned.app.domain.AddDate.dayToPickerMillis(initial),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { datePickerFor = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val day = dev.penned.app.domain.AddDate.pickerMillisToDay(millis)
                        if (which == 0) onPick(day, to) else onPick(from, day)
                    }
                    datePickerFor = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (which == 0) onPick(null, to) else onPick(from, null)
                    datePickerFor = null
                }) { Text("清除") }
            },
        ) {
            androidx.compose.material3.DatePicker(
                state = pickerState,
                title = { Text(if (which == 0) "选择起始日" else "选择结束日", modifier = Modifier.padding(PennedSpacing.lg)) },
            )
        }
    }
}

/** 多选抽屉（分类/账户/标签共用）：勾选即生效、抽屉不关，底部「选好了」。 */
@Composable
private fun MultiPickerSheet(
    title: String,
    rows: List<Pair<String, String>>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = title) {
        if (rows.isEmpty()) {
            Text(
                "还没有可选的内容",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PennedSpacing.xl, vertical = PennedSpacing.sm),
            )
        }
        rows.forEach { (id, label) ->
            PennedListRow(
                title = label,
                trailing = if (id in selectedIds) "已选" else null,
                showChevron = false,
                onClick = { onToggle(id) },
            )
        }
        Spacer(Modifier.height(PennedSpacing.sm))
        PennedListRow(
            title = "选好了",
            showChevron = false,
            onClick = onDismiss,
        )
    }
}

/** 金额区间：两个输入框（金额口径仍是"分"存储，这里只做元 ↔ 分的转换）。 */
@Composable
private fun AmountRangeSheet(
    minCents: Long?,
    maxCents: Long?,
    onPick: (Long?, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var minText by remember { mutableStateOf(minCents?.let { Money.format(it, grouped = false) } ?: "") }
    var maxText by remember { mutableStateOf(maxCents?.let { Money.format(it, grouped = false) } ?: "") }
    PennedSheet(onDismiss = onDismiss, title = "金额区间") {
        AmountInputRow(label = "最低", value = minText, onValueChange = { minText = it })
        AmountInputRow(label = "最高", value = maxText, onValueChange = { maxText = it })
        Spacer(Modifier.height(PennedSpacing.sm))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                minText = ""
                maxText = ""
                onPick(null, null)
            }) { Text("清除") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                onPick(Money.parseToCents(minText), Money.parseToCents(maxText))
                onDismiss()
            }) { Text("确定") }
        }
    }
}

@Composable
private fun AmountInputRow(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.xl, vertical = PennedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = PennedTextStyles.rowTitle, modifier = Modifier.width(PennedDimens.formLabel))
        BasicTextField(
            value = value,
            onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == '.' }) },
            singleLine = true,
            textStyle = PennedTextStyles.rowTitle.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text("不限", style = PennedTextStyles.rowTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
    }
}

/** 更多开关：三个都是"不限 / 是 / 否"三态（`null` = 不限）。 */
@Composable
private fun MoreSheet(
    excludeFromStats: Boolean?,
    excludeFromBudget: Boolean?,
    includeTransfer: Boolean?,
    onFilterChange: ((TxFilter) -> TxFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "更多条件") {
        TriStateRow(
            label = "不计入收支",
            value = excludeFromStats,
            onChange = { v -> onFilterChange { it.copy(excludeFromStats = v) } },
        )
        TriStateRow(
            label = "不计入预算",
            value = excludeFromBudget,
            onChange = { v -> onFilterChange { it.copy(excludeFromBudget = v) } },
        )
        TriStateRow(
            label = "结果含转账",
            value = includeTransfer,
            onChange = { v -> onFilterChange { it.copy(includeTransfer = v) } },
        )
    }
}

/** 三态行：不限 / 是 / 否（点一下循环；当前值写在行尾）。 */
@Composable
private fun TriStateRow(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    val text = when (value) {
        null -> "不限"
        true -> "是"
        false -> "否"
    }
    PennedListRow(
        title = label,
        subtitle = "点一下切换：不限 → 是 → 否",
        trailing = text,
        showChevron = false,
        onClick = {
            onChange(
                when (value) {
                    null -> true
                    true -> false
                    false -> null
                },
            )
        },
    )
}

/** 填充式搜索框：放大镜 + 输入 + 清除。进页自动聚焦并弹出键盘。 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    autofocus: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autofocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PennedCard(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(percent = 50),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.md, vertical = PennedSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    PennedIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(PennedDimens.iconSmall),
                )
                Spacer(Modifier.width(PennedSpacing.sm))
                // 用 BasicTextField：Robolectric 下能 idle（弹窗里的 OutlinedTextField 不行，见看板）
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    "搜索备注 / 分类 / 金额",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(PennedDimens.iconButton)) {
                        Icon(
                            PennedIcons.Close,
                            contentDescription = "清除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(PennedDimens.iconChevron),
                        )
                    }
                }
            }
        }
    }
}

/** 空查询/无结果的说明（紧贴输入框，左对齐）。 */
@Composable
private fun SearchHint(text: String) {
    Text(
        text = text,
        style = PennedTextStyles.rowTitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.lg),
    )
}
