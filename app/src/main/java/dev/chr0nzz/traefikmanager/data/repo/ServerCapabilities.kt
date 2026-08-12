package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.model.Agent
import dev.chr0nzz.traefikmanager.data.model.ServerSettings

/**
 * Which optional tabs and settings panels the *currently selected* server offers.
 *
 * The host answers for itself through GET /api/settings, but that endpoint strips agents and
 * reports host preferences, so an agent must be described by its own record instead. Unknown
 * always resolves to visible: hiding a working screen is worse than showing an empty one.
 */
data class ServerCapabilities(
    val isHost: Boolean = true,
    val name: String = "Host",
    private val settings: ServerSettings? = null,
    private val agent: Agent? = null,
) {
    fun tabVisible(tab: String): Boolean = when {
        !isHost -> agent?.tabVisible(tab) ?: true
        settings == null -> true
        else -> settings.tabVisible(tab)
    }

    /**
     * The host knows its own CrowdSec state. An agent does not report one: the record's
     * crowdsec_lapi_url only ever feeds the generated compose file and is never pushed to the
     * running agent, so an operator who set CROWDSEC_LAPI_URL in the container's env has a
     * working CrowdSec and an empty field. Always offer the screen on an agent and let a 404
     * from the LAPI call say "not configured", exactly as the web does.
     */
    val crowdsecConfigured: Boolean
        get() = when {
            !isHost -> true
            settings == null -> true
            else -> settings.crowdsecEnabled
        }

    /** Auth, connection and notification settings belong to the host, never to an agent. */
    val hostOnlySettings: Boolean get() = isHost
}
