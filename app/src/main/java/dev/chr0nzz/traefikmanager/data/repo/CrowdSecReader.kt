package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.TmApi
import dev.chr0nzz.traefikmanager.data.model.CrowdSecAnalytics
import dev.chr0nzz.traefikmanager.data.model.CrowdSecSnapshot
import dev.chr0nzz.traefikmanager.data.model.CrowdSecSummary
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.data.model.CsDecisionPage
import dev.chr0nzz.traefikmanager.data.model.CsDecisionQuery
import dev.chr0nzz.traefikmanager.data.model.CsRead
import dev.chr0nzz.traefikmanager.data.model.CsSummaryStep
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response

object CrowdSecReader {

    private const val UNREACHABLE = "Could not reach the CrowdSec LAPI"

    suspend fun read(api: TmApi, full: Boolean, previous: CrowdSecSnapshot?): CrowdSecSnapshot {
        val sent = previous?.takeIf { !full && it.decisions.ok && it.alerts.ok }?.version?.takeIf { it.isNotEmpty() }
        val response = try {
            api.crowdSecSummary(sent, if (full) "1" else null)
        } catch (failure: IOException) {
            val message = failure.message ?: UNREACHABLE
            return CrowdSecSnapshot(decisions = CsRead.Failed(message, null), alerts = CsRead.Failed(message, null))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return legacy(api, full)
        }
        val error = if (response.isSuccessful) null else errorMessage(response)
        return when (val step = CrowdSecSummary.step(response.code(), response.body(), sent, error)) {
            is CsSummaryStep.Fresh -> CrowdSecSummary.snapshot(step.summary).let {
                it.copy(decisions = it.decisions.withHint())
            }
            CsSummaryStep.Unchanged -> previous ?: legacy(api, full)
            CsSummaryStep.Legacy -> legacy(api, full)
            CsSummaryStep.NotConfigured -> CrowdSecSnapshot(
                decisions = CsRead.NotConfigured,
                alerts = CsRead.NotConfigured,
            )
            is CsSummaryStep.Failed -> CrowdSecSnapshot(
                decisions = CsRead.Failed(step.message, step.status).withHint(),
                alerts = CsRead.Failed(step.message, step.status),
            )
        }
    }

    suspend fun search(api: TmApi, query: CsDecisionQuery, page: Int, per: Int): CsRead<CsDecisionPage> {
        val response = api.crowdSecDecisionsSearch(
            q = query.q,
            origin = query.origin,
            type = query.type,
            ip = query.ip,
            scenario = query.scenario,
            page = page,
            per = per,
        )
        return read(response) { it ?: CsDecisionPage() }
    }

    suspend fun bans(api: TmApi): Int {
        val summary = api.crowdSecSummary()
        if (summary.code() == 404) {
            return api.crowdSecDecisions(null).takeIf { it.isSuccessful }?.body()?.size ?: -1
        }
        return summary.takeIf { it.isSuccessful }?.body()?.decisions?.takeIf { it.ok }?.total ?: -1
    }

    private suspend fun legacy(api: TmApi, full: Boolean): CrowdSecSnapshot = coroutineScope {
        val decisionsCall = async { runCatching { api.crowdSecDecisions(if (full) "1" else null) } }
        val alertsCall = async { runCatching { api.crowdSecAlerts() } }

        val decisionsResponse = decisionsCall.await()
        val decisions = decisionsResponse.fold(
            onSuccess = { response -> read(response) { it.orEmpty() } },
            onFailure = { CsRead.Failed(it.message ?: UNREACHABLE, null) },
        )
        val alertsResponse = alertsCall.await()
        val alerts = alertsResponse.fold(
            onSuccess = { response ->
                read(response) { list -> CrowdSecAnalytics.filterAlerts(list.orEmpty()) }
            },
            onFailure = { CsRead.Failed(it.message ?: UNREACHABLE, null) },
        )
        val headers = alertsResponse.getOrNull()?.headers()
        val staleNote = decisionsResponse.getOrNull()?.headers()?.get("X-CS-Stale")

        CrowdSecSnapshot(
            decisions = decisions.withHint(),
            alerts = alerts,
            alertLimit = headers?.get("X-CS-Alert-Limit")?.toIntOrNull(),
            decisionsStale = staleNote?.takeIf { it.isNotBlank() },
            alertsCapped = headers?.get("X-CS-Alert-Capped")?.let { it == "1" },
        )
    }

    private fun <T, R> read(response: Response<T>, transform: (T?) -> R): CsRead<R> = when {
        response.isSuccessful -> CsRead.Loaded(transform(response.body()))
        response.code() == 404 -> CsRead.NotConfigured
        else -> CsRead.Failed(
            message = errorMessage(response) ?: "CrowdSec LAPI unavailable (HTTP ${response.code()})",
            status = response.code(),
        )
    }

    private fun CsRead<List<CsDecision>>.withHint(): CsRead<List<CsDecision>> {
        val failed = this as? CsRead.Failed ?: return this
        val message = failed.message
        if (!message.contains("403") || message.contains("bouncer", ignoreCase = true)) return this
        return CsRead.Failed(
            message = message + ". CrowdSec only accepts a bouncer key on /v1/decisions, the machine " +
                "token is refused there, so CROWDSEC_API_KEY has to be set as well.",
            status = failed.status,
        )
    }

    fun errorMessage(response: Response<*>): String? {
        val body = runCatching { response.errorBody()?.string() }.getOrNull()
        if (body.isNullOrBlank()) return null
        val parsed = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        return (parsed["error"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    }
}
