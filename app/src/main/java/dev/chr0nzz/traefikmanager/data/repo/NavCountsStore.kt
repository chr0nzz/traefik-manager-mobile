package dev.chr0nzz.traefikmanager.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class NavCountsStore @Inject constructor(
    serverScope: ServerScope,
) {

    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    init {
        serverScope.onServerChanged { _counts.value = emptyMap() }
    }

    fun report(key: String, count: Int) {
        _counts.update { current -> if (current[key] == count) current else current + (key to count) }
    }

    companion object {
        const val ROUTES = "routes"
        const val MIDDLEWARES = "middlewares"
        const val SERVICES = "services"
        const val CERTIFICATES = "certificates"
        const val PLUGINS = "plugins"
        const val CROWDSEC = "crowdsec"
    }
}
