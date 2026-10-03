package dev.chr0nzz.traefikmanager.widget

import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.CertRows
import dev.chr0nzz.traefikmanager.data.model.CrowdSecAnalytics
import dev.chr0nzz.traefikmanager.data.model.CrowdSecSnapshot
import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsRanked
import dev.chr0nzz.traefikmanager.data.model.LogParser
import dev.chr0nzz.traefikmanager.data.model.toProtoEnvelope
import dev.chr0nzz.traefikmanager.data.repo.CrowdSecReader
import dev.chr0nzz.traefikmanager.data.repo.DashboardBuilder
import dev.chr0nzz.traefikmanager.data.repo.RawDashboard
import dev.chr0nzz.traefikmanager.data.repo.ServersRepository
import dev.chr0nzz.traefikmanager.data.repo.SignalCard
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText

@Singleton
class WidgetDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
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
            note = if (cards.isEmpty() && rows.isEmpty()) s(R.string.widget_unreachable) else "",
        )
    }

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
                val bans = runCatching { CrowdSecReader.bans(api) }.getOrDefault(-1)
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

    private fun entrypointsCard(rows: List<dev.chr0nzz.traefikmanager.data.repo.EntrypointRow>): WidgetCard {
        val idle = rows.count { it.idle }
        return WidgetCard(
            key = WidgetCardType.Entrypoints.key,
            title = s(R.string.dashboard_entry_points),
            hero = LogParser.formatCount(rows.size),
            unit = q(R.plurals.widget_unit_entry_points, rows.size),
            health = when {
                rows.any { it.health == TmStatus.Error } -> TmStatus.Error
                idle > 0 -> TmStatus.Warn
                else -> TmStatus.Ok
            }.wire(),
            chips = if (idle > 0) listOf(chip(s(R.string.widget_chip_idle, idle), TmStatus.Warn)) else emptyList(),
            sub = rows.firstOrNull { !it.idle }
                ?.let { s(R.string.widget_busiest, it.name, it.address) }
                ?: s(R.string.widget_nothing_bound),
            cells = rows.flatMap { row -> row.cells.map { it.wire() } }.take(CELLS),
            rows = rows.take(ROWS).map { row ->
                WidgetRow(
                    name = "${row.name} ${row.address}",
                    count = row.routerCount?.let { LogParser.formatCount(it) } ?: "-",
                    health = row.health.wire(),
                )
            },
            listTitle = s(R.string.dashboard_entry_points).uppercase(),
        )
    }

    private suspend fun certsCard(agentId: String?): WidgetCard {
        val soonest = CertRows.from(
            certs = apiProvider.apiFor(agentId).certs().certs,
            nowMillis = System.currentTimeMillis(),
        )
        val certs = soonest
        val expiring = certs.count { (it.daysLeft ?: 999) <= 30 }
        val critical = certs.count { (it.daysLeft ?: 999) <= 7 }
        return WidgetCard(
            key = WidgetCardType.Certs.key,
            title = s(R.string.nav_certificates),
            hero = LogParser.formatCount(certs.size),
            unit = q(R.plurals.widget_unit_certs, certs.size),
            health = when {
                critical > 0 -> TmStatus.Error
                expiring > 0 -> TmStatus.Warn
                else -> TmStatus.Ok
            }.wire(),
            chips = buildList {
                if (critical > 0) add(chip(s(R.string.widget_chip_critical, critical), TmStatus.Error))
                if (expiring > critical) add(chip(s(R.string.widget_chip_expiring, expiring - critical), TmStatus.Warn))
                if (expiring == 0) add(chip(s(R.string.widget_chip_current), TmStatus.Ok))
            },
            sub = soonest.firstOrNull()?.let { first ->
                first.daysLeft?.let { s(R.string.widget_soonest_days, first.main, it) }
                    ?: s(R.string.widget_soonest, first.main)
            } ?: s(R.string.widget_no_certs),
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
                    count = cert.daysLeft?.let { s(R.string.widget_days_short, it) } ?: "-",
                    health = when {
                        (cert.daysLeft ?: 999) <= 7 -> TmStatus.Error
                        (cert.daysLeft ?: 999) <= 30 -> TmStatus.Warn
                        else -> TmStatus.Ok
                    }.wire(),
                )
            },
        )
    }

    private suspend fun crowdSecCards(agentId: String?): Map<String, WidgetCard> {
        val snapshot = CrowdSecReader.read(apiProvider.apiFor(agentId), full = false, previous = null)
        if (!snapshot.decisions.ok && !snapshot.alerts.ok) return emptyMap()
        val alerts = snapshot.alertList
        val handled = snapshot::handled

        return buildMap {
            put(WidgetCardType.Sources.key, sourcesCard(alerts, handled))
            put(WidgetCardType.Scenarios.key, scenariosCard(alerts, handled))
            put(WidgetCardType.Paths.key, pathsCard(alerts, handled))
            put(WidgetCardType.Bans.key, bansCard(snapshot))
        }
    }

    private fun sourcesCard(alerts: List<CsAlert>, handled: (CsAlert) -> Boolean): WidgetCard {
        val sources = CrowdSecAnalytics.sources(alerts, handled)
        val loose = sources.count { it.open > 0 }
        val severe = sources.any { it.open > 0 && it.count > 1 }
        val repeats = sources.count { it.count > 1 }
        return WidgetCard(
            key = WidgetCardType.Sources.key,
            title = s(R.string.crowdsec_card_sources),
            hero = LogParser.formatCount(sources.size),
            unit = q(R.plurals.widget_unit_sources, sources.size),
            health = when {
                severe -> TmStatus.Error
                loose > 0 -> TmStatus.Warn
                else -> TmStatus.Ok
            }.wire(),
            chips = when {
                sources.isEmpty() -> emptyList()
                loose > 0 -> listOf(
                    chip(s(R.string.crowdsec_chip_loose, LogParser.formatCount(loose)), if (severe) TmStatus.Error else TmStatus.Warn),
                    chip(s(R.string.crowdsec_chip_banned, LogParser.formatCount(sources.size - loose)), TmStatus.Unknown),
                )
                else -> listOf(chip(s(R.string.crowdsec_chip_all_banned), TmStatus.Unknown))
            },
            sub = sources.firstOrNull()
                ?.let {
                    listOf(
                        s(R.string.crowdsec_worst, it.label),
                        q(R.plurals.crowdsec_events_count, it.weight, LogParser.formatCount(it.weight)),
                    ).joinToString(UiText.SEPARATOR)
                }
                ?: s(R.string.widget_no_scenario),
            cells = sources.take(CELLS).map {
                when {
                    it.open > 0 && it.count > 1 -> TmStatus.Error
                    it.open > 0 -> TmStatus.Warn
                    else -> TmStatus.Disabled
                }.wire()
            },
            footer = listOf(
                chip(s(R.string.crowdsec_chip_repeat, LogParser.formatCount(repeats)), TmStatus.Unknown),
                chip(s(R.string.crowdsec_chip_one_shot, LogParser.formatCount(sources.size - repeats)), TmStatus.Unknown),
            ),
        )
    }

    private fun scenariosCard(alerts: List<CsAlert>, handled: (CsAlert) -> Boolean): WidgetCard {
        val rows = CrowdSecAnalytics.scenarios(alerts, handled)
        val events = alerts.sumOf { it.eventsCount }
        val rest = rows.drop(ROWS)
        return WidgetCard(
            key = WidgetCardType.Scenarios.key,
            title = s(R.string.crowdsec_card_scenarios),
            hero = LogParser.formatCount(alerts.size),
            unit = s(R.string.crowdsec_unit_alerts),
            health = if (rows.any { it.open > 0 }) TmStatus.Warn.wire() else TmStatus.Ok.wire(),
            sub = rows.firstOrNull()
                ?.let {
                    listOf(
                        s(R.string.crowdsec_worst, it.label),
                        q(R.plurals.crowdsec_events_rolled_up, events, LogParser.formatCount(events)),
                    ).joinToString(UiText.SEPARATOR)
                }
                ?: s(R.string.crowdsec_scenarios_empty),
            rows = rows.take(ROWS).map(::rankRow),
            footer = tail(rest, rest.sumOf { it.count }, R.plurals.crowdsec_more_scenarios),
        )
    }

    private fun pathsCard(alerts: List<CsAlert>, handled: (CsAlert) -> Boolean): WidgetCard {
        val rows = CrowdSecAnalytics.paths(alerts, handled)
        val rest = rows.drop(ROWS)
        return WidgetCard(
            key = WidgetCardType.Paths.key,
            title = s(R.string.crowdsec_card_paths),
            hero = LogParser.formatCount(rows.size),
            unit = s(R.string.crowdsec_unit_paths),
            health = if (rows.any { it.open > 0 }) TmStatus.Warn.wire() else TmStatus.Ok.wire(),
            sub = rows.firstOrNull()?.let { s(R.string.crowdsec_most_wanted, it.label) } ?: s(R.string.crowdsec_routes_empty_none),
            rows = rows.take(ROWS).map(::rankRow),
            footer = tail(rest, rest.sumOf { it.weight }, R.plurals.crowdsec_more_paths),
        )
    }

    private fun bansCard(snapshot: CrowdSecSnapshot): WidgetCard {
        val total = snapshot.decisionTotal
        val own = snapshot.ownBans
        val cells = total.coerceAtMost(CELLS)
        val ownCells = if (total == 0) 0 else ((own.toLong() * cells + total - 1) / total).toInt()
        return WidgetCard(
            key = WidgetCardType.Bans.key,
            title = s(R.string.crowdsec_card_bans),
            hero = LogParser.formatCount(total),
            unit = q(R.plurals.widget_unit_bans, total),
            health = if (total == 0) TmStatus.Disabled.wire() else TmStatus.Ok.wire(),
            chips = snapshot.typeCount("ban").takeIf { it > 0 }
                ?.let { listOf(chip("${LogParser.formatCount(it)} ban", TmStatus.Unknown)) }
                .orEmpty(),
            sub = listOf(
                s(R.string.crowdsec_bans_own, LogParser.formatCount(own)),
                s(R.string.crowdsec_bans_subscribed, LogParser.formatCount(snapshot.subscribedBans)),
            ).joinToString(UiText.SEPARATOR),
            cells = List(cells) { index ->
                if (index < ownCells) TmStatus.Ok.wire() else TmStatus.Disabled.wire()
            },
            footer = snapshot.origins.take(3).map { origin ->
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

    private fun tail(rest: List<CsRanked>, sum: Int, @PluralsRes more: Int): List<WidgetChip> =
        if (rest.isEmpty()) {
            emptyList()
        } else {
            listOf(
                chip(
                    q(more, rest.size, LogParser.formatCount(sum), LogParser.formatCount(rest.size)),
                    TmStatus.Unknown,
                ),
            )
        }

    private fun s(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun q(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, count, *args.ifEmpty { arrayOf(count) })

    private fun chip(text: String, status: TmStatus): WidgetChip =
        WidgetChip(label = text, count = 0, health = status.wire())

    private fun SignalCard.asWidgetCard(): WidgetCard = WidgetCard(
        key = key,
        title = title.resolve(context.resources),
        hero = LogParser.formatCount(total ?: cells.size),
        unit = s(
            when (key) {
                "services" -> R.string.widget_unit_services
                "middlewares" -> R.string.widget_unit_middlewares
                else -> R.string.crowdsec_unit_routers
            },
        ),
        health = health.wire(),
        healthLabel = healthLabel.resolve(context.resources),
        sub = sub.resolve(context.resources),
        cells = cells.take(CELLS).map { it.wire() },
        chips = flags.map { chip("${LogParser.formatCount(it.count)} ${it.label.resolve(context.resources)}", it.status) },
        footer = providers.map { provider ->
            chip("${provider.name} ${LogParser.formatCount(provider.count)}", provider.worst)
        },
    )

    private fun unavailable(type: WidgetCardType): WidgetCard = WidgetCard(
        key = type.key,
        title = s(type.label),
        hero = "-",
        health = TmStatus.Unknown.wire(),
        healthLabel = s(R.string.widget_no_answer),
        sub = s(if (type.crowdsec) R.string.widget_crowdsec_silent else R.string.widget_server_silent),
    )

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
        const val CELLS = 72

        const val ROWS = 8
    }
}
