package dev.penned.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.penned.app.domain.LlmConfig
import dev.penned.app.domain.LlmEndpoints
import dev.penned.app.domain.LlmProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/** 一家供应商的四样配置（null = 这一家还没配过）。自定义那档落在 M4 的四个老键上（D113）。 */
data class LlmFields(
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val model: String? = null,
    val visionModel: String? = null,
)

/** 一家供应商的存储槽（键里那截后缀）。 */
private enum class LlmSlot(val suffix: String) {
    BASE_URL("base_url"),
    API_KEY("api_key"),
    MODEL("model"),
    VISION_MODEL("vision_model"),
}

/**
 * 设置用的 DataStore：**同一个文件路径一份实例**（文件名与历史一致，老用户的设置照旧读到）。
 *
 * 为什么不用 `by preferencesDataStore(name)`：那个委托是**进程级单例**，一旦创建就绑死第一个
 * Context 的目录，后续（被回收的目录上）再写就抛 `IOException: Unable to rename …preferences_pb.tmp`。
 *
 * 为什么按 **文件路径** 判新鲜、而不是只按 Context 缓存：Robolectric 同一个 Application 会换来换去
 * 地给不同 `filesDir`（测试方法之间），只认 Context 会把旧目录的实例一直用下去（M4-2/M4-3 各踩一次）。
 * 生产里 Application 唯一、filesDir 唯一 → 与"进程一份"完全等价，不会出现两个实例写同一个文件。
 */
private const val DATASTORE_DIR = "datastore/"
private const val DATASTORE_NAME = "penned_settings.preferences_pb"

/**
 * **一个文件路径一个实例，且从不驱逐**。
 *
 * 两条硬约束来自 DataStore 自己（M4-3 把两条都试过、踩明白的）：
 * ① 同一个文件**同时只能有一个活动实例**——多建一个就抛
 *    `IllegalStateException: There are multiple DataStores active for the same file`，
 *    所以"换新丢掉旧的"那种写法是错的；缓存键用**绝对路径**，同一路径永远复用同一份。
 * ② 目录可能已经被干掉（Robolectric 回收沙箱 / 用户清了数据），写入前先补目录，
 *    否则在 rename 那步抛 `IOException: Unable to rename …preferences_pb.tmp`。
 *
 * 生产里 Application 唯一、filesDir 唯一 → 永远只有一个实例，与"进程单例"完全等价
 * （原来的 `by preferencesDataStore(name)` 唯一的问题就是目录被干掉后不会自救）。
 */
private val stores = HashMap<String, DataStore<Preferences>>()
private val storeLock = Any()

private fun Context.settingsDataStore(): DataStore<Preferences> {
    val dir = File(applicationContext.filesDir, DATASTORE_DIR)
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, DATASTORE_NAME)
    return synchronized(storeLock) {
        stores.getOrPut(file.absolutePath) {
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { file },
            )
        }
    }
}

class SettingsRepository(private val context: Context) {

    val currentBookId: Flow<String?> = context.settingsDataStore().data.map { it[KEY_CURRENT_BOOK] }

    /** "salt:hash" 存储串；null = 未启用密码锁。 */
    val pinHash: Flow<String?> = context.settingsDataStore().data.map { it[KEY_PIN_HASH] }

    /** 上次**成功**拿到并解析清单的时刻（epochMillis）；null = 从未成功检查过（规格 M2 S2）。 */
    val lastUpdateCheckAt: Flow<Long?> = context.settingsDataStore().data.map { it[KEY_LAST_UPDATE_CHECK_AT] }

    /** 上次自动弹过更新提示的 versionCode——同一个版本只自动提示一次（D059 的不重复骚扰）。 */
    val notifiedUpdateVersionCode: Flow<Int?> =
        context.settingsDataStore().data.map { it[KEY_NOTIFIED_UPDATE_VERSION_CODE] }

    /**
     * 启动时自动检查更新。
     *
     * **当前没有开关**（D064）：自动检查恒为开，关于页的开关已下线、留后续里程碑；键与读写方法
     * 先留着，接回开关时不用再动存储层。
     */
    val autoUpdateCheck: Flow<Boolean> = context.settingsDataStore().data.map { it[KEY_AUTO_UPDATE_CHECK] ?: true }

    /**
     * 更新通道（D114）：存 `UpdateChannel.key`；**null = 还没决定**，首次启动按当前安装包初始化。
     *
     * 注意它和 [autoUpdateCheck] 是两件事：自动检查恒为开，这里只决定订正式版还是预发布版。
     */
    val updateChannel: Flow<String?> = context.settingsDataStore().data.map { it[KEY_UPDATE_CHANNEL] }

    /** 用户是否在「关于」页手动选过通道（D114）：true 时自动判定不再覆盖（显式选择优先）。 */
    val updateChannelExplicit: Flow<Boolean> =
        context.settingsDataStore().data.map { it[KEY_UPDATE_CHANNEL_EXPLICIT] ?: false }

    /** 品牌色相（M3-12）：存 `ThemeAccent.name`；没存过/认不出 = 青绿（默认）。 */
    val themeAccentKey: Flow<String?> = context.settingsDataStore().data.map { it[KEY_THEME_ACCENT] }

    /** 深浅模式（M3-12）：存 `DarkMode.name`；没存过 = 跟随系统。 */
    val darkModeKey: Flow<String?> = context.settingsDataStore().data.map { it[KEY_DARK_MODE] }

    /** 首页月卡头显示项（M3-13）：存逗号串（`HomeDisplay.encode`）；**没存过 = 全开**、空串 = 用户全关。 */
    val homeStatItems: Flow<String?> = context.settingsDataStore().data.map { it[KEY_HOME_STAT_ITEMS] }

    /** 统计模块顺序（M3-14）：逗号串（`StatsLayoutCodec`）；没存过 = 默认顺序。 */
    val statsOrder: Flow<String?> = context.settingsDataStore().data.map { it[KEY_STATS_ORDER] }

    /** 统计模块开关（M3-14）：逗号串；**没存过 = 全开**、空串 = 全关（统计页给空态，不兜底）。 */
    val statsEnabled: Flow<String?> = context.settingsDataStore().data.map { it[KEY_STATS_ENABLED] }

    // ---- M4 智能（M4-0：键一次到位；页面从 M4-2/M4-8 起接）----

    /**
     * AI 总开关（M4-2）。**默认 false**（规格 §二·19：AI 永远要用户显式开启、显式触发）。
     * 关着时所有 AI 入口只给引导，不发任何请求。
     */
    val llmEnabled: Flow<Boolean> = context.settingsDataStore().data.map { it[KEY_LLM_ENABLED] ?: false }

    /**
     * 当前的 AI 供应商（D113）：存 `LlmProvider.id`；**没存过 = 自定义**（老用户那份扁平配置的落点）。
     *
     * 每家的配置各存一份（见 [llmFields] / [setLlmBaseUrl] 等）；旧配置由
     * [adoptLegacyLlmConfigIfNeeded] 按地址认领到对应的家。
     */
    val llmProvider: Flow<String?> = context.settingsDataStore().data.map { it[KEY_LLM_PROVIDER] }

    /** 自动记账总开关（M4-8）。**默认 false**：勾了 App 也还得开着这个才处理通知。 */
    val autoEntryEnabled: Flow<Boolean> = context.settingsDataStore().data.map { it[KEY_AUTO_ENTRY_ENABLED] ?: false }

    /**
     * 自动记账的 App 白名单（M4-8）：逗号分隔的包名；**没存过/空串 = 什么都不处理**
     * （宁可漏，不可错记——不勾任何 App 时通知监听形同关闭）。
     */
    val autoEntryPackages: Flow<String?> = context.settingsDataStore().data.map { it[KEY_AUTO_ENTRY_PACKAGES] }

    suspend fun setLlmEnabled(enabled: Boolean) {
        context.settingsDataStore().edit { it[KEY_LLM_ENABLED] = enabled }
    }

    /**
     * 切换供应商（只写"当前是哪家"，各家自己的配置一个字不动）。
     * [provider] 传 null = **抹掉"选过"这件事**（回到"没存过 = 自定义"）——测试要能回到干净起点。
     */
    suspend fun setLlmProvider(provider: LlmProvider?) {
        context.settingsDataStore().edit {
            if (provider == null) it.remove(KEY_LLM_PROVIDER) else it[KEY_LLM_PROVIDER] = provider.id
        }
    }

    /** 读某一家的四样配置（一次读完，不做四趟）。 */
    suspend fun llmFields(provider: LlmProvider): LlmFields {
        val prefs = context.settingsDataStore().data.first()
        return LlmFields(
            baseUrl = prefs[keyLlm(provider, LlmSlot.BASE_URL)],
            apiKey = prefs[keyLlm(provider, LlmSlot.API_KEY)],
            model = prefs[keyLlm(provider, LlmSlot.MODEL)],
            visionModel = prefs[keyLlm(provider, LlmSlot.VISION_MODEL)],
        )
    }

    /** 服务地址（**只接受 https**，C2——校验在 `LlmEndpoints`，这里只存）。 */
    suspend fun setLlmBaseUrl(provider: LlmProvider, url: String?) = putLlm(provider, LlmSlot.BASE_URL, url)

    /** 用户的 API Key：**明文存本机 DataStore**（R5 裁决；引加密库 = 加依赖），页面明示风险。 */
    suspend fun setLlmApiKey(provider: LlmProvider, key: String?) = putLlm(provider, LlmSlot.API_KEY, key)

    /** 文本模型名。 */
    suspend fun setLlmModel(provider: LlmProvider, model: String?) = putLlm(provider, LlmSlot.MODEL, model)

    /** 视觉模型名；空 = 跟文本模型（同一家的多模态型号常常就是同一个名字）。 */
    suspend fun setLlmVisionModel(provider: LlmProvider, model: String?) =
        putLlm(provider, LlmSlot.VISION_MODEL, model)

    /**
     * 当前供应商的一份**可用**配置：总开关关着 / 没配齐 / 地址非法 → null（一个包都不发）。
     *
     * 截图导入与账单导入共用这一份口径（M4 时两处各抄了一遍，D113 收口到这里）。
     */
    suspend fun activeLlmConfig(): LlmConfig? {
        if (!llmEnabled.first()) return null
        val fields = llmFields(LlmProvider.fromId(llmProvider.first()))
        if (LlmEndpoints.validate(fields.baseUrl, fields.apiKey, fields.model) != null) return null
        return LlmEndpoints.config(
            fields.baseUrl.orEmpty(), fields.apiKey.orEmpty(), fields.model.orEmpty(), fields.visionModel,
        )
    }

    /**
     * 把 M4 那份扁平配置认领到对应的家（D113）：只在**没写过"当前供应商"**且老地址非空时做一次。
     *
     * 判据 = 地址（[LlmProvider.inferByBaseUrl]）；复制过去**只补空**（目标槽位已有值就不动），
     * 老键**不删**——用户想回「自定义」看原样、或将来想改归属，都还回得去。
     */
    suspend fun adoptLegacyLlmConfigIfNeeded() {
        val prefs = context.settingsDataStore().data.first()
        if (prefs[KEY_LLM_PROVIDER] != null) return
        val legacyBase = prefs[KEY_LLM_BASE_URL]?.takeIf { it.isNotBlank() } ?: return
        val provider = LlmProvider.inferByBaseUrl(legacyBase)
        context.settingsDataStore().edit { target ->
            target[KEY_LLM_PROVIDER] = provider.id
            if (provider != LlmProvider.CUSTOM) {
                val legacy = listOf(
                    LlmSlot.BASE_URL to prefs[KEY_LLM_BASE_URL],
                    LlmSlot.API_KEY to prefs[KEY_LLM_API_KEY],
                    LlmSlot.MODEL to prefs[KEY_LLM_MODEL],
                    LlmSlot.VISION_MODEL to prefs[KEY_LLM_VISION_MODEL],
                )
                legacy.forEach { (slot, value) ->
                    val key = keyLlm(provider, slot)
                    if (target[key] == null && value != null) target[key] = value
                }
            }
        }
    }

    private suspend fun putLlm(provider: LlmProvider, slot: LlmSlot, value: String?) {
        context.settingsDataStore().edit { prefs ->
            if (value == null) prefs.remove(keyLlm(provider, slot)) else prefs[keyLlm(provider, slot)] = value
        }
    }

    /**
     * 某一家某一槽的键：**自定义沿用 M4 的四个老键**（`llm_base_url` 等，老用户零迁移——D113），
     * 其余家各自 `llm_<id>_<槽>`。
     */
    private fun keyLlm(provider: LlmProvider, slot: LlmSlot): Preferences.Key<String> = when {
        provider != LlmProvider.CUSTOM -> stringPreferencesKey("llm_${provider.id}_${slot.suffix}")
        slot == LlmSlot.BASE_URL -> KEY_LLM_BASE_URL
        slot == LlmSlot.API_KEY -> KEY_LLM_API_KEY
        slot == LlmSlot.MODEL -> KEY_LLM_MODEL
        else -> KEY_LLM_VISION_MODEL
    }

    suspend fun setAutoEntryEnabled(enabled: Boolean) {
        context.settingsDataStore().edit { it[KEY_AUTO_ENTRY_ENABLED] = enabled }
    }

    suspend fun setAutoEntryPackages(commaSeparated: String) {
        context.settingsDataStore().edit { it[KEY_AUTO_ENTRY_PACKAGES] = commaSeparated }
    }

    suspend fun setThemeAccent(accentName: String) {
        context.settingsDataStore().edit { it[KEY_THEME_ACCENT] = accentName }
    }

    suspend fun setDarkMode(modeName: String) {
        context.settingsDataStore().edit { it[KEY_DARK_MODE] = modeName }
    }

    suspend fun setHomeStatItems(encoded: String) {
        context.settingsDataStore().edit { it[KEY_HOME_STAT_ITEMS] = encoded }
    }

    suspend fun setStatsOrder(encoded: String) {
        context.settingsDataStore().edit { it[KEY_STATS_ORDER] = encoded }
    }

    suspend fun setStatsEnabled(encoded: String) {
        context.settingsDataStore().edit { it[KEY_STATS_ENABLED] = encoded }
    }

    suspend fun setCurrentBook(id: String) {
        context.settingsDataStore().edit { it[KEY_CURRENT_BOOK] = id }
    }

    suspend fun setPinHash(stored: String?) {
        context.settingsDataStore().edit {
            if (stored == null) it.remove(KEY_PIN_HASH) else it[KEY_PIN_HASH] = stored
        }
    }

    suspend fun setLastUpdateCheckAt(epochMillis: Long) {
        context.settingsDataStore().edit { it[KEY_LAST_UPDATE_CHECK_AT] = epochMillis }
    }

    suspend fun setNotifiedUpdateVersionCode(versionCode: Int) {
        context.settingsDataStore().edit { it[KEY_NOTIFIED_UPDATE_VERSION_CODE] = versionCode }
    }

    suspend fun setAutoUpdateCheck(enabled: Boolean) {
        context.settingsDataStore().edit { it[KEY_AUTO_UPDATE_CHECK] = enabled }
    }

    /**
     * 写更新通道（D114）。[channel] 为 null = 清掉决定（测试复位用；生产只在首次初始化时写具体值）。
     *
     * [explicit] 必须跟着通道一起写：只写通道不写 explicit 会被自动判定覆盖。
     */
    suspend fun setUpdateChannel(channel: String?, explicit: Boolean) {
        context.settingsDataStore().edit {
            if (channel == null) it.remove(KEY_UPDATE_CHANNEL) else it[KEY_UPDATE_CHANNEL] = channel
            it[KEY_UPDATE_CHANNEL_EXPLICIT] = explicit
        }
    }

    companion object {
        private val KEY_CURRENT_BOOK = stringPreferencesKey("current_book_id")
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEY_LAST_UPDATE_CHECK_AT = longPreferencesKey("update_last_check_at")
        private val KEY_NOTIFIED_UPDATE_VERSION_CODE = intPreferencesKey("update_notified_version_code")
        private val KEY_AUTO_UPDATE_CHECK = booleanPreferencesKey("update_auto_check")
        private val KEY_UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        private val KEY_UPDATE_CHANNEL_EXPLICIT = booleanPreferencesKey("update_channel_explicit")
        private val KEY_THEME_ACCENT = stringPreferencesKey("theme_accent")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        private val KEY_HOME_STAT_ITEMS = stringPreferencesKey("home_stat_items")
        private val KEY_STATS_ORDER = stringPreferencesKey("stats_order")
        private val KEY_STATS_ENABLED = stringPreferencesKey("stats_enabled")
        // M4 智能（M4-0）；供应商槽位与归属见 D113
        private val KEY_LLM_ENABLED = booleanPreferencesKey("llm_enabled")
        private val KEY_LLM_PROVIDER = stringPreferencesKey("llm_provider")
        private val KEY_LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        private val KEY_LLM_API_KEY = stringPreferencesKey("llm_api_key")
        private val KEY_LLM_MODEL = stringPreferencesKey("llm_model")
        private val KEY_LLM_VISION_MODEL = stringPreferencesKey("llm_vision_model")
        private val KEY_AUTO_ENTRY_ENABLED = booleanPreferencesKey("auto_entry_enabled")
        private val KEY_AUTO_ENTRY_PACKAGES = stringPreferencesKey("auto_entry_packages")
    }
}
