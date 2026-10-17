package dev.penned.app.data.notify

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.domain.PendingKind
import dev.penned.app.domain.PendingStatus
import dev.penned.app.domain.PendingType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 通知监听的落库链（M4-8）：白名单过滤 → 解析 → **去重**（同 App + 同分钟 + 同原文只留一条）
 * → 落候选池 → 发本 App 的提醒（渠道 `auto_entry`）。
 *
 * 造通知用 `StatusBarNotification` 直接构造（不必依赖模拟机的真通知）；模拟机那一步走
 * `adb shell cmd notification post`（见 board 的走查记录）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class AutoEntryServiceTest {

    private val app: PennedApplication = ApplicationProvider.getApplicationContext()
    private val context: Context = app
    private val bookId = "book-notify"

    @Before
    fun setUp() = runBlocking {
        val now = System.currentTimeMillis()
        app.database.bookDao().upsert(
            BookEntity(id = bookId, name = "通知账本", sortOrder = 0, createdAt = now, updatedAt = now, deviceId = "t")
        )
        app.settings.setCurrentBook(bookId)
        app.settings.setAutoEntryEnabled(true)
        app.settings.setAutoEntryPackages("com.tencent.mm")
    }

    @After
    fun tearDown() = runBlocking {
        app.settings.setAutoEntryEnabled(false)
        app.settings.setAutoEntryPackages("")
    }

    private fun sbn(packageName: String, title: String, text: String): StatusBarNotification {
        val notification = Notification().apply {
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, title)
                putCharSequence(Notification.EXTRA_TEXT, text)
            }
        }
        return StatusBarNotification(
            packageName, packageName, 1, "tag", 0, 0, 0, notification,
            android.os.UserHandle.getUserHandleForUid(android.os.Process.myUid()), 0,
        )
    }

    private fun service(): AutoEntryService =
        Robolectric.buildService(AutoEntryService::class.java).create().get()

    @Test
    fun `白名单里的 App 落一条候选 并发出提醒`() = runBlocking {
        service().onNotificationPosted(sbn("com.tencent.mm", "微信支付", "微信支付 12.50 已支付"))
        // 落库走 IO 协程：等它一下（Robolectric 的 shadow looper 推不动 Dispatchers.IO，用真实等待）
        val deadline = System.currentTimeMillis() + 5_000
        var count = 0
        while (System.currentTimeMillis() < deadline) {
            count = app.database.pendingEntryDao().countPending()
            if (count > 0) break
            Thread.sleep(20)
        }
        assertEquals(1, count)

        val entry = app.database.pendingEntryDao().listByKind(PendingKind.NOTIFICATION).single()
        assertEquals("com.tencent.mm", entry.sourceApp)
        assertEquals(PendingType.EXPENSE, entry.type)
        assertEquals(1250L, entry.amountCents)
        assertEquals(PendingStatus.PENDING, entry.status)
        assertTrue(entry.rawText!!.contains("12.50"))

        // 本 App 的提醒（渠道 auto_entry）
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(manager).allNotifications
        assertEquals(1, posted.size)
        assertEquals("一笔账待确认", posted.first().extras.getString(Notification.EXTRA_TITLE))
        assertTrue(posted.first().extras.getString(Notification.EXTRA_TEXT)!!.contains("12.50"))
        assertTrue(posted.first().extras.getString(Notification.EXTRA_TEXT)!!.contains("支出"))
    }

    @Test
    fun `白名单外的 App 不看`() = runBlocking {
        service().onNotificationPosted(sbn("com.example.other", "某 App", "支付成功 12.50 元"))
        Thread.sleep(300)
        assertEquals(0, app.database.pendingEntryDao().countPending())
    }

    @Test
    fun `总开关关着就不处理`() = runBlocking {
        app.settings.setAutoEntryEnabled(false)
        service().onNotificationPosted(sbn("com.tencent.mm", "微信支付", "微信支付 12.50 已支付"))
        Thread.sleep(300)
        assertEquals(0, app.database.pendingEntryDao().countPending())
    }

    @Test
    fun `同一条通知重复回调只留一条候选`() = runBlocking {
        val service = service()
        val same = sbn("com.tencent.mm", "微信支付", "微信支付 12.50 已支付")
        service.onNotificationPosted(same)
        Thread.sleep(300)
        service.onNotificationPosted(same)
        Thread.sleep(300)
        service.onNotificationPosted(same)
        Thread.sleep(300)
        assertEquals(1, app.database.pendingEntryDao().countPending())
    }

    @Test
    fun `解析不出金额的通知不落库`() = runBlocking {
        service().onNotificationPosted(sbn("com.tencent.mm", "微信支付", "支付成功"))
        Thread.sleep(300)
        assertEquals(0, app.database.pendingEntryDao().countPending())
    }

    @Test
    fun `候选入账即标 ACCEPTED 并回填 txId`() = runBlocking {
        service().onNotificationPosted(sbn("com.tencent.mm", "微信支付", "微信支付 12.50 已支付"))
        // 落库在 IO 协程上：等它写进去（与上一条用例同一手法）
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && app.database.pendingEntryDao().countPending() == 0) {
            Thread.sleep(20)
        }
        val entry = app.database.pendingEntryDao().listByKind(PendingKind.NOTIFICATION).single()

        // 记一笔页保存回来（导航层做的那一步，两条来源共用）
        app.database.pendingEntryDao().markAccepted(entry.id, "tx-notify", System.currentTimeMillis())

        val saved = app.database.pendingEntryDao().getActive(entry.id)!!
        assertEquals(PendingStatus.ACCEPTED, saved.status)
        assertEquals("tx-notify", saved.txId)
        assertEquals("待确认里不再出现", 0, app.database.pendingEntryDao().countPending())
    }

    @Test
    fun `清空已处理是硬删（候选池唯一例外 D104）`() = runBlocking {
        service().onNotificationPosted(sbn("com.tencent.mm", "微信支付", "微信支付 12.50 已支付"))
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && app.database.pendingEntryDao().countPending() == 0) {
            Thread.sleep(20)
        }
        val entry = app.database.pendingEntryDao().listByKind(PendingKind.NOTIFICATION).single()
        app.database.pendingEntryDao().discard(entry.id, System.currentTimeMillis())

        val removed = app.database.pendingEntryDao().clearProcessed()
        assertEquals(1, removed)
        // 裸 SQL 也要走 IO（生产库没开 allowMainThreadQueries）
        val physical = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            app.database.query("SELECT COUNT(*) FROM pending_entries", null).use { cursor ->
                cursor.moveToFirst(); cursor.getInt(0)
            }
        }
        assertEquals(0, physical) // 真删掉了，不是 deleted = 1
    }
}
