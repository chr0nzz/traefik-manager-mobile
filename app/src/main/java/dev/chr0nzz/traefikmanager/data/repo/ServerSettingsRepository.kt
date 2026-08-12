package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.ServerSettings
import dev.chr0nzz.traefikmanager.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Server capabilities, including which optional tabs the server has switched on.
 * Null means "not known yet" - callers show everything rather than hiding a working tab.
 */
@Singleton
class ServerSettingsRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val _settings = MutableStateFlow<ServerSettings?>(null)
    val settings: StateFlow<ServerSettings?> = _settings.asStateFlow()

    fun refresh() {
        scope.launch { load() }
    }

    suspend fun load(): ServerSettings? {
        val fetched = runCatching { apiProvider.api().settings() }.getOrNull()
        if (fetched != null) _settings.value = fetched
        return fetched
    }
}
