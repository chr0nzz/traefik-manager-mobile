package dev.chr0nzz.traefikmanager.data.store

import androidx.datastore.core.DataStore
import dev.chr0nzz.traefikmanager.di.ConnectionDataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Connection(
    val baseUrl: String,
    val apiKey: String?,
    val demo: Boolean,
)

@Singleton
class ConnectionStore @Inject constructor(
    @param:ConnectionDataStore private val dataStore: DataStore<Preferences>,
    private val crypto: CredentialCipher,
) {

    val connection: Flow<Connection?> = dataStore.data.map { prefs ->
        val baseUrl = prefs[KEY_BASE_URL] ?: return@map null
        val demo = prefs[KEY_DEMO] ?: false
        val storedKey = prefs[KEY_API_KEY]
        val apiKey = storedKey?.let(crypto::decrypt)
        if (storedKey != null && apiKey == null && !demo) return@map null
        Connection(baseUrl = baseUrl, apiKey = apiKey, demo = demo)
    }

    suspend fun save(baseUrl: String, apiKey: String?) {
        dataStore.edit { prefs ->
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
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = DEMO_BASE_URL
            prefs.remove(KEY_API_KEY)
            prefs[KEY_DEMO] = true
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_API_KEY = stringPreferencesKey("api_key_enc")
        val KEY_DEMO = booleanPreferencesKey("demo")
        const val DEMO_BASE_URL = "https://demo.invalid"
    }
}
