package dev.penned.app.ui.data

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.data.db.entity.ImportBatchEntity
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/** M1 收尾：导入批次明细（"查看"钻取）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportBatchDetailScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val batch = ImportBatchEntity(
        id = "batch-1", bookId = "b1", fileName = "账单_1005183742.xls", count = 3,
        importedAt = 1_700_000_000_000L, createdAt = 1L, updatedAt = 1L, deviceId = "d",
    )

    private fun row(id: String, day: Int, cents: Long, type: TxType, note: String?, category: String?) =
        TxWithCategory(
            id = id, type = type, amountCents = cents, accountId = null, categoryId = null,
            dateEpochMinute = LocalDate.of(2024, 9, day).atTime(12, 30).atZone(ZoneId.systemDefault()).toEpochSecond() / 60,
            note = note, categoryName = category, categoryParentName = null, categoryIconKey = null,
            targetAccountName = null,
            excludeFromStats = false,
        )

    @Test
    fun captureBatchDetail() {
        composeRule.setContent {
            PennedTheme {
                ImportBatchDetailContent(
                    batch = batch,
                    rows = listOf(
                        row("t1", 3, 1600, TxType.EXPENSE, "北苑餐厅", "食品餐饮"),
                        row("t2", 2, 350000, TxType.INCOME, "工资", "工资"),
                        row("t3", 1, 0, TxType.EXPENSE, "遮光布", "购物消费"),
                    ),
                )
            }
        }
        composeRule.onNodeWithText("账单_1005183742.xls").assertIsDisplayed()
        composeRule.onNodeWithText("3 条流水 · 导入于 2023-11-15 06:13").assertIsDisplayed()
        composeRule.onNodeWithText("北苑餐厅").assertIsDisplayed()
        composeRule.onNodeWithText("-16.00").assertIsDisplayed()
        composeRule.onNodeWithText("+3500.00").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_import_batch_detail.png")
    }

    @Test
    fun captureBatchDetailAfterDelete() {
        // 批次删除后（或流水被单条删除）走空态
        composeRule.setContent { PennedTheme { ImportBatchDetailContent(batch = batch, rows = emptyList()) } }
        composeRule.onNodeWithText("这批流水已全部删除").assertIsDisplayed()
    }
}
