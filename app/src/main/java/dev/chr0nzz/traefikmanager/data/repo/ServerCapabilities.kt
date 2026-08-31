package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.model.Agent
import dev.chr0nzz.traefikmanager.data.model.ServerSettings

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

    val crowdsecConfigured: Boolean
        get() = when {
            !isHost -> true
            settings == null -> true
            else -> settings.crowdsecEnabled
        }

    val hostOnlySettings: Boolean get() = isHost
}
