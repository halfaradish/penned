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

    /** 首页月卡头显示项（M3-13）：存逗号串（`HomeDisplay.encode`）；**没存过 = 全开**、空串 = 用户全关。 */
    val homeStatItems: Flow<String?> = context.dataStore.data.map { it[KEY_HOME_STAT_ITEMS] }

    /** 统计模块顺序（M3-14）：逗号串（`StatsLayoutCodec`）；没存过 = 默认顺序。 */
    val statsOrder: Flow<String?> = context.dataStore.data.map { it[KEY_STATS_ORDER] }

    /** 统计模块开关（M3-14）：逗号串；**没存过 = 全开**、空串 = 全关（统计页给空态，不兜底）。 */
    val statsEnabled: Flow<String?> = context.dataStore.data.map { it[KEY_STATS_ENABLED] }

    // ---- M4 智能（M4-0：键一次到位；页面从 M4-2/M4-8 起接）----

    /**
     * AI 总开关（M4-2）。**默认 false**（规格 §二·19：AI 永远要用户显式开启、显式触发）。
     * 关着时所有 AI 入口只给引导，不发任何请求。
     */
    val llmEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LLM_ENABLED] ?: false }

    /** BYOK 服务地址（OpenAI 兼容 base，如 `https://open.bigmodel.cn/api/paas/v4`）。**只接受 https**（C2）。 */
    val llmBaseUrl: Flow<String?> = context.dataStore.data.map { it[KEY_LLM_BASE_URL] }

    /** 用户的 API Key：**明文存本机 DataStore**（R5 裁决；引加密库 = 加依赖），页面明示风险。 */
    val llmApiKey: Flow<String?> = context.dataStore.data.map { it[KEY_LLM_API_KEY] }

    /** 文本模型名。 */
    val llmModel: Flow<String?> = context.dataStore.data.map { it[KEY_LLM_MODEL] }

    /** 视觉模型名；空 = 跟 [llmModel]（同一家的多模态型号常常就是同一个名字）。 */
    val llmVisionModel: Flow<String?> = context.dataStore.data.map { it[KEY_LLM_VISION_MODEL] }

    /** 自动记账总开关（M4-8）。**默认 false**：勾了 App 也还得开着这个才处理通知。 */
    val autoEntryEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_ENTRY_ENABLED] ?: false }

    /**
     * 自动记账的 App 白名单（M4-8）：逗号分隔的包名；**没存过/空串 = 什么都不处理**
     * （宁可漏，不可错记——不勾任何 App 时通知监听形同关闭）。
     */
    val autoEntryPackages: Flow<String?> = context.dataStore.data.map { it[KEY_AUTO_ENTRY_PACKAGES] }

    suspend fun setLlmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LLM_ENABLED] = enabled }
    }

    suspend fun setLlmBaseUrl(url: String?) {
        context.dataStore.edit { if (url == null) it.remove(KEY_LLM_BASE_URL) else it[KEY_LLM_BASE_URL] = url }
    }

    suspend fun setLlmApiKey(key: String?) {
        context.dataStore.edit { if (key == null) it.remove(KEY_LLM_API_KEY) else it[KEY_LLM_API_KEY] = key }
    }

    suspend fun setLlmModel(model: String?) {
        context.dataStore.edit { if (model == null) it.remove(KEY_LLM_MODEL) else it[KEY_LLM_MODEL] = model }
    }

    suspend fun setLlmVisionModel(model: String?) {
        context.dataStore.edit {
            if (model == null) it.remove(KEY_LLM_VISION_MODEL) else it[KEY_LLM_VISION_MODEL] = model
        }
    }

    suspend fun setAutoEntryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_ENTRY_ENABLED] = enabled }
    }

    suspend fun setAutoEntryPackages(commaSeparated: String) {
        context.dataStore.edit { it[KEY_AUTO_ENTRY_PACKAGES] = commaSeparated }
    }

    suspend fun setThemeAccent(accentName: String) {
        context.dataStore.edit { it[KEY_THEME_ACCENT] = accentName }
    }

    suspend fun setDarkMode(modeName: String) {
        context.dataStore.edit { it[KEY_DARK_MODE] = modeName }
    }

    suspend fun setHomeStatItems(encoded: String) {
        context.dataStore.edit { it[KEY_HOME_STAT_ITEMS] = encoded }
    }

    suspend fun setStatsOrder(encoded: String) {
        context.dataStore.edit { it[KEY_STATS_ORDER] = encoded }
    }

    suspend fun setStatsEnabled(encoded: String) {
        context.dataStore.edit { it[KEY_STATS_ENABLED] = encoded }
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
        private val KEY_HOME_STAT_ITEMS = stringPreferencesKey("home_stat_items")
        private val KEY_STATS_ORDER = stringPreferencesKey("stats_order")
        private val KEY_STATS_ENABLED = stringPreferencesKey("stats_enabled")
        // M4 智能（M4-0）
        private val KEY_LLM_ENABLED = booleanPreferencesKey("llm_enabled")
        private val KEY_LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        private val KEY_LLM_API_KEY = stringPreferencesKey("llm_api_key")
        private val KEY_LLM_MODEL = stringPreferencesKey("llm_model")
        private val KEY_LLM_VISION_MODEL = stringPreferencesKey("llm_vision_model")
        private val KEY_AUTO_ENTRY_ENABLED = booleanPreferencesKey("auto_entry_enabled")
        private val KEY_AUTO_ENTRY_PACKAGES = stringPreferencesKey("auto_entry_packages")
    }
}
