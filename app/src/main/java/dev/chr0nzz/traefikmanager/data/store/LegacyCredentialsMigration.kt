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
private data class LegacyCredentials(
    val baseUrl: String = "",
    val apiKey: String = "",
)

@Singleton
class LegacyCredentialsMigration @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val connectionStore: ConnectionStore,
    private val json: Json,
) {

    private var done = false

    suspend fun runIfNeeded() {
        if (done) return
        done = true
        val file = File(context.filesDir, LEGACY_FILE)
        if (!file.exists()) return
        runCatching {
            if (connectionStore.connection.first() == null) {
                val legacy = json.decodeFromString<LegacyCredentials>(file.readText())
                if (legacy.baseUrl.isNotEmpty()) {
                    connectionStore.save(legacy.baseUrl, legacy.apiKey.ifEmpty { null })
                }
            }
            file.delete()
        }.onFailure { Log.w(TAG, "legacy credential migration failed", it) }
    }

    private companion object {
        const val LEGACY_FILE = "tm_widget_creds.json"
        const val TAG = "TmMigration"
    }
}
