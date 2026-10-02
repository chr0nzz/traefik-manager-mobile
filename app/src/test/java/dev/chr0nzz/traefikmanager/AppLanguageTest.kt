package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.api.LanguageInterceptor
import dev.chr0nzz.traefikmanager.data.store.AppLanguage
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AppLanguageTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tag(value: String): String? = AppLanguage.serverTagFor(Locale.forLanguageTag(value))

    private fun requestPath(language: String?, path: String): String {
        server.enqueue(MockResponse().setBody("{}"))
        val client = OkHttpClient.Builder().addInterceptor(LanguageInterceptor { language }).build()
        client.newCall(Request.Builder().url(server.url(path)).build()).execute().close()
        return server.takeRequest().path.orEmpty()
    }

    @Test
    fun `a plain language maps to its own tag`() {
        assertEquals("de", tag("de-AT"))
        assertEquals("ru", tag("ru-RU"))
        assertEquals("fr", tag("fr-BE"))
        assertEquals("pt", tag("pt-PT"))
    }

    @Test
    fun `regional variants the server ships keep their region`() {
        assertEquals("fr-CA", tag("fr-CA"))
        assertEquals("pt-BR", tag("pt-BR"))
        assertEquals("en-GB", tag("en-GB"))
        assertEquals("en-US", tag("en-US"))
    }

    @Test
    fun `the source English sends nothing`() {
        assertNull(tag("en"))
        assertNull(tag("en-CA"))
        assertNull(tag("und"))
    }

    @Test
    fun `only simplified Chinese is sent`() {
        assertEquals("zh-Hans", tag("zh-CN"))
        assertEquals("zh-Hans", tag("zh-Hans-SG"))
        assertEquals("zh-Hans", tag("zh"))
        assertNull(tag("zh-TW"))
        assertNull(tag("zh-Hant-HK"))
    }

    @Test
    fun `a native name starts with a capital`() {
        assertEquals("Deutsch", AppLanguage.displayName(Locale.forLanguageTag("de")))
        assertEquals("Français (Canada)", AppLanguage.displayName(Locale.forLanguageTag("fr-CA")))
    }

    @Test
    fun `the language is added to the request`() {
        assertEquals("/api/routes?lang=de", requestPath("de", "/api/routes"))
        assertEquals("/api/routes?status=down&lang=pt-BR", requestPath("pt-BR", "/api/routes?status=down"))
    }

    @Test
    fun `nothing is added without a language`() {
        assertEquals("/api/routes", requestPath(null, "/api/routes"))
    }

    @Test
    fun `a language already on the request is kept`() {
        assertEquals("/api/routes?lang=fr", requestPath("de", "/api/routes?lang=fr"))
    }
}
