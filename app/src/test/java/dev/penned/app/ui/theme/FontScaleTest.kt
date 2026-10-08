package dev.penned.app.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.TxType
import dev.penned.app.ui.add.AddTransactionContent
import dev.penned.app.ui.add.CategoryUi
import dev.penned.app.ui.home.HomeContent
import dev.penned.app.ui.home.HomeViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * 字体缩放检查（D044 / 界面规范第十一节第 5 条）：系统字体调到 1.5× 时关键屏不裁字、不重叠。
 *
 * 断言只做"内容还在"这一档（Compose 的裁字不会抛异常），真正的验收是**逐张看图**——
 * 图落在 `build/test-results/roborazzi/fontscale_*.png`（人工评审用，同 IconGallery 的定位）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontScaleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone: ZoneId = ZoneId.systemDefault()

    private val rows = listOf(
        tx("1", LocalDate.of(2026, 10, 4).atTime(19, 30), TxType.EXPENSE, 1600, "晚餐", "食品餐饮", "北苑餐厅"),
        tx("2", LocalDate.of(2026, 10, 4).atTime(8, 10), TxType.INCOME, 150000, "工资", null, null),
    )

    @Test
    fun homeAtFontScale15() {
        composeRule.setContent {
            PennedTheme { Scaled { Home() } }
        }
        composeRule.onNodeWithText("日常账本").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/fontscale_home.png")
    }

    @Test
    fun addAtFontScale15() {
        composeRule.setContent {
            PennedTheme { Scaled { Add() } }
        }
        composeRule.onNodeWithText("完成").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/fontscale_add.png")
    }

    @Test
    fun homeAtFontScale20() {
        composeRule.setContent {
            PennedTheme { Scaled(fontScale = 2f) { Home() } }
        }
        composeRule.onNodeWithText("日常账本").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/fontscale_home_200.png")
    }

    /** 把 fontScale 提到 1.5/2.0，其余（密度）沿用环境。 */
    @Composable
    private fun Scaled(fontScale: Float = 1.5f, content: @Composable () -> Unit) {
        val current = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = current.density, fontScale = fontScale),
        ) {
            content()
        }
    }

    @Composable
    private fun Home() {
        HomeContent(
            bookName = "日常账本",
            rows = rows,
            onAdd = {},
            budgetSummary = HomeViewModel.BudgetSummary(totalCents = 300000, spentCents = 2200),
            zone = zone,
        )
    }

    @Composable
    private fun Add() {
        AddTransactionContent(
            type = TxType.EXPENSE,
            amountText = "16.5",
            note = "牛肉蛋炒粉",
            selectedId = "p0",
            expandedId = "p0",
            canSave = true,
            showEquals = false,
            accountId = null,
            fromId = null,
            toId = null,
            feeText = "",
            accounts = emptyList(),
            parents = listOf(
                CategoryUi("p0", "其他", hasSubs = true),
                CategoryUi("p1", "购物消费", hasSubs = false),
                CategoryUi("p2", "食品餐饮", hasSubs = true),
            ),
            expandedSubs = listOf(CategoryUi("s1", "晚餐", hasSubs = false)),
            onSelectType = {},
            onKey = {},
            onSelectParent = {},
            onSelectSub = {},
            onNoteChange = {},
            onPickAccount = {},
            onPickFrom = {},
            onPickTo = {},
            onSwap = {},
            onFeeChange = {},
            onEvaluate = {},
            onSave = {},
            onSaveAgain = {},
            onBack = {},
            onEdit = {},
            modifier = Modifier.fillMaxSize(),
        )
    }

    private fun tx(
        id: String,
        date: java.time.LocalDateTime,
        type: TxType,
        cents: Long,
        name: String?,
        parent: String?,
        note: String?,
    ) = TxWithCategory(
        id = id, type = type, amountCents = cents, accountId = null, categoryId = null,
        dateEpochMinute = date.atZone(zone).toEpochSecond() / 60, note = note,
        categoryName = name, categoryParentName = parent,
        categoryIconKey = null, targetAccountName = null, excludeFromStats = false,
    )
}
