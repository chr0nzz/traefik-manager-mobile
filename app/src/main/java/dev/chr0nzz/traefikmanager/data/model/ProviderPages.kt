package dev.chr0nzz.traefikmanager.data.model

enum class ProviderPage(
    val tab: String,
    val label: String,
    val providers: Set<String>,
    val note: String,
    val empty: String,
) {
    Docker(
        tab = "docker",
        label = "Docker",
        providers = setOf("docker"),
        note = "Managed by Docker labels and read-only. Edit them through your container's labels.",
        empty = "Routes discovered through Docker labels show up here.",
    ),
    Kubernetes(
        tab = "kubernetes",
        label = "Kubernetes",
        providers = setOf("kubernetes", "kubernetescrd", "kubernetesingress", "kubernetesgateway"),
        note = "Managed by Kubernetes (CRD, Ingress or Gateway API) and read-only. Edit them through your Kubernetes resources.",
        empty = "Routes from Kubernetes CRDs, Ingress and Gateway API show up here.",
    ),
    Swarm(
        tab = "swarm",
        label = "Swarm",
        providers = setOf("swarm"),
        note = "Discovered through Docker Swarm labels and read-only. Edit them through your Swarm service definitions.",
        empty = "Routes discovered through Swarm labels show up here.",
    ),
    Nomad(
        tab = "nomad",
        label = "Nomad",
        providers = setOf("nomad"),
        note = "Discovered through HashiCorp Nomad and read-only. Edit them through your Nomad job definitions.",
        empty = "Routes discovered through Nomad show up here.",
    ),
    Ecs(
        tab = "ecs",
        label = "ECS",
        providers = setOf("ecs"),
        note = "Discovered through Amazon ECS and read-only. Edit them through your ECS task definitions.",
        empty = "Routes discovered through ECS show up here.",
    ),
    ConsulCatalog(
        tab = "consulcatalog",
        label = "Consul Catalog",
        providers = setOf("consulcatalog"),
        note = "Discovered through the Consul service catalog and read-only. Edit them through your Consul service registrations.",
        empty = "Routes discovered through the Consul catalog show up here.",
    ),
    Redis(
        tab = "redis",
        label = "Redis",
        providers = setOf("redis"),
        note = "Stored in Redis and read-only here. Edit them in your Redis KV store.",
        empty = "Routes stored in Redis show up here.",
    ),
    Etcd(
        tab = "etcd",
        label = "etcd",
        providers = setOf("etcd"),
        note = "Stored in etcd and read-only here. Edit them in your etcd KV store.",
        empty = "Routes stored in etcd show up here.",
    ),
    Consul(
        tab = "consul",
        label = "Consul KV",
        providers = setOf("consul"),
        note = "Stored in Consul's key-value store and read-only here. Edit them in Consul KV.",
        empty = "Routes stored in Consul KV show up here.",
    ),
    ZooKeeper(
        tab = "zookeeper",
        label = "ZooKeeper",
        providers = setOf("zookeeper"),
        note = "Stored in ZooKeeper and read-only here. Edit them in your ZooKeeper instance.",
        empty = "Routes stored in ZooKeeper show up here.",
    ),
    HttpProvider(
        tab = "http_provider",
        label = "HTTP Provider",
        providers = setOf("http"),
        note = "Sourced from an HTTP endpoint configured in Traefik and read-only here.",
        empty = "Routes from the HTTP provider show up here.",
    ),
    FileExternal(
        tab = "file_external",
        label = "File (external)",
        providers = setOf("file"),
        note = "From file provider configs Traefik Manager does not manage, and read-only here. Managed routes are on Routes.",
        empty = "Routes from external file provider configs show up here.",
    ),
    Internal(
        tab = "internal",
        label = "Internal",
        providers = setOf("internal"),
        note = "Traefik creates these itself. They are read-only here and turned on or off in your static config.",
        empty = "Traefik serves these itself. The dashboard, API and ping live here.",
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
    val headline: String
        get() = when {
            notServing > 0 -> "$notServing ${if (notServing == 1) "route" else "routes"} not serving"
            else -> "$routes ${if (routes == 1) "route" else "routes"} live"
        }
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
