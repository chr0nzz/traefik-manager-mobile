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

private val Context.connectionDataStore: DataStore<Preferences> by preferencesDataStore(name = "connection")

data class Connection(
    val baseUrl: String,
    val apiKey: String?,
    val demo: Boolean,
)

@Singleton
class ConnectionStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val crypto: CryptoManager,
) {

    val connection: Flow<Connection?> = context.connectionDataStore.data.map { prefs ->
        val baseUrl = prefs[KEY_BASE_URL] ?: return@map null
        Connection(
            baseUrl = baseUrl,
            apiKey = prefs[KEY_API_KEY]?.let(crypto::decrypt),
            demo = prefs[KEY_DEMO] ?: false,
        )
    }

    suspend fun save(baseUrl: String, apiKey: String?) {
        context.connectionDataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = baseUrl
            if (apiKey.isNullOrEmpty()) {
                prefs.remove(KEY_API_KEY)
            } else {
                prefs[KEY_API_KEY] = crypto.encrypt(apiKey)
            }
            prefs[KEY_DEMO] = false
        }
    }

    suspend fun enterDemo() {
        context.connectionDataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = DEMO_BASE_URL
            prefs.remove(KEY_API_KEY)
            prefs[KEY_DEMO] = true
        }
    }

    suspend fun clear() {
        context.connectionDataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_API_KEY = stringPreferencesKey("api_key_enc")
        val KEY_DEMO = booleanPreferencesKey("demo")
        const val DEMO_BASE_URL = "https://demo.invalid"
    }
}
