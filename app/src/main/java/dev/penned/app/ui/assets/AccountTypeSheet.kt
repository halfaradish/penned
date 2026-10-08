package dev.penned.app.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.penned.app.ui.components.CategoryAvatar
import dev.penned.app.ui.components.PennedSheet
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedDimens
import dev.penned.app.ui.theme.PennedSpacing

/**
 * 添加账户的第一步：一次铺开四个大类的类型格（对标参考图）。
 * 每组末尾一个「自定义」格；点任意格直接进表单页，不再回到这一步。
 */
@Composable
fun AccountTypeSheet(
    onPick: (AccountGroup, AccountPreset?) -> Unit,
    onDismiss: () -> Unit,
) {
    PennedSheet(onDismiss = onDismiss, title = "选择账户类型") {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            AccountGroup.entries.forEach { group ->
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(
                        start = PennedSpacing.xl,
                        end = PennedSpacing.xl,
                        top = PennedSpacing.md,
                        bottom = PennedSpacing.xs,
                    ),
                )
                val presets = ACCOUNT_PRESETS[group].orEmpty()
                // 末尾多一格「自定义」
                val tiles = presets.size + 1
                Column(Modifier.fillMaxWidth().padding(horizontal = PennedSpacing.lg)) {
                    (0 until tiles).chunked(5).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = PennedSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(PennedSpacing.sm),
                        ) {
                            row.forEach { index ->
                                val preset = presets.getOrNull(index)
                                AccountTypeTile(
                                    label = preset?.label ?: "自定义",
                                    slot = preset?.let { accountPigmentSlot(it.type) },
                                    onClick = { onPick(group, preset) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** 一个类型格：颜料头像（自定义格是加号圆）+ 名称，5 列网格里的一格。 */
@Composable
private fun AccountTypeTile(
    label: String,
    slot: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = PennedDimens.sheetCellHeight)
            .clickable(onClick = onClick)
            .padding(vertical = PennedSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (slot != null) {
            CategoryAvatar(name = label, size = PennedDimens.categoryBubble, slot = slot)
        } else {
            Box(
                Modifier
                    .size(PennedDimens.categoryBubble)
                    .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PennedIcons.Plus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(PennedDimens.iconInline),
                )
            }
        }
        Spacer(Modifier.size(PennedSpacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
