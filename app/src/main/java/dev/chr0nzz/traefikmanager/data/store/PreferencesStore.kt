package dev.chr0nzz.traefikmanager.data.store

import androidx.datastore.core.DataStore
import dev.chr0nzz.traefikmanager.di.SettingsDataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    /** How many notifications had been seen last time the bell was opened, as the web tracks it. */
    val notificationsRead: Int = 0,
    /** Whether this device asked a UnifiedPush distributor for an endpoint. */
    val pushEnabled: Boolean = false,
    /** The endpoint the distributor handed out, which is the URL servers post to. */
    val pushEndpoint: String = "",
    /** Base URL of each server that has a channel for this device, and that channel's id. */
    val pushChannels: Map<String, String> = emptyMap(),
    /** Why the last registration attempt failed, for the settings screen to explain. */
    val pushError: String = "",
    /** The last notification list read, as JSON, so a cold start has something to draw. */
    val notificationsCache: String = "",
    /** Which server that cache belongs to, since each one keeps its own notifications. */
    val notificationsCacheServer: String = "",
    /** The server's shared read marker as last seen, or -1 on a server without one. */
    val notificationsReadUntil: Int = -1,
    /** Routes of the destinations pinned to the bar, in order. Empty means the app decides. */
    val navItems: List<String> = emptyList(),
    val hideNavBar: Boolean = false,
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
            notificationsRead = prefs[KEY_NOTIFICATIONS_READ] ?: 0,
            navItems = prefs[KEY_NAV_ITEMS]?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
            hideNavBar = prefs[KEY_HIDE_NAV_BAR] ?: false,
            pushEnabled = prefs[KEY_PUSH_ENABLED] ?: false,
            pushEndpoint = prefs[KEY_PUSH_ENDPOINT].orEmpty(),
            pushChannels = decodeChannels(prefs[KEY_PUSH_CHANNELS]),
            pushError = prefs[KEY_PUSH_ERROR].orEmpty(),
            notificationsCache = prefs[KEY_NOTIFICATIONS_CACHE].orEmpty(),
            notificationsCacheServer = prefs[KEY_NOTIFICATIONS_CACHE_SERVER].orEmpty(),
            notificationsReadUntil = prefs[KEY_NOTIFICATIONS_READ_UNTIL] ?: -1,
        )
    }

    suspend fun setPushEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PUSH_ENABLED] = enabled }
    }

    suspend fun setNotificationsCache(server: String, payload: String, readUntil: Int) {
        dataStore.edit {
            it[KEY_NOTIFICATIONS_CACHE] = payload
            it[KEY_NOTIFICATIONS_CACHE_SERVER] = server
            it[KEY_NOTIFICATIONS_READ_UNTIL] = readUntil
        }
    }

    suspend fun setPushError(message: String) {
        dataStore.edit { it[KEY_PUSH_ERROR] = message }
    }

    suspend fun setPushEndpoint(endpoint: String) {
        dataStore.edit { it[KEY_PUSH_ENDPOINT] = endpoint }
    }

    suspend fun setPushChannel(server: String, channelId: String?) {
        dataStore.edit { prefs ->
            val current = decodeChannels(prefs[KEY_PUSH_CHANNELS]).toMutableMap()
            if (channelId == null) current.remove(server) else current[server] = channelId
            prefs[KEY_PUSH_CHANNELS] = encodeChannels(current)
        }
    }

    suspend fun setNavItems(routes: List<String>) {
        dataStore.edit { it[KEY_NAV_ITEMS] = routes.joinToString(",") }
    }

    suspend fun setHideNavBar(hidden: Boolean) {
        dataStore.edit { it[KEY_HIDE_NAV_BAR] = hidden }
    }

    suspend fun setNotificationsRead(count: Int) {
        dataStore.edit { it[KEY_NOTIFICATIONS_READ] = count.coerceAtLeast(0) }
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
        val KEY_NOTIFICATIONS_READ = intPreferencesKey("notifications_read")
        val KEY_PUSH_ENABLED = booleanPreferencesKey("push_enabled")
        val KEY_PUSH_ENDPOINT = stringPreferencesKey("push_endpoint")
        val KEY_PUSH_CHANNELS = stringPreferencesKey("push_channels")
        val KEY_PUSH_ERROR = stringPreferencesKey("push_error")
        val KEY_NOTIFICATIONS_CACHE = stringPreferencesKey("notifications_cache")
        val KEY_NOTIFICATIONS_CACHE_SERVER = stringPreferencesKey("notifications_cache_server")
        val KEY_NOTIFICATIONS_READ_UNTIL = intPreferencesKey("notifications_read_until")
        val KEY_NAV_ITEMS = stringPreferencesKey("nav_items")
        val KEY_HIDE_NAV_BAR = booleanPreferencesKey("hide_nav_bar")
    }
}

/** Server to channel id, kept as one string because DataStore has no map type. */
private fun decodeChannels(raw: String?): Map<String, String> =
    raw.orEmpty()
        .split('\n')
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val at = line.lastIndexOf('|')
            if (at <= 0) null else line.take(at) to line.substring(at + 1)
        }
        .toMap()

private fun encodeChannels(channels: Map<String, String>): String =
    channels.entries.joinToString("\n") { "${it.key}|${it.value}" }
