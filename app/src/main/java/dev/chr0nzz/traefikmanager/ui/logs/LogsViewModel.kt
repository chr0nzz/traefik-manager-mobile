package dev.chr0nzz.traefikmanager.ui.logs

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.Countries
import dev.chr0nzz.traefikmanager.data.model.CountryCount
import dev.chr0nzz.traefikmanager.data.model.LogAnalytics
import dev.chr0nzz.traefikmanager.data.model.LogEntry
import dev.chr0nzz.traefikmanager.data.model.LogFormat
import dev.chr0nzz.traefikmanager.data.model.LogLine
import dev.chr0nzz.traefikmanager.data.model.LogParser
import dev.chr0nzz.traefikmanager.data.model.LogWindow
import dev.chr0nzz.traefikmanager.data.repo.GeoRepository
import dev.chr0nzz.traefikmanager.data.repo.LogsRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val LOG_LINE_STEPS = listOf(100, 200, 500, 1000)

private const val AUTO_REFRESH_FAST_MILLIS = 10_000L
private const val AUTO_REFRESH_SLOW_MILLIS = 30_000L

enum class LogFacet(val key: String) {
    Status("status"),
    Method("method"),
    Domain("domain"),
    Path("path"),
    Ip("ip"),
    Service("service"),
    Duration("dur"),
}

data class LogsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val lines: List<LogLine> = emptyList(),
    val lineCount: Int = 100,
    val query: String = "",
    val facets: Map<LogFacet, String> = emptyMap(),
    val country: String? = null,
    val autoRefresh: Boolean = false,
    val geoEnabled: Boolean = false,
    val countryByIp: Map<String, String> = emptyMap(),
    val serverError: String? = null,
    val loadError: String? = null,
    val lastLoadedAt: Long = 0L,
) {
    val parsedEntries: List<LogEntry> by lazy { lines.mapNotNull { it.entry } }

    val fetched: Int get() = lines.size

    val unparsed: Int get() = lines.count { it.entry == null }

    val selected: Boolean get() = facets.isNotEmpty() || country != null || query.isNotBlank()

    private fun matchesQuery(line: LogLine): Boolean {
        val needle = query.trim().lowercase()
        return needle.isEmpty() || line.raw.lowercase().contains(needle)
    }

    private fun matchesFacets(entry: LogEntry?): Boolean {
        if (facets.isEmpty()) return true
        if (entry == null) return false
        return facets.all { (facet, value) -> LogFacets.hit(facet, value, entry) }
    }

    private fun matchesCountry(entry: LogEntry?): Boolean =
        country == null || (entry != null && countryByIp[entry.ip] == country)

    /** Search + facets, before the country filter, so the geography panel never collapses. */
    val faceted: List<LogLine> by lazy { lines.filter { matchesQuery(it) && matchesFacets(it.entry) } }

    val visible: List<LogLine> by lazy { faceted.filter { matchesCountry(it.entry) } }

    val visibleEntries: List<LogEntry> by lazy { visible.mapNotNull { it.entry } }

    /** Recomputed for the current selection, the way the web re-renders the desk on every filter. */
    val window: LogWindow by lazy { LogAnalytics.build(visibleEntries, fetched, unparsed) }

    val countries: List<CountryCount> by lazy {
        Countries.counts(countryByIp, faceted.mapNotNull { it.entry }.map { it.ip })
    }

    val deadFacets: Set<LogFacet>
        get() = facets.keys.filter { facet ->
            val others = facets - facet
            lines.none { line ->
                val entry = line.entry
                matchesQuery(line) && entry != null &&
                    others.all { (key, value) -> LogFacets.hit(key, value, entry) } &&
                    LogFacets.hit(facet, facets.getValue(facet), entry)
            }
        }.toSet()

    val nextLineStep: Int? get() = LOG_LINE_STEPS.firstOrNull { it > lineCount }

    val selectionSpan: Long?
        get() = if (selected) LogAnalytics.span(visibleEntries) else null

    val isJson: Boolean get() = parsedEntries.any { it.format == LogFormat.Json }

    val nanoPrecision: Boolean
        get() = parsedEntries.any { it.format == LogFormat.Json && (it.durMs ?: 0.0) % 1.0 != 0.0 }

    val tlsFields: Boolean get() = parsedEntries.any { it.tls.isNotEmpty() }

    val formatLabel: String
        get() = when {
            parsedEntries.isEmpty() -> "no parsed lines"
            isJson -> "json access log"
            parsedEntries.any { it.format == LogFormat.Clf } -> "clf access log"
            else -> "generic clf access log"
        }
}

object LogFacets {

    fun hit(facet: LogFacet, value: String, entry: LogEntry): Boolean = when (facet) {
        LogFacet.Status -> statusMatch(entry.status, value)
        LogFacet.Method -> entry.method == value
        LogFacet.Domain -> entry.domain == value
        LogFacet.Path -> if (value.startsWith("~")) entry.pattern == value.drop(1) else entry.path == value
        LogFacet.Ip -> entry.ip == value
        LogFacet.Service -> entry.service.ifEmpty { entry.router } == value
        LogFacet.Duration -> when {
            entry.durMs == null -> false
            value == "held" -> entry.heldOpen
            else -> !entry.heldOpen && LogParser.durBand(entry.durMs) == value
        }
    }

    fun statusMatch(status: Int, spec: String): Boolean = when {
        spec == "errors" -> status >= 400
        spec == "other" -> LogParser.statusClass(status) == "other"
        spec.length == 3 && spec.endsWith("xx") -> LogParser.statusClass(status) == spec
        else -> status.toString() == spec
    }

    fun label(facet: LogFacet, value: String): String = when (facet) {
        LogFacet.Path -> value.removePrefix("~")
        LogFacet.Service -> LogParser.shortName(value)
        else -> value
    }
}

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val repository: LogsRepository,
    private val geoRepository: GeoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LogsUiState())
    val state: StateFlow<LogsUiState> = _state.asStateFlow()

    val queryState = TextFieldState()

    private var autoJob: Job? = null

    init {
        load(initial = true)
    }

    override fun onCleared() {
        autoJob?.cancel()
        super.onCleared()
    }

    fun refresh() = load(initial = false)

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun toggleFacet(facet: LogFacet, value: String) = _state.update { current ->
        val facets = if (current.facets[facet] == value) {
            current.facets - facet
        } else {
            current.facets + (facet to value)
        }
        current.copy(facets = facets)
    }

    fun applyFacets(vararg pairs: Pair<LogFacet, String>) = _state.update { current ->
        val incoming = pairs.toMap()
        val alreadyOn = incoming.all { (facet, value) -> current.facets[facet] == value }
        current.copy(facets = if (alreadyOn) current.facets - incoming.keys else current.facets + incoming)
    }

    fun clearFacet(facet: LogFacet) = _state.update { it.copy(facets = it.facets - facet) }

    fun clearFacets() = _state.update { it.copy(facets = emptyMap()) }

    fun clearAll() {
        queryState.clearText()
        _state.update { it.copy(facets = emptyMap(), country = null, query = "") }
    }

    fun onCountryChange(code: String?) = _state.update {
        it.copy(country = if (it.country == code) null else code)
    }

    fun setLineCount(value: Int) {
        if (_state.value.lineCount == value) return
        _state.update { it.copy(lineCount = value) }
        load(initial = false)
    }

    fun toggleAutoRefresh() {
        val enabled = !_state.value.autoRefresh
        _state.update { it.copy(autoRefresh = enabled) }
        autoJob?.cancel()
        if (!enabled) return
        autoJob = viewModelScope.launch {
            while (true) {
                delay(if (_state.value.lineCount <= 200) AUTO_REFRESH_FAST_MILLIS else AUTO_REFRESH_SLOW_MILLIS)
                load(initial = false)
            }
        }
    }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.lines.isEmpty(), refreshing = !initial, loadError = null) }
        viewModelScope.launch {
            runCatching { repository.load(_state.value.lineCount) }.fold(
                onSuccess = { snapshot ->
                    _state.update { current ->
                        current.copy(
                            loading = false,
                            refreshing = false,
                            lines = snapshot.lines,
                            serverError = snapshot.error,
                            loadError = null,
                            lastLoadedAt = System.currentTimeMillis(),
                        )
                    }
                    resolveCountries(snapshot.lines.mapNotNull { it.entry })
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            loadError = throwable.message ?: "Could not load the access log",
                        )
                    }
                },
            )
        }
    }

    private fun resolveCountries(entries: List<LogEntry>) {
        viewModelScope.launch {
            val status = geoRepository.status()
            if (!status.usable) {
                _state.update { it.copy(geoEnabled = false) }
                return@launch
            }
            val codes = geoRepository.lookup(entries.map { it.ip })
            _state.update { it.copy(geoEnabled = true, countryByIp = it.countryByIp + codes) }
        }
    }
}
