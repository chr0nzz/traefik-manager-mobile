package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.CertCleanup
import dev.chr0nzz.traefikmanager.data.model.CertDeleteResponse
import dev.chr0nzz.traefikmanager.data.model.CertEntry
import dev.chr0nzz.traefikmanager.data.model.CertManageState
import dev.chr0nzz.traefikmanager.data.model.CertUsage
import dev.chr0nzz.traefikmanager.data.model.CertVerdict
import dev.chr0nzz.traefikmanager.data.model.Route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertManageTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `usage, manage and delete responses decode`() {
        val usage = json.decodeFromString<CertUsage>(
            """{"certs":[{"main":"old.example.com","resolver":"le","source":"acme.json","expired":false,
               "unused":true,"orphaned":false,"why":""}],"unused_known":true,"why":"","resolvers_known":true}""",
        )
        assertTrue(usage.unusedKnown)
        assertTrue(usage.verdict("le", "old.example.com")!!.unused)

        val manage = json.decodeFromString<CertManageState>(
            """{"available":true,"writable":true,"restart_method":"proxy","reason":"","paths":["/app/acme.json"]}""",
        )
        assertTrue(manage.available)
        assertEquals("proxy", manage.restartMethod)

        val partial = json.decodeFromString<CertDeleteResponse>(
            """{"error":"store b changed","removed":1,"partial":true,"backup":"acme.json.bak","restarted":true}""",
        )
        assertTrue(partial.partial)
        assertFalse(partial.ok)
    }

    @Test
    fun `a route delete frees only unused resolver certificates for its hosts`() {
        val route = Route(id = "app", rule = "Host(`app.example.com`) && !Host(`skip.example.com`)", tls = JsonPrimitive(true))
        val hosts = CertCleanup.hostsOf(route)
        assertEquals(setOf("app.example.com"), hosts)

        val certs = listOf(
            CertEntry(resolver = "le", main = "app.example.com"),
            CertEntry(resolver = "le", main = "example.com", sans = listOf("app.example.com")),
            CertEntry(resolver = "file", main = "app.example.com"),
            CertEntry(resolver = "le", main = "skip.example.com"),
        )
        val usage = CertUsage(
            unusedKnown = true,
            certs = listOf(
                CertVerdict(main = "app.example.com", resolver = "le", unused = true),
                CertVerdict(main = "example.com", resolver = "le", unused = false),
                CertVerdict(main = "app.example.com", resolver = "file", unused = true),
                CertVerdict(main = "skip.example.com", resolver = "le", unused = true),
            ),
        )
        assertEquals(listOf("app.example.com"), CertCleanup.freedBy(hosts, certs, usage).map { it.main })
        assertTrue(CertCleanup.freedBy(hosts, certs, usage.copy(unusedKnown = false)).isEmpty())
        assertTrue(CertCleanup.hostsOf(route.copy(tls = null)).isEmpty())
    }

    @Test
    fun `the outcome says when Traefik did not restart`() {
        assertEquals("Removed 2 certificates", CertCleanup.outcome(CertDeleteResponse(ok = true, removed = 2, restarted = true), 2))
        assertEquals(
            "Removed 1 certificate, but Traefik did not restart: no socket. The change is undone until it does.",
            CertCleanup.outcome(CertDeleteResponse(ok = true, removed = 1, restartError = "no socket"), 1),
        )
        assertEquals("Read only", CertCleanup.outcome(CertDeleteResponse(error = "Read only"), 1))
    }
}
