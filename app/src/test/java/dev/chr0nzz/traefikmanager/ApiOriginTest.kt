package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.ui.settings.ApiOrigin
import dev.chr0nzz.traefikmanager.ui.settings.ConnectionSettingsUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiOriginTest {

    @Test
    fun `default ports, case and a trailing slash do not change the origin`() {
        assertTrue(ApiOrigin.same("http://traefik:8080", "http://traefik:8080/"))
        assertTrue(ApiOrigin.same("https://Traefik.example.com", "https://traefik.example.com:443"))
        assertTrue(ApiOrigin.same("http://traefik_api", "http://traefik_api:80"))
        assertTrue(ApiOrigin.same("http://[::1]:8080/api", "http://[::1]:8080/api/"))
    }

    @Test
    fun `scheme, host, port or path changes are a new origin`() {
        assertFalse(ApiOrigin.same("http://traefik:8080", "https://traefik:8080"))
        assertFalse(ApiOrigin.same("http://traefik:8080", "http://other:8080"))
        assertFalse(ApiOrigin.same("http://traefik:8080", "http://traefik:9090"))
        assertFalse(ApiOrigin.same("http://traefik:8080/a", "http://traefik:8080/b"))
    }

    @Test
    fun `anything unparseable never matches`() {
        assertFalse(ApiOrigin.same("", ""))
        assertFalse(ApiOrigin.same("traefik:8080", "traefik:8080"))
        assertFalse(ApiOrigin.same("http://traefik:port", "http://traefik:port"))
    }

    @Test
    fun `a saved password must be re-entered when the api url moves`() {
        val base = ConnectionSettingsUiState(
            loading = false,
            url = "http://traefik:8080",
            savedUrl = "http://traefik:8080",
            user = "admin",
            passwordStored = true,
            domains = listOf("example.com"),
        )
        assertFalse(base.passwordRequired)
        assertTrue(base.canSave)

        val moved = base.copy(url = "http://traefik-new:8080")
        assertTrue(moved.passwordRequired)
        assertFalse(moved.canSave)
        assertTrue(moved.copy(password = "secret").canSave)

        assertFalse(moved.copy(user = "").passwordRequired)
        assertFalse(moved.copy(passwordStored = false).passwordRequired)
    }
}
