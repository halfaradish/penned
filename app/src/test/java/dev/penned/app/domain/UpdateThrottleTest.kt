package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 更新检查节流与提示去重（规格 M2 S2）——纯口径，表驱动。 */
class UpdateThrottleTest {

    private val day = UpdateThrottle.MIN_INTERVAL_MILLIS

    @Test
    fun `shouldAutoCheck table`() {
        // now 相对基准 to lastCheckAt（null = 从未成功过） to 开关 to 期望
        val cases = listOf(
            Triple(0L, null, true) to true,          // 从未成功检查 → 要跑
            Triple(day - 1, 0L, true) to false,      // 差 1ms 不满一天 → 不跑
            Triple(day, 0L, true) to true,           // 正好满一天 → 跑
            Triple(day * 3, 0L, true) to true,       // 很久没检查 → 跑
            Triple(0L, null, false) to false,        // 开关关掉 → 不跑
            Triple(day * 3, 0L, false) to false,     // 开关关掉，再久也不跑
            Triple(0L, day, true) to false,          // lastCheckAt 在未来（改过系统时间）→ 不跑
        )
        cases.forEach { (input, expected) ->
            val (now, last, enabled) = input
            assertEquals(
                "now=$now last=$last enabled=$enabled",
                expected,
                UpdateThrottle.shouldAutoCheck(now, last, enabled),
            )
        }
    }

    @Test
    fun `shouldNotify only once per version`() {
        assertTrue(UpdateThrottle.shouldNotify(2, null))   // 还没提示过
        assertTrue(UpdateThrottle.shouldNotify(3, 2))      // 又出了新版本 → 可以再提示
        assertFalse(UpdateThrottle.shouldNotify(2, 2))     // 这个版本提示过了 → 不重复弹
    }
}
