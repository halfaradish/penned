package dev.penned.app.ui.stats

import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.domain.StatsPeriod
import dev.penned.app.domain.StatsPeriods
import dev.penned.app.domain.StatsView
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 四个视图各记各的位置（AN 轮）：用户报的 bug 是"从年统计切回月统计会跳到 12 月"，
 * 这里直接对着状态断言（不收集 state，省掉协程与库依赖）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class StatsViewModelTest {

    private val app: PennedApplication = ApplicationProvider.getApplicationContext()

    private fun periodOf(vm: StatsViewModel, view: StatsView) = vm.periods.value.getValue(view)

    @Test
    fun `每_个视图各记各的位置_切回来不会跳月`() {
        val vm = StatsViewModel(app)
        // 月统计先挑一个老月份
        vm.selectMonth(YearMonth.of(2025, 3))
        assertEquals(StatsPeriod.Month(YearMonth.of(2025, 3)), periodOf(vm, StatsView.MONTH))

        // 首次进年统计：锚在当期最后一天（2025-03-31 → 2025 年），再自己挑 2024
        vm.selectView(StatsView.YEAR)
        assertEquals(StatsPeriod.Year(2025), periodOf(vm, StatsView.YEAR))
        vm.selectYear(2024)

        // 切回月统计：还是 2025-03（不是被年统计锚成 12 月）
        vm.selectView(StatsView.MONTH)
        assertEquals(StatsPeriod.Month(YearMonth.of(2025, 3)), periodOf(vm, StatsView.MONTH))

        // 再切回年统计：还是 2024
        vm.selectView(StatsView.YEAR)
        assertEquals(StatsPeriod.Year(2024), periodOf(vm, StatsView.YEAR))
    }

    @Test
    fun `首次进自定义_装的是正在看的那一段_之后记住用户改的区间`() {
        val vm = StatsViewModel(app)
        vm.selectMonth(YearMonth.of(2025, 3))
        vm.selectView(StatsView.CUSTOM)
        // 首次：2025 年 3 月整月，而不是"12月1日–12月31日"
        assertEquals(
            StatsPeriods.custom(
                LocalDate.of(2025, 3, 1).toEpochDay(),
                LocalDate.of(2025, 3, 31).toEpochDay(),
            ),
            periodOf(vm, StatsView.CUSTOM),
        )

        // 用户改成 1/5–2/20 → 切走再切回来仍是它
        vm.setCustomRange(LocalDate.of(2025, 1, 5).toEpochDay(), LocalDate.of(2025, 2, 20).toEpochDay())
        vm.selectView(StatsView.MONTH)
        vm.selectView(StatsView.CUSTOM)
        assertEquals(
            StatsPeriods.custom(
                LocalDate.of(2025, 1, 5).toEpochDay(),
                LocalDate.of(2025, 2, 20).toEpochDay(),
            ),
            periodOf(vm, StatsView.CUSTOM),
        )
    }

    @Test
    fun `近七日固定_支出收入选择切视图后保留`() {
        val vm = StatsViewModel(app)
        vm.selectView(StatsView.LAST_7_DAYS)
        assertEquals(StatsPeriod.Last7Days, periodOf(vm, StatsView.LAST_7_DAYS))

        vm.selectType(dev.penned.app.domain.TxType.INCOME)
        vm.selectView(StatsView.MONTH)
        assertEquals(dev.penned.app.domain.TxType.INCOME, vm.type.value)
    }

    @Test
    fun `默认停在月统计_看的是本月`() {
        val vm = StatsViewModel(app)
        assertEquals(StatsView.MONTH, vm.view.value)
        assertEquals(StatsPeriod.Month(YearMonth.now()), periodOf(vm, StatsView.MONTH))
    }
}
