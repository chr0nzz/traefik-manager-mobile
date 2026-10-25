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

@Singleton
class ServerScope @Inject constructor(
    apiProvider: ApiProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val _generation = MutableStateFlow(0)

    val generation: StateFlow<Int> = _generation.asStateFlow()

    val activeAgentId: StateFlow<String?> = apiProvider.state
        .map { (it as? ApiState.Ready)?.agentId }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val target: StateFlow<String?> = apiProvider.state
        .mapNotNull { state -> (state as? ApiState.Ready)?.let { "${it.demo}|${it.baseUrl}|${it.agentId}" } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val listeners = mutableListOf<() -> Unit>()

    init {
        scope.launch {
            target.filterNotNull().drop(1).collect {
                synchronized(listeners) { listeners.toList() }.forEach { it() }
                _generation.value += 1
            }
        }
    }

    fun onServerChanged(block: () -> Unit) {
        synchronized(listeners) { listeners += block }
    }
}
