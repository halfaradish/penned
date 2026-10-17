package dev.penned.app.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import dev.penned.app.MainActivity
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.PendingEntryEntity
import dev.penned.app.domain.KeywordRules
import dev.penned.app.domain.NotifyParse
import dev.penned.app.domain.PendingKind
import dev.penned.app.domain.PendingStatus
import dev.penned.app.domain.PendingType
import dev.penned.app.domain.RuleTargetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 自动记账的通知监听（M4-8）：系统按需绑定，**随时可在系统设置里撤销授权**。
 *
 * 边界（规格 §二·3 / §三 M4-8）：
 * - **只处理白名单里的 App**（默认空 = 什么都不处理）；**只处理服务启用后新到的**通知（不回扫历史）
 * - 解析不出金额/方向 → **直接丢弃**（宁可漏，不可错记）；候选池落库只为"杀进程不丢"
 * - **原文只在本机、不进 LLM、不读短信、不装无障碍**
 * - 去重：`NotificationListenerService` 会重复回调同一条通知 → `(sourceApp + 同分钟 + rawText)` 查重
 */
class AutoEntryService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val packageName = sbn.packageName ?: return
        if (packageName == applicationContext.packageName) return // 自己发的提醒不算
        val app = applicationContext as? PennedApplication ?: return
        // 文案来源：标题 + 正文 + **大文本**（`cmd notification post -S bigtext` 与很多支付通知
        // 都把内容放在 EXTRA_BIG_TEXT 里，只读 EXTRA_TEXT 会读不到金额 → 被当成解析不出而丢弃）
        val extras = notification.extras
        fun extra(key: String): String? = extras?.getCharSequence(key)?.toString()?.trim()?.ifEmpty { null }
        val title = extra(android.app.Notification.EXTRA_TITLE)
        val text = listOfNotNull(
            extra(android.app.Notification.EXTRA_TEXT),
            extra(android.app.Notification.EXTRA_BIG_TEXT),
            extra(android.app.Notification.EXTRA_SUB_TEXT),
            extra(android.app.Notification.EXTRA_SUMMARY_TEXT),
        ).distinct().joinToString(" ").ifEmpty { null }

        scope.launch {
            if (!app.settings.autoEntryEnabled.first()) return@launch
            val whitelist = NotifyParse.whitelist(app.settings.autoEntryPackages.first())
            if (packageName !in whitelist) return@launch

            val draft = NotifyParse.parse(packageName, title, text) ?: return@launch
            val minute = System.currentTimeMillis() / 60_000

            // 去重：同 App + 同一分钟 + 同一段原文 → 只留一条
            val pending = app.database.pendingEntryDao().listByKind(PendingKind.NOTIFICATION)
            val duplicated = pending.any {
                it.sourceApp == packageName && it.rawText == draft.rawText &&
                    it.createdAt / 60_000 == minute
            }
            if (duplicated) return@launch

            val bookId = app.settings.currentBookId.first()
            val rules = bookId?.let { app.database.keywordRuleDao().allInBook(it) } ?: emptyList()
            val hit = KeywordRules.match(draft.rawText, rules)
            val now = System.currentTimeMillis()
            val entry = PendingEntryEntity(
                id = UUID.randomUUID().toString(),
                kind = PendingKind.NOTIFICATION,
                sourceApp = packageName,
                rawText = draft.rawText,
                type = if (draft.expense) PendingType.EXPENSE else PendingType.INCOME,
                amountCents = draft.amountCents,
                occurredAtEpochMinute = minute,
                merchant = null,
                note = draft.rawText.take(60),
                categoryId = hit?.takeIf { it.targetType == RuleTargetType.CATEGORY }?.targetId,
                status = PendingStatus.PENDING,
                createdAt = now,
                updatedAt = now,
                deviceId = app.deviceId,
            )
            app.database.pendingEntryDao().upsert(entry)
            notifyPending(NotifyParse.alertText(draft))
        }
    }

    /** 本 App 的提醒（渠道 `auto_entry`）：点它进「待确认」——**先过密码锁**（LockGate 在组合根）。 */
    private fun notifyPending(message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        // minSdk 26 = O：渠道一定可用（不再包 SDK_INT 判断，lint 会当死代码）
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "自动记账候选",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "识别到一笔账时的提醒（点开去确认）" }
        )
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_PENDING, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(dev.penned.app.R.drawable.ic_notify_pending)
                .setContentTitle("一笔账待确认")
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build(),
        )
    }

    companion object {
        const val CHANNEL_ID = "auto_entry"
        const val NOTIFICATION_ID = 1001

        /** 提醒点击带来的 intent 里那个标记（导航层据此进「待确认」）。 */
        const val EXTRA_OPEN_PENDING = "penned.open_pending"
    }
}
