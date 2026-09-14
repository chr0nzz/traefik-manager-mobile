package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CsDecisionsSummary(
    val ok: Boolean = false,
    val error: String = "",
    val stale: String = "",
    val total: Int = 0,
    val own: Int = 0,
    val subscribed: Int = 0,
    val wide: Int = 0,
    val origins: Map<String, Int> = emptyMap(),
    val types: Map<String, Int> = emptyMap(),
    val rows: List<CsDecision> = emptyList(),
    @SerialName("rows_more")
    val rowsMore: Int = 0,
)

@Serializable
data class CsAlertsSummary(
    val ok: Boolean = false,
    val error: String = "",
    val status: Int = 0,
    val limit: Int = 0,
    val capped: Boolean = false,
    val rows: List<CsAlert> = emptyList(),
)

@Serializable
data class CsSummary(
    val version: String = "",
    val unchanged: Boolean = false,
    val decisions: CsDecisionsSummary? = null,
    val alerts: CsAlertsSummary? = null,
)

@Serializable
data class CsDecisionPage(
    val rows: List<CsDecision> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
    val per: Int = 20,
    @SerialName("facet_totals")
    val facetTotals: Map<String, Int> = emptyMap(),
)

sealed interface CsSummaryStep {
    data class Fresh(val summary: CsSummary) : CsSummaryStep
    data object Unchanged : CsSummaryStep
    data object Legacy : CsSummaryStep
    data object NotConfigured : CsSummaryStep
    data class Failed(val message: String, val status: Int?) : CsSummaryStep
}

object CrowdSecSummary {

    private const val UNAVAILABLE = "CrowdSec LAPI unavailable"

    fun step(status: Int, body: CsSummary?, sentVersion: String?, error: String?): CsSummaryStep = when {
        status == 404 -> CsSummaryStep.Legacy
        status == 503 && error?.contains("not configured", ignoreCase = true) == true -> CsSummaryStep.NotConfigured
        status !in 200..299 -> CsSummaryStep.Failed(error ?: "$UNAVAILABLE (HTTP $status)", status)
        body == null -> CsSummaryStep.Failed("CrowdSec summary was empty", status)
        body.unchanged && sentVersion != null && body.version == sentVersion -> CsSummaryStep.Unchanged
        body.decisions == null && body.alerts == null -> CsSummaryStep.Failed("CrowdSec summary was empty", status)
        else -> CsSummaryStep.Fresh(body)
    }

    fun snapshot(summary: CsSummary): CrowdSecSnapshot {
        val decisions = summary.decisions ?: CsDecisionsSummary()
        val alerts = summary.alerts ?: CsAlertsSummary()
        return CrowdSecSnapshot(
            decisions = if (decisions.ok) {
                CsRead.Loaded(decisions.rows)
            } else {
                CsRead.Failed(decisions.error.ifBlank { UNAVAILABLE }, null)
            },
            alerts = if (alerts.ok) {
                CsRead.Loaded(CrowdSecAnalytics.filterAlerts(alerts.rows))
            } else {
                CsRead.Failed(alerts.error.ifBlank { UNAVAILABLE }, alerts.status.takeIf { it > 0 })
            },
            alertLimit = alerts.limit.takeIf { alerts.ok && it > 0 },
            alertsCapped = alerts.capped.takeIf { alerts.ok },
            decisionsStale = decisions.stale.takeIf { decisions.ok && it.isNotBlank() },
            counts = decisions.takeIf { it.ok }?.copy(rows = emptyList()),
            version = summary.version,
        )
    }
}

data class CsDecisionQuery(
    val q: String? = null,
    val origin: String? = null,
    val type: String? = null,
    val ip: String? = null,
    val scenario: String? = null,
) {
    val filtered: Boolean get() = origin != null || type != null || ip != null || scenario != null
}

data class CsDecisionFeed(
    val query: CsDecisionQuery,
    val version: String?,
    val rows: List<CsDecision> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val pages: Int = 1,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val loaded: Boolean get() = page > 0

    val more: Boolean get() = loaded && page < pages

    fun accept(next: CsDecisionPage): CsDecisionFeed = copy(
        rows = if (next.page <= 1 || next.page != page + 1) next.rows else rows + next.rows,
        total = next.total,
        page = next.page.coerceAtLeast(1),
        pages = next.pages.coerceAtLeast(1),
        loading = false,
        error = null,
    )

    companion object {
        const val PER = 50
    }
}
