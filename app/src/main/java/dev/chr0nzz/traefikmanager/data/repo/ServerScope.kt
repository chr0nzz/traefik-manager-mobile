package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.api.ApiState
import dev.chr0nzz.traefikmanager.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One counter every cache and screen watches, bumped whenever the app starts pointing at a
 * different Traefik instance. Data fetched for one server must never be shown under another,
 * so a switch clears the shared caches and every screen reloads from the new target.
 */
@Singleton
class ServerScope @Inject constructor(
    apiProvider: ApiProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val _generation = MutableStateFlow(0)

    /** Increments on every server change. Screens reload when it does. */
    val generation: StateFlow<Int> = _generation.asStateFlow()

    /** The active agent id, or null for the host. */
    val activeAgentId: StateFlow<String?> = apiProvider.state
        .map { (it as? ApiState.Ready)?.agentId }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Everything that decides which instance the data came from. Switching agents is not the only
     * way to change servers: disconnecting and connecting to a different manager, or dropping into
     * the demo, has to clear the caches too. Disconnected gaps are ignored so reconnecting to the
     * same server keeps what it already had.
     */
    private val target: StateFlow<String?> = apiProvider.state
        .mapNotNull { state -> (state as? ApiState.Ready)?.let { "${it.demo}|${it.baseUrl}|${it.agentId}" } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val listeners = mutableListOf<() -> Unit>()

    init {
        scope.launch {
            // The initial null and the first server the app lands on are not changes.
            target.filterNotNull().drop(1).collect {
                synchronized(listeners) { listeners.toList() }.forEach { it() }
                _generation.value += 1
            }
        }
    }

    /** Registered by caches that hold per-server data; called before the generation bumps. */
    fun onServerChanged(block: () -> Unit) {
        synchronized(listeners) { listeners += block }
    }
}
