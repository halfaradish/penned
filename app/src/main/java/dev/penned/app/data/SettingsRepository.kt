package dev.penned.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "penned_settings")

/** 轻量设置仓库：当前账本 id、密码锁哈希、更新检查节流等（D023 / M2 S2）。 */
class SettingsRepository(private val context: Context) {

    val currentBookId: Flow<String?> = context.dataStore.data.map { it[KEY_CURRENT_BOOK] }

    /** "salt:hash" 存储串；null = 未启用密码锁。 */
    val pinHash: Flow<String?> = context.dataStore.data.map { it[KEY_PIN_HASH] }

    /** 上次**成功**拿到并解析清单的时刻（epochMillis）；null = 从未成功检查过（规格 M2 S2）。 */
    val lastUpdateCheckAt: Flow<Long?> = context.dataStore.data.map { it[KEY_LAST_UPDATE_CHECK_AT] }

    /** 上次自动弹过更新提示的 versionCode——同一个版本只自动提示一次（D059 的不重复骚扰）。 */
    val notifiedUpdateVersionCode: Flow<Int?> =
        context.dataStore.data.map { it[KEY_NOTIFIED_UPDATE_VERSION_CODE] }

    /**
     * 启动时自动检查更新。
     *
     * **当前没有开关**（D064）：自动检查恒为开，关于页的开关已下线、留后续里程碑；键与读写方法
     * 先留着，接回开关时不用再动存储层。
     */
    val autoUpdateCheck: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_UPDATE_CHECK] ?: true }

    /** 品牌色相（M3-12）：存 `ThemeAccent.name`；没存过/认不出 = 青绿（默认）。 */
    val themeAccentKey: Flow<String?> = context.dataStore.data.map { it[KEY_THEME_ACCENT] }

    /** 深浅模式（M3-12）：存 `DarkMode.name`；没存过 = 跟随系统。 */
    val darkModeKey: Flow<String?> = context.dataStore.data.map { it[KEY_DARK_MODE] }

    suspend fun setThemeAccent(accentName: String) {
        context.dataStore.edit { it[KEY_THEME_ACCENT] = accentName }
    }

    suspend fun setDarkMode(modeName: String) {
        context.dataStore.edit { it[KEY_DARK_MODE] = modeName }
    }

    suspend fun setCurrentBook(id: String) {
        context.dataStore.edit { it[KEY_CURRENT_BOOK] = id }
    }

    suspend fun setPinHash(stored: String?) {
        context.dataStore.edit {
            if (stored == null) it.remove(KEY_PIN_HASH) else it[KEY_PIN_HASH] = stored
        }
    }

    suspend fun setLastUpdateCheckAt(epochMillis: Long) {
        context.dataStore.edit { it[KEY_LAST_UPDATE_CHECK_AT] = epochMillis }
    }

    suspend fun setNotifiedUpdateVersionCode(versionCode: Int) {
        context.dataStore.edit { it[KEY_NOTIFIED_UPDATE_VERSION_CODE] = versionCode }
    }

    suspend fun setAutoUpdateCheck(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_UPDATE_CHECK] = enabled }
    }

    companion object {
        private val KEY_CURRENT_BOOK = stringPreferencesKey("current_book_id")
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEY_LAST_UPDATE_CHECK_AT = longPreferencesKey("update_last_check_at")
        private val KEY_NOTIFIED_UPDATE_VERSION_CODE = intPreferencesKey("update_notified_version_code")
        private val KEY_AUTO_UPDATE_CHECK = booleanPreferencesKey("update_auto_check")
        private val KEY_THEME_ACCENT = stringPreferencesKey("theme_accent")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
    }
}
