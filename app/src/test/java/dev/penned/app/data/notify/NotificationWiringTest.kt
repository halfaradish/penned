package dev.penned.app.data.notify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.ui.intelligence.shouldAskNotificationPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M4-8 的接线（在设备上"看不见摸不着"的那半边）：
 * ① 监听的 service 真被系统按通知使用权列表收进去（action + 权限 + 显示名）；
 * ② `POST_NOTIFICATIONS` 真在清单里（33+ 才有意义，26~32 装包时不申请）；
 * ③ 开自动记账时申请通知权限的判定（表驱动）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class NotificationWiringTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `监听服务在系统通知使用权列表里 且带正确的权限与显示名`() {
        val services = context.packageManager.queryIntentServices(
            Intent("android.service.notification.NotificationListenerService"),
            PackageManager.GET_META_DATA,
        )
        val mine = services.firstOrNull {
            it.serviceInfo?.name == AutoEntryService::class.java.name
        }
        assertNotNull("系统要能按 action 找到我们的监听服务", mine)
        assertEquals(
            "BIND_NOTIFICATION_LISTENER_SERVICE 由系统持有",
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
            mine!!.serviceInfo.permission,
        )
        assertEquals("一笔一记 · 自动记账", mine.serviceInfo.loadLabel(context.packageManager).toString())
        assertTrue("系统按需绑定 → 必须导出", mine.serviceInfo.exported)
    }

    @Test
    fun `通知权限在清单里声明`() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val declared = info.requestedPermissions?.toList().orEmpty()
        assertTrue(declared.contains("android.permission.POST_NOTIFICATIONS"))
        // 只读通知，不碰短信/无障碍/存储
        assertFalse(declared.any { it.contains("READ_SMS") || it.contains("ACCESSIBILITY") })
    }

    @Test
    fun `通知使用权判定认的是本机的组件`() {
        val me = ComponentName(context, AutoEntryService::class.java)
        assertTrue(
            "把本机组件写进 enabled_notification_listeners 就算已授权",
            me.flattenToString().contains(context.packageName),
        )
        val app = context as PennedApplication
        assertEquals(false, dev.penned.app.ui.intelligence.NotificationAccess.isGranted(app))
    }

    @Test
    fun `33 以上没授权才申请`() {
        assertTrue(shouldAskNotificationPermission(33, granted = false))
        assertTrue(shouldAskNotificationPermission(34, granted = false))
        assertFalse("已授权不再打扰", shouldAskNotificationPermission(33, granted = true))
        assertFalse("32 及以下没有这个权限", shouldAskNotificationPermission(32, granted = false))
        assertFalse(shouldAskNotificationPermission(26, granted = false))
    }
}
