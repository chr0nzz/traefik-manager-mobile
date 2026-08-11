package dev.chr0nzz.traefikmanager.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")

enum class ThemeMode { Light, Dark, System }

data class TmPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val appLock: Boolean = false,
    val activeAgentId: String? = null,
)

@Singleton
class PreferencesStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val preferences: Flow<TmPreferences> = context.preferencesDataStore.data.map { prefs ->
        TmPreferences(
            themeMode = prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.System,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
            appLock = prefs[KEY_APP_LOCK] ?: false,
            activeAgentId = prefs[KEY_ACTIVE_AGENT],
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.preferencesDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.preferencesDataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAppLock(enabled: Boolean) {
        context.preferencesDataStore.edit { it[KEY_APP_LOCK] = enabled }
    }

    suspend fun setActiveAgent(agentId: String?) {
        context.preferencesDataStore.edit { prefs ->
            if (agentId == null) prefs.remove(KEY_ACTIVE_AGENT) else prefs[KEY_ACTIVE_AGENT] = agentId
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_APP_LOCK = booleanPreferencesKey("app_lock")
        val KEY_ACTIVE_AGENT = stringPreferencesKey("active_agent")
    }
}
