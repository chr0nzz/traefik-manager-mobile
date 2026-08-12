package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.model.Entrypoint
import dev.chr0nzz.traefikmanager.data.model.Overview
import dev.chr0nzz.traefikmanager.data.model.ProtoEnvelope
import dev.chr0nzz.traefikmanager.data.model.toProtoEnvelope
import dev.chr0nzz.traefikmanager.data.model.TraefikObject
import dev.chr0nzz.traefikmanager.ui.components.TmStatus

enum class ObjectState { Err, Warn, Idle, Ok }

data class ObjectSignal(
    val name: String,
    val state: ObjectState,
    val reason: String?,
    val provider: String,
)

data class SignalFlag(
    val count: Int,
    val label: String,
    val status: TmStatus,
)

data class ProviderCount(
    val name: String,
    val count: Int,
    val worst: TmStatus,
)

data class SignalCard(
    val key: String,
    val title: String,
    val total: Int?,
    val cells: List<TmStatus>,
    val flags: List<SignalFlag>,
    val sub: String,
    val health: TmStatus,
    val healthLabel: String,
    val providers: List<ProviderCount>,
    val stripEmptyLabel: String,
)

data class EntrypointRow(
    val name: String,
    val address: String,
    val proto: String,
    val routerCount: Int?,
    val cells: List<TmStatus>,
    val facts: String,
    val health: TmStatus,
    val idle: Boolean,
)

data class Verdict(
    val headline: String,
    val detail: String,
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
                title = "HTTP Routers",
                signals = httpRouters,
                listed = routersListed,
                overviewTotal = overview?.http?.routers?.total,
                emptyLabel = "no HTTP routers configured",
                okSub = { ok -> "$ok live" },
            ),
            card(
                key = "stream",
                title = "TCP / UDP Routers",
                signals = streamRouters,
                listed = routersListed,
                overviewTotal = sumOf(overview?.tcp?.routers?.total, overview?.udp?.routers?.total),
                emptyLabel = "no stream routers configured",
                okSub = {
                    val parts = buildList {
                        if (tcpRouters.isNotEmpty()) add("TCP ${tcpRouters.size}")
                        if (udpRouters.isNotEmpty()) add("UDP ${udpRouters.size}")
                        add("all forwarding")
                    }
                    parts.joinToString(" · ")
                },
            ),
            card(
                key = "services",
                title = "Services",
                signals = allServices,
                listed = servicesListed,
                overviewTotal = sumOf(
                    overview?.http?.services?.total,
                    overview?.tcp?.services?.total,
                    overview?.udp?.services?.total,
                ),
                emptyLabel = "no services configured",
                okSub = {
                    if (backendsTotal == 0) "no health checks configured"
                    else "$backendsUp of $backendsTotal backends up"
                },
            ),
            card(
                key = "middlewares",
                title = "Middlewares",
                signals = allMiddlewares,
                listed = middlewaresListed,
                overviewTotal = sumOf(overview?.http?.middlewares?.total, overview?.tcp?.middlewares?.total),
                emptyLabel = "no middlewares configured",
                okSub = { ok ->
                    val unused = allMiddlewares.count { it.state == ObjectState.Idle }
                    if (unused > 0) "$ok in use · $unused unused" else "$ok in use"
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
            obj.status == "disabled" -> ObjectSignal(obj.shortName, ObjectState.Err, "disabled", obj.provider)
            obj.status == "warning" -> ObjectSignal(obj.shortName, ObjectState.Warn, "warning", obj.provider)
            obj.status != "enabled" -> ObjectSignal(obj.shortName, ObjectState.Idle, "no status reported", obj.provider)
            bound.isEmpty() -> ObjectSignal(obj.shortName, ObjectState.Idle, "bound to no entry point", obj.provider)
            else -> ObjectSignal(obj.shortName, ObjectState.Ok, null, obj.provider)
        }
    }

    fun classifyService(obj: TraefikObject): ObjectSignal {
        val servers = obj.serverStatus
        val down = servers?.count { it.value.uppercase() != "UP" } ?: 0
        return when {
            obj.status == "disabled" -> ObjectSignal(obj.shortName, ObjectState.Err, "disabled", obj.provider)
            obj.status == "warning" -> ObjectSignal(obj.shortName, ObjectState.Warn, "warning", obj.provider)
            obj.status != "enabled" -> ObjectSignal(obj.shortName, ObjectState.Idle, "no status reported", obj.provider)
            servers.isNullOrEmpty() -> ObjectSignal(obj.shortName, ObjectState.Idle, "no health check configured", obj.provider)
            down > 0 -> ObjectSignal(obj.shortName, ObjectState.Warn, "$down of ${servers.size} backends down", obj.provider)
            else -> ObjectSignal(obj.shortName, ObjectState.Ok, null, obj.provider)
        }
    }

    fun classifyMiddleware(obj: TraefikObject, referenced: Set<String>): ObjectSignal {
        val used = obj.usedBy?.isNotEmpty() == true || obj.shortName in referenced || obj.name in referenced
        return when {
            obj.status == "disabled" -> ObjectSignal(obj.shortName, ObjectState.Err, "disabled", obj.provider)
            obj.status == "warning" -> ObjectSignal(obj.shortName, ObjectState.Warn, "warning", obj.provider)
            obj.status != "enabled" -> ObjectSignal(obj.shortName, ObjectState.Idle, "no status reported", obj.provider)
            !used -> ObjectSignal(obj.shortName, ObjectState.Idle, "referenced by no router", obj.provider)
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
        title: String,
        signals: List<ObjectSignal>,
        listed: Boolean,
        overviewTotal: Int?,
        emptyLabel: String,
        okSub: (Int) -> String,
    ): SignalCard {
        val err = signals.count { it.state == ObjectState.Err }
        val warn = signals.count { it.state == ObjectState.Warn }
        val idle = signals.count { it.state == ObjectState.Idle }
        val ok = signals.count { it.state == ObjectState.Ok }
        // Traefik's overview counts every provider and its own internal objects, while the
        // tabs list only what this app manages. When the list is present the list is the truth,
        // otherwise the card would contradict the screen it links to.
        val total = when {
            listed -> signals.size
            overviewTotal != null -> overviewTotal
            else -> null
        }
        val hidden = if (listed && overviewTotal != null) (overviewTotal - signals.size).coerceAtLeast(0) else 0

        val flags = buildList {
            if (err > 0) add(SignalFlag(err, "disabled", TmStatus.Error))
            if (warn > 0) add(SignalFlag(warn, "warnings", TmStatus.Warn))
            if (idle > 0) add(SignalFlag(idle, if (key == "middlewares") "unused" else "idle", TmStatus.Unknown))
        }

        val sub = when {
            !listed && total == null -> "Traefik API unreachable"
            !listed -> "total from overview, the list is unavailable"
            total == 0 -> emptyLabel
            hidden > 0 && err == 0 && warn == 0 -> "${okSub(ok)} · +$hidden internal or other providers"
            err > 0 -> signals.first { it.state == ObjectState.Err }.let { "${it.name}: ${it.reason}" }
            warn > 0 -> signals.first { it.state == ObjectState.Warn }.let { "${it.name}: ${it.reason}" }
            else -> okSub(ok)
        }

        val health = when {
            !listed && total == null -> TmStatus.Warn
            err > 0 -> TmStatus.Error
            warn > 0 -> TmStatus.Warn
            else -> TmStatus.Ok
        }

        val healthLabel = when {
            !listed && total == null -> "unknown"
            err > 0 -> "down"
            warn > 0 -> "degraded"
            else -> "healthy"
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
            stripEmptyLabel = if (!listed) "no data" else "nothing configured",
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

    private fun entrypointFacts(ep: Entrypoint, boundCount: Int, proto: String, listed: Boolean): String {
        if (!listed) return "router list unavailable, bindings unknown"
        if (boundCount == 0) return "no router binds this entry point"
        val facts = buildList {
            if (ep.asDefault) add("asDefault")
            val resolver = ep.http?.tls?.certResolver
            if (!resolver.isNullOrEmpty()) add("TLS via $resolver")
            if (ep.hasTls && resolver.isNullOrEmpty()) add("TLS, no certResolver")
            ep.http?.tls?.options?.takeIf { it.isNotEmpty() }?.let { add("TLS options $it") }
            ep.http?.middlewares?.takeIf { it.isNotEmpty() }
                ?.let { add(it.joinToString(", ") { name -> name.substringBefore('@') }) }
        }
        if (facts.isNotEmpty()) return facts.joinToString(" · ")
        return when (proto) {
            "UDP" -> "raw UDP datagrams"
            "TCP" -> "raw TCP passthrough"
            "HTTPS" -> "HTTPS front door"
            else -> "plain HTTP entry point"
        }
    }

    private fun verdict(cards: List<SignalCard>, reachable: Boolean, backendsUp: Int, backendsTotal: Int): Verdict {
        if (!reachable) {
            return Verdict(
                headline = "Traefik API unreachable",
                detail = "counts and health cannot be read",
                status = TmStatus.Warn,
            )
        }
        val errors = cards.sumOf { card -> card.flags.filter { it.status == TmStatus.Error }.sumOf { it.count } }
        val warnings = cards.sumOf { card -> card.flags.filter { it.status == TmStatus.Warn }.sumOf { it.count } }
        return when {
            errors > 0 -> Verdict(
                headline = if (errors == 1) "1 object is down" else "$errors objects are down",
                detail = if (warnings > 0) "$warnings degraded" else "traffic is not flowing for them",
                status = TmStatus.Error,
            )
            warnings > 0 -> Verdict(
                headline = if (warnings == 1) "1 object is degraded" else "$warnings objects are degraded",
                detail = "still serving, worth a look",
                status = TmStatus.Warn,
            )
            else -> Verdict(
                headline = "All healthy",
                detail = if (backendsTotal > 0) {
                    "no errors or warnings reported · $backendsUp of $backendsTotal backends up"
                } else {
                    "no errors or warnings reported"
                },
                status = TmStatus.Ok,
            )
        }
    }
}
