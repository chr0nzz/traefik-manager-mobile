package dev.chr0nzz.traefikmanager.data.api

import dev.chr0nzz.traefikmanager.data.model.Agent
import dev.chr0nzz.traefikmanager.data.model.AgentHealth
import dev.chr0nzz.traefikmanager.data.model.AgentsResponse
import dev.chr0nzz.traefikmanager.data.model.ApiKeyStatus
import dev.chr0nzz.traefikmanager.data.model.ConfigError
import dev.chr0nzz.traefikmanager.data.model.Entrypoint
import dev.chr0nzz.traefikmanager.data.model.EntrypointHttp
import dev.chr0nzz.traefikmanager.data.model.EntrypointTls
import dev.chr0nzz.traefikmanager.data.model.ManagerVersion
import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.data.model.OkResponse
import dev.chr0nzz.traefikmanager.data.model.Overview
import dev.chr0nzz.traefikmanager.data.model.OverviewCounts
import dev.chr0nzz.traefikmanager.data.model.OverviewSection
import dev.chr0nzz.traefikmanager.data.model.ProtoEnvelope
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.model.RoutesResponse
import dev.chr0nzz.traefikmanager.data.model.ToggleRequest
import dev.chr0nzz.traefikmanager.data.model.TraefikObject
import dev.chr0nzz.traefikmanager.data.model.TraefikVersion
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray

class DemoApi : TmApi {

    private val disabled = mutableSetOf("routes.yml::old-blog")

    override suspend fun apiKeyStatus(): ApiKeyStatus = ApiKeyStatus(enabled = true, count = 1)

    override suspend fun managerVersion() = ManagerVersion(version = "1.10.1", repo = "chr0nzz/traefik-manager")

    override suspend fun traefikVersion() = TraefikVersion(version = "3.5.0", codename = "chevrotin")

    override suspend fun overview(): Overview {
        settle()
        return Overview(
            http = OverviewSection(
                routers = OverviewCounts(8, 1, 1),
                services = OverviewCounts(8, 0, 1),
                middlewares = OverviewCounts(5, 0, 0),
            ),
            tcp = OverviewSection(
                routers = OverviewCounts(2, 0, 0),
                services = OverviewCounts(2, 0, 0),
            ),
            udp = OverviewSection(
                routers = OverviewCounts(1, 0, 0),
                services = OverviewCounts(1, 0, 0),
            ),
            providers = listOf("File", "Docker", "Internal"),
        )
    }

    override suspend fun entrypoints(): List<Entrypoint> {
        settle()
        return listOf(
            Entrypoint(
                name = "web",
                address = ":80",
            ),
            Entrypoint(
                name = "websecure",
                address = ":443",
                asDefault = true,
                http = EntrypointHttp(
                    middlewares = listOf("crowdsec@file"),
                    tls = EntrypointTls(certResolver = "letsencrypt"),
                ),
            ),
            Entrypoint(name = "wireguard", address = ":51820/udp"),
        )
    }

    override suspend fun routers(): ProtoEnvelope {
        settle()
        return ProtoEnvelope(
            http = DEMO_ROUTES.filter { it.protocol == "http" }.map { route ->
                TraefikObject(
                    name = "${route.name}@file",
                    provider = "file",
                    status = if (route.name == "broken-app") "disabled" else "enabled",
                    rule = route.rule,
                    service = route.serviceName,
                    using = route.entryPointNames,
                    entryPoints = route.entryPointNames,
                )
            },
            tcp = listOf(
                TraefikObject("postgres@file", "file", "enabled", using = listOf("postgres")),
                TraefikObject("redis@file", "file", "enabled", using = listOf("redis")),
            ),
            udp = listOf(TraefikObject("wireguard@file", "file", "enabled", using = listOf("wireguard"))),
        )
    }

    override suspend fun services(): ProtoEnvelope {
        settle()
        return ProtoEnvelope(
            http = DEMO_ROUTES.filter { it.protocol == "http" }.map { route ->
                TraefikObject(
                    name = "${route.serviceName}@file",
                    provider = "file",
                    status = if (route.name == "broken-app") "disabled" else "enabled",
                    type = "loadbalancer",
                    serverStatus = if (route.name == "media") mapOf(
                        "http://10.0.0.9:8096" to "UP",
                        "http://10.0.0.10:8096" to "DOWN",
                    ) else mapOf(route.target to "UP"),
                )
            },
        )
    }

    override suspend fun middlewares(): ProtoEnvelope {
        settle()
        return ProtoEnvelope(
            http = listOf(
                TraefikObject("crowdsec@file", "file", "enabled", type = "plugin", usedBy = listOf("dashboard@file")),
                TraefikObject("secure-headers@file", "file", "enabled", type = "headers", usedBy = listOf("media@file")),
                TraefikObject("basic-auth@file", "file", "enabled", type = "basicauth"),
                TraefikObject("rate-limit@file", "file", "enabled", type = "ratelimit", usedBy = listOf("api@file")),
                TraefikObject("redirect-https@file", "file", "enabled", type = "redirectscheme", usedBy = listOf("blog@file")),
            ),
        )
    }

    override suspend fun routes(): RoutesResponse {
        settle()
        return RoutesResponse(
            apps = DEMO_ROUTES.map { it.copy(enabled = it.id !in disabled) },
            middlewares = listOf(
                MiddlewareDef("crowdsec", "http", "plugin:\n  crowdsec-bouncer:\n    enabled: true\n", "routes.yml"),
                MiddlewareDef("secure-headers", "http", "headers:\n  frameDeny: true\n  stsSeconds: 31536000\n", "routes.yml"),
                MiddlewareDef("basic-auth", "http", "basicAuth:\n  users:\n    - admin:\$apr1\$demo\n", "routes.yml"),
            ),
            configErrors = emptyList<ConfigError>(),
        )
    }

    override suspend fun agentRoutes(agentId: String): RoutesResponse = routes()

    override suspend fun toggleRoute(routeId: String, body: ToggleRequest): OkResponse {
        settle()
        if (body.enable) disabled.remove(routeId) else disabled.add(routeId)
        return OkResponse(ok = true)
    }

    override suspend fun agents(): AgentsResponse = AgentsResponse(
        agents = listOf(
            Agent(id = "demo-agent-1", name = "vps-frankfurt", url = "https://agent-fra.example.com:8090"),
            Agent(id = "demo-agent-2", name = "vps-nyc", url = "https://agent-nyc.example.com:8090"),
        ),
    )

    override suspend fun agentHealth(agentId: String) = AgentHealth(ok = true, version = "1.10.1", latencyMs = 12)

    private suspend fun settle() = delay(220)

    private companion object {
        fun route(
            name: String,
            host: String,
            target: String,
            protocol: String = "http",
            entryPoint: String = "websecure",
            middlewares: List<String> = emptyList(),
            tls: Boolean = true,
            servers: List<String> = emptyList(),
        ) = Route(
            id = "routes.yml::$name",
            name = name,
            rule = if (protocol == "udp") "" else "Host(`$host`)",
            serviceName = name,
            target = target,
            servers = servers.ifEmpty { listOf(target) },
            middlewares = JsonArray(middlewares.map { JsonPrimitive(it) }),
            entryPoints = JsonArray(listOf(JsonPrimitive(entryPoint))),
            protocol = protocol,
            tls = JsonPrimitive(tls),
            certResolver = if (tls) "letsencrypt" else "",
            configFile = "routes.yml",
        )

        val DEMO_ROUTES = listOf(
            route("dashboard", "dash.example.com", "http://10.0.0.4:3000", middlewares = listOf("crowdsec")),
            route("media", "media.example.com", "http://10.0.0.9:8096", middlewares = listOf("secure-headers"), servers = listOf("http://10.0.0.9:8096", "http://10.0.0.10:8096")),
            route("api", "api.example.com", "http://10.0.0.12:8000", middlewares = listOf("rate-limit")),
            route("blog", "blog.example.com", "http://10.0.0.14:2368", middlewares = listOf("redirect-https")),
            route("git", "git.example.com", "http://10.0.0.16:3000"),
            route("photos", "photos.example.com", "http://10.0.0.18:2283"),
            route("broken-app", "broken.example.com", "N/A"),
            route("old-blog", "old.example.com", "http://10.0.0.20:8080"),
            route("postgres", "", "10.0.0.30:5432", protocol = "tcp", entryPoint = "postgres", tls = false),
            route("redis", "", "10.0.0.31:6379", protocol = "tcp", entryPoint = "redis", tls = false),
            route("wireguard", "", "10.0.0.32:51820", protocol = "udp", entryPoint = "wireguard", tls = false),
        )
    }
}
