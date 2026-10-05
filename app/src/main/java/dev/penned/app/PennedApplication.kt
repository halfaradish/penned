package dev.penned.app

import android.app.Application
import android.content.Context
import dev.penned.app.data.db.PennedDatabase
import dev.penned.app.data.seed.DefaultSeed
import dev.penned.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            DefaultSeed.seedIfEmpty(database, deviceId, System.currentTimeMillis())
        }
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
