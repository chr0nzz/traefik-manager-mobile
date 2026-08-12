package dev.chr0nzz.traefikmanager.data.store

import androidx.datastore.core.DataStore
import dev.chr0nzz.traefikmanager.di.SettingsDataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { Light, Dark, System }

data class TmPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val appLock: Boolean = false,
    val activeAgentId: String? = null,
    val migratedFromV1: Boolean = false,
    val migrationNotice: String? = null,
)

@Singleton
class PreferencesStore @Inject constructor(
    @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
) {

    val preferences: Flow<TmPreferences> = dataStore.data.map { prefs ->
        TmPreferences(
            themeMode = prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.System,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
            appLock = prefs[KEY_APP_LOCK] ?: false,
            activeAgentId = prefs[KEY_ACTIVE_AGENT],
            migratedFromV1 = prefs[KEY_MIGRATED_V1] ?: false,
            migrationNotice = prefs[KEY_MIGRATION_NOTICE],
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAppLock(enabled: Boolean) {
        dataStore.edit { it[KEY_APP_LOCK] = enabled }
    }

    suspend fun setActiveAgent(agentId: String?) {
        dataStore.edit { prefs ->
            if (agentId == null) prefs.remove(KEY_ACTIVE_AGENT) else prefs[KEY_ACTIVE_AGENT] = agentId
        }
    }

    suspend fun setMigrationNotice(notice: String?) {
        dataStore.edit { prefs ->
            if (notice == null) prefs.remove(KEY_MIGRATION_NOTICE) else prefs[KEY_MIGRATION_NOTICE] = notice
        }
    }

    suspend fun setMigratedFromV1(migrated: Boolean) {
        dataStore.edit { it[KEY_MIGRATED_V1] = migrated }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_APP_LOCK = booleanPreferencesKey("app_lock")
        val KEY_ACTIVE_AGENT = stringPreferencesKey("active_agent")
        val KEY_MIGRATED_V1 = booleanPreferencesKey("migrated_from_v1")
        val KEY_MIGRATION_NOTICE = stringPreferencesKey("migration_notice")
    }
}
