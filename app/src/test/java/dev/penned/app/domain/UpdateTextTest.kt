package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/** 更新页文案口径（规格 M2 S6）——表驱动。 */
class UpdateTextTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `check time label table`() {
        // epochMillis to 期望（Asia/Shanghai）
        val cases = listOf(
            // 2026-10-10T21:30+08:00
            1_791_639_000_000L to "10月10日 21:30",
            // 2026-01-01T00:05+08:00（跨年、补零）
            1_767_197_100_000L to "1月1日 00:05",
            // 2026-12-31T23:59+08:00
            1_798_732_740_000L to "12月31日 23:59",
        )
        cases.forEach { (millis, expected) ->
            assertEquals("millis=$millis", expected, UpdateText.checkTimeLabel(millis, zone))
        }
    }

    @Test
    fun `progress percent table`() {
        assertEquals(0, UpdateText.progressPercent(0, 1000))
        assertEquals(42, UpdateText.progressPercent(420, 1000))
        assertEquals(100, UpdateText.progressPercent(1000, 1000))
        assertEquals(0, UpdateText.progressPercent(5, 0))     // 总长未知 → 0，别崩也别 NaN
        assertEquals(100, UpdateText.progressPercent(2000, 1000)) // 超出也夹在 100
    }

    @Test
    fun `size label table`() {
        assertEquals("0.0 MB", UpdateText.sizeLabel(0))
        assertEquals("1.0 MB", UpdateText.sizeLabel(1024L * 1024L))
        assertEquals("8.1 MB", UpdateText.sizeLabel(8_500_000L))
        assertEquals("0.5 MB", UpdateText.sizeLabel(524_288L))
    }
}
