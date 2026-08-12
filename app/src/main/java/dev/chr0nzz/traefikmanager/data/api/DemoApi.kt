package dev.chr0nzz.traefikmanager.data.api

import dev.chr0nzz.traefikmanager.data.model.Agent
import dev.chr0nzz.traefikmanager.data.model.AgentHealth
import dev.chr0nzz.traefikmanager.data.model.AgentConfig
import dev.chr0nzz.traefikmanager.data.model.AgentMutationResponse
import dev.chr0nzz.traefikmanager.data.model.AgentsResponse
import dev.chr0nzz.traefikmanager.data.model.CreateAgentRequest
import dev.chr0nzz.traefikmanager.data.model.ApiKeyEntry
import dev.chr0nzz.traefikmanager.data.model.ApiKeyStatus
import dev.chr0nzz.traefikmanager.data.model.AuthActionResponse
import dev.chr0nzz.traefikmanager.data.model.ChangePasswordRequest
import dev.chr0nzz.traefikmanager.data.model.GenerateKeyRequest
import dev.chr0nzz.traefikmanager.data.model.GenerateKeyResponse
import dev.chr0nzz.traefikmanager.data.model.OtpStatus
import dev.chr0nzz.traefikmanager.data.model.RevokeKeyRequest
import dev.chr0nzz.traefikmanager.data.model.ConfigError
import dev.chr0nzz.traefikmanager.data.model.ConfigFile
import dev.chr0nzz.traefikmanager.data.model.ConfigsResponse
import dev.chr0nzz.traefikmanager.data.model.TlsOptionProfile
import okhttp3.RequestBody
import dev.chr0nzz.traefikmanager.data.model.Entrypoint
import dev.chr0nzz.traefikmanager.data.model.EntrypointHttp
import dev.chr0nzz.traefikmanager.data.model.EntrypointTls
import dev.chr0nzz.traefikmanager.data.model.ManagerVersion
import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.data.model.DigestRequest
import dev.chr0nzz.traefikmanager.data.model.HtpasswdRequest
import dev.chr0nzz.traefikmanager.data.model.HtpasswdResponse
import dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplate
import dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplatesResponse
import dev.chr0nzz.traefikmanager.data.model.OkResponse
import dev.chr0nzz.traefikmanager.data.model.TemplateBody
import dev.chr0nzz.traefikmanager.data.model.PingResult
import dev.chr0nzz.traefikmanager.data.model.RawRoute
import dev.chr0nzz.traefikmanager.data.model.RawRouteSave
import dev.chr0nzz.traefikmanager.data.model.Overview
import dev.chr0nzz.traefikmanager.data.model.OverviewCounts
import dev.chr0nzz.traefikmanager.data.model.OverviewSection
import dev.chr0nzz.traefikmanager.data.model.CertEntry
import dev.chr0nzz.traefikmanager.data.model.AddDecisionRequest
import dev.chr0nzz.traefikmanager.data.model.CertsResponse
import dev.chr0nzz.traefikmanager.data.model.ClientIpDiagnostic
import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.data.model.CsMetaEntry
import dev.chr0nzz.traefikmanager.data.model.CsSource
import retrofit2.Response
import dev.chr0nzz.traefikmanager.data.model.GeoLookupRequest
import dev.chr0nzz.traefikmanager.data.model.GeoLookupResponse
import dev.chr0nzz.traefikmanager.data.model.GeoStatus
import dev.chr0nzz.traefikmanager.data.model.LogsResponse
import dev.chr0nzz.traefikmanager.data.model.PluginEntry
import dev.chr0nzz.traefikmanager.data.model.PluginsResponse
import dev.chr0nzz.traefikmanager.data.model.ProtoEnvelope
import dev.chr0nzz.traefikmanager.data.model.ServiceEnvelope
import dev.chr0nzz.traefikmanager.data.model.ServiceLoadBalancer
import dev.chr0nzz.traefikmanager.data.model.ServiceServer
import dev.chr0nzz.traefikmanager.data.model.TraefikService
import dev.chr0nzz.traefikmanager.data.model.WeightedChild
import dev.chr0nzz.traefikmanager.data.model.WeightedService
import kotlinx.serialization.json.JsonObject
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.model.RoutesResponse
import dev.chr0nzz.traefikmanager.data.model.CertResolversResponse
import dev.chr0nzz.traefikmanager.data.model.DashboardConfig
import dev.chr0nzz.traefikmanager.data.model.UiPrefs
import dev.chr0nzz.traefikmanager.data.model.UiPrefsResponse
import dev.chr0nzz.traefikmanager.data.model.SaveSettingsResponse
import dev.chr0nzz.traefikmanager.data.model.ServerSettings
import dev.chr0nzz.traefikmanager.data.model.TmNotification
import dev.chr0nzz.traefikmanager.data.model.WebhookTestRequest
import dev.chr0nzz.traefikmanager.data.model.WebhookTestResult
import dev.chr0nzz.traefikmanager.data.model.TestConnectionRequest
import dev.chr0nzz.traefikmanager.data.model.TestConnectionResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import dev.chr0nzz.traefikmanager.data.model.StaticConfigResponse
import dev.chr0nzz.traefikmanager.data.model.ToggleRequest
import dev.chr0nzz.traefikmanager.data.model.TraefikObject
import dev.chr0nzz.traefikmanager.data.model.TraefikVersion
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray

class DemoApi : TmApi {

    private val disabled = mutableSetOf("routes.yml::old-blog")

    override suspend fun apiKeyStatus(): ApiKeyStatus = ApiKeyStatus(
        enabled = true,
        count = 1,
        keys = listOf(ApiKeyEntry("This phone", "abcd1234...ef56", "2026-08-01 09:14")),
    )

    override suspend fun changePassword(body: ChangePasswordRequest) = AuthActionResponse(success = true)

    override suspend fun otpStatus() = OtpStatus(otpEnabled = false)

    override suspend fun disableOtp() = AuthActionResponse(success = true)

    override suspend fun generateApiKey(body: GenerateKeyRequest) =
        GenerateKeyResponse(ok = true, key = "demo-key-not-a-real-secret-000000000000000")

    override suspend fun revokeApiKey(body: RevokeKeyRequest) = AuthActionResponse(ok = true)

    override suspend fun clientIpDiagnostic() = ClientIpDiagnostic(
        effectiveIp = "203.0.113.7",
        effectiveClass = "public",
        socketPeer = "172.18.0.1",
        socketPeerClass = "private",
        headers = mapOf(
            "X-Forwarded-For" to "203.0.113.7, 172.18.0.1",
            "X-Real-IP" to "203.0.113.7",
            "CF-Connecting-IP" to "",
            "X-Forwarded-Proto" to "https",
            "X-Forwarded-Host" to "manager.example.com",
        ),
        forwardedForChain = listOf("203.0.113.7", "172.18.0.1"),
        proxyHops = 1,
        classes = mapOf("203.0.113.7" to "public", "172.18.0.1" to "private"),
    )

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

    override suspend fun services(): ServiceEnvelope {
        settle()
        return ServiceEnvelope(
            http = DEMO_ROUTES.filter { it.protocol == "http" }.map { route ->
                val servers = if (route.name == "media") {
                    listOf("http://10.0.0.9:8096", "http://10.0.0.10:8096")
                } else {
                    listOf(route.target)
                }
                TraefikService(
                    name = "${route.serviceName}@file",
                    provider = "file",
                    status = if (route.name == "broken-app") "disabled" else "enabled",
                    type = "loadbalancer",
                    usedBy = listOf("${route.name}@file"),
                    serverStatus = if (route.name == "media") mapOf(
                        "http://10.0.0.9:8096" to "UP",
                        "http://10.0.0.10:8096" to "DOWN",
                    ) else mapOf(route.target to "UP"),
                    loadBalancer = ServiceLoadBalancer(
                        servers = servers.map { ServiceServer(url = it) },
                        passHostHeader = true,
                        healthCheck = if (route.name == "media") JsonObject(emptyMap()) else null,
                    ),
                )
            } + TraefikService(
                name = "media-pool@file",
                provider = "file",
                status = "enabled",
                type = "weighted",
                usedBy = listOf("media@file"),
                weighted = WeightedService(
                    services = listOf(
                        WeightedChild("media@file", 3),
                        WeightedChild("media-backup@file", 1),
                    ),
                ),
            ),
            tcp = listOf(
                TraefikService(
                    name = "postgres@file",
                    provider = "file",
                    status = "enabled",
                    type = "loadbalancer",
                    usedBy = listOf("postgres@file"),
                    serverStatus = mapOf("10.0.0.20:5432" to "UP"),
                    loadBalancer = ServiceLoadBalancer(
                        servers = listOf(ServiceServer(address = "10.0.0.20:5432")),
                    ),
                ),
            ),
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

    override suspend fun crowdSecDecisions(full: String?): Response<List<CsDecision>> {
        settle()
        val demo = listOf(
            CsDecision(1, "213.209.159.154", "ban", "Ip", "cscli", "manual ban from Traefik Manager", "590h47m11s"),
            CsDecision(2, "45.148.10.238", "ban", "Ip", "crowdsec", "crowdsecurity/http-probing", "3h57m11s"),
            CsDecision(3, "62.210.142.174", "ban", "Ip", "CAPI", "community blocklist", "167h2m4s"),
            CsDecision(4, "185.220.101.0/24", "ban", "Range", "lists", "firehol_level1", "23h11m9s"),
        )
        return Response.success(demo)
    }

    override suspend fun crowdSecAlerts(): Response<List<CsAlert>> {
        settle()
        val now = java.time.Instant.now()
        val demo = listOf(
            demoAlert(1, "45.148.10.125", "crowdsecurity/http-sensitive-files", "NL", "Techoff Srv Limited", 5, now.minusSeconds(120), listOf("/.env")),
            demoAlert(2, "62.210.142.174", "crowdsecurity/http-technology-probing", "FR", "Scaleway S.a.s.", 1, now.minusSeconds(1440), listOf("/remote/login")),
            demoAlert(3, "212.87.212.246", "crowdsecurity/http-probing", "DE", "ITP-Solutions GmbH", 11, now.minusSeconds(3600), listOf("/")),
            demoAlert(4, "45.142.193.221", "crowdsecurity/http-cve-probing", "RO", "Skynet Network", 1, now.minusSeconds(25200), listOf("/global-protect/login.esp")),
        )
        return Response.success(demo)
    }

    private fun demoAlert(
        id: Long,
        ip: String,
        scenario: String,
        country: String,
        asName: String,
        events: Int,
        at: java.time.Instant,
        uris: List<String>,
    ) = CsAlert(
        uuid = "demo-$id",
        id = id,
        scenario = scenario,
        eventsCount = events,
        capacity = 5,
        leakspeed = "10s",
        machineId = "demo-machine",
        startAt = at.toString(),
        source = CsSource(ip = ip, value = ip, scope = "Ip", cn = country, asName = asName, asNumber = "12876"),
        meta = listOf(
            CsMetaEntry("target_uri", uris.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\"")),
            CsMetaEntry("method", "GET"),
            CsMetaEntry("user_agent", "Mozilla/5.0 (compatible; scanner)"),
        ),
    )

    override suspend fun crowdSecAddDecision(body: AddDecisionRequest): Response<OkResponse> {
        settle()
        return Response.success(OkResponse(ok = true))
    }

    override suspend fun crowdSecDeleteDecision(id: Long): Response<OkResponse> {
        settle()
        return Response.success(OkResponse(ok = true))
    }

    override suspend fun logs(lines: Int): LogsResponse {
        settle()
        val now = java.time.Instant.now()
        val sample = listOf(
            Triple("GET", "/", 200),
            Triple("GET", "/api/routes", 200),
            Triple("POST", "/api/crowdsec/decisions", 201),
            Triple("GET", "/.env", 404),
            Triple("GET", "/favicon.ico", 404),
            Triple("HEAD", "/", 403),
            Triple("GET", "/api/health", 200),
            Triple("GET", "/media/stream/12345", 206),
            Triple("GET", "/wp-admin/setup-config.php", 404),
            Triple("GET", "/api/agents/proxy/1/traefik/overview", 502),
        )
        val ips = listOf("62.210.142.174", "18.218.118.203", "212.87.212.246", "100.87.28.37", "45.148.10.125")
        val services = listOf("tma@docker", "dash-service@file", "gk-auth-service@file")
        val generated = (0 until minOf(lines, 120)).map { index ->
            val (method, path, status) = sample[index % sample.size]
            val stamp = now.minusSeconds((120L - index) * 37)
            val durationNs = listOf(421_000L, 1_200_000L, 2_400_000L, 96_000_000L, 3_570_000_000L)[index % 5]
            """{"ClientHost":"${ips[index % ips.size]}","RequestMethod":"$method","RequestPath":"$path",""" +
                """"DownstreamStatus":$status,"OriginStatus":${if (status == 404) 0 else status},""" +
                """"Duration":$durationNs,"DownstreamContentSize":${status * 7},""" +
                """"RequestHost":"kwa.xyzlab.app","RequestScheme":"https","entryPointName":"websecure",""" +
                """"RouterName":"tma@docker","ServiceName":"${services[index % services.size]}",""" +
                """"ServiceURL":"http://172.18.0.3:8090","TLSVersion":"1.3","RetryAttempts":0,""" +
                """"StartUTC":"$stamp"}"""
        }
        return LogsResponse(lines = generated)
    }

    override suspend fun geoStatus(): GeoStatus = GeoStatus(
        enabled = true,
        available = true,
        dbPath = "/app/geoip/dbip-country-lite.mmdb",
        dbDate = "2026-08-01",
    )

    override suspend fun geoLookup(body: GeoLookupRequest): GeoLookupResponse {
        val codes = body.ips.associateWith { ip ->
            when ((ip.substringBefore('.').toIntOrNull() ?: 0) % 5) {
                0 -> "US"
                1 -> "DE"
                2 -> "FR"
                3 -> "NL"
                else -> "RO"
            }
        }
        return GeoLookupResponse(enabled = true, available = true, codes = codes)
    }

    override suspend fun certs(): CertsResponse {
        settle()
        return CertsResponse(
            certs = listOf(
                CertEntry(
                    resolver = "letsencrypt",
                    main = "example.com",
                    sans = listOf("example.com", "www.example.com", "api.example.com", "blog.example.com"),
                    notAfter = isoDaysFromNow(64),
                    source = "acme.json",
                ),
                CertEntry(
                    resolver = "letsencrypt",
                    main = "media.example.com",
                    notAfter = isoDaysFromNow(21),
                    source = "acme.json",
                ),
                CertEntry(
                    resolver = "cloudflare",
                    main = "vpn.example.com",
                    notAfter = isoDaysFromNow(3),
                    source = "cloudflare.json",
                ),
                CertEntry(
                    resolver = "file",
                    main = "chain.pem",
                    certFile = "/etc/traefik/certs/chain.pem",
                ),
            ),
        )
    }

    override suspend fun plugins(): PluginsResponse {
        settle()
        return PluginsResponse(
            plugins = listOf(
                PluginEntry(
                    name = "crowdsec-bouncer",
                    moduleName = "github.com/maxlerebourg/crowdsec-bouncer-traefik-plugin",
                    version = "v1.3.5",
                ),
                PluginEntry(name = "geoblock", moduleName = "plugins-local/geoblock", version = ""),
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

    override suspend fun saveRoute(body: RequestBody): OkResponse {
        settle()
        return OkResponse(ok = true)
    }

    override suspend fun deleteRoute(routeId: String, body: RequestBody): OkResponse {
        settle()
        disabled.remove(routeId)
        return OkResponse(ok = true)
    }

    override suspend fun agentCertResolvers(agentId: String) =
        CertResolversResponse(resolvers = listOf("letsencrypt"))

    override suspend fun staticConfig() = StaticConfigResponse()

    private val demoTemplates = mutableListOf(
        MiddlewareTemplate(id = "demo-1", name = "Secure Headers", yaml = "headers:\n  sslRedirect: true\n"),
    )

    override suspend fun saveMiddleware(body: RequestBody) = OkResponse(ok = true)

    override suspend fun deleteMiddleware(name: String, body: RequestBody) = OkResponse(ok = true)

    override suspend fun middlewareTemplates() = MiddlewareTemplatesResponse(demoTemplates.toList())

    override suspend fun createMiddlewareTemplate(body: TemplateBody): OkResponse {
        demoTemplates += MiddlewareTemplate(id = "demo-${demoTemplates.size + 1}", name = body.name, yaml = body.yaml)
        return OkResponse(ok = true)
    }

    override suspend fun updateMiddlewareTemplate(id: String, body: TemplateBody): OkResponse {
        val index = demoTemplates.indexOfFirst { it.id == id }
        if (index >= 0) demoTemplates[index] = MiddlewareTemplate(id, body.name, body.yaml)
        return OkResponse(ok = true)
    }

    override suspend fun deleteMiddlewareTemplate(id: String): OkResponse {
        demoTemplates.removeAll { it.id == id }
        return OkResponse(ok = true)
    }

    override suspend fun htpasswd(body: HtpasswdRequest) =
        HtpasswdResponse(ok = true, entry = "${body.username}:\$apr1\$demo\$hash")

    override suspend fun digestAuth(body: DigestRequest) =
        HtpasswdResponse(ok = true, entry = "${body.username}:${body.realm}:demodigesthash")

    override suspend fun routeRaw(routeId: String) = RawRoute(
        raw = "http:\n  routers:\n    demo:\n      rule: Host(`demo.example.com`)\n      service: demo\n",
        configFile = "routes.yml",
        proto = "http",
    )

    override suspend fun saveRouteRaw(routeId: String, body: RawRouteSave) = OkResponse(ok = true)

    override suspend fun ping(url: String, fallback: String?) =
        PingResult(ok = true, latencyMs = 24, statusCode = 200)

    override suspend fun uiPrefs() = UiPrefsResponse(uiPrefs = UiPrefs(showRouteIcons = true))

    override suspend fun dashboardConfig(server: String?) = DashboardConfig()

    override suspend fun settings(): ServerSettings = ServerSettings(
        domains = listOf("example.com", "xyzlab.dev"),
        certResolver = "letsencrypt,cloudflare",
        visibleTabs = mapOf("certs" to true, "plugins" to true, "logs" to true, "crowdsec" to true),
        crowdsecEnabled = true,
    )

    override suspend fun createAgent(body: CreateAgentRequest): AgentMutationResponse {
        settle()
        return AgentMutationResponse(
            ok = true,
            agent = AgentConfig(
                id = "demo-agent",
                name = body.name,
                url = body.url,
                apiKeyRaw = "demo-key-not-a-real-secret-0000000000000",
            ),
        )
    }

    override suspend fun updateAgent(agentId: String, body: JsonObject): OkResponse {
        settle()
        return OkResponse(ok = true)
    }

    override suspend fun deleteAgent(agentId: String): OkResponse {
        settle()
        return OkResponse(ok = true)
    }

    override suspend fun rotateAgentKey(agentId: String): AgentMutationResponse {
        settle()
        return AgentMutationResponse(
            ok = true,
            agent = AgentConfig(id = agentId, apiKeyRaw = "demo-rotated-key-0000000000000000000000"),
        )
    }

    override suspend fun notifications(): List<TmNotification> {
        settle()
        return listOf(
            TmNotification("2026-08-12 10:31:04", "warning", "Ping all: 7/8 online - unreachable: bin"),
            TmNotification("2026-08-12 09:12:44", "success", "Route media saved"),
            TmNotification("2026-08-11 22:04:01", "info", "Traefik v3.7.10 is available - update now"),
            TmNotification("2026-08-11 21:58:12", "error", "CrowdSec LAPI unreachable"),
        )
    }

    override suspend fun testWebhook(body: WebhookTestRequest): WebhookTestResult {
        settle()
        return WebhookTestResult(ok = true)
    }

    override suspend fun settingsRaw(): JsonObject = buildJsonObject {
        put("domains", buildJsonArray { add(JsonPrimitive("example.com")); add(JsonPrimitive("xyzlab.dev")) })
        put("cert_resolver", JsonPrimitive("letsencrypt,cloudflare"))
        put("traefik_api_url", JsonPrimitive("http://traefik:8080"))
        put("traefik_api_user", JsonPrimitive("admin"))
        put("traefik_api_password_set", JsonPrimitive(true))
        put("acme_json_path", JsonPrimitive("/app/acme.json"))
        put("access_log_path", JsonPrimitive("/app/logs/access.log"))
        put("static_config_path", JsonPrimitive("/app/traefik.yml"))
        put("crowdsec_enabled", JsonPrimitive(true))
    }

    override suspend fun saveSettings(body: JsonObject): SaveSettingsResponse {
        settle()
        return SaveSettingsResponse(success = true)
    }

    override suspend fun testTraefikConnection(body: TestConnectionRequest): TestConnectionResult {
        settle()
        return TestConnectionResult(ok = true, version = "3.7.10")
    }

    override suspend fun configs(): ConfigsResponse = ConfigsResponse(
        files = listOf(
            ConfigFile(label = "routes.yml", path = "/app/config/routes.yml"),
            ConfigFile(label = "extra.yml", path = "/app/config/extra.yml"),
        ),
        configDirSet = true,
    )

    override suspend fun tlsOptions(server: String?): List<TlsOptionProfile> = listOf(
        TlsOptionProfile(name = "default", configFile = "routes.yml", minVersion = "VersionTLS12"),
        TlsOptionProfile(name = "modern", configFile = "routes.yml", minVersion = "VersionTLS13"),
    )

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

    private fun isoDaysFromNow(days: Long): String = java.time.Instant.now()
        .plus(java.time.Duration.ofDays(days))
        .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        .toString()
}