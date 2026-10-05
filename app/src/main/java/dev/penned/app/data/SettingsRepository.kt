package dev.penned.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "penned_settings")

/** 轻量设置仓库：当前账本 id 等（D023）。 */
class SettingsRepository(private val context: Context) {

    val currentBookId: Flow<String?> = context.dataStore.data.map { it[KEY_CURRENT_BOOK] }

    suspend fun setCurrentBook(id: String) {
        context.dataStore.edit { it[KEY_CURRENT_BOOK] = id }
    }

    companion object {
        private val KEY_CURRENT_BOOK = stringPreferencesKey("current_book_id")
    }
}
