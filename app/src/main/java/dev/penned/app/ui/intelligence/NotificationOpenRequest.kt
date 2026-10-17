package dev.penned.app.ui.intelligence

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 提醒点击（M4-8）：`AutoEntryService` 发的提醒里带一个标记，`MainActivity` 收到就放这儿，
 * 导航层观察它跳「待确认」——**在解锁之后**（`PennedApp` 只在 LockGate 放行后才组合，
 * 所以这里天然满足"通知点击先过密码锁"，C4 裁决）。
 */
object NotificationOpenRequest {

    private val _requested = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = _requested

    fun offerFrom(intent: Intent?) {
        if (intent?.getBooleanExtra(dev.penned.app.data.notify.AutoEntryService.EXTRA_OPEN_PENDING, false) == true) {
            _requested.value = true
        }
    }

    fun consume() {
        _requested.value = false
    }
}

/** 通知使用权是否已授予（"已授权 / 未授权 + 去开启"那张状态卡用）。 */
object NotificationAccess {

    fun isGranted(context: Context): Boolean {
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        val me = ComponentName(context, dev.penned.app.data.notify.AutoEntryService::class.java)
        return enabled.split(':').any { it.equals(me.flattenToString(), ignoreCase = true) }
    }
}
