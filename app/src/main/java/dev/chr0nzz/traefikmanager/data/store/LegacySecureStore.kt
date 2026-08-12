package dev.chr0nzz.traefikmanager.data.store

import android.content.Context
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

interface LegacyStoreReader {
    fun read(key: String): String?
    fun hasAnyEntry(): Boolean
    fun clear()
}

data class LegacyEnvelope(
    val ciphertext: String,
    val iv: String,
    val tagBits: Int,
    val keystoreAlias: String,
)

object LegacySecureStoreFormat {

    const val PREFS_NAME = "SecureStore"
    const val DEFAULT_KEYCHAIN_SERVICE = "key_v1"
    const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    const val UNAUTHENTICATED_SUFFIX = "keystoreUnauthenticated"
    const val MIN_TAG_BITS = 96
    const val DEFAULT_TAG_BITS = 128

    fun prefsKeysFor(itemKey: String): List<String> =
        listOf("$DEFAULT_KEYCHAIN_SERVICE-$itemKey", itemKey)

    fun parse(envelopeJson: String): LegacyEnvelope? = runCatching {
        val envelope = JSONObject(envelopeJson)
        if (envelope.optString("scheme") != "aes") return null
        if (envelope.optBoolean("requireAuthentication", false)) return null

        val tagBits = envelope.optInt("tlen", DEFAULT_TAG_BITS)
        if (tagBits < MIN_TAG_BITS) return null

        val keychainService = envelope.optString("keystoreAlias")
            .takeIf { it.isNotEmpty() } ?: DEFAULT_KEYCHAIN_SERVICE
        val baseAlias = "$AES_TRANSFORMATION:$keychainService"
        val alias = if (envelope.optBoolean("usesKeystoreSuffix", false)) {
            "$baseAlias:$UNAUTHENTICATED_SUFFIX"
        } else {
            baseAlias
        }

        LegacyEnvelope(
            ciphertext = envelope.getString("ct"),
            iv = envelope.getString("iv"),
            tagBits = tagBits,
            keystoreAlias = alias,
        )
    }.getOrNull()
}

@Singleton
class LegacySecureStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LegacyStoreReader {

    override fun read(key: String): String? = runCatching {
        val prefs = context.getSharedPreferences(
            LegacySecureStoreFormat.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val envelopeJson = LegacySecureStoreFormat.prefsKeysFor(key)
            .firstNotNullOfOrNull { prefs.getString(it, null)?.takeIf(String::isNotEmpty) }
            ?: return null

        val envelope = LegacySecureStoreFormat.parse(envelopeJson) ?: return null

        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(envelope.keystoreAlias)) return null
        val entry = keyStore.getEntry(envelope.keystoreAlias, null) as? KeyStore.SecretKeyEntry
            ?: return null

        val cipher = Cipher.getInstance(LegacySecureStoreFormat.AES_TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                entry.secretKey,
                GCMParameterSpec(envelope.tagBits, Base64.decode(envelope.iv, Base64.DEFAULT)),
            )
        }
        String(
            cipher.doFinal(Base64.decode(envelope.ciphertext, Base64.DEFAULT)),
            Charsets.UTF_8,
        )
    }.onFailure { Log.w(TAG, "could not read legacy value for $key", it) }.getOrNull()

    override fun hasAnyEntry(): Boolean = runCatching {
        context.getSharedPreferences(LegacySecureStoreFormat.PREFS_NAME, Context.MODE_PRIVATE)
            .all.keys.any { it.endsWith(KEY_BASE_URL) || it.endsWith(KEY_API_KEY) }
    }.getOrDefault(false)

    override fun clear() {
        runCatching {
            context.getSharedPreferences(LegacySecureStoreFormat.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    companion object {
        const val KEY_BASE_URL = "tm_base_url"
        const val KEY_API_KEY = "tm_api_key"
        const val KEY_ACTIVE_AGENT = "tm_active_agent_id"
        const val KEY_THEME_MODE = "tm_theme_mode"
        const val KEY_DYNAMIC_COLORS = "tm_dynamic_colors"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TAG = "TmLegacyStore"
    }
}
