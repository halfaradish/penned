package dev.penned.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.SearchQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SearchContent(
    query: String,
    results: List<TxWithCategory>,
    onQueryChange: (String) -> Unit = {},
    onOpenDetail: (String) -> Unit = {},
    onBack: () -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ 返回") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 用 BasicTextField：Robolectric 下能 idle（弹窗里的 OutlinedTextField 不行，见看板）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    modifier = Modifier.weight(1f),
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
                    Text(
                        "清除",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onQueryChange("") },
                    )
                }
            }

            val groups = HomeGroups.groupByDay(results, zone)
            when {
                !SearchQuery.isSearchable(query) -> Hint("输入备注、分类名或金额开始搜索")
                groups.isEmpty() -> Hint("没有匹配的流水")
                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "count") {
                            Text(
                                "找到 ${results.size} 条",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        groups.forEach { group ->
                            item(key = "day-${group.epochDay}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(HomeGroups.dayLabel(group.epochDay, zone), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "收 ${dev.penned.app.domain.Money.format(group.incomeCents)}  支 ${dev.penned.app.domain.Money.format(group.expenseCents)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(group.items, key = { it.id }) { row ->
                                TransactionRow(
                                    row = row,
                                    selectionMode = false,
                                    selected = false,
                                    onClick = { onOpenDetail(row.id) },
                                    onLongClick = {},
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
