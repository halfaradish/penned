package dev.penned.app.ui.add

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.PennedApplication
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.Money
import dev.penned.app.ui.theme.PennedTheme
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun AddTransactionScreen(
    app: PennedApplication,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        initializer = { AddViewModel(app) },
    )
    val type by viewModel.type.collectAsStateWithLifecycle()
    val amount by viewModel.amountText.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()

    AddTransactionContent(
        type = type,
        amountText = amount,
        selectedCategoryId = selectedId,
        note = note,
        canSave = canSave,
        categories = categories.map { CategoryChip(it.id, it.name) },
        onSelectType = viewModel::selectType,
        onKey = viewModel::pressKey,
        onSelectCategory = viewModel::selectCategory,
        onNoteChange = viewModel::setNote,
        onSave = { viewModel.save(onDone) },
        modifier = modifier,
    )
}

@Composable
fun AddTransactionContent(
    type: CategoryType,
    amountText: String,
    selectedCategoryId: String?,
    note: String,
    canSave: Boolean,
    categories: List<CategoryChip>,
    onSelectType: (CategoryType) -> Unit,
    onKey: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                SegmentedButton(
                    selected = type == CategoryType.EXPENSE,
                    onClick = { onSelectType(CategoryType.EXPENSE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("支出") }
                SegmentedButton(
                    selected = type == CategoryType.INCOME,
                    onClick = { onSelectType(CategoryType.INCOME) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("收入") }
            }

            Text(
                text = if (amountText.isEmpty()) "0.00" else amountText,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )

            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                placeholder = { Text("备注") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${LocalDate.now().monthValue}月${LocalDate.now().dayOfMonth}日",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "今天",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                CategoryGrid(
                    categories = categories,
                    selectedId = selectedCategoryId,
                    onSelect = onSelectCategory,
                )
            }

            AmountKeyboard(
                canSave = canSave,
                onKey = onKey,
                onSave = onSave,
            )
        }
    }
}

@Preview(showBackground = true, name = "记账页", widthDp = 400, heightDp = 780)
@Composable
private fun AddScreenPreview() {
    PennedTheme {
        AddTransactionContent(
            type = CategoryType.EXPENSE,
            amountText = "16.5",
            selectedCategoryId = null,
            note = "",
            canSave = true,
            categories = listOf("食品餐饮", "购物消费", "出行交通", "文化教育", "休闲娱乐", "居家生活", "健康医疗", "其他", "工资", "兼职")
                .mapIndexed { i, n -> CategoryChip(i.toString(), n) },
            onSelectType = {}, onKey = {}, onSelectCategory = {}, onNoteChange = {}, onSave = {},
        )
    }
}
