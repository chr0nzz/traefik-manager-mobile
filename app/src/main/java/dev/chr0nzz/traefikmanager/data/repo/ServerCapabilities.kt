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

    fun tabEnabled(tab: String): Boolean = when {
        !isHost -> agent?.visibleTabs?.get(tab) == true
        else -> settings?.visibleTabs?.get(tab) == true
    }

    val crowdsecConfigured: Boolean
        get() = when {
            !isHost -> true
            settings == null -> true
            else -> settings.crowdsecEnabled
        }

    val hostOnlySettings: Boolean get() = isHost
}
