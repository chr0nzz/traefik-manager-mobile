package dev.chr0nzz.traefikmanager.data.api

import dev.chr0nzz.traefikmanager.data.store.ConnectionStore
import dev.chr0nzz.traefikmanager.data.store.LegacyCredentialsMigration
import dev.chr0nzz.traefikmanager.data.store.PreferencesStore
import dev.chr0nzz.traefikmanager.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ApiState {
    data object Loading : ApiState
    data object Disconnected : ApiState
    data class Ready(
        val api: TmApi,
        val baseUrl: String,
        val demo: Boolean,
        val agentId: String?,
        val apiKey: String? = null,
    ) : ApiState
}

@Singleton
class ApiProvider @Inject constructor(
    private val factory: ApiFactory,
    private val connectionStore: ConnectionStore,
    private val preferencesStore: PreferencesStore,
    private val migration: LegacyCredentialsMigration,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    val state: StateFlow<ApiState> = flow {
        migration.runIfNeeded()
        emitAll(
            combine(
                connectionStore.connection,
                preferencesStore.preferences.map { it.activeAgentId }.distinctUntilChanged(),
            ) { connection, agentId ->
                when {
                    connection == null -> ApiState.Disconnected
                    connection.demo -> ApiState.Ready(DemoApi(), connection.baseUrl, true, null)
                    else -> ApiState.Ready(
                        api = factory.create(connection.baseUrl, connection.apiKey, agentId),
                        baseUrl = connection.baseUrl,
                        demo = false,
                        agentId = agentId,
                        apiKey = connection.apiKey,
                    )
                }
            },
        )
    }.stateIn(scope, SharingStarted.Eagerly, ApiState.Loading)

    suspend fun ready(): ApiState.Ready =
        state.first { it is ApiState.Ready } as ApiState.Ready

    suspend fun api(): TmApi = ready().api

    /**
     * A client aimed at one particular server, whatever the app has selected. The widgets need
     * this: a home screen can hold one widget watching the host and another watching an agent,
     * and neither should move when the app switches.
     */
    suspend fun apiFor(agentId: String?): TmApi {
        val ready = ready()
        if (ready.demo) return ready.api
        if (agentId == ready.agentId) return ready.api
        return factory.create(ready.baseUrl, ready.apiKey, agentId)
    }
}
