package dev.chr0nzz.traefikmanager.ui.certs

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.CertCleanup
import dev.chr0nzz.traefikmanager.data.model.CertHealth
import dev.chr0nzz.traefikmanager.data.model.CertManageState
import dev.chr0nzz.traefikmanager.data.model.CertRef
import dev.chr0nzz.traefikmanager.data.model.CertRow
import dev.chr0nzz.traefikmanager.data.model.CertRows
import dev.chr0nzz.traefikmanager.data.model.CertUsage
import dev.chr0nzz.traefikmanager.data.model.CertVerdict
import dev.chr0nzz.traefikmanager.data.repo.ServerScope
import dev.chr0nzz.traefikmanager.data.repo.Verdict
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import dev.chr0nzz.traefikmanager.data.repo.CertificatesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.annotation.StringRes
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText

enum class CertFilter(@StringRes val label: Int) {
    All(R.string.certs_filter_all),
    Unused(R.string.certs_filter_unused),
    NoResolver(R.string.certs_filter_no_resolver),
    Expiring(R.string.certs_filter_expiring),
}

data class CertificatesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val certs: List<CertRow> = emptyList(),
    val query: String = "",
    val serverError: String? = null,
    val loadError: UiText? = null,
    val filter: CertFilter = CertFilter.All,
    val usage: CertUsage? = null,
    val manage: CertManageState = CertManageState(),
    val removing: Boolean = false,
    val message: UiText? = null,
) {
    fun verdict(cert: CertRow): CertVerdict? = usage?.verdict(cert.resolver, cert.main)

    fun removable(cert: CertRow): Boolean =
        manage.available && cert.main.isNotEmpty() && cert.resolver.isNotEmpty() && cert.resolver != "file"

    val visible: List<CertRow>
        get() {
            val needle = query.lowercase()
            return certs.filter { cert ->
                val matchesQuery = needle.isEmpty() ||
                    cert.main.lowercase().contains(needle) ||
                    cert.extraDomains.any { it.lowercase().contains(needle) }
                matchesQuery && when (filter) {
                    CertFilter.All -> true
                    CertFilter.Unused -> verdict(cert)?.unused == true
                    CertFilter.NoResolver -> verdict(cert)?.orphaned == true
                    CertFilter.Expiring -> cert.health == CertHealth.Critical || cert.health == CertHealth.Expiring
                }
            }
        }

    val unusedRemovable: List<CertRow>
        get() = certs.filter { verdict(it)?.unused == true && removable(it) }

    val statusVerdict: Verdict
        get() {
            val days = certs.mapNotNull { it.daysLeft }
            val expired = days.count { it <= 0 }
            val critical = days.count { it in 1..6 }
            val expiring = days.count { it in 7..29 }
            val next = days.filter { it > 0 }.minOrNull()
            val resolvers = certs.map { it.resolver }.filter { it.isNotEmpty() }.distinct().size
            val unused = usage?.certs?.count { it.unused } ?: 0
            val orphaned = usage?.certs?.count { it.orphaned } ?: 0
            val headline = when {
                expired > 0 -> UiText.plural(R.plurals.certs_verdict_expired, expired)
                critical > 0 -> UiText.plural(R.plurals.certs_verdict_week, critical)
                expiring > 0 -> UiText.plural(R.plurals.certs_verdict_month, expiring)
                else -> UiText.of(R.string.certs_verdict_healthy)
            }
            val detail = UiText.join(
                buildList {
                    add(UiText.plural(R.plurals.certs_detail_count, certs.size))
                    if (expired > 0) add(UiText.plural(R.plurals.certs_detail_expired, expired))
                    if (critical > 0) add(UiText.plural(R.plurals.certs_detail_week, critical))
                    if (expiring > 0) add(UiText.plural(R.plurals.certs_detail_month, expiring))
                    if (expired == 0 && critical == 0 && expiring == 0) add(UiText.of(R.string.certs_detail_none_soon))
                    if (resolvers > 1) add(UiText.plural(R.plurals.certs_detail_resolvers, resolvers))
                    if (unused > 0) add(UiText.plural(R.plurals.certs_detail_unused, unused))
                    if (orphaned > 0) add(UiText.plural(R.plurals.certs_detail_no_resolver, orphaned))
                    next?.let { add(UiText.of(R.string.certs_detail_next_expiry, it)) }
                },
            )
            val status = when {
                expired > 0 || critical > 0 -> TmStatus.Error
                expiring > 0 -> TmStatus.Warn
                else -> TmStatus.Ok
            }
            return Verdict(headline = headline, detail = detail, status = status)
        }

    val statusNote: UiText
        get() = UiText.join(
            listOfNotNull(
                UiText.of(R.string.certs_note_managed),
                when {
                    manage.available -> UiText.of(R.string.certs_note_removal_restarts)
                    manage.reason.isNotBlank() -> UiText.of(R.string.certs_note_removal_off, manage.reason)
                    else -> UiText.of(R.string.certs_note_renew)
                },
                usage?.why?.takeIf { it.isNotBlank() }?.let(UiText::Raw),
            ),
            separator = " ",
        )
}

@HiltViewModel
class CertificatesViewModel @Inject constructor(
    private val repository: CertificatesRepository,
    private val serverScope: ServerScope,
) : ViewModel() {

    private val _state = MutableStateFlow(CertificatesUiState())
    val state: StateFlow<CertificatesUiState> = _state.asStateFlow()

    val queryState = TextFieldState()

    init {
        viewModelScope.launch {
            val stored = runCatching { repository.cached() }.getOrNull()
            if (stored != null && _state.value.certs.isEmpty()) {
                _state.update { it.copy(loading = false, refreshing = true, certs = CertRows.from(stored.certs, System.currentTimeMillis())) }
            }
            load(initial = stored == null)
        }
        watchServerChanges()
    }

    fun refresh() = load(initial = false)

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onFilterChange(filter: CertFilter) = _state.update { it.copy(filter = filter) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun remove(rows: List<CertRow>) {
        if (rows.isEmpty() || _state.value.removing) return
        _state.update { it.copy(removing = true) }
        viewModelScope.launch {
            val message = runCatching { repository.remove(rows.map { CertRef(it.resolver, it.main) }) }.fold(
                onSuccess = { response -> CertCleanup.outcome(response, rows.size) },
                onFailure = { throwable -> UiText.messageOr(throwable.message, R.string.certs_error_remove) },
            )
            _state.update { it.copy(removing = false, message = message) }
            load(initial = false)
        }
    }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.certs.isEmpty(), refreshing = !initial, loadError = null) }
        viewModelScope.launch {
            runCatching { repository.load() }.fold(
                onSuccess = { response ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            certs = CertRows.from(response.certs, System.currentTimeMillis()),
                            serverError = response.error?.takeIf { message -> message.isNotBlank() },
                            loadError = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            loadError = UiText.messageOr(throwable.message, R.string.certs_error_load),
                        )
                    }
                },
            )
        }
        viewModelScope.launch {
            val manage = runCatching { repository.manage() }.getOrNull()
            val usage = runCatching { repository.usage() }.getOrNull()
            _state.update { it.copy(manage = manage ?: it.manage, usage = usage ?: it.usage) }
        }
    }

    private fun watchServerChanges() {
        viewModelScope.launch {
            serverScope.generation.drop(1).collect {
                _state.value = CertificatesUiState()
                load(initial = true)
            }
        }
    }
}
