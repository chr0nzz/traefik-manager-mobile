package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.Agent
import dev.chr0nzz.traefikmanager.data.model.AgentCompose
import dev.chr0nzz.traefikmanager.data.model.AgentConfig
import dev.chr0nzz.traefikmanager.data.model.AgentMutationResponse
import dev.chr0nzz.traefikmanager.data.model.ApiKeyStatus
import dev.chr0nzz.traefikmanager.data.model.ServerSettings
import dev.chr0nzz.traefikmanager.data.model.TmNotification
import dev.chr0nzz.traefikmanager.data.repo.ServerCapabilities
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P4Test {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Test
    fun `an agent is gated by its own record, never by host settings`() {
        val hostSettings = ServerSettings(
            visibleTabs = mapOf("crowdsec" to false, "logs" to false),
            crowdsecEnabled = false,
        )
        val agent = Agent(
            id = "a1",
            name = "edge",
            visibleTabs = mapOf("crowdsec" to true, "logs" to true),
            crowdsecLapiUrl = "http://crowdsec:8080",
        )

        val onAgent = ServerCapabilities(isHost = false, name = "edge", settings = hostSettings, agent = agent)
        assertTrue(onAgent.tabVisible("crowdsec"))
        assertTrue(onAgent.crowdsecConfigured)
        assertFalse(onAgent.hostOnlySettings)

        val onHost = ServerCapabilities(isHost = true, settings = hostSettings)
        assertFalse(onHost.tabVisible("crowdsec"))
        assertFalse(onHost.crowdsecConfigured)
        assertTrue(onHost.hostOnlySettings)
    }

    @Test
    fun `an agent with no configured tabs shows everything rather than nothing`() {
        val fresh = ServerCapabilities(isHost = false, agent = Agent(id = "a1", name = "new"))
        assertTrue(fresh.tabVisible("logs"))
        assertTrue(fresh.tabVisible("certs"))
    }

    @Test
    fun `an agent keeps crowdsec offered even with an empty lapi url on the hub record`() {
        // The hub field only feeds the compose snippet; the agent reads CROWDSEC_LAPI_URL from its
        // own environment, so hiding the tab on an empty field would hide a working screen.
        val capabilities = ServerCapabilities(
            isHost = false,
            agent = Agent(id = "a1", name = "edge", visibleTabs = mapOf("crowdsec" to true)),
        )
        assertTrue(capabilities.tabVisible("crowdsec"))
        assertTrue(capabilities.crowdsecConfigured)
    }

    @Test
    fun `an agent can still hide crowdsec through its visible tabs`() {
        val capabilities = ServerCapabilities(
            isHost = false,
            agent = Agent(id = "a1", name = "edge", visibleTabs = mapOf("crowdsec" to false)),
        )
        assertFalse(capabilities.tabVisible("crowdsec"))
    }

    @Test
    fun `unknown capabilities keep every tab visible`() {
        val unknown = ServerCapabilities()
        assertTrue(unknown.tabVisible("crowdsec"))
        assertTrue(unknown.crowdsecConfigured)
    }

    @Test
    fun `agent records decode the gating fields and tolerate the masked secrets`() {
        val agent = json.decodeFromString<Agent>(
            """{"id":"a1","name":"edge","url":"http://10.0.0.5:8090","api_key":"***",
               "visible_tabs":{"logs":true,"crowdsec":false},"crowdsec_lapi_url":"http://cs:8080",
               "created_at":"2026-01-01T00:00:00+00:00"}""",
        )
        assertTrue(agent.tabVisible("logs"))
        assertFalse(agent.tabVisible("crowdsec"))
        assertTrue(agent.crowdsecConfigured)
    }

    @Test
    fun `the rotated key is read from the nested agent object, not the top level`() {
        val response = json.decodeFromString<AgentMutationResponse>(
            """{"ok":true,"agent":{"id":"a1","name":"edge","api_key":"***","api_key_raw":"abc123"}}""",
        )
        assertEquals("abc123", response.rawKey)

        val topLevelOnly = json.decodeFromString<AgentMutationResponse>(
            """{"ok":true,"api_key_raw":"abc123"}""",
        )
        assertNull(topLevelOnly.rawKey)
    }

    @Test
    fun `numeric agent fields survive being sent as numbers`() {
        val agent = json.decodeFromString<AgentConfig>(
            """{"id":"a1","tma_port":8090,"tma_rate_limit":300,"backup_keep_count":5}""",
        )
        assertEquals("8090", agent.tmaPort)
        assertEquals("300", agent.tmaRateLimit)
        assertEquals("5", agent.backupKeepCount)
    }

    @Test
    fun `the install snippet always carries the three required env lines`() {
        val lines = AgentCompose.envLines(AgentConfig(name = "edge"), apiKey = null)
        assertEquals("TMA_API_KEY=<your-api-key>", lines[0])
        assertEquals("TRAEFIK_API_URL=http://traefik:8080", lines[1])
        assertEquals("CONFIG_PATH=/app/config", lines[2])
    }

    @Test
    fun `default port and rate limit are omitted from the snippet`() {
        val defaults = AgentCompose.envLines(AgentConfig(tmaPort = "8090", tmaRateLimit = "300"), "k")
        assertTrue(defaults.none { it.startsWith("TMA_PORT") })
        assertTrue(defaults.none { it.startsWith("TMA_RATE_LIMIT") })

        val custom = AgentCompose.envLines(AgentConfig(tmaPort = "9000", tmaRateLimit = "60"), "k")
        assertTrue(custom.contains("TMA_PORT=9000"))
        assertTrue(custom.contains("TMA_RATE_LIMIT=60"))
    }

    @Test
    fun `restart method pulls in only its own companion variables`() {
        val proxy = AgentCompose.envLines(
            AgentConfig(restartMethod = "proxy", traefikContainer = "traefik", dockerHost = "tcp://dp:2375"),
            "k",
        )
        assertTrue(proxy.contains("RESTART_METHOD=proxy"))
        assertTrue(proxy.contains("TRAEFIK_CONTAINER=traefik"))
        assertTrue(proxy.contains("DOCKER_HOST=tcp://dp:2375"))
        assertTrue(proxy.none { it.startsWith("SIGNAL_FILE_PATH") })

        val pill = AgentCompose.envLines(
            AgentConfig(restartMethod = "poison-pill", signalFilePath = "/signals/restart"),
            "k",
        )
        assertTrue(pill.contains("SIGNAL_FILE_PATH=/signals/restart"))
        assertTrue(pill.none { it.startsWith("DOCKER_HOST") })
    }

    @Test
    fun `volumes fall back to a named backup volume and add the socket only for socket restarts`() {
        val plain = AgentCompose.volumeLines(AgentConfig())
        assertTrue(plain.contains("/app/config:/app/config"))
        assertTrue(plain.contains("tma_backups:/app/backups"))

        val withDir = AgentCompose.volumeLines(AgentConfig(backupDir = "/srv/backups"))
        assertTrue(withDir.contains("/srv/backups:/app/backups"))
        assertTrue(withDir.none { it.startsWith("tma_backups") })

        val socket = AgentCompose.volumeLines(AgentConfig(restartMethod = "socket"))
        assertTrue(socket.contains("/var/run/docker.sock:/var/run/docker.sock:ro"))

        val readOnly = AgentCompose.volumeLines(AgentConfig(acmeJsonPath = "/app/acme.json"))
        assertTrue(readOnly.contains("/app/acme.json:/app/acme.json:ro"))
    }

    @Test
    fun `the compose snippet declares the named volumes it references`() {
        val snippet = AgentCompose.compose(AgentConfig(restartMethod = "poison-pill"), "k")
        assertTrue(snippet.contains("traefik-signals:/signals"))
        assertTrue(snippet.contains("volumes:\n  tma_backups:"))
        assertTrue(snippet.contains("traefik-signals:"))
    }

    @Test
    fun `api keys expose the preview that revoke needs and flag legacy ones`() {
        val status = json.decodeFromString<ApiKeyStatus>(
            """{"enabled":true,"count":2,"keys":[
                 {"name":"Phone","preview":"abcd1234...ef56","created_at":"2026-08-01 09:14"},
                 {"name":"Legacy","preview":"","created_at":""}]}""",
        )
        assertEquals(2, status.keys.size)
        assertTrue(status.keys[0].revocable)
        assertFalse(status.keys[1].revocable)
    }

    @Test
    fun `notification severity falls back to info for anything unexpected`() {
        assertEquals("Warning", TmNotification(type = "warning").severity.name)
        assertEquals("Error", TmNotification(type = "error").severity.name)
        assertEquals("Success", TmNotification(type = "success").severity.name)
        assertEquals("Info", TmNotification(type = "wat").severity.name)
        assertEquals("Info", json.decodeFromString<TmNotification>("""{"type":null}""").severity.name)
    }

    @Test
    fun `notification timestamps are rendered verbatim in server time`() {
        val notification = TmNotification(ts = "2026-04-13 20:25:03", msg = "hi")
        assertEquals("13 Apr, 20:25:03", notification.stamp)
        assertEquals("nonsense", TmNotification(ts = "nonsense").stamp)
    }

    @Test
    fun `settings expose the tab map and secret flags without the secrets`() {
        val settings = json.decodeFromString<ServerSettings>(
            """{"domains":["a.com"],"cert_resolver":"le","visible_tabs":{"logs":true},
               "crowdsec_enabled":true,"crowdsec_lapi_url":"http://cs:8080"}""",
        )
        assertEquals(listOf("le"), settings.certResolvers)
        assertTrue(settings.tabVisible("logs"))
        assertTrue(settings.crowdsecEnabled)
        assertEquals("http://cs:8080", settings.crowdsecLapiUrl)
    }
}
