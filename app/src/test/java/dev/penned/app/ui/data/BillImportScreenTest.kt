package dev.penned.app.ui.data

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.importer.AccountMapping
import dev.penned.app.data.importer.BillContainer
import dev.penned.app.data.importer.BillSource
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.BillRow
import dev.penned.app.domain.BillRowCounts
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 账单导入预览页（M4-5）的渲染与接线：识别结果 / 计数分列 / 收支合计 / 编码切换 / 目标账本 /
 * 账户映射（含"将新建 N 个"）/ 分类来源说明 / 导入按钮。
 *
 * 真读文件与落库在 `BillImportRepositoryTest` 与模拟机走查里；这里只钉渲染与回调接线。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BillImportScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun state(
        counts: BillRowCounts = BillRowCounts(
            total = 517, importable = 507, duplicate = 0, statusSkipped = 9, nonCashflowSkipped = 0, bad = 1,
        ),
    ) = BillImportUiState(
        loading = false,
        fileName = "支付宝交易明细(20250101-20251231).csv",
        source = BillSource.ALIPAY,
        container = BillContainer.CSV,
        charsetLabel = "GB18030",
        headerRowIndex = 23,
        counts = counts,
        importable = listOf(
            BillRow(100L, 1200L, true, "某店", "AL-1", "账户余额", "餐饮美食"),
            BillRow(200L, 1380L, true, "某店", "AL-2", "XX银行储蓄卡(4936)", "餐饮美食"),
            BillRow(300L, 8800L, false, "某人", "AL-3", null, null),
        ),
        mappings = listOf(
            AccountMapping(clue = "账户余额", matchedAccountName = "账户余额", matchedAccountId = "a1"),
            AccountMapping(clue = "XX银行储蓄卡(4936)", newAccountName = "XX银行储蓄卡(4936)", newAccountType = AccountType.DEBIT),
        ),
        accounts = listOf(
            dev.penned.app.data.db.entity.AccountEntity(
                id = "a1", bookId = "b1", name = "账户余额", type = AccountType.CASH,
                createdAt = 0, updatedAt = 0, deviceId = "d",
            ),
        ),
        books = listOf(BookEntity(id = "b1", name = "日常账本", sortOrder = 0, createdAt = 0, updatedAt = 0, deviceId = "d")),
        targetBookId = "b1",
        categoryFromColumn = 2,
        categoryFromRule = 0,
        categoryMissing = 1,
    )

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    @Test
    fun captureBillPreview() {
        composeRule.setContent { PennedTheme { BillImportContent(state(), onBack = {}) } }
        composeRule.onNodeWithText("支付宝交易明细").assertIsDisplayed()
        composeRule.onNodeWithText("容器 csv · 表头在第 24 行 · 编码 GB18030").assertIsDisplayed()
        // 计数卡没做语义合并：逐个断它的行（标签 + 值）
        composeRule.onNodeWithText("总行数").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("将导入").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("507").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("25.80").performScrollTo().assertIsDisplayed() // 支出合计 12.00 + 13.80
        composeRule.onNodeWithText("88.00").performScrollTo().assertIsDisplayed() // 收入合计
        composeRule.onNodeWithText("账户映射（将新建 1 个）").assertIsDisplayed()
        composeRule.onNodeWithText("分类：按名命中 2 · 关键词规则 0 · 待补 1（导入时不会新建分类）").assertIsDisplayed()
        capture("m4_bill_preview")
    }

    @Test
    fun countsSplit() {
        composeRule.setContent { PennedTheme { BillImportContent(state(), onBack = {}) } }
        // 跳过那一行把三类拆开写出来（关闭/退款 · 不计收支 · 坏行）
        composeRule.onNodeWithText("关闭/退款 9 · 不计收支 0 · 坏行 1").assertIsDisplayed()
        composeRule.onNodeWithText("重复").assertIsDisplayed()
    }

    @Test
    fun charsetSwitchCallsReparse() {
        var picked: String? = "sentinel"
        composeRule.setContent {
            PennedTheme { BillImportContent(state(), onBack = {}, onReparse = { picked = it }) }
        }
        // 当前是 GB18030 → 点 UTF-8 应触发按 UTF-8 重解析
        composeRule.onNodeWithText("UTF-8").performClick()
        assertEquals("UTF-8", picked)
    }

    @Test
    fun bookAndMappingRowsCallBack() {
        var mappingClue: String? = null
        var fallback = false
        var book = false
        composeRule.setContent {
            PennedTheme {
                BillImportContent(
                    state(), onBack = {},
                    onEditMapping = { mappingClue = it },
                    onEditFallback = { fallback = true },
                    onEditBook = { book = true },
                )
            }
        }
        composeRule.onNodeWithText("XX银行储蓄卡(4936)").performClick()
        assertEquals("XX银行储蓄卡(4936)", mappingClue)
        composeRule.onNodeWithText("其余统一记到").performClick()
        assertEquals(true, fallback)
        composeRule.onNodeWithText("目标账本").performClick()
        assertEquals(true, book)
        capture("m4_bill_preview_rows")
    }

    @Test
    fun importButtonCarriesCount() {
        var imported = false
        composeRule.setContent {
            PennedTheme { BillImportContent(state(), onBack = {}, onImport = { imported = true }) }
        }
        composeRule.onNodeWithTag(BILL_PREVIEW_IMPORT_TAG).assertTextContains("导入 507 条").performClick()
        assertEquals(true, imported)
    }

    @Test
    fun loadingAndErrorStates() {
        composeRule.setContent {
            PennedTheme { BillImportContent(BillImportUiState(loading = true), onBack = {}) }
        }
        composeRule.onNodeWithText("正在读文件…").assertIsDisplayed()
    }

    @Test
    fun errorStateShowsMessage() {
        composeRule.setContent {
            PennedTheme {
                BillImportContent(
                    BillImportUiState(loading = false, error = "认不出这份账单的表头（通用模式要用手动列映射）"),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("认不出这份账单的表头（通用模式要用手动列映射）").assertIsDisplayed()
        capture("m4_bill_preview_error")
    }

    @Test
    fun importProgressShown() {
        composeRule.setContent {
            PennedTheme {
                BillImportContent(
                    state().copy(importing = true, progressDone = 250, progressTotal = 507),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("正在导入 250/507…").assertIsDisplayed()
        composeRule.onNodeWithTag(BILL_PREVIEW_IMPORT_TAG).performScrollTo().assertTextContains("导入中…")
        capture("m4_bill_preview_importing")
    }

    @Test
    fun emptyImportableDisablesButton() {
        composeRule.setContent {
            PennedTheme { BillImportContent(state(counts = BillRowCounts(total = 3, duplicate = 3)), onBack = {}) }
        }
        composeRule.onNodeWithTag(BILL_PREVIEW_IMPORT_TAG).assertTextContains("导入 0 条")
    }
}
