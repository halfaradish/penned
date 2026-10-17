package dev.penned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.ui.theme.PennedSpacing

/** 新建标签弹窗里名称输入框的测试标记：页面上还有备注输入框，测试要能钉准这一个。 */
const val TAG_NAME_FIELD_TAG = "tag-name-field"

/**
 * 新建标签（M3-1）：名称 + 位置（一级 / 某个一级标签之下）。
 *
 * 纯文本输入走 [PennedDialog]（与分类新增同一个范式，D024）；调用方负责建完即选中
 * （记一笔里是 `AddViewModel.addTagAndSelect`，标签管理页是 `TagsViewModel.addTag`）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagCreateDialog(
    parents: List<TagEntity>,
    onConfirm: (name: String, parentId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    val parentName = parents.firstOrNull { it.id == parentId }?.name
    PennedDialog(
        onDismiss = onDismiss,
        title = "新建标签",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onConfirm(name, parentId) },
    ) {
        PennedNameField(
            name = name,
            placeholder = "标签名称",
            onNameChange = { name = it },
            modifier = Modifier.testTag(TAG_NAME_FIELD_TAG),
        )
        Spacer(Modifier.height(PennedSpacing.md))
        Text(
            "位置：${parentName?.let { "$it 之下" } ?: "一级标签"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(PennedSpacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm)) {
            PennedChip(label = "一级", selected = parentId == null, onClick = { parentId = null })
            parents.forEach { parent ->
                PennedChip(
                    label = parent.name,
                    selected = parentId == parent.id,
                    onClick = { parentId = parent.id },
                )
            }
        }
    }
}

/** 名称输入：填充式（与预算/账本/分类弹窗同一套）。用 BasicTextField 而非 OutlinedTextField——后者在弹窗里 Robolectric 不 idle。 */
@Composable
fun PennedNameField(
    name: String,
    placeholder: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 密文显示（M4-2 的 API Key 用 `PasswordVisualTransformation`）；默认明文。 */
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(PennedSpacing.md),
    ) {
        if (name.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            ),
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
