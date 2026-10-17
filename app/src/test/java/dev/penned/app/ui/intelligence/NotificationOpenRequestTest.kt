package dev.penned.app.ui.intelligence

import android.content.Intent
import dev.penned.app.data.notify.AutoEntryService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 提醒点击的 deep link 标记（M4-8，裁决 C4）：intent 里带标记就置位，
 * 导航层**在解锁之后**才消费它（`PennedApp` 只在 LockGate 放行后组合，所以"先锁屏再补跳"由结构保证）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class NotificationOpenRequestTest {

    @Test
    fun `带标记的 intent 置位 消费后归位`() {
        NotificationOpenRequest.consume()
        assertEquals(false, NotificationOpenRequest.requested.value)

        NotificationOpenRequest.offerFrom(
            Intent().putExtra(AutoEntryService.EXTRA_OPEN_PENDING, true)
        )
        assertEquals(true, NotificationOpenRequest.requested.value)

        NotificationOpenRequest.consume()
        assertEquals(false, NotificationOpenRequest.requested.value)
    }

    @Test
    fun `不带标记与 null intent 都不影响`() {
        NotificationOpenRequest.consume()
        NotificationOpenRequest.offerFrom(Intent())
        assertEquals(false, NotificationOpenRequest.requested.value)
        NotificationOpenRequest.offerFrom(null)
        assertEquals(false, NotificationOpenRequest.requested.value)
    }
}
