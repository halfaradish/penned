package dev.penned.app

import android.app.Application
import android.content.Context
import dev.penned.app.data.db.PennedDatabase
import dev.penned.app.data.seed.DefaultSeed
import dev.penned.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

class PennedApplication : Application() {

    val database: PennedDatabase by lazy { PennedDatabase.build(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    /** 本设备标识：同步/创建者字段用（D003）。 */
    val deviceId: String by lazy {
        val prefs = getSharedPreferences("penned", Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 应用级协程（导航层这类没 ViewModel 的地方写一小笔状态用；别塞重活）。 */
    fun appScopeLaunch(block: suspend () -> Unit) {
        appScope.launch { block() }
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            DefaultSeed.seedIfEmpty(database, deviceId, System.currentTimeMillis())
            // 首次启动把当前账本设置落盘。**只能用"未设置才写"**：种子协程与 UI 并发，
            // 先读后写会把用户/测试刚选的账本覆盖掉（D117 收尾实测）。
            database.bookDao().observeAll().firstOrNull()?.firstOrNull()?.let {
                settings.setCurrentBookIfAbsent(it.id)
            }
        }
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
