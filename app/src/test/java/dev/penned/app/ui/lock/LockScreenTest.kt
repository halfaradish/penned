package dev.penned.app.ui.lock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.domain.PinHasher
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LockScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `PinState verifies at full length`() {
        val stored = PinHasher.encode("2506", "salt")
        var unlocked = false
        val state = PinState { s, pin ->
            if (PinHasher.verify(pin, stored)) unlocked = true else s.fail("密码不正确，请重试")
        }
        "2506".forEach { state.onDigit(it) }
        assertTrue(unlocked)
        assertEquals(null, state.error)
    }

    @Test
    fun `PinState wrong pin clears and shows error`() {
        val stored = PinHasher.encode("2506", "salt")
        val state = PinState { s, pin ->
            if (PinHasher.verify(pin, stored)) Unit else s.fail("密码不正确，请重试")
        }
        "9999".forEach { state.onDigit(it) }
        assertEquals("密码不正确，请重试", state.error)
        assertEquals("", state.pin)
    }

    @Test
    fun `PinState ignores extra digits and non-digits`() {
        val state = PinState { _, _ -> }
        "12".forEach { state.onDigit(it) }
        state.onDigit('x')
        "3".forEach { state.onDigit(it) }
        "456".forEach { state.onDigit(it) } // 第 5、6 位应被忽略
        assertEquals("1234", state.pin)
    }

    @Test
    fun captureLockScreen() {
        val stored = PinHasher.encode("1234", "salt")
        composeRule.setContent {
            PennedTheme { LockScreen(storedHash = stored, onUnlock = {}) }
        }
        composeRule.onNodeWithText("一笔一记已锁定").assertIsDisplayed()
        composeRule.onNodeWithText("8").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/m1_lock_screen.png")
    }
}
