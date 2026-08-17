package dev.chr0nzz.traefikmanager.widget

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.api.TmApi
import dev.chr0nzz.traefikmanager.data.model.CertRows
import dev.chr0nzz.traefikmanager.data.model.CrowdSecAnalytics
import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.data.model.CsRanked
import dev.chr0nzz.traefikmanager.data.model.LogParser
import dev.chr0nzz.traefikmanager.data.model.toProtoEnvelope
import dev.chr0nzz.traefikmanager.data.repo.DashboardBuilder
import dev.chr0nzz.traefikmanager.data.repo.RawDashboard
import dev.chr0nzz.traefikmanager.data.repo.ServersRepository
import dev.chr0nzz.traefikmanager.data.repo.SignalCard
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Builds a widget's cards out of the same code the screens use: DashboardBuilder for the Traefik
 * cards, CrowdSecAnalytics for the security ones. Nothing here re-derives a number the app
 * already knows how to derive.
 *
 * Every call is aimed at one named server rather than whichever the app has selected, so a widget
 * keeps watching what it was pointed at while you read a different server in the app.
 */
@Singleton
class WidgetDataSource @Inject constructor(
    private val apiProvider: ApiProvider,
    private val serversRepository: ServersRepository,
) {

    suspend fun load(config: WidgetConfig): WidgetPayload = coroutineScope {
        val dashboard = if (config.needsDashboard) {
            async { runCatching { dashboardCards(config.serverId) }.getOrDefault(emptyMap()) }
        } else {
            null
        }
        val crowdsec = if (config.needsCrowdSec) {
            async { runCatching { crowdSecCards(config.serverId) }.getOrDefault(emptyMap()) }
        } else {
            null
        }
        val certs = if (config.cards.contains(WidgetCardType.Certs)) {
            async { runCatching { certsCard(config.serverId) }.getOrNull() }
        } else {
            null
        }
        val overview = if (config.needsOverview) async { servers() } else null

        val built = buildMap {
            dashboard?.await()?.let { putAll(it) }
            crowdsec?.await()?.let { putAll(it) }
            certs?.await()?.let { put(WidgetCardType.Certs.key, it) }
        }
        val cards = config.cards.mapNotNull { type ->
            if (type == WidgetCardType.Overview) null else built[type.key] ?: unavailable(type)
        }
        val rows = overview?.await().orEmpty()
        WidgetPayload(
            cards = cards,
            servers = rows,
            note = if (cards.isEmpty() && rows.isEmpty()) "Unreachable" else "",
        )
    }

    /** The overview: every server, with what it runs and what is wrong with it. */
    private suspend fun servers(): List<WidgetServerRow> = coroutineScope {
        val servers = runCatching { serversRepository.servers(probeHealth = false) }.getOrDefault(emptyList())
        servers.map { server ->
            async {
                val api = apiProvider.apiFor(server.id)
                val routers = runCatching { api.routers() }.getOrNull()
                    ?: return@async WidgetServerRow(
                        id = server.id.orEmpty(),
                        name = server.name,
                        reachable = false,
                    )
                val classified = routers.all.map { statusOf(it.status) }
                val services = runCatching { api.services() }.getOrNull()
                    ?.toProtoEnvelope()?.all.orEmpty()
                // A server without CrowdSec answers 404, which is not the same as zero bans.
                val bans = runCatching { api.crowdSecDecisions(null) }.getOrNull()
                    ?.takeIf { it.isSuccessful }
                    ?.body()
                    ?.size
                    ?: -1
                WidgetServerRow(
                    id = server.id.orEmpty(),
                    name = server.name,
                    routers = classified.size,
                    warn = classified.count { it == TmStatus.Warn },
                    err = classified.count { it == TmStatus.Error },
                    services = services.size,
                    cells = services.take(CELLS).map { serviceWire(it.status) },
                    bans = bans,
                    reachable = routers.reachable,
                )
            }
        }.map { it.await() }
    }

    private suspend fun dashboardCards(agentId: String?): Map<String, WidgetCard> = coroutineScope {
        val api = apiProvider.apiFor(agentId)
        val overview = async { runCatching { api.overview() }.getOrNull() }
        val entrypoints = async { runCatching { api.entrypoints() }.getOrNull() }
        val routers = async { runCatching { api.routers() }.getOrNull() }
        val services = async { runCatching { api.services() }.getOrNull() }
        val middlewares = async { runCatching { api.middlewares() }.getOrNull() }
        val raw = RawDashboard(
            overview = overview.await(),
            entrypoints = entrypoints.await(),
            routers = routers.await(),
            services = services.await(),
            middlewares = middlewares.await(),
        )
        val snapshot = DashboardBuilder.build(raw, providerFilter = null)
        buildMap {
            snapshot.cards.forEach { put(it.key, it.asWidgetCard()) }
            put(WidgetCardType.Entrypoints.key, entrypointsCard(snapshot.entrypoints))
        }
    }

    /** The doors in: what each one is, and how much is bound to it. */
    private fun entrypointsCard(rows: List<dev.chr0nzz.traefikmanager.data.repo.EntrypointRow>): WidgetCard {
        val idle = rows.count { it.idle }
        return WidgetCard(
            key = WidgetCardType.Entrypoints.key,
            title = "Entry points",
            hero = LogParser.formatCount(rows.size),
            unit = if (rows.size == 1) "entry point" else "entry points",
            health = when {
                rows.any { it.health == TmStatus.Error } -> TmStatus.Error
                idle > 0 -> TmStatus.Warn
                else -> TmStatus.Ok
            }.wire(),
            chips = if (idle > 0) listOf(chip("$idle idle", TmStatus.Warn)) else emptyList(),
            sub = rows.firstOrNull { !it.idle }
                ?.let { "busiest ${it.name} · ${it.address}" }
                ?: "nothing bound",
            cells = rows.flatMap { row -> row.cells.map { it.wire() } }.take(CELLS),
            rows = rows.take(ROWS).map { row ->
                WidgetRow(
                    name = "${row.name} ${row.address}",
                    count = row.routerCount?.let { LogParser.formatCount(it) } ?: "-",
                    health = row.health.wire(),
                )
            },
            listTitle = "ENTRY POINTS",
        )
    }

    private suspend fun certsCard(agentId: String?): WidgetCard {
        // CertRows works out the days left and the ordering the certificates screen uses.
        val soonest = CertRows.from(
            certs = apiProvider.apiFor(agentId).certs().certs,
            nowMillis = System.currentTimeMillis(),
        )
        val certs = soonest
        val expiring = certs.count { (it.daysLeft ?: 999) <= 30 }
        val critical = certs.count { (it.daysLeft ?: 999) <= 7 }
        return WidgetCard(
            key = WidgetCardType.Certs.key,
            title = "Certificates",
            hero = LogParser.formatCount(certs.size),
            unit = if (certs.size == 1) "cert" else "certs",
            health = when {
                critical > 0 -> TmStatus.Error
                expiring > 0 -> TmStatus.Warn
                else -> TmStatus.Ok
            }.wire(),
            chips = buildList {
                if (critical > 0) add(chip("$critical critical", TmStatus.Error))
                if (expiring > critical) add(chip("${expiring - critical} expiring", TmStatus.Warn))
                if (expiring == 0) add(chip("all current", TmStatus.Ok))
            },
            sub = soonest.firstOrNull()?.let { first ->
                first.daysLeft?.let { "soonest ${first.main} · ${it}d left" } ?: "soonest ${first.main}"
            } ?: "no certificates",
            cells = soonest.take(CELLS).map {
                when {
                    (it.daysLeft ?: 999) <= 7 -> TmStatus.Error
                    (it.daysLeft ?: 999) <= 30 -> TmStatus.Warn
                    else -> TmStatus.Ok
                }.wire()
            },
            rows = soonest.take(ROWS).map { cert ->
                WidgetRow(
                    name = cert.main,
                    count = cert.daysLeft?.let { "${it}d" } ?: "-",
                    health = when {
                        (cert.daysLeft ?: 999) <= 7 -> TmStatus.Error
                        (cert.daysLeft ?: 999) <= 30 -> TmStatus.Warn
                        else -> TmStatus.Ok
                    }.wire(),
                )
            },
        )
    }

    private suspend fun crowdSecCards(agentId: String?): Map<String, WidgetCard> = coroutineScope {
        val api = apiProvider.apiFor(agentId)
        val decisionsCall = async { runCatching { api.crowdSecDecisions(null) }.getOrNull() }
        val alertsCall = async { runCatching { api.crowdSecAlerts() }.getOrNull() }
        val decisions = decisionsCall.await()?.takeIf { it.isSuccessful }?.body().orEmpty()
        val alerts = alertsCall.await()?.takeIf { it.isSuccessful }?.body()
            ?.let { CrowdSecAnalytics.filterAlerts(it) }
            .orEmpty()
        val banned = decisions.filter { it.scope == "Ip" || it.scope == "Range" }.map { it.value }.toSet()

        buildMap {
            put(WidgetCardType.Sources.key, sourcesCard(alerts, banned))
            put(WidgetCardType.Scenarios.key, scenariosCard(alerts, banned))
            put(WidgetCardType.Paths.key, pathsCard(alerts, banned))
            put(WidgetCardType.Bans.key, bansCard(decisions))
        }
    }

    /** The desk's sources card: chips and a mosaic, no ranked rows. */
    private fun sourcesCard(alerts: List<CsAlert>, banned: Set<String>): WidgetCard {
        val sources = CrowdSecAnalytics.sources(alerts, banned)
        val loose = sources.count { it.open > 0 }
        val severe = sources.any { it.open > 0 && it.count > 1 }
        val repeats = sources.count { it.count > 1 }
        return WidgetCard(
            key = WidgetCardType.Sources.key,
            title = "Attacking sources",
            hero = LogParser.formatCount(sources.size),
            unit = if (sources.size == 1) "source" else "sources",
            health = when {
                severe -> TmStatus.Error
                loose > 0 -> TmStatus.Warn
                else -> TmStatus.Ok
            }.wire(),
            chips = when {
                sources.isEmpty() -> emptyList()
                loose > 0 -> listOf(
                    chip("${LogParser.formatCount(loose)} loose", if (severe) TmStatus.Error else TmStatus.Warn),
                    chip("${LogParser.formatCount(sources.size - loose)} banned", TmStatus.Unknown),
                )
                else -> listOf(chip("every source banned", TmStatus.Unknown))
            },
            sub = sources.firstOrNull()
                ?.let { "worst ${it.label} · ${LogParser.formatCount(it.weight)} events" }
                ?: "nobody tripped a scenario",
            cells = sources.take(CELLS).map {
                when {
                    it.open > 0 && it.count > 1 -> TmStatus.Error
                    it.open > 0 -> TmStatus.Warn
                    else -> TmStatus.Disabled
                }.wire()
            },
            footer = listOf(
                chip("${LogParser.formatCount(repeats)} repeat", TmStatus.Unknown),
                chip("${LogParser.formatCount(sources.size - repeats)} one-shot", TmStatus.Unknown),
            ),
        )
    }

    private fun scenariosCard(alerts: List<CsAlert>, banned: Set<String>): WidgetCard {
        val rows = CrowdSecAnalytics.scenarios(alerts, banned)
        val events = alerts.sumOf { it.eventsCount }
        val rest = rows.drop(ROWS)
        return WidgetCard(
            key = WidgetCardType.Scenarios.key,
            title = "Scenarios",
            hero = LogParser.formatCount(alerts.size),
            unit = "alerts",
            health = if (rows.any { it.open > 0 }) TmStatus.Warn.wire() else TmStatus.Ok.wire(),
            sub = rows.firstOrNull()
                ?.let { "worst ${it.label} · ${LogParser.formatCount(events)} events rolled up" }
                ?: "nothing to rank in the retained window",
            rows = rows.take(ROWS).map(::rankRow),
            footer = tail(rest, rest.sumOf { it.count }, "alerts", "scenarios"),
        )
    }

    private fun pathsCard(alerts: List<CsAlert>, banned: Set<String>): WidgetCard {
        val rows = CrowdSecAnalytics.paths(alerts, banned)
        val rest = rows.drop(ROWS)
        return WidgetCard(
            key = WidgetCardType.Paths.key,
            title = "Targeted paths",
            hero = LogParser.formatCount(rows.size),
            unit = "paths",
            health = if (rows.any { it.open > 0 }) TmStatus.Warn.wire() else TmStatus.Ok.wire(),
            sub = rows.firstOrNull()?.let { "most wanted ${it.label}" } ?: "nothing was aimed at",
            rows = rows.take(ROWS).map(::rankRow),
            footer = tail(rest, rest.sumOf { it.weight }, "hits", "paths"),
        )
    }

    private fun bansCard(decisions: List<CsDecision>): WidgetCard {
        val own = decisions.count { it.own }
        return WidgetCard(
            key = WidgetCardType.Bans.key,
            title = "Bans in force",
            hero = LogParser.formatCount(decisions.size),
            unit = if (decisions.size == 1) "ban" else "bans",
            health = if (decisions.isEmpty()) TmStatus.Disabled.wire() else TmStatus.Ok.wire(),
            chips = decisions.count { it.type == "ban" }.takeIf { it > 0 }
                ?.let { listOf(chip("${LogParser.formatCount(it)} ban", TmStatus.Unknown)) }
                .orEmpty(),
            sub = "${LogParser.formatCount(own)} from this host · " +
                "${LogParser.formatCount(decisions.size - own)} subscribed",
            cells = decisions.take(CELLS).map {
                if (it.own) TmStatus.Ok.wire() else TmStatus.Disabled.wire()
            },
            footer = CrowdSecAnalytics.origins(decisions).take(3).map { origin ->
                chip("${origin.origin} ${LogParser.formatCount(origin.count)}", TmStatus.Unknown)
            },
        )
    }

    private fun rankRow(row: CsRanked): WidgetRow = WidgetRow(
        name = row.label,
        count = LogParser.formatCount(row.count),
        flag = row.open.takeIf { it > 0 }?.let { LogParser.formatCount(it) }.orEmpty(),
        health = when {
            row.open > 0 && row.open == row.count -> TmStatus.Error
            row.open > 0 -> TmStatus.Warn
            else -> TmStatus.Ok
        }.wire(),
    )

    /** The card's tail line, worded the way the desk words it. */
    private fun tail(rest: List<CsRanked>, sum: Int, unit: String, noun: String): List<WidgetChip> =
        if (rest.isEmpty()) {
            emptyList()
        } else {
            listOf(
                chip(
                    "+${LogParser.formatCount(sum)} $unit across ${LogParser.formatCount(rest.size)} more $noun",
                    TmStatus.Unknown,
                ),
            )
        }

    private fun chip(text: String, status: TmStatus): WidgetChip =
        WidgetChip(label = text, count = 0, health = status.wire())

    private fun SignalCard.asWidgetCard(): WidgetCard = WidgetCard(
        key = key,
        title = title,
        hero = LogParser.formatCount(total ?: cells.size),
        unit = when {
            key == "services" -> "services"
            key == "middlewares" -> "middlewares"
            else -> "routers"
        },
        health = health.wire(),
        healthLabel = healthLabel,
        sub = sub,
        cells = cells.take(CELLS).map { it.wire() },
        chips = flags.map { chip("${LogParser.formatCount(it.count)} ${it.label}", it.status) },
        footer = providers.map { provider ->
            chip("${provider.name} ${LogParser.formatCount(provider.count)}", provider.worst)
        },
    )

    /** A card the server could not answer for still draws, saying so rather than showing zero. */
    private fun unavailable(type: WidgetCardType): WidgetCard = WidgetCard(
        key = type.key,
        title = type.label,
        hero = "-",
        health = TmStatus.Unknown.wire(),
        healthLabel = "no answer",
        sub = if (type.crowdsec) "CrowdSec did not answer" else "the server did not answer",
    )

    /** Services without a status are internal rather than broken, so they read idle, not red. */
    private fun serviceWire(raw: String?): String = when {
        raw.equals("enabled", ignoreCase = true) -> TmStatus.Ok
        raw.equals("warning", ignoreCase = true) -> TmStatus.Warn
        raw.equals("disabled", ignoreCase = true) -> TmStatus.Disabled
        raw == null -> TmStatus.Disabled
        else -> TmStatus.Error
    }.wire()

    private fun statusOf(raw: String?): TmStatus = when {
        raw.equals("enabled", ignoreCase = true) -> TmStatus.Ok
        raw.equals("warning", ignoreCase = true) -> TmStatus.Warn
        raw == null -> TmStatus.Warn
        else -> TmStatus.Error
    }

    private companion object {
        /** The strip stays readable at widget size well before the desk's 240 cap. */
        const val CELLS = 72

        /** Carried in the payload; the renderer slices by how much room the widget has. */
        const val ROWS = 8
    }
}
