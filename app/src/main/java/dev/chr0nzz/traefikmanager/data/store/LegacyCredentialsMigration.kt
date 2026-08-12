package dev.chr0nzz.traefikmanager.data.store

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LegacyWidgetCredentials(
    val baseUrl: String = "",
    val apiKey: String = "",
)

@Singleton
class LegacyCredentialsMigration @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val connectionStore: ConnectionStore,
    private val preferencesStore: PreferencesStore,
    private val legacyStore: LegacyStoreReader,
    private val json: Json,
) {

    private var done = false

    suspend fun runIfNeeded() {
        if (done) return
        done = true
        if (preferencesStore.preferences.first().migratedFromV1) return

        runCatching { migrate() }
            .onFailure { Log.w(TAG, "v1 migration failed", it) }

        preferencesStore.setMigratedFromV1(true)
    }

    private suspend fun migrate() {
        val alreadyConnected = connectionStore.connection.first() != null

        if (!alreadyConnected) {
            val secureStoreUrl = legacyStore.read(LegacySecureStore.KEY_BASE_URL)
            val secureStoreKey = legacyStore.read(LegacySecureStore.KEY_API_KEY)
            val hadV1Data = legacyStore.hasAnyEntry() ||
                File(context.filesDir, LEGACY_WIDGET_FILE).exists()

            if (!secureStoreUrl.isNullOrEmpty()) {
                connectionStore.save(secureStoreUrl, secureStoreKey?.ifEmpty { null })
                Log.i(TAG, "imported v1 connection from expo-secure-store")
                preferencesStore.setMigrationNotice("Your server and API key were carried over from version 1.")
            } else if (importFromWidgetFile()) {
                Log.i(TAG, "imported v1 connection from the widget credentials file")
                preferencesStore.setMigrationNotice("Your server and API key were carried over from version 1.")
            } else if (hadV1Data) {
                Log.w(TAG, "v1 data present but unreadable")
                preferencesStore.setMigrationNotice(
                    "Version 1 credentials could not be read. Please enter your server and API key again.",
                )
            } else {
                Log.i(TAG, "no v1 connection found")
            }
        }

        legacyStore.read(LegacySecureStore.KEY_ACTIVE_AGENT)
            ?.takeIf { it.isNotEmpty() }
            ?.let { preferencesStore.setActiveAgent(it) }

        legacyStore.read(LegacySecureStore.KEY_THEME_MODE)?.let { mode ->
            when (mode) {
                "light" -> ThemeMode.Light
                "dark" -> ThemeMode.Dark
                "system" -> ThemeMode.System
                else -> null
            }?.let { preferencesStore.setThemeMode(it) }
        }

        legacyStore.read(LegacySecureStore.KEY_DYNAMIC_COLORS)?.let { flag ->
            preferencesStore.setDynamicColor(flag == "1" || flag.equals("true", ignoreCase = true))
        }

        legacyStore.clear()
        cleanUpReactNativeLeftovers()
    }

    private suspend fun importFromWidgetFile(): Boolean {
        val file = File(context.filesDir, LEGACY_WIDGET_FILE)
        if (!file.exists()) return false
        return runCatching {
            val legacy = json.decodeFromString<LegacyWidgetCredentials>(file.readText())
            if (legacy.baseUrl.isNotEmpty()) {
                connectionStore.save(legacy.baseUrl, legacy.apiKey.ifEmpty { null })
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun cleanUpReactNativeLeftovers() {
        val dataDir = context.filesDir.parentFile ?: return
        val leftovers = listOf(
            File(context.filesDir, LEGACY_WIDGET_FILE),
            File(dataDir, "databases/RKStorage"),
            File(dataDir, "databases/RKStorage-journal"),
            File(dataDir, "databases/androidx.work.workdb"),
            File(dataDir, "databases/androidx.work.workdb-shm"),
            File(dataDir, "databases/androidx.work.workdb-wal"),
            File(dataDir, "shared_prefs/SecureStore.xml"),
        )
        leftovers.forEach { file ->
            runCatching { if (file.exists()) file.delete() }
        }
        runCatching { context.cacheDir.deleteRecursively() }
    }

    private companion object {
        const val LEGACY_WIDGET_FILE = "tm_widget_creds.json"
        const val TAG = "TmMigration"
    }
}
