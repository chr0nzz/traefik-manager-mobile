package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.store.LegacySecureStoreFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LegacyEnvelopeTest {

    private fun envelope(
        scheme: String = "aes",
        usesSuffix: Boolean = true,
        requireAuth: Boolean = false,
        keystoreAlias: String = "key_v1",
        tlen: Int = 128,
    ) = """
        {"ct":"Y2lwaGVy","iv":"aXZpdml2aXZpdg==","tlen":$tlen,"scheme":"$scheme",
         "usesKeystoreSuffix":$usesSuffix,"keystoreAlias":"$keystoreAlias",
         "requireAuthentication":$requireAuth}
    """.trimIndent()

    @Test
    fun `prefs keys try the keychain-prefixed form first, then the bare key`() {
        assertEquals(
            listOf("key_v1-tm_api_key", "tm_api_key"),
            LegacySecureStoreFormat.prefsKeysFor("tm_api_key"),
        )
    }

    @Test
    fun `current format resolves the suffixed keystore alias`() {
        val parsed = LegacySecureStoreFormat.parse(envelope())
        assertEquals(
            "AES/GCM/NoPadding:key_v1:keystoreUnauthenticated",
            parsed?.keystoreAlias,
        )
        assertEquals(128, parsed?.tagBits)
        assertEquals("Y2lwaGVy", parsed?.ciphertext)
    }

    @Test
    fun `pre-12_5 format without the suffix flag resolves the bare alias`() {
        val legacy = """{"ct":"Y2lwaGVy","iv":"aXY=","tlen":128,"scheme":"aes"}"""
        assertEquals(
            "AES/GCM/NoPadding:key_v1",
            LegacySecureStoreFormat.parse(legacy)?.keystoreAlias,
        )
    }

    @Test
    fun `a custom keychain service is honoured`() {
        val parsed = LegacySecureStoreFormat.parse(envelope(keystoreAlias = "custom"))
        assertEquals("AES/GCM/NoPadding:custom:keystoreUnauthenticated", parsed?.keystoreAlias)
    }

    @Test
    fun `entries needing biometric auth are skipped rather than guessed at`() {
        assertNull(LegacySecureStoreFormat.parse(envelope(requireAuth = true)))
    }

    @Test
    fun `the hybrid scheme is not supported`() {
        assertNull(LegacySecureStoreFormat.parse(envelope(scheme = "hybrid")))
    }

    @Test
    fun `a short GCM tag is rejected`() {
        assertNull(LegacySecureStoreFormat.parse(envelope(tlen = 64)))
    }

    @Test
    fun `garbage parses to null instead of throwing`() {
        assertNull(LegacySecureStoreFormat.parse("not json"))
        assertNull(LegacySecureStoreFormat.parse("{}"))
    }
}
