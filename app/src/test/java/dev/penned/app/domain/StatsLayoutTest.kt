package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统计模块布局（M3-14）表驱动：顺序/开关的编解码（含缺段、认不出的段）、上移下移边界、全关不兜底。
 */
class StatsLayoutTest {

    @Test
    fun `默认：七个模块全开、顺序就是枚举顺序`() {
        val layout = StatsLayout()
        assertEquals(StatsModule.entries.toList(), layout.order)
        assertEquals(StatsModule.entries.toList(), layout.visibleModules)
        StatsModule.entries.forEach { assertTrue(it.name, layout.isEnabled(it)) }
    }

    @Test
    fun `顺序解码：认不出的段忽略、缺的模块按枚举顺序补在末尾`() {
        // 老版本存的串（少了后加的模块）也要读成一份完整顺序，不能把模块弄丢
        val decoded = StatsLayoutCodec.decodeOrder("DATA,SUMMARY,FOO")
        assertEquals(
            listOf(
                StatsModule.DATA, StatsModule.SUMMARY,
                StatsModule.TREND, StatsModule.DONUT,
                StatsModule.TAG_DONUT, StatsModule.TAG_DATA, StatsModule.ASSET_TREND,
            ),
            decoded,
        )
        assertEquals(StatsModule.entries.toList(), StatsLayoutCodec.decodeOrder(null))
        // 重复项只算一次
        assertEquals(StatsModule.entries.toList(), StatsLayoutCodec.decodeOrder("SUMMARY,SUMMARY"))
    }

    @Test
    fun `开关解码：没存过 = 全开；空串 = 全关（不兜底）`() {
        assertEquals(StatsModule.entries.toSet(), StatsLayoutCodec.decodeEnabled(null))
        assertTrue(StatsLayoutCodec.decodeEnabled("").isEmpty())
        assertEquals(
            setOf(StatsModule.SUMMARY, StatsModule.DONUT),
            StatsLayoutCodec.decodeEnabled("SUMMARY,DONUT,FOO"),
        )
    }

    @Test
    fun `编解码往返`() {
        val layout = StatsLayout(
            order = listOf(StatsModule.DATA, StatsModule.SUMMARY) + StatsModule.entries.filterNot {
                it == StatsModule.DATA || it == StatsModule.SUMMARY
            },
            enabled = setOf(StatsModule.SUMMARY, StatsModule.ASSET_TREND),
        )
        val back = StatsLayoutCodec.decode(
            StatsLayoutCodec.encodeOrder(layout.order),
            StatsLayoutCodec.encodeEnabled(layout.enabled),
        )
        assertEquals(layout, back)
    }

    @Test
    fun `上移下移：边界原样返回、中间交换相邻项`() {
        val layout = StatsLayout()
        assertEquals(layout, layout.moveUp(StatsModule.SUMMARY))
        assertEquals(layout, layout.moveDown(StatsModule.ASSET_TREND))

        val moved = layout.moveDown(StatsModule.SUMMARY)
        assertEquals(
            listOf(StatsModule.TREND, StatsModule.SUMMARY, StatsModule.DONUT),
            moved.order.take(3),
        )
        assertEquals(layout, moved.moveUp(StatsModule.SUMMARY))
    }

    @Test
    fun `visibleModules：只留开关打开的、按顺序排`() {
        val layout = StatsLayout(
            order = listOf(StatsModule.DATA, StatsModule.SUMMARY) + StatsModule.entries.filterNot {
                it == StatsModule.DATA || it == StatsModule.SUMMARY
            },
            enabled = setOf(StatsModule.DATA, StatsModule.SUMMARY),
        )
        assertEquals(listOf(StatsModule.DATA, StatsModule.SUMMARY), layout.visibleModules)
        assertFalse(layout.isEnabled(StatsModule.TREND))
    }
}
