package dev.chr0nzz.traefikmanager.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.BackendMode
import dev.chr0nzz.traefikmanager.data.model.BackendServer
import dev.chr0nzz.traefikmanager.data.model.ConfigFile
import dev.chr0nzz.traefikmanager.data.model.HeadersPresetForm
import dev.chr0nzz.traefikmanager.data.model.HealthCheckConfig
import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.model.RouteForm
import dev.chr0nzz.traefikmanager.data.model.RouteProtocol
import dev.chr0nzz.traefikmanager.data.model.StickyConfig
import dev.chr0nzz.traefikmanager.data.model.TcpTlsMode
import dev.chr0nzz.traefikmanager.data.model.TlsOptionProfile
import dev.chr0nzz.traefikmanager.data.repo.DashboardRepository
import dev.chr0nzz.traefikmanager.data.repo.RoutesRepository
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RouteFormUiState(
    val form: RouteForm = RouteForm(),
    val loading: Boolean = true,
    val configFiles: List<ConfigFile> = emptyList(),
    val canCreateConfigFile: Boolean = false,
    val tlsProfiles: List<TlsOptionProfile> = emptyList(),
    val entryPointOptions: List<String> = emptyList(),
    val entryPointsUnavailable: Boolean = false,
    val domainOptions: List<String> = emptyList(),
    val certResolverOptions: List<String> = emptyList(),
    val serviceOptions: List<String> = emptyList(),
    val middlewareOptions: List<MiddlewareDef> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val protocolMiddlewares: List<MiddlewareDef>
        get() = middlewareOptions.filter { it.type.ifEmpty { "http" } == form.protocol.wire }
}

@HiltViewModel
class RouteFormViewModel @Inject constructor(
    private val routesRepository: RoutesRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RouteFormUiState())
    val state: StateFlow<RouteFormUiState> = _state.asStateFlow()

    private var editRouteId: String? = null
    private var optionsLoaded = false
    private var pendingRoute: Route? = null

    init {
        viewModelScope.launch { loadOptions() }
    }

    fun loadRoute(routeId: String) {
        if (editRouteId == routeId) return
        editRouteId = routeId
        if (!optionsLoaded) return
        viewModelScope.launch {
            val route = runCatching { routesRepository.load() }
                .getOrNull()
                ?.routes
                ?.firstOrNull { it.id == routeId }
                ?: return@launch
            pendingRoute = route
            _state.update { applyRoute(it, route) }
        }
    }

    private suspend fun loadOptions() = coroutineScope {
        val settingsAsync = async { routesRepository.serverSettings() }
        val configsAsync = async { routesRepository.configs() }
        val profilesAsync = async { routesRepository.tlsOptions() }
        val snapshotAsync = async { runCatching { routesRepository.load() }.getOrNull() }
        val resolversAsync = async { routesRepository.certResolvers() }
        val entryPointsAsync = async { routesRepository.entryPointNames() }

        val settings = settingsAsync.await()
        val configs = configsAsync.await()
        val profiles = profilesAsync.await()
        val snapshot = snapshotAsync.await()
        val resolvers = resolversAsync.await()
        val entryPoints = entryPointsAsync.await()

        if (pendingRoute == null) {
            pendingRoute = editRouteId?.let { id -> snapshot?.routes?.firstOrNull { it.id == id } }
        }
        optionsLoaded = true
        _state.update { current ->
            val isEdit = current.form.isEdit
            current.copy(
                loading = false,
                configFiles = configs.files,
                canCreateConfigFile = configs.configDirSet,
                tlsProfiles = profiles,
                entryPointOptions = entryPoints,
                entryPointsUnavailable = entryPoints.isEmpty(),
                domainOptions = mergedDomains(settings.domains, snapshot?.routes.orEmpty()),
                certResolverOptions = resolvers,
                serviceOptions = snapshot?.services?.forProtocol(current.form.protocol.wire).orEmpty(),
                middlewareOptions = snapshot?.middlewares.orEmpty(),
                form = if (isEdit) {
                    current.form
                } else {
                    current.form.copy(
                        configFile = current.form.configFile.ifEmpty {
                            configs.files.singleOrNull()?.label.orEmpty()
                        },
                        entryPoints = current.form.entryPoints.ifEmpty {
                            listOfNotNull(
                                entryPoints.firstOrNull { it.contains("sec") } ?: entryPoints.firstOrNull(),
                            )
                        },
                        domains = current.form.domains.ifEmpty {
                            listOfNotNull(settings.domains.firstOrNull())
                        },
                        certResolver = current.form.certResolver.ifEmpty { resolvers.firstOrNull().orEmpty() },
                        tlsEnabled = resolvers.isNotEmpty(),
                    )
                },
            )
        }
        applyPendingRoute()
    }

    private fun applyPendingRoute() {
        val route = pendingRoute ?: return
        _state.update { applyRoute(it, route) }
    }

    private fun mergedDomains(configured: List<String>, routes: List<Route>): List<String> {
        val derived = routes.flatMap { it.hosts }
            .mapNotNull { host -> configured.firstOrNull { host == it || host.endsWith(".$it") } }
        return (configured + derived).distinct()
    }

    private fun applyRoute(state: RouteFormUiState, route: Route): RouteFormUiState {
        val known = state.domainOptions
        val split = splitHosts(route.hosts, known)
        val plainRule = route.protocol == "http" && route.isPlainHostRule && split != null
        val sharedService = route.serviceName.isNotEmpty() &&
            route.serviceName.substringBefore('@') != "${route.name}-service"

        return state.copy(
            loading = false,
            serviceOptions = state.serviceOptions,
            form = RouteForm(
                name = route.name,
                protocol = RouteProtocol.from(route.protocol),
                backendMode = if (sharedService) BackendMode.ExistingService else BackendMode.Manual,
                serviceRef = if (sharedService) route.serviceName else "",
                advancedRule = route.protocol == "http" && !plainRule,
                httpRule = if (route.protocol == "http" && !plainRule) route.rule else "",
                subdomain = split?.subdomain.orEmpty(),
                domains = split?.domains ?: emptyList(),
                tcpRule = if (route.protocol == "tcp") route.rule else "",
                entryPoints = route.entryPointNames,
                middlewares = route.middlewareNames,
                passHostHeader = route.passHostHeader ?: true,
                insecureSkipVerify = route.insecureSkipVerify,
                certResolver = route.certResolver,
                tlsEnabled = route.tlsEnabled,
                tcpTlsMode = when {
                    route.protocol != "tcp" -> TcpTlsMode.None
                    route.tlsPassthrough -> TcpTlsMode.Passthrough
                    route.tlsEnabled -> TcpTlsMode.Terminate
                    else -> TcpTlsMode.None
                },
                wildcardEnabled = route.hasWildcard,
                tlsMainDomain = route.tlsDomains.firstOrNull()?.main.orEmpty(),
                tlsSans = route.tlsDomains.firstOrNull()?.sans.orEmpty(),
                tlsOptionsProfile = route.tlsOptionsProfile,
                backends = route.servers.ifEmpty { listOf(route.target) }
                    .mapNotNull(::parseBackend)
                    .ifEmpty { listOf(BackendServer()) },
                sticky = StickyConfig(
                    enabled = route.stickyEnabled,
                    cookieName = route.sticky?.name.orEmpty(),
                    secure = route.sticky?.secure ?: false,
                    httpOnly = route.sticky?.httpOnly ?: false,
                ),
                healthCheck = HealthCheckConfig(
                    enabled = route.healthCheck != null,
                    path = route.healthCheck?.path.orEmpty(),
                    interval = route.healthCheck?.interval.orEmpty(),
                    timeout = route.healthCheck?.timeout.orEmpty(),
                ),
                priority = route.priority,
                streamingPresent = route.protocol == "http" && route.provider == "file",
                streamingEnabled = route.streaming,
                headersPreset = route.headersPreset?.let { preset ->
                    HeadersPresetForm(
                        present = true,
                        enabled = preset.state != "off",
                        custom = preset.state == "custom",
                        perms = preset.toggles.perms.ifEmpty { HeadersPresetForm().perms },
                        hsts = preset.toggles.hsts,
                        nosniff = preset.toggles.nosniff,
                        frameDeny = preset.toggles.frameDeny,
                        referrer = preset.toggles.referrer.ifEmpty { HeadersPresetForm().referrer },
                    )
                } ?: HeadersPresetForm(),
                serviceType = route.serviceType,
                configFile = route.configFile,
                isEdit = true,
                originalId = route.id,
                originalName = route.name,
            ),
        )
    }

    data class HostSplit(val subdomain: String, val domains: List<String>)

    fun setProtocol(protocol: RouteProtocol) = _state.update { current ->
        current.copy(
            form = current.form.copy(protocol = protocol),
            serviceOptions = emptyList(),
        ).also { viewModelScope.launch { refreshServiceOptions(protocol) } }
    }

    private suspend fun refreshServiceOptions(protocol: RouteProtocol) {
        val snapshot = runCatching { routesRepository.load() }.getOrNull() ?: return
        _state.update { it.copy(serviceOptions = snapshot.services.forProtocol(protocol.wire)) }
    }

    fun setAdvancedRule(enabled: Boolean) {
        if (enabled) {
            update { form ->
                form.copy(
                    advancedRule = true,
                    httpRule = form.httpRule.ifEmpty { form.currentHostRule() },
                )
            }
            return
        }
        _state.update { current ->
            val hosts = HOST_RULE.findAll(current.form.httpRule).map { it.groupValues[1] }.toList()
            val split = splitHosts(hosts, current.domainOptions)
            current.copy(
                form = current.form.copy(
                    advancedRule = false,
                    httpRule = "",
                    subdomain = split?.subdomain ?: current.form.subdomain,
                    domains = split?.domains ?: current.form.domains,
                ),
            )
        }
    }

    private fun RouteForm.currentHostRule(): String {
        val sub = subdomain.trim()
        val hosts = domains.map { domain ->
            when {
                sub.isEmpty() -> domain
                sub.contains('.') -> sub
                else -> "$sub.$domain"
            }
        }.distinct()
        return hosts.joinToString(" || ") { "Host(`$it`)" }
    }

    fun setBackendMode(mode: BackendMode) = update { it.copy(backendMode = mode) }

    fun update(transform: (RouteForm) -> RouteForm) {
        _state.update { it.copy(form = transform(it.form), error = null) }
    }

    fun updatePreset(transform: (HeadersPresetForm) -> HeadersPresetForm) = update { form ->
        form.copy(headersPreset = transform(form.headersPreset).copy(custom = false))
    }

    fun setPresetEnabled(enabled: Boolean) = update { form ->
        form.copy(
            headersPreset = form.headersPreset.copy(present = true, enabled = enabled),
            middlewares = if (enabled) {
                form.middlewares
            } else {
                form.middlewares.filterNot { it.substringBefore('@') == "${form.name}-headers" }
            },
        )
    }

    fun addBackend() = update { it.copy(backends = it.backends + BackendServer()) }

    fun removeBackend(index: Int) = update {
        it.copy(
            backends = it.backends
                .filterIndexed { position, _ -> position != index }
                .ifEmpty { listOf(BackendServer()) },
        )
    }

    fun updateBackend(index: Int, transform: (BackendServer) -> BackendServer) = update { form ->
        form.copy(
            backends = form.backends.mapIndexed { position, backend ->
                if (position == index) transform(backend) else backend
            },
        )
    }

    fun updateSticky(transform: (StickyConfig) -> StickyConfig) = update { it.copy(sticky = transform(it.sticky)) }

    fun updateHealthCheck(transform: (HealthCheckConfig) -> HealthCheckConfig) =
        update { it.copy(healthCheck = transform(it.healthCheck)) }

    fun save() {
        val form = _state.value.form
        val problem = form.validationError
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { routesRepository.save(form) }.fold(
                onSuccess = { _state.update { it.copy(saving = false, saved = true) } },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(saving = false, error = throwable.message ?: "Could not save the route")
                    }
                },
            )
        }
    }

    companion object {

        private val HOST_RULE = Regex("Host\\(`([^`]+)`\\)")

        fun splitHosts(hosts: List<String>, knownDomains: List<String>): HostSplit? {
            if (hosts.isEmpty()) return null
            val pairs = hosts.map { host ->
                val base = knownDomains
                    .filter { host == it || host.endsWith(".$it") }
                    .maxByOrNull { it.length }
                    ?: return null
                val sub = if (host == base) "" else host.removeSuffix(".$base")
                sub to base
            }
            val subdomains = pairs.map { it.first }.distinct()
            if (subdomains.size > 1) return null
            return HostSplit(subdomain = subdomains.first(), domains = pairs.map { it.second }.distinct())
        }

        fun parseBackend(raw: String): BackendServer? {
            if (raw.isBlank() || raw == "N/A" || raw == "Unknown") return null
            val scheme = if (raw.contains("://")) raw.substringBefore("://") else "http"
            val rest = raw.substringAfter("://", raw)
            if (rest.contains('/')) return BackendServer(scheme = scheme, host = rest, port = "")
            val tail = rest.substringAfterLast(':', "")
            return if (tail.isNotEmpty() && tail.all(Char::isDigit) && !rest.endsWith("]")) {
                BackendServer(scheme = scheme, host = rest.substringBeforeLast(':'), port = tail)
            } else {
                BackendServer(scheme = scheme, host = rest, port = "")
            }
        }
    }
}
