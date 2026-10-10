package dev.penned.app.ui.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.penned.app.data.db.dao.TxWithCategory
import dev.penned.app.domain.TxType
import dev.penned.app.ui.theme.PennedTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 月卡折叠/展开时，**流水行必须始终跟着日头条走**，不许自己再跑一条位移动画（UI-14）。
 *
 * 缺陷原样：月卡头部是 `AnimatedVisibility`（高度逐帧变），行若同时带 `animateItem()` 的位移弹簧，
 * 就是在追一个每帧都在动的目标——实测展开中途行顶比日头条底高 24dp（钻进 28dp 高的日头条里）、
 * 收起中途甩出 32dp 空档，约 400ms 才归位；一屏几张卡时看着就是"条目互相压盖、有的到位有的没有"。
 * 日头条（`stickyHeader`）身上没有动画、直接落位，所以这个差一旦变，就是行自己脱开了。
 *
 * 测法：停表逐帧量"行顶 − 上方日头条底"，要求它**等于静止态的基准**（只允许 1dp 抖动），
 * 同时要求日头条在这段时间里确实移动过（否则这条测试什么都没测到）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeMonthToggleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.systemDefault()

    private fun row(id: String, date: LocalDate, name: String) = TxWithCategory(
        id = id, type = TxType.EXPENSE, amountCents = 1000L, accountId = null, categoryId = null,
        dateEpochMinute = date.atTime(12, 0).atZone(zone).toEpochSecond() / 60, note = null,
        categoryName = name, categoryParentName = "食品餐饮", categoryIconKey = null,
        targetAccountName = null, excludeFromStats = false,
    )

    @Test
    fun `行跟日头条全程不脱节`() {
        val today = LocalDate.now(zone)
        val firstOfMonth = today.withDayOfMonth(1)
        val prevDay = firstOfMonth.minusMonths(1)
        composeRule.setContent {
            PennedTheme {
                HomeContent(
                    bookName = "日常账本",
                    rows = listOf(
                        row("cur", today, "本月行"),
                        row("prev", prevDay, "上月行"),
                        row("old", firstOfMonth.minusMonths(2), "更早的行"),
                    ),
                    onAdd = {},
                )
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeByFrame()

        val prevLabel = "${prevDay.year}/${prevDay.monthValue}"
        val bandLabel = HomeGroups.dayLabel(prevDay.toEpochDay(), zone)
        val rowTitle = "食品餐饮 · 上月行"

        fun bandBottom() = composeRule.onNodeWithText(bandLabel, substring = true)
            .getUnclippedBoundsInRoot().bottom.value
        fun titleBottom() = composeRule.onNodeWithText(prevLabel).getUnclippedBoundsInRoot().bottom.value
        fun rowTop() = composeRule.onNodeWithText(rowTitle, substring = true)
            .getUnclippedBoundsInRoot().top.value

        val baseline = rowTop() - bandBottom()
        val offsets = mutableListOf<Float>()
        val bands = mutableListOf<Float>()
        val titles = mutableListOf<Float>()

        fun sample() {
            offsets += rowTop() - bandBottom()
            bands += bandBottom()
            titles += titleBottom()
        }

        // 展开 → 收起，两程都逐帧量
        composeRule.onNodeWithText(prevLabel).performClick()
        repeat(14) {
            composeRule.mainClock.advanceTimeBy(50)
            sample()
        }
        composeRule.onNodeWithText(prevLabel).performClick()
        repeat(14) {
            composeRule.mainClock.advanceTimeBy(50)
            sample()
        }
        composeRule.mainClock.advanceTimeBy(2000)
        sample()

        // 先证明"确实动过"：日头条在过程里移动了 40dp 以上，否则这条测试是空转
        val bandMin = bands.minOrNull() ?: 0f
        val bandMax = bands.maxOrNull() ?: 0f
        assertTrue("日头条全程没动（$bandMin..$bandMax），这条测试没测到东西", bandMax - bandMin > 40f)

        offsets.forEachIndexed { index, offset ->
            assertEquals(
                "第 $index 帧：行与日头条脱节了（基准 $baseline，全部 $offsets）",
                baseline,
                offset,
                1f,
            )
        }
        // 顺带钉住截图里另一种穿帮：行别爬到月标题上面去
        offsets.forEachIndexed { index, offset ->
            val rowTopAtFrame = offset + bands[index]
            assertTrue(
                "第 $index 帧：行顶 $rowTopAtFrame 钻到月标题底 ${titles[index]} 上面去了",
                rowTopAtFrame >= titles[index] - 0.5f,
            )
        }
    }
}
