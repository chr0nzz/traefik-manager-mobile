package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.model.Entrypoint
import dev.chr0nzz.traefikmanager.data.model.Overview
import dev.chr0nzz.traefikmanager.data.model.ProtoEnvelope
import dev.chr0nzz.traefikmanager.data.model.toProtoEnvelope
import dev.chr0nzz.traefikmanager.data.model.TraefikObject
import dev.chr0nzz.traefikmanager.data.model.UiText
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import dev.chr0nzz.traefikmanager.R

enum class ObjectState { Err, Warn, Idle, Ok }

data class ObjectSignal(
    val name: String,
    val state: ObjectState,
    val reason: UiText?,
    val provider: String,
)

data class SignalFlag(
    val count: Int,
    val label: UiText,
    val status: TmStatus,
)

data class ProviderCount(
    val name: String,
    val count: Int,
    val worst: TmStatus,
)

data class SignalCard(
    val key: String,
    val title: UiText,
    val total: Int?,
    val cells: List<TmStatus>,
    val flags: List<SignalFlag>,
    val sub: UiText,
    val health: TmStatus,
    val healthLabel: UiText,
    val providers: List<ProviderCount>,
    val stripEmptyLabel: UiText,
)

data class EntrypointRow(
    val name: String,
    val address: String,
    val proto: String,
    val routerCount: Int?,
    val cells: List<TmStatus>,
    val facts: UiText,
    val health: TmStatus,
    val idle: Boolean,
)

data class Verdict(
    val headline: UiText,
    val detail: UiText,
    val status: TmStatus,
)

data class RuntimeInfo(
    val version: String?,
    val codename: String?,
    val metrics: String?,
    val tracing: String?,
    val accessLog: Boolean?,
)

data class DashboardSnapshot(
    val verdict: Verdict,
    val cards: List<SignalCard>,
    val entrypoints: List<EntrypointRow>,
    val traefikReachable: Boolean,
    val providers: List<ProviderCount>,
    val runtime: RuntimeInfo,
    val providerFilter: String?,
)

object DashboardBuilder {

    fun build(raw: RawDashboard, providerFilter: String?): DashboardSnapshot = build(
        overview = raw.overview,
        entrypoints = raw.entrypoints,
        routers = raw.routers?.filterProvider(providerFilter),
        services = raw.services?.toProtoEnvelope()?.filterProvider(providerFilter),
        middlewares = raw.middlewares?.filterProvider(providerFilter),
        runtime = RuntimeInfo(
            version = raw.version?.version,
            codename = raw.version?.codename,
            metrics = raw.overview?.features?.metrics,
            tracing = raw.overview?.features?.tracing,
            accessLog = raw.overview?.features?.accessLog,
        ),
        providerFilter = providerFilter,
        allProviders = providerCounts(
            (raw.routers?.all.orEmpty().map(::classifyRouter)) +
                (raw.services?.toProtoEnvelope()?.all.orEmpty().map(::classifyService)) +
                (raw.middlewares?.all.orEmpty().map { classifyMiddleware(it, emptySet()) }),
        ),
    )

    private fun ProtoEnvelope.filterProvider(provider: String?): ProtoEnvelope {
        if (provider == null) return this
        return copy(
            http = http.filter { it.provider == provider },
            tcp = tcp.filter { it.provider == provider },
            udp = udp.filter { it.provider == provider },
        )
    }

    fun build(
        overview: Overview?,
        entrypoints: List<Entrypoint>?,
        routers: ProtoEnvelope?,
        services: ProtoEnvelope?,
        middlewares: ProtoEnvelope?,
        runtime: RuntimeInfo = RuntimeInfo(null, null, null, null, null),
        providerFilter: String? = null,
        allProviders: List<ProviderCount>? = null,
    ): DashboardSnapshot {
        val routersListed = routers != null && routers.reachable
        val servicesListed = services != null && services.reachable
        val middlewaresListed = middlewares != null && middlewares.reachable
        val reachable = routersListed || overview?.isBlind == false

        val httpRouters = routers?.http.orEmpty().map(::classifyRouter)
        val tcpRouters = routers?.tcp.orEmpty().map(::classifyRouter)
        val udpRouters = routers?.udp.orEmpty().map(::classifyRouter)
        val streamRouters = tcpRouters + udpRouters
        val serviceObjects = services?.all.orEmpty()
        val allServices = serviceObjects.map(::classifyService)
        val referenced = referencedMiddlewares(entrypoints)
        val allMiddlewares = middlewares?.all.orEmpty().map { classifyMiddleware(it, referenced) }

        val backendsUp = serviceObjects.sumOf { obj -> obj.serverStatus.orEmpty().count { it.value.uppercase() == "UP" } }
        val backendsTotal = serviceObjects.sumOf { it.serverStatus.orEmpty().size }

        val cards = listOf(
            card(
                key = "http",
                title = UiText.of(R.string.dashboard_card_http),
                signals = httpRouters,
                listed = routersListed,
                overviewTotal = overview?.http?.routers?.total,
                emptyLabel = UiText.of(R.string.dashboard_empty_http),
                okSub = { ok -> UiText.of(R.string.dashboard_sub_live, ok) },
            ),
            card(
                key = "stream",
                title = UiText.of(R.string.dashboard_card_stream),
                signals = streamRouters,
                listed = routersListed,
                overviewTotal = sumOf(overview?.tcp?.routers?.total, overview?.udp?.routers?.total),
                emptyLabel = UiText.of(R.string.dashboard_empty_stream),
                okSub = {
                    UiText.join(
                        buildList {
                            if (tcpRouters.isNotEmpty()) add(UiText.Raw("TCP ${tcpRouters.size}"))
                            if (udpRouters.isNotEmpty()) add(UiText.Raw("UDP ${udpRouters.size}"))
                            add(UiText.of(R.string.dashboard_sub_forwarding))
                        },
                    )
                },
            ),
            card(
                key = "services",
                title = UiText.of(R.string.common_label_services),
                signals = allServices,
                listed = servicesListed,
                overviewTotal = sumOf(
                    overview?.http?.services?.total,
                    overview?.tcp?.services?.total,
                    overview?.udp?.services?.total,
                ),
                emptyLabel = UiText.of(R.string.dashboard_empty_services),
                okSub = {
                    if (backendsTotal == 0) UiText.of(R.string.dashboard_sub_no_health_checks)
                    else UiText.plural(R.plurals.dashboard_backends_up, backendsTotal, backendsUp, backendsTotal)
                },
            ),
            card(
                key = "middlewares",
                title = UiText.of(R.string.common_label_middlewares),
                signals = allMiddlewares,
                listed = middlewaresListed,
                overviewTotal = sumOf(overview?.http?.middlewares?.total, overview?.tcp?.middlewares?.total),
                emptyLabel = UiText.of(R.string.dashboard_empty_middlewares),
                okSub = { ok ->
                    val unused = allMiddlewares.count { it.state == ObjectState.Idle }
                    UiText.join(
                        listOfNotNull(
                            UiText.of(R.string.dashboard_sub_in_use, ok),
                            if (unused > 0) UiText.of(R.string.dashboard_sub_unused, unused) else null,
                        ),
                    )
                },
            ),
        )

        return DashboardSnapshot(
            verdict = verdict(cards, reachable, backendsUp, backendsTotal),
            cards = cards,
            entrypoints = entrypointRows(entrypoints, routers),
            traefikReachable = reachable,
            providers = allProviders
                ?: providerCounts(httpRouters + streamRouters + allServices + allMiddlewares),
            runtime = runtime,
            providerFilter = providerFilter,
        )
    }

    fun classifyRouter(obj: TraefikObject): ObjectSignal {
        val bound = (obj.using ?: obj.entryPoints).orEmpty()
        return when {
            obj.status == "disabled" -> ObjectSignal(obj.shortName, ObjectState.Err, UiText.of(R.string.dashboard_reason_disabled), obj.provider)
            obj.status == "warning" -> ObjectSignal(obj.shortName, ObjectState.Warn, UiText.of(R.string.dashboard_reason_warning), obj.provider)
            obj.status != "enabled" -> ObjectSignal(obj.shortName, ObjectState.Idle, UiText.of(R.string.dashboard_reason_no_status), obj.provider)
            bound.isEmpty() -> ObjectSignal(obj.shortName, ObjectState.Idle, UiText.of(R.string.dashboard_reason_unbound), obj.provider)
            else -> ObjectSignal(obj.shortName, ObjectState.Ok, null, obj.provider)
        }
    }

    fun classifyService(obj: TraefikObject): ObjectSignal {
        val servers = obj.serverStatus
        val down = servers?.count { it.value.uppercase() != "UP" } ?: 0
        return when {
            obj.status == "disabled" -> ObjectSignal(obj.shortName, ObjectState.Err, UiText.of(R.string.dashboard_reason_disabled), obj.provider)
            obj.status == "warning" -> ObjectSignal(obj.shortName, ObjectState.Warn, UiText.of(R.string.dashboard_reason_warning), obj.provider)
            obj.status != "enabled" -> ObjectSignal(obj.shortName, ObjectState.Idle, UiText.of(R.string.dashboard_reason_no_status), obj.provider)
            servers.isNullOrEmpty() && obj.type != null && obj.type != "loadBalancer" ->
                ObjectSignal(obj.shortName, ObjectState.Ok, null, obj.provider)
            servers.isNullOrEmpty() -> ObjectSignal(obj.shortName, ObjectState.Idle, UiText.of(R.string.dashboard_reason_no_health_check), obj.provider)
            down == servers.size ->
                ObjectSignal(
                    obj.shortName,
                    ObjectState.Err,
                    UiText.plural(R.plurals.dashboard_reason_all_down, servers.size),
                    obj.provider,
                )
            down > 0 -> ObjectSignal(
                obj.shortName,
                ObjectState.Warn,
                UiText.plural(R.plurals.dashboard_reason_some_down, servers.size, down, servers.size),
                obj.provider,
            )
            else -> ObjectSignal(obj.shortName, ObjectState.Ok, null, obj.provider)
        }
    }

    fun classifyMiddleware(obj: TraefikObject, referenced: Set<String>): ObjectSignal {
        val used = obj.usedBy?.isNotEmpty() == true || obj.shortName in referenced || obj.name in referenced
        return when {
            obj.status == "disabled" -> ObjectSignal(obj.shortName, ObjectState.Err, UiText.of(R.string.dashboard_reason_disabled), obj.provider)
            obj.status == "warning" -> ObjectSignal(obj.shortName, ObjectState.Warn, UiText.of(R.string.dashboard_reason_warning), obj.provider)
            obj.status != "enabled" -> ObjectSignal(obj.shortName, ObjectState.Idle, UiText.of(R.string.dashboard_reason_no_status), obj.provider)
            !used -> ObjectSignal(obj.shortName, ObjectState.Idle, UiText.of(R.string.dashboard_reason_unreferenced), obj.provider)
            else -> ObjectSignal(obj.shortName, ObjectState.Ok, null, obj.provider)
        }
    }

    private fun referencedMiddlewares(entrypoints: List<Entrypoint>?): Set<String> =
        entrypoints.orEmpty()
            .flatMap { it.http?.middlewares.orEmpty() }
            .flatMap { listOf(it, it.substringBefore('@')) }
            .toSet()

    private fun sumOf(vararg values: Int?): Int? =
        if (values.all { it == null }) null else values.sumOf { it ?: 0 }

    private fun cellsOf(signals: List<ObjectSignal>): List<TmStatus> =
        signals.sortedBy { it.state.ordinal }.map {
            when (it.state) {
                ObjectState.Err -> TmStatus.Error
                ObjectState.Warn -> TmStatus.Warn
                ObjectState.Idle -> TmStatus.Unknown
                ObjectState.Ok -> TmStatus.Ok
            }
        }

    private fun providerCounts(signals: List<ObjectSignal>): List<ProviderCount> =
        signals
            .filter { it.provider.isNotEmpty() }
            .groupBy { it.provider }
            .map { (name, group) ->
                ProviderCount(
                    name = name,
                    count = group.size,
                    worst = when {
                        group.any { it.state == ObjectState.Err } -> TmStatus.Error
                        group.any { it.state == ObjectState.Warn } -> TmStatus.Warn
                        else -> TmStatus.Ok
                    },
                )
            }
            .sortedWith(compareByDescending<ProviderCount> { it.count }.thenBy { it.name })

    private fun card(
        key: String,
        title: UiText,
        signals: List<ObjectSignal>,
        listed: Boolean,
        overviewTotal: Int?,
        emptyLabel: UiText,
        okSub: (Int) -> UiText,
    ): SignalCard {
        val err = signals.count { it.state == ObjectState.Err }
        val warn = signals.count { it.state == ObjectState.Warn }
        val idle = signals.count { it.state == ObjectState.Idle }
        val ok = signals.count { it.state == ObjectState.Ok }
        val total = when {
            listed -> signals.size
            overviewTotal != null -> overviewTotal
            else -> null
        }
        val hidden = if (listed && overviewTotal != null) (overviewTotal - signals.size).coerceAtLeast(0) else 0

        val flags = buildList {
            if (err > 0) add(SignalFlag(err, UiText.of(R.string.dashboard_reason_disabled), TmStatus.Error))
            if (warn > 0) add(SignalFlag(warn, UiText.of(R.string.dashboard_flag_warnings), TmStatus.Warn))
            if (idle > 0) {
                add(
                    SignalFlag(
                        idle,
                        UiText.of(if (key == "middlewares") R.string.middlewares_unused else R.string.dashboard_flag_idle),
                        TmStatus.Unknown,
                    ),
                )
            }
        }

        val sub = when {
            !listed && total == null -> UiText.of(R.string.dashboard_unreachable)
            !listed -> UiText.of(R.string.dashboard_sub_overview_only)
            total == 0 -> emptyLabel
            hidden > 0 && err == 0 && warn == 0 ->
                UiText.join(listOf(okSub(ok), UiText.of(R.string.dashboard_sub_hidden, hidden)))
            err > 0 -> signals.first { it.state == ObjectState.Err }.let(::named)
            warn > 0 -> signals.first { it.state == ObjectState.Warn }.let(::named)
            else -> okSub(ok)
        }

        val health = when {
            !listed && total == null -> TmStatus.Warn
            err > 0 -> TmStatus.Error
            warn > 0 -> TmStatus.Warn
            else -> TmStatus.Ok
        }

        val healthLabel = when {
            !listed && total == null -> UiText.of(R.string.common_unknown)
            err > 0 -> UiText.of(R.string.dashboard_health_down)
            warn > 0 -> UiText.of(R.string.routes_ping_degraded)
            else -> UiText.of(R.string.dashboard_health_healthy)
        }

        return SignalCard(
            key = key,
            title = title,
            total = total,
            cells = if (listed) cellsOf(signals) else emptyList(),
            flags = flags,
            sub = sub,
            health = health,
            healthLabel = healthLabel,
            providers = if (listed) providerCounts(signals) else emptyList(),
            stripEmptyLabel = UiText.of(if (!listed) R.string.dashboard_strip_no_data else R.string.dashboard_strip_empty),
        )
    }

    private fun entrypointRows(entrypoints: List<Entrypoint>?, routers: ProtoEnvelope?): List<EntrypointRow> {
        if (entrypoints == null) return emptyList()
        val listed = routers != null && routers.reachable
        return entrypoints.filter { it.name.isNotEmpty() }.map { ep ->
            val bound = routers?.all.orEmpty().filter { ep.name in (it.using ?: it.entryPoints).orEmpty() }
            val signals = bound.map(::classifyRouter)
            val httpBound = routers?.http.orEmpty().count { ep.name in (it.using ?: it.entryPoints).orEmpty() }
            val tcpBound = routers?.tcp.orEmpty().any { ep.name in (it.using ?: it.entryPoints).orEmpty() }
            val proto = when {
                ep.isUdp -> "UDP"
                httpBound == 0 && tcpBound -> "TCP"
                ep.hasTls || ep.address.endsWith(":443") || ep.address.endsWith(":8443") -> "HTTPS"
                else -> "HTTP"
            }
            EntrypointRow(
                name = ep.name,
                address = ep.address,
                proto = proto,
                routerCount = if (listed) bound.size else null,
                cells = if (listed) cellsOf(signals) else emptyList(),
                facts = entrypointFacts(ep, bound.size, proto, listed),
                health = when {
                    signals.any { it.state == ObjectState.Err } -> TmStatus.Error
                    signals.any { it.state == ObjectState.Warn } -> TmStatus.Warn
                    else -> TmStatus.Ok
                },
                idle = listed && bound.isEmpty(),
            )
        }
    }

    private fun named(signal: ObjectSignal): UiText =
        UiText.join(listOfNotNull(UiText.Raw(signal.name), signal.reason), ": ")

    private fun entrypointFacts(ep: Entrypoint, boundCount: Int, proto: String, listed: Boolean): UiText {
        if (!listed) return UiText.of(R.string.dashboard_entry_unlisted)
        if (boundCount == 0) return UiText.of(R.string.dashboard_entry_unbound)
        val facts = buildList {
            if (ep.asDefault) add(UiText.Raw("asDefault"))
            val resolver = ep.http?.tls?.certResolver
            if (!resolver.isNullOrEmpty()) add(UiText.of(R.string.dashboard_entry_tls_via, resolver))
            if (ep.hasTls && resolver.isNullOrEmpty()) add(UiText.of(R.string.dashboard_entry_tls_no_resolver))
            ep.http?.tls?.options?.takeIf { it.isNotEmpty() }
                ?.let { add(UiText.of(R.string.dashboard_entry_tls_options, it)) }
            ep.http?.middlewares?.takeIf { it.isNotEmpty() }
                ?.let { add(UiText.Raw(it.joinToString(", ") { name -> name.substringBefore('@') })) }
        }
        if (facts.isNotEmpty()) return UiText.join(facts)
        return UiText.of(
            when (proto) {
                "UDP" -> R.string.dashboard_entry_udp
                "TCP" -> R.string.dashboard_entry_tcp
                "HTTPS" -> R.string.dashboard_entry_https
                else -> R.string.dashboard_entry_http
            },
        )
    }

    private fun verdict(cards: List<SignalCard>, reachable: Boolean, backendsUp: Int, backendsTotal: Int): Verdict {
        if (!reachable) {
            return Verdict(
                headline = UiText.of(R.string.dashboard_unreachable),
                detail = UiText.of(R.string.dashboard_verdict_unreadable),
                status = TmStatus.Warn,
            )
        }
        val errors = cards.sumOf { card -> card.flags.filter { it.status == TmStatus.Error }.sumOf { it.count } }
        val warnings = cards.sumOf { card -> card.flags.filter { it.status == TmStatus.Warn }.sumOf { it.count } }
        return when {
            errors > 0 -> Verdict(
                headline = UiText.plural(R.plurals.dashboard_verdict_down, errors),
                detail = if (warnings > 0) {
                    UiText.plural(R.plurals.dashboard_verdict_also_degraded, warnings)
                } else {
                    UiText.of(R.string.dashboard_verdict_no_traffic)
                },
                status = TmStatus.Error,
            )
            warnings > 0 -> Verdict(
                headline = UiText.plural(R.plurals.dashboard_verdict_degraded, warnings),
                detail = UiText.of(R.string.dashboard_verdict_still_serving),
                status = TmStatus.Warn,
            )
            else -> Verdict(
                headline = UiText.of(R.string.dashboard_verdict_healthy),
                detail = UiText.join(
                    listOfNotNull(
                        UiText.of(R.string.dashboard_verdict_clean),
                        if (backendsTotal > 0) {
                            UiText.plural(R.plurals.dashboard_backends_up, backendsTotal, backendsUp, backendsTotal)
                        } else {
                            null
                        },
                    ),
                ),
                status = TmStatus.Ok,
            )
        }
    }
}
