package dev.chr0nzz.traefikmanager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dev.chr0nzz.traefikmanager.data.store.ConnectionStore
import dev.chr0nzz.traefikmanager.data.store.CredentialCipher
import dev.chr0nzz.traefikmanager.data.store.LegacyCredentialsMigration
import dev.chr0nzz.traefikmanager.data.store.LegacySecureStore
import dev.chr0nzz.traefikmanager.data.store.LegacyStoreReader
import dev.chr0nzz.traefikmanager.data.store.PreferencesStore
import dev.chr0nzz.traefikmanager.data.store.ThemeMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeLegacyStore(
    private val values: MutableMap<String, String> = mutableMapOf(),
) : LegacyStoreReader {
    var cleared = false
        private set

    override fun read(key: String): String? = values[key]
    override fun hasAnyEntry(): Boolean = values.isNotEmpty()
    override fun clear() {
        cleared = true
        values.clear()
    }
}

private class FakeCrypto : CredentialCipher {
    override fun encrypt(plain: String): String = "enc:$plain"
    override fun decrypt(encoded: String): String? = encoded.removePrefix("enc:")
}

@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var connectionStore: ConnectionStore
    private lateinit var preferencesStore: PreferencesStore

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { tempFolder.newFile("$name.preferences_pb") }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        connectionStore = ConnectionStore(store("connection"), FakeCrypto())
        preferencesStore = PreferencesStore(store("preferences"))
        File(context.filesDir, "tm_widget_creds.json").delete()
    }

    @After
    fun tearDown() {
        File(context.filesDir, "tm_widget_creds.json").delete()
    }

    private fun migration(legacy: LegacyStoreReader) = LegacyCredentialsMigration(
        context = context,
        connectionStore = connectionStore,
        preferencesStore = preferencesStore,
        legacyStore = legacy,
        json = Json { ignoreUnknownKeys = true },
    )

    @Test
    fun `v1_6 user with only SecureStore keeps their connection`() = runTest {
        val legacy = FakeLegacyStore(
            mutableMapOf(
                LegacySecureStore.KEY_BASE_URL to "https://tm.example.com",
                LegacySecureStore.KEY_API_KEY to "secret-key",
            ),
        )

        migration(legacy).runIfNeeded()

        val connection = connectionStore.connection.first()
        assertEquals("https://tm.example.com", connection?.baseUrl)
        assertEquals("secret-key", connection?.apiKey)
        assertTrue(legacy.cleared)
    }

    @Test
    fun `v1_7 user with only the widget file keeps their connection`() = runTest {
        File(context.filesDir, "tm_widget_creds.json")
            .writeText("""{"baseUrl":"https://widget.example.com","apiKey":"from-file"}""")

        migration(FakeLegacyStore()).runIfNeeded()

        val connection = connectionStore.connection.first()
        assertEquals("https://widget.example.com", connection?.baseUrl)
        assertEquals("from-file", connection?.apiKey)
    }

    @Test
    fun `SecureStore wins over the widget file`() = runTest {
        File(context.filesDir, "tm_widget_creds.json")
            .writeText("""{"baseUrl":"https://stale.example.com","apiKey":"stale"}""")
        val legacy = FakeLegacyStore(
            mutableMapOf(
                LegacySecureStore.KEY_BASE_URL to "https://current.example.com",
                LegacySecureStore.KEY_API_KEY to "current",
            ),
        )

        migration(legacy).runIfNeeded()

        assertEquals("https://current.example.com", connectionStore.connection.first()?.baseUrl)
    }

    @Test
    fun `a fresh install with no v1 data stays disconnected`() = runTest {
        migration(FakeLegacyStore()).runIfNeeded()
        assertNull(connectionStore.connection.first())
    }

    @Test
    fun `an existing v2 connection is never overwritten`() = runTest {
        connectionStore.save("https://already.example.com", "already")
        val legacy = FakeLegacyStore(
            mutableMapOf(
                LegacySecureStore.KEY_BASE_URL to "https://old.example.com",
                LegacySecureStore.KEY_API_KEY to "old",
            ),
        )

        migration(legacy).runIfNeeded()

        assertEquals("https://already.example.com", connectionStore.connection.first()?.baseUrl)
    }

    @Test
    fun `preferences and active agent come across`() = runTest {
        val legacy = FakeLegacyStore(
            mutableMapOf(
                LegacySecureStore.KEY_BASE_URL to "https://tm.example.com",
                LegacySecureStore.KEY_API_KEY to "k",
                LegacySecureStore.KEY_ACTIVE_AGENT to "agent-7",
                LegacySecureStore.KEY_THEME_MODE to "dark",
                LegacySecureStore.KEY_DYNAMIC_COLORS to "1",
            ),
        )

        migration(legacy).runIfNeeded()

        val prefs = preferencesStore.preferences.first()
        assertEquals("agent-7", prefs.activeAgentId)
        assertEquals(ThemeMode.Dark, prefs.themeMode)
        assertTrue(prefs.dynamicColor)
        assertTrue(prefs.migratedFromV1)
    }

    @Test
    fun `migration runs once, so a later disconnect is not undone`() = runTest {
        val legacy = FakeLegacyStore(
            mutableMapOf(
                LegacySecureStore.KEY_BASE_URL to "https://tm.example.com",
                LegacySecureStore.KEY_API_KEY to "k",
            ),
        )
        migration(legacy).runIfNeeded()
        connectionStore.clear()

        migration(
            FakeLegacyStore(
                mutableMapOf(LegacySecureStore.KEY_BASE_URL to "https://tm.example.com"),
            ),
        ).runIfNeeded()

        assertNull(connectionStore.connection.first())
    }

    @Test
    fun `the plaintext widget credentials file is deleted`() = runTest {
        val file = File(context.filesDir, "tm_widget_creds.json")
        file.writeText("""{"baseUrl":"https://x.example.com","apiKey":"leaky"}""")

        migration(FakeLegacyStore()).runIfNeeded()

        assertFalse(file.exists())
    }

    @Test
    fun `corrupt widget json does not break the migration`() = runTest {
        File(context.filesDir, "tm_widget_creds.json").writeText("{ not json")

        migration(FakeLegacyStore()).runIfNeeded()

        assertNull(connectionStore.connection.first())
        assertTrue(preferencesStore.preferences.first().migratedFromV1)
    }
}
