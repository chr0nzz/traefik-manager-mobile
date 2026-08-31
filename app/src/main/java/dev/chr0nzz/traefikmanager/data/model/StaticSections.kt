package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object StaticSections {

    const val ENTRYPOINTS = "entrypoints"
    const val RESOLVERS = "resolvers"
    const val PROVIDERS = "providers"
    const val API = "api"
    const val LOG = "log"
    const val OBSERVABILITY = "observability"
    const val SYSTEM = "system"
    const val PLUGINS = "plugins"
}

private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

private fun JsonObject?.str(key: String): String =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject?.bool(key: String, fallback: Boolean = false): Boolean =
    (this?.get(key) as? JsonPrimitive)?.booleanOrNull ?: fallback

private fun JsonObject?.lines(key: String): String =
    (this?.get(key) as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?.joinToString("\n")
        .orEmpty()

private fun JsonObject?.csv(key: String): String =
    (this?.get(key) as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?.joinToString(", ")
        .orEmpty()

private fun JsonObject?.has(key: String): Boolean = this?.containsKey(key) == true

data class EntrypointForm(
    val name: String = "",
    val address: String = "",
    val redirectTo: String = "",
    val http3: Boolean = false,
    val underscoreHeaders: String = "",
    val trustedIps: String = "",
    val proxyTrustedIps: String = "",
    val forwardedInsecure: Boolean = false,
    val proxyInsecure: Boolean = false,
    val middlewares: String = "",
    val tlsEnabled: Boolean = false,
    val tlsCertResolver: String = "",
    val tlsOptions: String = "",
    val asDefault: Boolean = false,
    val readTimeout: String = "",
    val writeTimeout: String = "",
    val idleTimeout: String = "",
) {
    fun data(): JsonObject = buildJsonObject {
        put("address", JsonPrimitive(address.trim()))
        put("redirect_to", JsonPrimitive(redirectTo.trim()))
        put("http3", JsonPrimitive(http3))
        put("underscore_headers", JsonPrimitive(underscoreHeaders))
        put("trusted_ips", JsonPrimitive(trustedIps))
        put("forwarded_insecure", JsonPrimitive(forwardedInsecure))
        put("proxy_trusted_ips", JsonPrimitive(proxyTrustedIps))
        put("proxy_insecure", JsonPrimitive(proxyInsecure))
        put("middlewares", JsonPrimitive(middlewares))
        put("tls_enabled", JsonPrimitive(tlsEnabled))
        put("tls_cert_resolver", JsonPrimitive(tlsCertResolver.trim()))
        put("tls_options", JsonPrimitive(tlsOptions.trim()))
        put("as_default", JsonPrimitive(asDefault))
        put("read_timeout", JsonPrimitive(readTimeout.trim()))
        put("write_timeout", JsonPrimitive(writeTimeout.trim()))
        put("idle_timeout", JsonPrimitive(idleTimeout.trim()))
    }

    companion object {
        fun read(name: String, entry: JsonObject?): EntrypointForm {
            val http = entry.obj("http")
            val tls = http.obj("tls")
            val forwarded = entry.obj("forwardedHeaders")
            val proxy = entry.obj("proxyProtocol")
            val timeouts = entry.obj("transport").obj("respondingTimeouts")
            return EntrypointForm(
                name = name,
                address = entry.str("address"),
                redirectTo = http.obj("redirections").obj("entryPoint").str("to"),
                http3 = entry.has("http3"),
                underscoreHeaders = http.str("underscoreHeadersStrategy"),
                trustedIps = forwarded.lines("trustedIPs"),
                proxyTrustedIps = proxy.lines("trustedIPs"),
                forwardedInsecure = forwarded.bool("insecure"),
                proxyInsecure = proxy.bool("insecure"),
                middlewares = http.csv("middlewares"),
                tlsEnabled = http.has("tls"),
                tlsCertResolver = tls.str("certResolver"),
                tlsOptions = tls.str("options"),
                asDefault = entry.bool("asDefault"),
                readTimeout = timeouts.str("readTimeout"),
                writeTimeout = timeouts.str("writeTimeout"),
                idleTimeout = timeouts.str("idleTimeout"),
            )
        }

        val underscoreOptions = listOf(
            "" to "Keep (default)",
            "delete" to "Delete - strip underscore headers",
            "reject" to "Reject - 400 on underscore headers",
        )
    }
}

data class ResolverForm(
    val name: String = "",
    val email: String = "",
    val storage: String = "/acme.json",
    val challengeType: String = "dnsChallenge",
    val provider: String = "",
    val httpEntrypoint: String = "web",
    val caServer: String = "",
    val keyType: String = "",
    val eabKid: String = "",
    val eabHmac: String = "",
    val dnsResolvers: String = "",
    val dnsDelay: String = "",
    val dnsDisableChecks: Boolean = false,
) {
    fun data(): JsonObject = buildJsonObject {
        put("email", JsonPrimitive(email.trim()))
        put("storage", JsonPrimitive(storage.trim()))
        put("challenge_type", JsonPrimitive(challengeType))
        put("provider", JsonPrimitive(provider.trim()))
        put("http_entrypoint", JsonPrimitive(httpEntrypoint.trim()))
        put("ca_server", JsonPrimitive(caServer.trim()))
        put("key_type", JsonPrimitive(keyType))
        put("eab_kid", JsonPrimitive(eabKid.trim()))
        put("eab_hmac", JsonPrimitive(eabHmac.trim()))
        put("dns_resolvers", JsonPrimitive(dnsResolvers))
        put("dns_delay", JsonPrimitive(dnsDelay.trim()))
        put("dns_disable_checks", JsonPrimitive(dnsDisableChecks))
    }

    companion object {
        fun read(name: String, entry: JsonObject?): ResolverForm {
            val acme = entry.obj("acme")
            val dns = acme.obj("dnsChallenge")
            val challenge = when {
                acme.has("httpChallenge") -> "httpChallenge"
                acme.has("tlsChallenge") -> "tlsChallenge"
                else -> "dnsChallenge"
            }
            return ResolverForm(
                name = name,
                email = acme.str("email"),
                storage = acme.str("storage").ifEmpty { "/acme.json" },
                challengeType = challenge,
                provider = dns.str("provider"),
                httpEntrypoint = acme.obj("httpChallenge").str("entryPoint").ifEmpty { "web" },
                caServer = acme.str("caServer"),
                keyType = acme.str("keyType"),
                eabKid = acme.obj("eab").str("kid"),
                eabHmac = acme.obj("eab").str("hmacEncoded"),
                dnsResolvers = dns.lines("resolvers"),
                dnsDelay = dns.obj("propagation").str("delayBeforeChecks"),
                dnsDisableChecks = dns.obj("propagation").bool("disableChecks"),
            )
        }

        val challengeOptions = listOf(
            "dnsChallenge" to "DNS Challenge",
            "httpChallenge" to "HTTP Challenge",
            "tlsChallenge" to "TLS Challenge",
        )

        val keyTypeOptions = listOf(
            "" to "Default (RSA4096)",
            "EC256" to "EC256",
            "EC384" to "EC384",
            "RSA2048" to "RSA2048",
            "RSA3072" to "RSA3072",
            "RSA4096" to "RSA4096",
            "RSA8192" to "RSA8192",
        )
    }
}

data class StaticPluginForm(
    val name: String = "",
    val moduleName: String = "",
    val version: String = "",
    val local: Boolean = false,
) {
    fun data(): JsonObject = buildJsonObject {
        put("moduleName", JsonPrimitive(moduleName.trim()))
        put("version", JsonPrimitive(version.trim()))
        put("local", JsonPrimitive(local))
    }
}

data class ProvidersForm(
    val docker: Boolean = false,
    val dockerEndpoint: String = "",
    val dockerExposedByDefault: Boolean = true,
    val dockerWatch: Boolean = true,
    val file: Boolean = false,
    val fileDirectory: String = "",
    val fileWatch: Boolean = true,
    val throttle: String = "",
) {
    fun data(): JsonObject = buildJsonObject {
        put("docker", JsonPrimitive(docker))
        put("dockerEndpoint", JsonPrimitive(dockerEndpoint.trim()))
        put("dockerExposedByDefault", JsonPrimitive(dockerExposedByDefault))
        put("dockerWatch", JsonPrimitive(dockerWatch))
        put("file", JsonPrimitive(file))
        put("fileDirectory", JsonPrimitive(fileDirectory.trim()))
        put("fileWatch", JsonPrimitive(fileWatch))
        put("providers_throttle", JsonPrimitive(throttle.trim()))
    }

    companion object {
        fun read(providers: JsonObject?): ProvidersForm {
            val docker = providers.obj("docker")
            val file = providers.obj("file")
            return ProvidersForm(
                docker = providers.has("docker"),
                dockerEndpoint = docker.str("endpoint"),
                dockerExposedByDefault = docker.bool("exposedByDefault", true),
                dockerWatch = docker.bool("watch", true),
                file = providers.has("file"),
                fileDirectory = file.str("directory"),
                fileWatch = file.bool("watch", true),
                throttle = providers.str("providersThrottleDuration"),
            )
        }

        fun others(providers: JsonObject?): List<String> =
            providers?.keys.orEmpty()
                .filterNot { it == "docker" || it == "file" || it == "providersThrottleDuration" }
                .sorted()
    }
}

data class ApiForm(
    val enabled: Boolean = false,
    val dashboard: Boolean = true,
    val insecure: Boolean = false,
    val debug: Boolean = false,
) {
    fun data(): JsonObject = buildJsonObject {
        put("enabled", JsonPrimitive(enabled))
        put("dashboard", JsonPrimitive(dashboard))
        put("insecure", JsonPrimitive(insecure))
        put("debug", JsonPrimitive(debug))
    }

    companion object {
        fun read(root: JsonObject?): ApiForm {
            val api = root.obj("api")
            return ApiForm(
                enabled = root.has("api"),
                dashboard = api?.get("dashboard")?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: true,
                insecure = api.bool("insecure"),
                debug = api.bool("debug"),
            )
        }
    }
}

data class LogForm(
    val level: String = "ERROR",
    val logFormat: String = "",
    val logFile: String = "",
    val maxSize: String = "",
    val maxBackups: String = "",
    val maxAge: String = "",
    val compress: Boolean = false,
    val accessLog: Boolean = false,
    val accessLogPath: String = "",
    val alFormat: String = "",
    val alStatusCodes: String = "",
    val alMinDuration: String = "",
    val alBuffering: String = "",
    val alHeadersMode: String = "",
    val alRetry: Boolean = false,
) {
    fun data(): JsonObject = buildJsonObject {
        put("level", JsonPrimitive(level))
        put("log_format", JsonPrimitive(logFormat))
        put("log_file", JsonPrimitive(logFile.trim()))
        put("log_max_size", JsonPrimitive(maxSize.trim()))
        put("log_max_backups", JsonPrimitive(maxBackups.trim()))
        put("log_max_age", JsonPrimitive(maxAge.trim()))
        put("log_compress", JsonPrimitive(compress))
        put("accessLog", JsonPrimitive(accessLog))
        put("accessLogPath", JsonPrimitive(accessLogPath.trim()))
        put("al_format", JsonPrimitive(alFormat))
        put("al_status_codes", JsonPrimitive(alStatusCodes))
        put("al_min_duration", JsonPrimitive(alMinDuration.trim()))
        put("al_buffering", JsonPrimitive(alBuffering.trim()))
        put("al_headers_mode", JsonPrimitive(alHeadersMode))
        put("al_retry", JsonPrimitive(alRetry))
    }

    companion object {
        fun read(root: JsonObject?): LogForm {
            val log = root.obj("log")
            val access = root.obj("accessLog")
            val filters = access.obj("filters")
            return LogForm(
                level = log.str("level").uppercase().ifEmpty { "ERROR" },
                logFormat = if (log.str("format").equals("json", true)) "json" else "",
                logFile = log.str("filePath"),
                maxSize = log.str("maxSize"),
                maxBackups = log.str("maxBackups"),
                maxAge = log.str("maxAge"),
                compress = log.bool("compress"),
                accessLog = root.has("accessLog"),
                accessLogPath = access.str("filePath"),
                alFormat = if (access.str("format").equals("json", true)) "json" else "",
                alStatusCodes = filters.csv("statusCodes"),
                alMinDuration = filters.str("minDuration"),
                alBuffering = access.str("bufferingSize"),
                alHeadersMode = access.obj("fields").obj("headers").str("defaultMode"),
                alRetry = filters.bool("retryAttempts"),
            )
        }

        val levels = listOf("DEBUG", "INFO", "WARN", "ERROR")
        val formats = listOf("" to "Text (default)", "json" to "JSON")
        val accessFormats = listOf("" to "CLF (default)", "json" to "JSON")
        val headerModes = listOf(
            "" to "Drop (default)",
            "keep" to "Keep",
            "redact" to "Redact",
        )
    }
}

data class ObservabilityForm(
    val ping: Boolean = false,
    val prometheus: Boolean = false,
    val promEpLabels: Boolean = true,
    val promRouterLabels: Boolean = false,
    val promSvcLabels: Boolean = true,
    val tracing: Boolean = false,
    val traceService: String = "",
    val traceSample: String = "",
    val traceEndpoint: String = "",
) {
    fun data(): JsonObject = buildJsonObject {
        put("ping", JsonPrimitive(ping))
        put("prometheus", JsonPrimitive(prometheus))
        put("prom_ep_labels", JsonPrimitive(promEpLabels))
        put("prom_router_labels", JsonPrimitive(promRouterLabels))
        put("prom_svc_labels", JsonPrimitive(promSvcLabels))
        put("tracing", JsonPrimitive(tracing))
        put("trace_service", JsonPrimitive(traceService.trim()))
        put("trace_sample", JsonPrimitive(traceSample.trim()))
        put("trace_endpoint", JsonPrimitive(traceEndpoint.trim()))
    }

    companion object {
        fun read(root: JsonObject?): ObservabilityForm {
            val prom = root.obj("metrics").obj("prometheus")
            val tracing = root.obj("tracing")
            return ObservabilityForm(
                ping = root.has("ping"),
                prometheus = root.obj("metrics").has("prometheus"),
                promEpLabels = prom.bool("addEntryPointsLabels", true),
                promRouterLabels = prom.bool("addRoutersLabels", false),
                promSvcLabels = prom.bool("addServicesLabels", true),
                tracing = root.has("tracing"),
                traceService = tracing.str("serviceName"),
                traceSample = tracing.str("sampleRate"),
                traceEndpoint = tracing.obj("otlp").obj("http").str("endpoint"),
            )
        }
    }
}

data class SystemForm(
    val checkNewVersion: Boolean = true,
    val sendUsage: Boolean = false,
    val ruleSyntax: String = "",
    val stInsecure: Boolean = false,
    val stRootCAs: String = "",
    val stMaxIdle: String = "",
    val stDial: String = "",
    val stRespHeader: String = "",
    val stIdleConn: String = "",
) {
    fun data(): JsonObject = buildJsonObject {
        put("check_new_version", JsonPrimitive(checkNewVersion))
        put("send_usage", JsonPrimitive(sendUsage))
        put("rule_syntax", JsonPrimitive(ruleSyntax))
        put("st_insecure", JsonPrimitive(stInsecure))
        put("st_root_cas", JsonPrimitive(stRootCAs))
        put("st_max_idle", JsonPrimitive(stMaxIdle.trim()))
        put("st_dial", JsonPrimitive(stDial.trim()))
        put("st_resp_header", JsonPrimitive(stRespHeader.trim()))
        put("st_idle_conn", JsonPrimitive(stIdleConn.trim()))
    }

    companion object {
        fun read(root: JsonObject?): SystemForm {
            val global = root.obj("global")
            val transport = root.obj("serversTransport")
            val timeouts = transport.obj("forwardingTimeouts")
            return SystemForm(
                checkNewVersion = global.bool("checkNewVersion", true),
                sendUsage = global.bool("sendAnonymousUsage", false),
                ruleSyntax = root.obj("core").str("defaultRuleSyntax"),
                stInsecure = transport.bool("insecureSkipVerify"),
                stRootCAs = transport.lines("rootCAs"),
                stMaxIdle = transport.str("maxIdleConnsPerHost"),
                stDial = timeouts.str("dialTimeout"),
                stRespHeader = timeouts.str("responseHeaderTimeout"),
                stIdleConn = timeouts.str("idleConnTimeout"),
            )
        }

        val ruleSyntaxOptions = listOf("" to "v3 (default)", "v2" to "v2 (compatibility)")
    }
}

object StaticEntries {

    fun entrypoints(root: JsonObject?): List<Pair<String, JsonObject>> = named(root.obj("entryPoints"))

    fun resolvers(root: JsonObject?): List<Pair<String, JsonObject>> =
        named(root.obj("certificatesResolvers"))

    fun plugins(root: JsonObject?): List<Triple<String, String, Boolean>> {
        val experimental = root.obj("experimental")
        val declared = named(experimental.obj("plugins")).map {
            Triple(it.first, it.second.str("moduleName"), false)
        }
        val local = named(experimental.obj("localPlugins")).map {
            Triple(it.first, it.second.str("moduleName"), true)
        }
        return declared + local
    }

    private fun named(section: JsonObject?): List<Pair<String, JsonObject>> =
        section?.entries.orEmpty().mapNotNull { (key, value) ->
            (value as? JsonObject)?.let { key to it }
        }
}
