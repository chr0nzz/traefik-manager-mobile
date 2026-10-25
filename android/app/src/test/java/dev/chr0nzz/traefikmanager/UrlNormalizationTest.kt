package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.ui.connect.ConnectViewModel.Companion.normalizeUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlNormalizationTest {

    @Test
    fun `hostnames get https`() {
        assertEquals("https://manager.example.com", normalizeUrl("manager.example.com"))
    }

    @Test
    fun `bare ipv4 gets http`() {
        assertEquals("http://10.0.0.5", normalizeUrl("10.0.0.5"))
        assertEquals("http://10.0.0.5:8080", normalizeUrl("10.0.0.5:8080"))
    }

    @Test
    fun `existing scheme is kept regardless of case`() {
        assertEquals("http://manager.example.com", normalizeUrl("http://manager.example.com"))
        assertEquals("HTTPS://manager.example.com", normalizeUrl("HTTPS://manager.example.com"))
    }

    @Test
    fun `whitespace and trailing slashes are stripped`() {
        assertEquals("https://manager.example.com", normalizeUrl("  manager.example.com///  "))
    }

    @Test
    fun `subpath installs keep their path`() {
        assertEquals("https://example.com/tm", normalizeUrl("example.com/tm/"))
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", normalizeUrl("   "))
    }
}
