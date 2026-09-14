package dev.chr0nzz.traefikmanager.ui.crowdsec

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.AddDecisionRequest
import dev.chr0nzz.traefikmanager.data.model.Countries
import dev.chr0nzz.traefikmanager.data.model.CountryCount
import dev.chr0nzz.traefikmanager.data.model.CrowdSecSnapshot
import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.data.model.CsDecisionFeed
import dev.chr0nzz.traefikmanager.data.model.CsDecisionQuery
import dev.chr0nzz.traefikmanager.data.model.CsReads
import dev.chr0nzz.traefikmanager.data.model.CsFacets
import dev.chr0nzz.traefikmanager.data.model.CsFacet
import dev.chr0nzz.traefikmanager.data.model.CsRead
import dev.chr0nzz.traefikmanager.data.repo.ServerScope
import dev.chr0nzz.traefikmanager.data.repo.CrowdSecRepository
import dev.chr0nzz.traefikmanager.data.repo.GeoRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

enum class CrowdSecView(val label: String) {
    Evidence("Attack evidence"),
    Bans("Bans in force"),
}

data class CrowdSecUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val snapshot: CrowdSecSnapshot = CrowdSecSnapshot(),
    val view: CrowdSecView = CrowdSecView.Evidence,
    val query: String = "",
    val facets: CsFacets = CsFacets(),
    val countryByIp: Map<String, String> = emptyMap(),
    val geoEnabled: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val loadError: String? = null,
    val notConfigured: Boolean = false,
    val readAt: Long? = null,
    val decisionFeed: CsDecisionFeed? = null,
) {
    val alerts: List<CsAlert> get() = snapshot.alertList

    val decisions: List<CsDecision> get() = snapshot.decisionList

    val alertsOk: Boolean get() = snapshot.alerts.ok

    val decisionsOk: Boolean get() = snapshot.decisions.ok

    val alertsError: String?
        get() = (snapshot.alerts as? CsRead.Failed)?.message

    val decisionsError: String?
        get() = (snapshot.decisions as? CsRead.Failed)?.message

    val filtersActive: Boolean get() = !facets.isEmpty || query.isNotEmpty()

    val country: String? get() = facets[CsFacet.Country]

    fun countryOf(alert: CsAlert): String = alert.countryCode.ifEmpty { countryByIp[alert.ip].orEmpty() }

    fun matchesQuery(alert: CsAlert): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        val hay = buildString {
            append(alert.ip).append(' ')
            append(alert.scenarioName).append(' ')
            append(alert.source.asName).append(' ')
            append(countryOf(alert)).append(' ')
            append(alert.message).append(' ')
            append(alert.machineId).append(' ')
            append(alert.uris.joinToString(" ")).append(' ')
            append(alert.userAgents.joinToString(" ")).append(' ')
            append(alert.users.joinToString(" ")).append(' ')
            append(alert.source.range).append(' ')
            append(alert.routers.joinToString(" ")).append(' ')
            append(alert.hosts.joinToString(" "))
        }.lowercase()
        return hay.contains(needle)
    }

    val visibleAlerts: List<CsAlert> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        alerts
            .filter { facets.matches(it, ::countryOf, snapshot::handled) && matchesQuery(it) }
            .sortedByDescending { it.startMillis }
    }

    fun alertsFor(facet: CsFacet): List<CsAlert> = alerts.filter {
        facets.matches(it, ::countryOf, snapshot::handled, skip = facet) && matchesQuery(it)
    }

    val visibleDecisions: List<CsDecision> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val needle = query.trim().lowercase()
        decisions.filter { decision ->
            val matchesQuery = needle.isEmpty() ||
                decision.value.lowercase().contains(needle) ||
                decision.scenario.lowercase().contains(needle) ||
                decision.originKey.contains(needle) ||
                decision.scope.lowercase().contains(needle) ||
                decision.type.lowercase().contains(needle)
            facets.matches(decision) && matchesQuery
        }.sortedWith(compareByDescending<CsDecision> { it.own }.thenByDescending { it.id })
    }

    val decisionQuery: CsDecisionQuery
        get() = facets.decisionQuery(if (view == CrowdSecView.Bans) query else "")

    val decisionsNeeded: Boolean get() = view == CrowdSecView.Bans || decisionQuery.filtered

    val feed: CsDecisionFeed?
        get() = decisionFeed?.takeIf {
            snapshot.serverSearch && it.query == decisionQuery && it.version == snapshot.version
        }

    val bansCount: Int?
        get() {
            if (!decisionsOk) return null
            if (!snapshot.serverSearch) return visibleDecisions.size
            val wanted = decisionQuery
            if (!wanted.filtered && wanted.q == null) return snapshot.decisionTotal
            return feed?.takeIf { it.loaded }?.total
        }

    val countries: List<CountryCount> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val tally = mutableMapOf<String, Int>()
        alertsFor(CsFacet.Country).forEach { alert ->
            val code = countryOf(alert)
            if (code.isNotEmpty()) tally[code] = (tally[code] ?: 0) + 1
        }
        tally.map { CountryCount(it.key, Countries.name(it.key), it.value) }
            .sortedWith(compareByDescending<CountryCount> { it.count }.thenBy { it.name })
    }
}

@HiltViewModel
class CrowdSecViewModel @Inject constructor(
    private val repository: CrowdSecRepository,
    private val geoRepository: GeoRepository,
    private val serverScope: ServerScope,
) : ViewModel() {

    private var viewFollowsFacets = false

    private val _state = MutableStateFlow(CrowdSecUiState())
    val state: StateFlow<CrowdSecUiState> = _state.asStateFlow()

    val queryState = TextFieldState()

    private val loadLock = Mutex()

    private var feedJob: Job? = null

    init {
        showCachedThenRevalidate()
        watchServerChanges()
    }

    fun refresh() {
        _state.update { it.copy(decisionFeed = null) }
        load(initial = false, full = true)
    }

    private fun showCachedThenRevalidate() {
        val cached = repository.cached()
        if (cached == null) {
            viewModelScope.launch {
                val stored = runCatching { repository.restore() }.getOrNull()
                if (stored != null && _state.value.snapshot.alertList.isEmpty()) {
                    _state.update { it.copy(loading = false, refreshing = true, snapshot = stored) }
                }
                load(initial = stored == null, full = false)
            }
            return
        }
        _state.update {
            it.copy(
                loading = false,
                refreshing = true,
                snapshot = cached,
                notConfigured = false,
                loadError = null,
                readAt = repository.cachedAge()?.let { age -> System.currentTimeMillis() - age },
            )
        }
        syncDecisions()
        load(initial = false, full = false)
    }

    fun onQueryChange(value: String) {
        if (value == _state.value.query) return
        _state.update { it.copy(query = value) }
        syncDecisions(delayMs = QUERY_DEBOUNCE_MS)
    }

    fun onViewChange(view: CrowdSecView) {
        _state.update {
            viewFollowsFacets = false
            it.copy(view = view)
        }
        syncDecisions()
    }

    fun toggleFacet(facet: CsFacet, value: String) {
        _state.update { state ->
            val facets = state.facets.toggle(facet, value)
            val applied = facets[facet] != null
            val view = when {
                !applied -> if (viewFollowsFacets && facets.isEmpty) {
                    viewFollowsFacets = false
                    CrowdSecView.Evidence
                } else {
                    state.view
                }
                facet.reads == CsReads.Decisions && state.view != CrowdSecView.Bans -> {
                    viewFollowsFacets = true
                    CrowdSecView.Bans
                }
                facet.reads == CsReads.Alerts && state.view != CrowdSecView.Evidence -> {
                    viewFollowsFacets = true
                    CrowdSecView.Evidence
                }
                else -> state.view
            }
            state.copy(facets = facets, view = view)
        }
        syncDecisions()
    }

    fun onCountryChange(code: String?) = toggleFacet(CsFacet.Country, code.orEmpty())

    fun onScenarioChange(name: String?) = toggleFacet(CsFacet.Scenario, name.orEmpty())

    fun removeFacet(facet: CsFacet) {
        _state.update { it.copy(facets = it.facets.without(facet)) }
        syncDecisions()
    }

    fun clearFilters() {
        _state.update {
            viewFollowsFacets = false
            queryState.edit { replace(0, length, "") }
            it.copy(facets = it.facets.clear())
        }
        syncDecisions()
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun loadMoreDecisions() {
        val feed = _state.value.feed ?: return
        if (!feed.more || feed.loading) return
        _state.update { it.copy(decisionFeed = feed.copy(loading = true, error = null)) }
        feedJob = viewModelScope.launch { fetchDecisions(feed.query, feed.version, feed.page + 1) }
    }

    fun addDecision(value: String, type: String, duration: String, reason: String) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                repository.addDecision(
                    AddDecisionRequest(
                        value = value.trim(),
                        type = type,
                        duration = duration,
                        reason = reason.ifBlank { "manual ban from Traefik Manager" },
                    ),
                )
            }.fold(
                onSuccess = {
                    _state.update {
                        it.copy(saving = false, message = "Decision added for ${value.trim()}", decisionFeed = null)
                    }
                    load(initial = false, full = true)
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(saving = false, message = throwable.message ?: "Could not add the decision")
                    }
                },
            )
        }
    }

    fun deleteDecision(decision: CsDecision) {
        viewModelScope.launch {
            runCatching { repository.deleteDecision(decision.id) }.fold(
                onSuccess = {
                    _state.update { it.copy(message = "${decision.value} unbanned", decisionFeed = null) }
                    load(initial = false, full = true)
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(message = throwable.message ?: "Could not delete the decision")
                    }
                },
            )
        }
    }

    private fun syncDecisions(delayMs: Long = 0) {
        val state = _state.value
        val snapshot = state.snapshot
        if (!snapshot.serverSearch || !snapshot.decisions.ok || !state.decisionsNeeded) return
        val query = state.decisionQuery
        val current = state.decisionFeed
        if (current != null && current.query == query && current.version == snapshot.version) return
        feedJob?.cancel()
        _state.update { it.copy(decisionFeed = CsDecisionFeed(query, snapshot.version, loading = true)) }
        feedJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            fetchDecisions(query, snapshot.version, 1)
        }
    }

    private suspend fun fetchDecisions(query: CsDecisionQuery, version: String?, page: Int) {
        val result = repository.searchDecisions(query, page)
        _state.update { state ->
            val feed = state.decisionFeed
            if (feed == null || feed.query != query || feed.version != version) return@update state
            when (result) {
                is CsRead.Loaded -> state.copy(decisionFeed = feed.accept(result.value))
                is CsRead.Failed -> state.copy(decisionFeed = feed.copy(loading = false, error = result.message))
                CsRead.NotConfigured -> state.copy(
                    decisionFeed = feed.copy(loading = false, error = "Decisions unavailable"),
                )
            }
        }
    }

    private fun load(initial: Boolean, full: Boolean) {
        _state.update { it.copy(loading = initial, refreshing = !initial, loadError = null) }
        viewModelScope.launch {
            if (!loadLock.tryLock()) return@launch
            try {
                runCatching { repository.load(full) }.fold(
                    onSuccess = { snapshot ->
                        val notConfigured = snapshot.decisions is CsRead.NotConfigured &&
                            snapshot.alerts is CsRead.NotConfigured
                        _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                snapshot = snapshot,
                                notConfigured = notConfigured,
                                loadError = null,
                                readAt = System.currentTimeMillis(),
                            )
                        }
                        resolveCountries(snapshot)
                    },
                    onFailure = { throwable ->
                        _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                loadError = throwable.message ?: "Could not reach the CrowdSec LAPI",
                            )
                        }
                    },
                )
                syncDecisions()
            } finally {
                loadLock.unlock()
            }
        }
    }

    private fun resolveCountries(snapshot: CrowdSecSnapshot) {
        val alerts = snapshot.alertList
        if (alerts.isEmpty() || alerts.any { it.countryCode.isNotEmpty() }) return
        viewModelScope.launch {
            val status = geoRepository.status()
            if (!status.usable) {
                _state.update { it.copy(geoEnabled = false) }
                return@launch
            }
            val codes = geoRepository.lookup(alerts.map { it.ip })
            _state.update { it.copy(geoEnabled = true, countryByIp = it.countryByIp + codes) }
        }
    }

    private fun watchServerChanges() {
        viewModelScope.launch {
            serverScope.generation.drop(1).collect {
                feedJob?.cancel()
                _state.value = CrowdSecUiState(view = _state.value.view)
                showCachedThenRevalidate()
            }
        }
    }

    private companion object {
        const val QUERY_DEBOUNCE_MS = 300L
    }
}
