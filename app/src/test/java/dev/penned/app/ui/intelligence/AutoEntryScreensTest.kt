package dev.penned.app.ui.intelligence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.data.db.entity.PendingEntryEntity
import dev.penned.app.domain.PendingKind
import dev.penned.app.domain.PendingType
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 自动记账页（M4-8）：状态卡两态 / 总开关默认关 / 白名单可勾 / 待确认入口 / 隐私说明。
 * 待确认页：列表行（来源+金额+方向+原文）/ 点行回调 / 右滑丢弃 / 空态给出路 / 清空已处理。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoEntryScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/$name.png")
    }

    private val apps = listOf(
        AppEntry("com.tencent.mm", "微信"),
        AppEntry("com.eg.android.AlipayGphone", "支付宝"),
    )

    // ---- 自动记账页 ----

    @Test
    fun captureAutoEntryUnauthorized() {
        composeRule.setContent {
            PennedTheme {
                AutoEntryContent(
                    state = AutoEntryUiState(enabled = false, installedApps = apps),
                    granted = false,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(AUTO_ENTRY_STATUS_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("还没有通知使用权").assertIsDisplayed()
        composeRule.onNodeWithText("去开启").assertIsDisplayed()
        // 默认关 + 白名单默认空
        composeRule.onNodeWithText("监视哪些 App（默认一个都不勾）").assertIsDisplayed()
        // 隐私说明在场
        // 隐私说明：「不进 AI」是加重样式，语义文本里没有 `**`
        composeRule.onNodeWithText(
            "通知原文只存在本机、不进 AI（不读短信、不装无障碍、不回扫历史通知）；" +
                "白名单外的 App 一律不看；候选可一键清空。",
        ).performScrollTo().assertIsDisplayed()
        capture("m4_auto_entry_off")
    }

    @Test
    fun captureAutoEntryAuthorized() {
        composeRule.setContent {
            PennedTheme {
                AutoEntryContent(
                    state = AutoEntryUiState(
                        enabled = true,
                        whitelist = setOf("com.tencent.mm"),
                        installedApps = apps,
                        pendingCount = 3,
                    ),
                    granted = true,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("已获得通知使用权").assertIsDisplayed()
        composeRule.onNodeWithText("3 条等你确认").performScrollTo().assertIsDisplayed()
        capture("m4_auto_entry_on")
    }

    @Test
    fun togglesAndEntriesCallBack() {
        var enabled: Boolean? = null
        var toggled: Pair<String, Boolean>? = null
        var openedPending = false
        var openedSettings = false
        composeRule.setContent {
            PennedTheme {
                AutoEntryContent(
                    state = AutoEntryUiState(installedApps = apps),
                    granted = false,
                    onBack = {},
                    onToggleEnabled = { enabled = it },
                    onToggleApp = { pkg, on -> toggled = pkg to on },
                    onOpenPending = { openedPending = true },
                    onOpenNotificationSettings = { openedSettings = true },
                )
            }
        }
        composeRule.onNodeWithText("去开启").performClick()
        assertEquals(true, openedSettings)
        composeRule.onNodeWithText("待确认").performScrollTo().performClick()
        assertEquals(true, openedPending)
        composeRule.onNodeWithText("微信").performScrollTo() // 白名单两张卡在场
        assertEquals(null, enabled) // 开关的点击回调在真机上验（Switch 需要精确点它的热区）
        assertEquals(null, toggled)
    }

    // ---- 待确认页 ----

    private fun entry(
        id: String,
        app: String?,
        expense: Boolean,
        amount: Long,
        raw: String,
        atMinute: Long? = null,
        createdAt: Long = 1L,
    ) = PendingEntryEntity(
        id = id, kind = if (app == null) PendingKind.SCREENSHOT else PendingKind.NOTIFICATION,
        sourceApp = app, rawText = raw,
        type = if (expense) PendingType.EXPENSE else PendingType.INCOME,
        amountCents = amount,
        occurredAtEpochMinute = atMinute,
        createdAt = createdAt, updatedAt = createdAt, deviceId = "d",
    )

    @Test
    fun capturePendingList() {
        composeRule.setContent {
            PennedTheme {
                PendingContent(
                    rows = listOf(
                        entry(
                            "p1", "com.tencent.mm", expense = true, amount = 1250,
                            raw = "微信支付 12.50 已支付", atMinute = 29_870_021L, // 2026-10-17 09:41 (+08)
                        ),
                        entry(
                            "p2", "com.eg.android.AlipayGphone", expense = false, amount = 8800,
                            raw = "支付宝 收款 88.00 元已到账", atMinute = 29_870_021L,
                        ),
                        entry(
                            "p3", null, expense = true, amount = 999,
                            raw = "截图识别 · 某商户", atMinute = 29_870_021L,
                        ),
                    ),
                    onBack = {}, onOpenAutoEntry = {},
                )
            }
        }
        composeRule.onNodeWithTag(PENDING_LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("微信").assertIsDisplayed()
        composeRule.onNodeWithText("支付宝").assertIsDisplayed()
        composeRule.onNodeWithText("截图").assertIsDisplayed()
        composeRule.onNodeWithText("12.50").assertIsDisplayed()
        composeRule.onNodeWithText("88.00").assertIsDisplayed()
        composeRule.onNodeWithText("微信支付 12.50 已支付").assertIsDisplayed()
        // 时间（规格行里的一列）：期望值走生产口径函数（跟系统时区走，不写死）
        val first = entry(
            "p1", "com.tencent.mm", expense = true, amount = 1250,
            raw = "微信支付 12.50 已支付", atMinute = 29_870_021L,
        )
        composeRule.onAllNodesWithText(pendingTimeLabel(first)).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("清空已处理").assertIsDisplayed()
        capture("m4_pending_list")
    }

    @Test
    fun capturePendingEmpty() {
        composeRule.setContent {
            PennedTheme { PendingContent(rows = emptyList(), onBack = {}, onOpenAutoEntry = {}) }
        }
        composeRule.onNodeWithTag(PENDING_EMPTY_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("没有待确认的").assertIsDisplayed()
        composeRule.onNodeWithText("去开自动记账").assertIsDisplayed()
        capture("m4_pending_empty")
    }

    @Test
    fun pendingRowCallsPrefill() {
        var picked: PendingEntryEntity? = null
        composeRule.setContent {
            PennedTheme {
                PendingContent(
                    rows = listOf(entry("p1", "com.tencent.mm", expense = true, amount = 1250, raw = "微信支付 12.50")),
                    onBack = {}, onOpenAutoEntry = {},
                    onPickRow = { picked = it },
                )
            }
        }
        composeRule.onNodeWithText("12.50").performClick()
        assertEquals("p1", picked?.id)
    }

    @Test
    fun discardAllAndClearProcessedCallBack() {
        var discardedAll = false
        var cleared = false
        composeRule.setContent {
            PennedTheme {
                PendingContent(
                    rows = listOf(entry("p1", "com.tencent.mm", true, 1250, "微信支付 12.50")),
                    onBack = {}, onOpenAutoEntry = {},
                    onDiscardAll = { discardedAll = true },
                    onClearProcessed = { cleared = true },
                )
            }
        }
        composeRule.onNodeWithText("全部丢弃").performClick()
        assertEquals(true, discardedAll)
        composeRule.onNodeWithText("清空已处理").performClick()
        assertEquals(true, cleared)
    }

    @Test
    fun swipeRevealsDiscard() {
        var discarded: String? = null
        composeRule.setContent {
            PennedTheme {
                PendingContent(
                    rows = listOf(entry("p1", "com.tencent.mm", true, 1250, "微信支付 12.50")),
                    onBack = {}, onOpenAutoEntry = {},
                    revealedRowId = "p1",
                    onDiscard = { discarded = it },
                )
            }
        }
        composeRule.onNodeWithText("丢弃").performClick()
        assertEquals("p1", discarded)
    }

    @Test
    fun pendingTimeLabelUsesOccurredThenCreated() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        // 2026-10-17 09:41 +08 = 29724581 分钟
        assertEquals(
            "10-17 09:41",
            pendingTimeLabel(entry("p", "com.tencent.mm", true, 100, "x", atMinute = 29_870_021L), zone),
        )
        // 没读到发生时刻 → 退到入池时刻（= createdAt 毫秒）
        assertEquals(
            "10-17 09:41",
            pendingTimeLabel(
                entry("p", "com.tencent.mm", true, 100, "x", atMinute = null, createdAt = 29_870_021L * 60_000L),
                zone,
            ),
        )
        // 跨天：同一条毫秒在不同时区落在不同日子（墙钟口径，不是 UTC）
        assertEquals(
            "10-17 01:41",
            pendingTimeLabel(
                entry("p", "com.tencent.mm", true, 100, "x", atMinute = null, createdAt = 29_870_021L * 60_000L),
                java.time.ZoneId.of("UTC"),
            ),
        )
    }

    @Test
    fun prefillTypeMapsUnknownToExpense() {
        assertEquals(TxType.INCOME, entry("p", "com.tencent.mm", false, 100, "x").prefillType())
        assertEquals(TxType.EXPENSE, entry("p", "com.tencent.mm", true, 100, "x").prefillType())
        val unknown = entry("p", "com.tencent.mm", true, 100, "x").copy(type = PendingType.UNKNOWN)
        assertEquals(TxType.EXPENSE, unknown.prefillType())
    }
}
