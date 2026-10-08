package dev.penned.app.ui.stats

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.penned.app.domain.StatsMath
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 数据卡行的可点性（UI-9 / D049）：真实分类可点进详细页，「未分类」不可点。
 * 判定读 [StatsMath.CategoryTotal.drillable]，**不看名字**——回归场景：
 * 真实分类就叫「其他」（种子数据如此），修复前它与合并桶同名、被一并判成不可点。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatsDataCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(onOpenCategory: (String) -> Unit) {
        composeRule.setContent {
            PennedTheme {
                StatsDataCard(
                    title = "支出数据",
                    categories = listOf(
                        StatsMath.CategoryTotal("食品餐饮", 95344, 110, 0.6f),
                        StatsMath.CategoryTotal("其他", 2000, 1, 0.013f),
                        StatsMath.CategoryTotal("未分类", 1680, 1, 0.011f, drillable = false),
                    ),
                    type = TxType.EXPENSE,
                    onOpenCategory = onOpenCategory,
                )
            }
        }
    }

    @Test
    fun `真实分类「其他」可点并带上名字`() {
        var opened: String? = null
        render { opened = it }
        composeRule.onNodeWithText("其他").assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals("其他", opened)
    }

    @Test
    fun `普通真实分类可点`() {
        var opened: String? = null
        render { opened = it }
        composeRule.onNodeWithText("食品餐饮").assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals("食品餐饮", opened)
    }

    @Test
    fun `未分类行不可点`() {
        var opened: String? = null
        render { opened = it }
        composeRule.onNodeWithText("未分类").assertHasNoClickAction()
        assertNull(opened)
    }
}
