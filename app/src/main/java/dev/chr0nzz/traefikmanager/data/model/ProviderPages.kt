package dev.chr0nzz.traefikmanager.data.model

import androidx.annotation.StringRes
import dev.chr0nzz.traefikmanager.R

enum class ProviderPage(
    val tab: String,
    val label: UiText,
    val providers: Set<String>,
    @StringRes val note: Int,
    @StringRes val empty: Int,
) {
    Docker(
        tab = "docker",
        label = UiText.Raw("Docker"),
        providers = setOf("docker"),
        note = R.string.providers_docker_note,
        empty = R.string.providers_docker_empty,
    ),
    Kubernetes(
        tab = "kubernetes",
        label = UiText.Raw("Kubernetes"),
        providers = setOf("kubernetes", "kubernetescrd", "kubernetesingress", "kubernetesgateway"),
        note = R.string.providers_kubernetes_note,
        empty = R.string.providers_kubernetes_empty,
    ),
    Swarm(
        tab = "swarm",
        label = UiText.Raw("Swarm"),
        providers = setOf("swarm"),
        note = R.string.providers_swarm_note,
        empty = R.string.providers_swarm_empty,
    ),
    Nomad(
        tab = "nomad",
        label = UiText.Raw("Nomad"),
        providers = setOf("nomad"),
        note = R.string.providers_nomad_note,
        empty = R.string.providers_nomad_empty,
    ),
    Ecs(
        tab = "ecs",
        label = UiText.Raw("ECS"),
        providers = setOf("ecs"),
        note = R.string.providers_ecs_note,
        empty = R.string.providers_ecs_empty,
    ),
    ConsulCatalog(
        tab = "consulcatalog",
        label = UiText.Raw("Consul Catalog"),
        providers = setOf("consulcatalog"),
        note = R.string.providers_consulcatalog_note,
        empty = R.string.providers_consulcatalog_empty,
    ),
    Redis(
        tab = "redis",
        label = UiText.Raw("Redis"),
        providers = setOf("redis"),
        note = R.string.providers_redis_note,
        empty = R.string.providers_redis_empty,
    ),
    Etcd(
        tab = "etcd",
        label = UiText.Raw("etcd"),
        providers = setOf("etcd"),
        note = R.string.providers_etcd_note,
        empty = R.string.providers_etcd_empty,
    ),
    Consul(
        tab = "consul",
        label = UiText.Raw("Consul KV"),
        providers = setOf("consul"),
        note = R.string.providers_consul_note,
        empty = R.string.providers_consul_empty,
    ),
    ZooKeeper(
        tab = "zookeeper",
        label = UiText.Raw("ZooKeeper"),
        providers = setOf("zookeeper"),
        note = R.string.providers_zookeeper_note,
        empty = R.string.providers_zookeeper_empty,
    ),
    HttpProvider(
        tab = "http_provider",
        label = UiText.of(R.string.providers_label_http),
        providers = setOf("http"),
        note = R.string.providers_http_provider_note,
        empty = R.string.providers_http_provider_empty,
    ),
    FileExternal(
        tab = "file_external",
        label = UiText.of(R.string.providers_label_file_external),
        providers = setOf("file"),
        note = R.string.providers_file_external_note,
        empty = R.string.providers_file_external_empty,
    ),
    Internal(
        tab = "internal",
        label = UiText.of(R.string.providers_label_internal),
        providers = setOf("internal"),
        note = R.string.providers_internal_note,
        empty = R.string.providers_internal_empty,
    ),
    ;

    val route: String get() = "provider_$tab"
}

enum class ProviderProtocol(val label: String) { Http("HTTP"), Tcp("TCP"), Udp("UDP") }

data class ProviderRoute(
    val name: String,
    val shortName: String,
    val protocol: ProviderProtocol,
    val status: String,
    val rule: String,
    val service: String,
    val target: String?,
    val entryPoints: List<String>,
    val middlewares: List<String>,
    val tls: Boolean,
    val provider: String,
) {
    val serving: Boolean get() = status.isEmpty() || status.equals("enabled", ignoreCase = true)

    val hosts: List<String>
        get() = HOST.findAll(rule).filter { it.groupValues[1] != "!" }.map { it.groupValues[2] }.toList()

    val plainHost: String?
        get() = hosts.firstOrNull()?.takeIf { PLAIN.matches(rule.trim()) }

    private companion object {
        val HOST = Regex("""(!?)\s*Host\(`([^`]+)`\)""")
        val PLAIN = Regex("""^Host\(`[^`]+`\)(\s*\|\|\s*Host\(`[^`]+`\))*$""")
    }
}

data class ProviderMiddleware(
    val name: String,
    val type: String,
    val status: String,
)

data class ProviderVerdict(
    val routes: Int,
    val notServing: Int,
    val http: Int,
    val tcp: Int,
    val udp: Int,
    val middlewares: Int,
) {
    val headline: UiText
        get() = when {
            notServing > 0 -> UiText.plural(R.plurals.providers_verdict_not_serving, notServing)
            else -> UiText.plural(R.plurals.providers_verdict_live, routes)
        }

    val detail: UiText
        get() = buildList {
            if (http > 0) add(UiText.Raw("$http HTTP"))
            if (tcp > 0) add(UiText.Raw("$tcp TCP"))
            if (udp > 0) add(UiText.Raw("$udp UDP"))
            if (middlewares > 0) add(UiText.plural(R.plurals.common_middlewares_count, middlewares))
        }.takeIf { it.isNotEmpty() }?.let { UiText.join(it) } ?: UiText.of(R.string.providers_verdict_none)
}

object ProviderRows {

    fun providerOf(name: String, provider: String): String =
        provider.ifEmpty { name.substringAfter('@', "") }.lowercase()

    fun routes(
        page: ProviderPage,
        routers: ProtoEnvelope,
        services: ServiceEnvelope?,
        managed: Set<String> = emptySet(),
    ): List<ProviderRoute> {
        val targets = targets(page, services)
        return listOf(
            ProviderProtocol.Http to routers.http,
            ProviderProtocol.Tcp to routers.tcp,
            ProviderProtocol.Udp to routers.udp,
        ).flatMap { (protocol, list) ->
            list.mapNotNull { router ->
                val provider = providerOf(router.name, router.provider)
                if (provider !in page.providers) return@mapNotNull null
                if (page == ProviderPage.FileExternal && router.shortName in managed) return@mapNotNull null
                val service = router.service.orEmpty()
                ProviderRoute(
                    name = router.name,
                    shortName = router.shortName,
                    protocol = protocol,
                    status = router.status.orEmpty(),
                    rule = router.rule.orEmpty(),
                    service = service,
                    target = targets[service] ?: targets[service.substringBefore('@')],
                    entryPoints = router.entryPoints.orEmpty(),
                    middlewares = router.middlewares.orEmpty(),
                    tls = router.tls != null && router.tls !is kotlinx.serialization.json.JsonNull,
                    provider = provider,
                )
            }
        }.sortedBy { it.name.lowercase() }
    }

    fun middlewares(page: ProviderPage, envelope: ProtoEnvelope?): List<ProviderMiddleware> =
        (envelope?.http.orEmpty() + envelope?.tcp.orEmpty())
            .filter { providerOf(it.name, it.provider) in page.providers }
            .map { ProviderMiddleware(it.shortName, it.type.orEmpty(), it.status.orEmpty()) }
            .sortedBy { it.name.lowercase() }

    fun verdict(routes: List<ProviderRoute>, middlewares: List<ProviderMiddleware>): ProviderVerdict = ProviderVerdict(
        routes = routes.size,
        notServing = routes.count { !it.serving },
        http = routes.count { it.protocol == ProviderProtocol.Http },
        tcp = routes.count { it.protocol == ProviderProtocol.Tcp },
        udp = routes.count { it.protocol == ProviderProtocol.Udp },
        middlewares = middlewares.size,
    )

    private fun targets(page: ProviderPage, services: ServiceEnvelope?): Map<String, String> {
        if (services == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        (services.http + services.tcp + services.udp).forEach { service ->
            if (providerOf(service.name, service.provider) !in page.providers) return@forEach
            val target = service.loadBalancer?.servers?.firstOrNull()?.target?.takeIf { it.isNotEmpty() }
                ?: service.serverStatus?.keys?.firstOrNull()
                ?: return@forEach
            out[service.name] = target
            out.putIfAbsent(service.name.substringBefore('@'), target)
        }
        return out
    }
}
