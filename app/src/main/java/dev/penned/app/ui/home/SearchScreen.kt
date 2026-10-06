package dev.penned.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import dev.penned.app.domain.SearchQuery
import dev.penned.app.ui.components.DayHeaderRow
import dev.penned.app.ui.components.PennedCard
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedSpacing
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 首页搜索（备注/分类/金额，当前账本内）。
 * 组合筛选（日期范围/类型/账户/标签）留 M3。
 */
class SearchViewModel(private val app: PennedApplication) : ViewModel() {

    private val db = app.database

    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentBook = db.bookDao().observeAll()
        .flatMapLatest { books ->
            app.settings.currentBookId.map { id ->
                id?.let { wanted -> books.firstOrNull { it.id == wanted } } ?: books.firstOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<TxWithCategory>> = combine(currentBook, query) { book, q -> book to q }
        .flatMapLatest { (book, q) ->
            if (book == null || !SearchQuery.isSearchable(q)) {
                flowOf(emptyList())
            } else {
                db.transactionDao().observeSearchWithCategory(
                    bookId = book.id,
                    pattern = SearchQuery.likePattern(q),
                    cents = SearchQuery.amountCentsOrNull(q),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(text: String) {
        query.value = text
    }
}

@Composable
fun SearchScreen(
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    val app = dev.penned.app.ui.LocalApp.current
    val viewModel: SearchViewModel = viewModel(initializer = { SearchViewModel(app) })
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    SearchContent(
        query = query,
        results = results,
        onQueryChange = viewModel::setQuery,
        onOpenDetail = onOpenDetail,
        onBack = onBack,
    )
}

/** 无状态入口：截图测试/预览用。 */
@Composable
fun SearchContent(
    query: String,
    results: List<TxWithCategory>,
    onQueryChange: (String) -> Unit = {},
    onOpenDetail: (String) -> Unit = {},
    onBack: () -> Unit = {},
    autofocus: Boolean = true,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        topBar = { PennedTopBar(title = "搜索", onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                autofocus = autofocus,
            )

            val groups = HomeGroups.groupByDay(results, zone)
            when {
                // 未输入：提示紧贴输入框左对齐，不再在整屏中间吊一行字（原来中段一大片空白）
                !SearchQuery.isSearchable(query) -> SearchHint("输入备注、分类名或金额开始搜索")
                groups.isEmpty() -> SearchHint("没有匹配的流水")
                else -> {
                    LazyColumn(Modifier.fillMaxSize().imePadding()) {
                        item(key = "count") {
                            Text(
                                "找到 ${results.size} 条",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = PennedSpacing.lg, vertical = PennedSpacing.sm),
                            )
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
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = PennedSpacing.lg),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            shape = RoundedCornerShape(percent = 50),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PennedSpacing.md, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    PennedIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
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
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            PennedIcons.Close,
                            contentDescription = "清除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
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
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PennedSpacing.xl, vertical = PennedSpacing.lg),
    )
}
