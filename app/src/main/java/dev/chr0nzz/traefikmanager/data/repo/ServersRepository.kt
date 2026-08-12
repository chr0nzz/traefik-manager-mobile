package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.Agent
import dev.chr0nzz.traefikmanager.data.model.AgentHealth
import dev.chr0nzz.traefikmanager.data.model.CreateAgentRequest
import dev.chr0nzz.traefikmanager.data.store.PreferencesStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class ServerEntry(
    val id: String?,
    val name: String,
    val url: String = "",
    val health: AgentHealth? = null,
    val agent: Agent? = null,
) {
    val isHost: Boolean get() = id == null

    /** Health is only probed for agents; the host is reachable by definition of being connected. */
    val reachable: Boolean get() = isHost || health?.ok == true

    val detail: String
        get() = when {
            isHost -> "This Traefik Manager"
            health == null -> url
            health.ok -> listOfNotNull(
                health.version?.let { "v$it" },
                health.latencyMs?.let { "${it}ms" },
            ).joinToString(" · ").ifEmpty { url }
            else -> health.error ?: "unreachable"
        }
}

@Singleton
class ServersRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val agentsRepository: AgentsRepository,
    private val preferencesStore: PreferencesStore,
) {

    /** The host plus every configured agent, each agent probed for health in parallel. */
    suspend fun servers(probeHealth: Boolean = true): List<ServerEntry> = coroutineScope {
        val agents = runCatching { agentsRepository.agents() }.getOrDefault(emptyList())
        val host = ServerEntry(id = null, name = "Host")
        if (!probeHealth || agents.isEmpty()) {
            return@coroutineScope listOf(host) + agents.map { ServerEntry(it.id, it.name, it.url, agent = it) }
        }
        val probes = agents.map { agent ->
            async {
                ServerEntry(
                    id = agent.id,
                    name = agent.name.ifEmpty { agent.id },
                    url = agent.url,
                    health = runCatching { apiProvider.api().agentHealth(agent.id) }.getOrNull(),
                    agent = agent,
                )
            }
        }
        listOf(host) + probes.map { it.await() }
    }

    suspend fun select(id: String?) = preferencesStore.setActiveAgent(id)

    suspend fun create(name: String, url: String): String? {
        require(name.isNotBlank() && url.isNotBlank()) { "A name and a URL are required" }
        val response = apiProvider.api().createAgent(CreateAgentRequest(name.trim(), url.trim().trimEnd('/')))
        if (!response.ok) error(response.error ?: "Could not add the server")
        return response.rawKey
    }

    /**
     * Sends only the changed keys. Blank name or url would make the hub drop the record on the
     * next read, so those two are refused here rather than silently deleting the server.
     */
    suspend fun update(id: String, changes: Map<String, JsonElement>) {
        val name = (changes["name"] as? JsonPrimitive)?.content
        val url = (changes["url"] as? JsonPrimitive)?.content
        require(name == null || name.isNotBlank()) { "The name cannot be empty" }
        require(url == null || url.isNotBlank()) { "The URL cannot be empty" }
        val response = apiProvider.api().updateAgent(id, JsonObject(changes))
        if (!response.ok) error(response.error ?: "Could not save the server")
    }

    /** Clears the selection first, so nothing keeps proxying to an id the hub no longer knows. */
    suspend fun delete(id: String) {
        if (preferencesStore.preferences.first().activeAgentId == id) preferencesStore.setActiveAgent(null)
        val response = apiProvider.api().deleteAgent(id)
        if (!response.ok) error(response.error ?: "Could not remove the server")
    }

    suspend fun rotateKey(id: String): String {
        val response = apiProvider.api().rotateAgentKey(id)
        if (!response.ok) error(response.error ?: "Could not rotate the key")
        return response.rawKey ?: error("The server did not return a new key")
    }

    /** Drops a stored selection that no longer exists, so the app never proxies to a dead id. */
    suspend fun reconcileActive(agents: List<Agent>): Boolean {
        val active = preferencesStore.preferences.first().activeAgentId ?: return false
        if (agents.any { it.id == active }) return false
        preferencesStore.setActiveAgent(null)
        return true
    }
}
