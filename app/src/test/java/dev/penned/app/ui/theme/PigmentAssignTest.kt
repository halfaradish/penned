package dev.penned.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一张图里的颜料分配（AO 轮起，AR 轮换盘）：用户反馈"连续三种一样的颜色"——根因是各分类按名字哈希落槽，
 * 暖色槽会在同一张图里撞上。这里锁住"同图不撞色"的规则。
 *
 * AR 轮（D043）换了八色，分配算法也从"贪心 + 爬山"改成"不撞色不动、撞色才穷举求最优"——
 * 旧盘五分类爬山只能到 36~40°，新盘能到最优的 46°。
 */
class PigmentAssignTest {

    /** 各槽的色相角，与 [PennedPigment] 里的表一致（测试里独立抄一份来做校验）。 */
    private val hues = listOf(341f, 17f, 157f, 193f, 267f, 233f, 295f, 40f)

    private fun hueGap(a: Int, b: Int): Float {
        val diff = kotlin.math.abs(hues[a] - hues[b])
        return kotlin.math.min(diff, 360f - diff)
    }

    @Test
    fun `同一张图里两两不撞色`() {
        // 用用户那屏的真实分类
        val names = listOf("出行交通", "休闲娱乐", "购物消费", "食品餐饮")
        val slots = PennedPigment.assignSlots(names)

        assertEquals(names.toSet(), slots.keys)
        // 两两色相差 ≥ 阈值（就是"看不出区别"的那条线）
        slots.values.forEach { a ->
            slots.values.forEach { b ->
                if (a < b) assertTrue("slot $a 与 $b 撞色", hueGap(a, b) >= PennedPigment.MIN_HUE_GAP)
            }
        }
        // 相邻两行（图例顺序）必须看得出区别
        names.zipWithNext().forEach { (a, b) ->
            val gap = hueGap(slots.getValue(a), slots.getValue(b))
            assertTrue("$a 与 $b 只差 $gap 度", gap >= PennedPigment.MIN_HUE_GAP)
        }
    }

    @Test
    fun `不撞色时保持名字哈希的稳定色`() {
        // 单独一个分类：就用它自己的哈希槽（跨页/跨期稳定）
        val name = "食品餐饮"
        assertEquals(PennedPigment.slotOf(name), PennedPigment.assignSlots(listOf(name)).getValue(name))
        // 名字顺序变了也不影响"先到先用"
        val a = PennedPigment.assignSlots(listOf("食品餐饮", "购物消费"))
        val b = PennedPigment.assignSlots(listOf("购物消费"))
        assertEquals(b.getValue("购物消费"), a.getValue("购物消费"))
    }

    @Test
    fun `五个分类也掰得开（撞色时穷举求最优）`() {
        // 分类详细页的真实集合：五个名字里有两个哈希到同一槽，撞色后走穷举——
        // 新盘的最优是 46°（旧盘爬山只能到 36~40°）
        val names = listOf("晚餐", "午餐", "休闲零食", "早餐", "食品餐饮")
        val slots = PennedPigment.assignSlots(names)
        val values = slots.values.toList()
        var min = 360f
        for (i in values.indices) {
            for (j in i + 1 until values.size) {
                min = kotlin.math.min(min, hueGap(values[i], values[j]))
            }
        }
        assertTrue("最小色相差只有 $min 度", min >= PennedPigment.MIN_HUE_GAP)
    }

    @Test
    fun `八个分类也拿得到八个不同槽`() {
        val names = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val slots = PennedPigment.assignSlots(names)
        assertEquals(8, slots.size)
        assertEquals(8, slots.values.toSet().size)
    }

    @Test
    fun `六个彩色分类拿到这套盘的最优 36 度`() {
        // 统计页最常见的同屏集合（六个真实分类；D049 起不再有尾巴合并的第七行）。
        // 八槽在 45° 线下最多支持 5 色（见界面规范第四节的极限记录），
        // 第 6 色退到 36° —— 这是**最优解**，不是算法没尽力：撞色时穷举了全部 P(8,6) 个排列。
        val names = listOf("食品餐饮", "购物消费", "出行交通", "休闲娱乐", "文化教育", "居家生活")
        val slots = PennedPigment.assignSlots(names)
        val values = slots.values.toList()
        var min = 360f
        for (i in values.indices) {
            for (j in i + 1 until values.size) {
                min = kotlin.math.min(min, hueGap(values[i], values[j]))
            }
        }
        assertEquals(36f, min, 0.01f)
        assertEquals(6, values.toSet().size)
    }

    @Test
    fun `中性名字不参与分配`() {
        // 中性的只有转账与未分类（D049：统计页不再有「其他」合并桶，「其他」是真实分类）
        val slots = PennedPigment.assignSlots(listOf("食品餐饮", "其他", "未分类", "转账"))
        assertEquals(-1, slots.getValue("未分类"))
        assertEquals(-1, slots.getValue("转账"))
        assertNotEquals(-1, slots.getValue("食品餐饮"))
        assertNotEquals(-1, slots.getValue("其他"))
    }

    @Test
    fun `「其他」按名字哈希取彩色`() {
        // D049 前它被"合并桶同名"误判成中性灰；现在它是普通分类，落 0 号玫红（见 hue 注释）
        assertEquals(0, PennedPigment.slotOf("其他"))
    }

    @Test
    fun `种子八个一级分类同屏也各占一槽`() {
        // D049 起统计页全列真实分类，一屏最多可能同时出现种子的 8 个一级分类（+ 中性的「未分类」）
        val names = listOf("食品餐饮", "购物消费", "出行交通", "文化教育", "休闲娱乐", "居家生活", "健康医疗", "其他")
        val slots = PennedPigment.assignSlots(names + "未分类")
        assertEquals(-1, slots.getValue("未分类"))
        assertEquals(8, names.map { slots.getValue(it) }.toSet().size)
    }

    @Test
    fun `结果确定可复现`() {
        val names = listOf("出行交通", "休闲娱乐", "购物消费", "食品餐饮", "其他")
        assertEquals(PennedPigment.assignSlots(names), PennedPigment.assignSlots(names))
        // 重复名字只算一次
        assertEquals(
            PennedPigment.assignSlots(listOf("食品餐饮", "购物消费")),
            PennedPigment.assignSlots(listOf("食品餐饮", "食品餐饮", "购物消费")),
        )
    }
}
