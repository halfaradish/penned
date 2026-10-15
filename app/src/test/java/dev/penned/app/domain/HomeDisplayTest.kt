package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页显示项（M3-13）表驱动：编解码的"没存过 vs 全关"语义、顺序固定、兜底只留「支出」。
 */
class HomeDisplayTest {

    @Test
    fun `没存过 = 全开；空串 = 用户全关（靠兜底渲染）`() {
        assertEquals(HomeStatItem.entries.toSet(), HomeDisplay.decode(null).items)
        assertTrue(HomeDisplay.decode("").items.isEmpty())
    }

    @Test
    fun `编解码往返：勾选项原样回来，顺序按枚举固定`() {
        val cases = listOf(
            HomeStatItem.entries.toSet(),
            setOf(HomeStatItem.EXPENSE),
            setOf(HomeStatItem.INCOME, HomeStatItem.BALANCE),
            setOf(HomeStatItem.BUDGET_PROGRESS, HomeStatItem.EXPENSE),
            emptySet(),
        )
        cases.forEach { items ->
            val encoded = HomeDisplay.encode(items)
            assertEquals("往返：$items", items, HomeDisplay.decode(encoded).items)
        }
        // 顺序固定：支出 → 收入 → 结余 → 预算进度（与月卡里的排列一致）
        assertEquals(
            "EXPENSE,BALANCE,BUDGET_PROGRESS",
            HomeDisplay.encode(setOf(HomeStatItem.BUDGET_PROGRESS, HomeStatItem.BALANCE, HomeStatItem.EXPENSE)),
        )
    }

    @Test
    fun `认不出的段忽略（老版本写进去的值不会炸）`() {
        val decoded = HomeDisplay.decode("EXPENSE,FOO, ,INCOME")
        assertEquals(setOf(HomeStatItem.EXPENSE, HomeStatItem.INCOME), decoded.items)
    }

    @Test
    fun `全不勾：兜底只渲染「支出」`() {
        val none = HomeDisplaySettings(items = emptySet())
        assertEquals(listOf(HomeStatItem.EXPENSE), none.visibleItems)
        assertTrue(none.isVisible(HomeStatItem.EXPENSE))
        assertFalse(none.isVisible(HomeStatItem.INCOME))
        assertFalse(none.isVisible(HomeStatItem.BUDGET_PROGRESS))
    }

    @Test
    fun `部分勾选：visibleItems 按固定顺序、勾什么渲什么`() {
        val settings = HomeDisplaySettings(items = setOf(HomeStatItem.BALANCE, HomeStatItem.INCOME))
        assertEquals(listOf(HomeStatItem.INCOME, HomeStatItem.BALANCE), settings.visibleItems)
        assertFalse(settings.isVisible(HomeStatItem.EXPENSE))
    }

    @Test
    fun `全关后再勾一项：兜底的支出先落进集合`() {
        val none = HomeDisplaySettings(items = emptySet())
        // 全关时「支出」的开关是开着的（兜底渲染）——再勾「收入」不能让支出消失
        val withIncome = none.toggle(HomeStatItem.INCOME, true)
        assertEquals(setOf(HomeStatItem.EXPENSE, HomeStatItem.INCOME), withIncome.items)
        assertEquals(listOf(HomeStatItem.EXPENSE, HomeStatItem.INCOME), withIncome.visibleItems)
        // 反过来：从全关状态"关掉"兜底那一项 = 还是全关（点了没反应，但不会崩）
        assertEquals(emptySet<HomeStatItem>(), none.toggle(HomeStatItem.EXPENSE, false).items)
    }

    @Test
    fun `toggle 加一项或减一项，其它不动`() {
        val start = HomeDisplaySettings()
        val off = start.toggle(HomeStatItem.INCOME, false)
        assertFalse(off.isVisible(HomeStatItem.INCOME))
        assertTrue(off.isVisible(HomeStatItem.EXPENSE))
        val back = off.toggle(HomeStatItem.INCOME, true)
        assertEquals(HomeStatItem.entries.toSet(), back.items)
    }
}
