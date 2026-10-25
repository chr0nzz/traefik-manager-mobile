package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.AgentBackupsResponse
import dev.chr0nzz.traefikmanager.data.model.CreateBackupResponse
import dev.chr0nzz.traefikmanager.data.model.GitCommit
import dev.chr0nzz.traefikmanager.data.model.GitStatus
import dev.chr0nzz.traefikmanager.data.model.HubBackup
import dev.chr0nzz.traefikmanager.data.model.OkResponse
import dev.chr0nzz.traefikmanager.data.model.RestoreResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P5BackupsTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Test
    fun `the hub answers a bare array with a server-local stamp`() {
        val body = """
            [{"name":"dynamic.yml.20260812_143001.bak","size":2048,
              "modified":"2026-08-12 14:30:01","kind":"routes"}]
        """.trimIndent()
        val list = json.decodeFromString<List<HubBackup>>(body)
        assertEquals(1, list.size)
        assertEquals("2026-08-12 14:30:01", list.first().modified)
        assertEquals("routes", list.first().kind)
    }

    @Test
    fun `an agent with no backups sends null, not an empty array`() {
        val response = json.decodeFromString<AgentBackupsResponse>("""{"backups":null,"static_configured":true}""")
        assertTrue(response.backups.orEmpty().isEmpty())
        assertTrue(response.staticConfigured)
    }

    @Test
    fun `an agent that fails to read its directory omits the capability flag`() {
        val response = json.decodeFromString<AgentBackupsResponse>("""{"backups":[]}""")
        assertTrue(response.backups.orEmpty().isEmpty())
        assertFalse(response.staticConfigured)
    }

    @Test
    fun `create is success plus names on the hub and ok plus name on an agent`() {
        val hub = json.decodeFromString<CreateBackupResponse>(
            """{"success":true,"names":["a.bak","b.bak"],"count":2}""",
        )
        assertTrue(hub.worked)
        assertEquals(listOf("a.bak", "b.bak"), hub.created)

        val agent = json.decodeFromString<CreateBackupResponse>("""{"ok":true,"name":"a.bak","count":1}""")
        assertTrue(agent.worked)
        assertEquals(listOf("a.bak"), agent.created)
    }

    @Test
    fun `restore answers success rather than ok`() {
        assertTrue(json.decodeFromString<RestoreResponse>("""{"success":true}""").worked)
        val refused = json.decodeFromString<RestoreResponse>(
            """{"error":"No config file matches 'stray.bak'"}""",
        )
        assertFalse(refused.worked)
        assertEquals("No config file matches 'stray.bak'", refused.error)
    }

    @Test
    fun `a certificate store backup is listed with its own kind`() {
        val hub = json.decodeFromString<List<HubBackup>>(
            """[{"name":"acme.json.20260913_101500.bak","size":5120,"modified":"2026-09-13 10:15:00","kind":"certs"}]""",
        )
        assertEquals("certs", hub.single().kind)
        val agent = json.decodeFromString<AgentBackupsResponse>(
            """{"backups":[{"name":"acme.json.20260913_101500.bak","size":5120,"date":"2026-09-13T10:15:00Z","kind":"certs"}]}""",
        )
        assertEquals("certs", agent.backups.orEmpty().single().kind)
    }

    @Test
    fun `a certificate restore says whether Traefik restarted`() {
        val host = json.decodeFromString<RestoreResponse>(
            """{"success":true,"restarted":false,"restart_error":"no restart method"}""",
        )
        assertTrue(host.worked)
        assertEquals(false, host.restarted)
        assertEquals("no restart method", host.restartError)

        val agent = json.decodeFromString<RestoreResponse>("""{"ok":true,"restarted":true}""")
        assertTrue(agent.worked)
        assertEquals(true, agent.restarted)
        assertNull(json.decodeFromString<RestoreResponse>("""{"success":true}""").restarted)
    }

    @Test
    fun `delete answers success on the hub and ok on an agent`() {
        assertTrue(json.decodeFromString<OkResponse>("""{"success":true}""").worked)
        assertTrue(json.decodeFromString<OkResponse>("""{"ok":true}""").worked)
        assertFalse(json.decodeFromString<OkResponse>("""{"error":"invalid filename"}""").worked)
    }

    @Test
    fun `git status only carries a branch when the call named an agent`() {
        val host = json.decodeFromString<GitStatus>(
            """{"enabled":true,"configured":true,"last_sha":"a1b2c3d4","last_push":"2026-08-12 05:15:00 +0000"}""",
        )
        assertTrue(host.branch.isEmpty())
        assertEquals("a1b2c3d4", host.lastSha)

        val agent = json.decodeFromString<GitStatus>(
            """{"enabled":true,"configured":true,"last_sha":"","last_push":"","branch":"agent-edge"}""",
        )
        assertEquals("agent-edge", agent.branch)
    }

    @Test
    fun `an unconfigured repo reports commits as an empty array`() {
        assertTrue(json.decodeFromString<List<GitCommit>>("[]").isEmpty())
    }
}
