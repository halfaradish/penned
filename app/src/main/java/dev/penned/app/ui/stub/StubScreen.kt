package dev.penned.app.ui.stub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.penned.app.ui.components.EmptyState
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedSpacing

/** 未实现页的占位：Tab 页形态（可滚走的页头 + 居中的说明），不再直接说"将在 Mn 实现"。 */
@Composable
fun StubScreen(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    // 不透明页面：下钻推入时父子两页同屏，透明根会让下层页透出来（AP-2）。
    // 页头大标题已去掉：底栏 Tab 已经写着「日历」，同一句话不必出现两次（D035 推广）
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EmptyState(
            title = title,
            hint = hint,
            icon = PennedIcons.Calendar,
            modifier = Modifier.padding(top = PennedSpacing.xxl),
        )
    }
}
