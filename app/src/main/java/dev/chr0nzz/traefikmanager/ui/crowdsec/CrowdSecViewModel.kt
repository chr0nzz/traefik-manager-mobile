package dev.chr0nzz.traefikmanager.ui.crowdsec

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.AddDecisionRequest
import dev.chr0nzz.traefikmanager.data.model.Countries
import dev.chr0nzz.traefikmanager.data.model.CountryCount
import dev.chr0nzz.traefikmanager.data.model.CrowdSecAnalytics
import dev.chr0nzz.traefikmanager.data.model.CrowdSecSnapshot
import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.data.model.CsReads
import dev.chr0nzz.traefikmanager.data.model.CsFacets
import dev.chr0nzz.traefikmanager.data.model.CsFacet
import dev.chr0nzz.traefikmanager.data.model.CsRead
import dev.chr0nzz.traefikmanager.data.repo.ServerScope
import dev.chr0nzz.traefikmanager.data.repo.CrowdSecRepository
import dev.chr0nzz.traefikmanager.data.repo.GeoRepository
import javax.inject.Inject
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
            append(alert.source.range)
        }.lowercase()
        return hay.contains(needle)
    }

    val visibleAlerts: List<CsAlert>
        get() = alerts
            .filter { facets.matches(it, ::countryOf, snapshot::handled) && matchesQuery(it) }
            .sortedByDescending { it.startMillis }

    fun alertsFor(facet: CsFacet): List<CsAlert> = alerts.filter {
        facets.matches(it, ::countryOf, snapshot::handled, skip = facet) && matchesQuery(it)
    }

    val visibleDecisions: List<CsDecision>
        get() {
            val needle = query.trim().lowercase()
            return decisions.filter { decision ->
                val matchesQuery = needle.isEmpty() ||
                    decision.value.lowercase().contains(needle) ||
                    decision.scenario.lowercase().contains(needle) ||
                    decision.originKey.contains(needle) ||
                    decision.scope.lowercase().contains(needle) ||
                    decision.type.lowercase().contains(needle)
                facets.matches(decision) && matchesQuery
            }.sortedWith(compareByDescending<CsDecision> { it.own }.thenByDescending { it.id })
        }

    val countries: List<CountryCount>
        get() {
            val tally = mutableMapOf<String, Int>()
            alertsFor(CsFacet.Country).forEach { alert ->
                val code = countryOf(alert)
                if (code.isNotEmpty()) tally[code] = (tally[code] ?: 0) + 1
            }
            return tally.map { CountryCount(it.key, Countries.name(it.key), it.value) }
                .sortedWith(compareByDescending<CountryCount> { it.count }.thenBy { it.name })
        }

    val ownBans: Int get() = decisions.count { it.own }

    val subscribedBans: Int get() = decisions.size - ownBans
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

    init {
        showCachedThenRevalidate()
        watchServerChanges()
    }

    fun refresh() = load(initial = false, full = true)

    private fun showCachedThenRevalidate() {
        val cached = repository.cached()
        if (cached == null) {
            load(initial = true, full = false)
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
        load(initial = false, full = false)
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onViewChange(view: CrowdSecView) = _state.update {
        viewFollowsFacets = false
        it.copy(view = view)
    }

    fun toggleFacet(facet: CsFacet, value: String) = _state.update { state ->
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

    fun onCountryChange(code: String?) = toggleFacet(CsFacet.Country, code.orEmpty())

    fun onScenarioChange(name: String?) = toggleFacet(CsFacet.Scenario, name.orEmpty())

    fun removeFacet(facet: CsFacet) = _state.update { it.copy(facets = it.facets.without(facet)) }

    fun clearFilters() = _state.update {
        viewFollowsFacets = false
        queryState.edit { replace(0, length, "") }
        it.copy(facets = it.facets.clear())
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

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
                    _state.update { it.copy(saving = false, message = "Decision added for ${value.trim()}") }
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
                    _state.update { it.copy(message = "${decision.value} unbanned") }
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
                _state.value = CrowdSecUiState(view = _state.value.view)
                showCachedThenRevalidate()
            }
        }
    }
}
