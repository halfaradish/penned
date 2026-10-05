package dev.penned.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.Money
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.ExpenseRed
import dev.penned.app.ui.theme.IncomeTeal
import dev.penned.app.ui.theme.PennedTheme
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    bookName: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    HomeContent(
        bookName = bookName,
        rows = rows,
        onAdd = onAdd,
        modifier = modifier,
    )
}

@Composable
fun HomeContent(
    bookName: String,
    rows: List<TxWithCategory>,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = bookName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text(text = "搜索", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }

        val today = LocalDate.now(zone)
        val totals = HomeGroups.monthTotals(rows, today.year, today.monthValue, zone)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SummaryCell("本月支出", Money.format(totals.expenseCents), ExpenseRed)
                SummaryCell("本月收入", Money.format(totals.incomeCents), IncomeTeal)
                SummaryCell("本月结余", Money.format(totals.incomeCents - totals.expenseCents), MaterialTheme.colorScheme.onSurface)
            }
        }

        Button(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text("添加一条新记账")
        }

        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("还没有记账", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("点上方按钮记下第一笔", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                HomeGroups.groupByDay(rows, zone).forEach { group ->
                    item(key = "day-${group.epochDay}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = HomeGroups.dayLabel(group.epochDay, zone),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "收 ${Money.format(group.incomeCents)}  支 ${Money.format(group.expenseCents)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(group.items, key = { it.id }) { row ->
                        TransactionRow(row)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}

@Composable
private fun TransactionRow(row: TxWithCategory) {
    val title = listOfNotNull(row.categoryParentName, row.categoryName).joinToString(" · ").ifEmpty { "未分类" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(36.dp)
                .background(categoryColor(row.categoryName ?: "?"), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (row.categoryName ?: "?").take(1),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!row.note.isNullOrBlank()) {
                Text(
                    row.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val color = when (row.type) {
            TxType.EXPENSE -> ExpenseRed
            TxType.INCOME -> IncomeTeal
            TxType.TRANSFER -> MaterialTheme.colorScheme.onSurface
        }
        val prefix = when (row.type) {
            TxType.EXPENSE -> "-"
            TxType.INCOME -> "+"
            TxType.TRANSFER -> ""
        }
        Text(
            text = prefix + Money.format(row.amountCents),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            textAlign = TextAlign.End,
        )
    }
}

/** 由分类名哈希生成稳定配色：黄金角散列避免色相聚集（M0 方案，图标系统随 M3 落地）。 */
fun categoryColor(name: String): Color {
    val golden = 0.61803398875
    val h = name.hashCode().toLong() and 0xFFFFFFFFL
    val hueFraction = ((h * golden) % 1.0).toFloat()
    return Color.hsl(hueFraction * 360f, 0.42f, 0.55f) // Color.hsl 的 hue 单位是度
}

@Preview(showBackground = true, name = "首页-空")
@Composable
private fun HomeEmptyPreview() {
    PennedTheme { HomeContent("日常账本", emptyList(), onAdd = {}) }
}

@Preview(showBackground = true, name = "首页-有数据", widthDp = 400, heightDp = 780)
@Composable
private fun HomeDataPreview() {
    val rows = listOf(
        tx("1", minuteOf(2026, 10, 4, 19, 30), TxType.EXPENSE, 1600, "晚餐", "食品餐饮", "北苑餐厅"),
        tx("2", minuteOf(2026, 10, 4, 8, 10), TxType.INCOME, 150000, "工资", null, null),
        tx("3", minuteOf(2026, 10, 3, 12, 0), TxType.EXPENSE, 700, "午餐", "食品餐饮", "西门"),
        tx("4", minuteOf(2026, 10, 2, 21, 15), TxType.EXPENSE, 2450, "手机数码", "购物消费", "数据线"),
    )
    PennedTheme { HomeContent("日常账本", rows, onAdd = {}) }
}

private fun tx(
    id: String,
    minute: Long,
    type: TxType,
    cents: Long,
    name: String?,
    parent: String?,
    note: String?,
) = TxWithCategory(
    id = id, type = type, amountCents = cents, accountId = null, categoryId = null,
    dateEpochMinute = minute, note = note, categoryName = name, categoryParentName = parent, categoryIconKey = null,
)

private fun minuteOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    LocalDate.of(year, month, day).atTime(hour, minute).atZone(ZoneId.systemDefault()).toEpochSecond() / 60
