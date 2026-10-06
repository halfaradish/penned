package dev.penned.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.ui.icons.PennedIcons
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 底部抽屉（D024 交互范式一）：Robolectric 下能否 idle + 能否截图，决定名单选择类弹窗
 * 是否统一改走 ModalBottomSheet。截图 `m1_sheet_accounts.png` 供人工过目。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PennedSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun accountSheetRendersAndPicks() {
        var picked = ""
        composeRule.setContent {
            PennedTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PennedSheet(onDismiss = {}, title = "选择账户") {
                        PennedListRow(
                            title = "无账户",
                            leadingIcon = PennedIcons.Wallet,
                            onClick = { picked = "none" },
                        )
                        PennedListRow(
                            title = "支付宝",
                            subtitle = "储蓄卡",
                            leadingIcon = PennedIcons.Wallet,
                            onClick = { picked = "alipay" },
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("选择账户").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_sheet_accounts.png")
        composeRule.onNodeWithText("支付宝").performClick()
        assert(picked == "alipay") { "抽屉里的行没有回传点击：$picked" }
    }
}
