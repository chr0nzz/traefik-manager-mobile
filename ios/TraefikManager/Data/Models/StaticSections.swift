import Foundation

/// Section names understood by `POST /api/static/section`, port of `StaticSections.kt`.
enum StaticSection {
    static let entrypoints = "entrypoints"
    static let resolvers = "resolvers"
    static let providers = "providers"
    static let api = "api"
    static let log = "log"
    static let observability = "observability"
    static let system = "system"
    static let plugins = "plugins"
}

/// A value/label pair for the pickers in the section sheets.
struct StaticOption: Hashable {
    var value: String
    var label: String
}

private extension Optional where Wrapped == JSONObject {
    func obj(_ key: String) -> JSONObject? { self?[key]?.objectValue }
    func str(_ key: String) -> String { self?[key]?.stringValue ?? "" }
    func has(_ key: String) -> Bool { self?[key] != nil }

    func bool(_ key: String, _ fallback: Bool = false) -> Bool {
        if case let .bool(value)? = self?[key] { return value }
        return fallback
    }

    func strings(_ key: String) -> [String] { self?[key]?.arrayValue?.compactMap(\.stringValue) ?? [] }
    func lines(_ key: String) -> String { strings(key).joined(separator: "\n") }
    func csv(_ key: String) -> String { strings(key).joined(separator: ", ") }
}

struct EntrypointForm: Hashable {
    var name = ""
    var address = ""
    var redirectTo = ""
    var http3 = false
    var underscoreHeaders = ""
    var trustedIps = ""
    var proxyTrustedIps = ""
    var forwardedInsecure = false
    var proxyInsecure = false
    var middlewares = ""
    var tlsEnabled = false
    var tlsCertResolver = ""
    var tlsOptions = ""
    var asDefault = false
    var readTimeout = ""
    var writeTimeout = ""
    var idleTimeout = ""

    var data: JSONObject {
        [
            "address": .string(address.trimmed),
            "redirect_to": .string(redirectTo.trimmed),
            "http3": .bool(http3),
            "underscore_headers": .string(underscoreHeaders),
            "trusted_ips": .string(trustedIps),
            "forwarded_insecure": .bool(forwardedInsecure),
            "proxy_trusted_ips": .string(proxyTrustedIps),
            "proxy_insecure": .bool(proxyInsecure),
            "middlewares": .string(middlewares),
            "tls_enabled": .bool(tlsEnabled),
            "tls_cert_resolver": .string(tlsCertResolver.trimmed),
            "tls_options": .string(tlsOptions.trimmed),
            "as_default": .bool(asDefault),
            "read_timeout": .string(readTimeout.trimmed),
            "write_timeout": .string(writeTimeout.trimmed),
            "idle_timeout": .string(idleTimeout.trimmed),
        ]
    }

    static func read(_ name: String, _ entry: JSONObject?) -> EntrypointForm {
        let http = entry.obj("http")
        let tls = http.obj("tls")
        let forwarded = entry.obj("forwardedHeaders")
        let proxy = entry.obj("proxyProtocol")
        let timeouts = entry.obj("transport").obj("respondingTimeouts")
        return EntrypointForm(
            name: name,
            address: entry.str("address"),
            redirectTo: http.obj("redirections").obj("entryPoint").str("to"),
            http3: entry.has("http3"),
            underscoreHeaders: http.str("underscoreHeadersStrategy"),
            trustedIps: forwarded.lines("trustedIPs"),
            proxyTrustedIps: proxy.lines("trustedIPs"),
            forwardedInsecure: forwarded.bool("insecure"),
            proxyInsecure: proxy.bool("insecure"),
            middlewares: http.csv("middlewares"),
            tlsEnabled: http.has("tls"),
            tlsCertResolver: tls.str("certResolver"),
            tlsOptions: tls.str("options"),
            asDefault: entry.bool("asDefault"),
            readTimeout: timeouts.str("readTimeout"),
            writeTimeout: timeouts.str("writeTimeout"),
            idleTimeout: timeouts.str("idleTimeout")
        )
    }

    static var underscoreOptions: [StaticOption] {
        [
            StaticOption(value: "", label: String(localized: "Keep (default)")),
            StaticOption(value: "delete", label: String(localized: "Delete - strip underscore headers")),
            StaticOption(value: "reject", label: String(localized: "Reject - 400 on underscore headers")),
        ]
    }
}

struct ResolverForm: Hashable {
    var name = ""
    var email = ""
    var storage = "/acme.json"
    var challengeType = "dnsChallenge"
    var provider = ""
    var httpEntrypoint = "web"
    var caServer = ""
    var keyType = ""
    var eabKid = ""
    var eabHmac = ""
    var dnsResolvers = ""
    var dnsDelay = ""
    var dnsDisableChecks = false

    var data: JSONObject {
        [
            "email": .string(email.trimmed),
            "storage": .string(storage.trimmed),
            "challenge_type": .string(challengeType),
            "provider": .string(provider.trimmed),
            "http_entrypoint": .string(httpEntrypoint.trimmed),
            "ca_server": .string(caServer.trimmed),
            "key_type": .string(keyType),
            "eab_kid": .string(eabKid.trimmed),
            "eab_hmac": .string(eabHmac.trimmed),
            "dns_resolvers": .string(dnsResolvers),
            "dns_delay": .string(dnsDelay.trimmed),
            "dns_disable_checks": .bool(dnsDisableChecks),
        ]
    }

    static func read(_ name: String, _ entry: JSONObject?) -> ResolverForm {
        let acme = entry.obj("acme")
        let dns = acme.obj("dnsChallenge")
        let challenge = acme.has("httpChallenge") ? "httpChallenge" : acme.has("tlsChallenge") ? "tlsChallenge" : "dnsChallenge"
        return ResolverForm(
            name: name,
            email: acme.str("email"),
            storage: acme.str("storage").ifEmpty("/acme.json"),
            challengeType: challenge,
            provider: dns.str("provider"),
            httpEntrypoint: acme.obj("httpChallenge").str("entryPoint").ifEmpty("web"),
            caServer: acme.str("caServer"),
            keyType: acme.str("keyType"),
            eabKid: acme.obj("eab").str("kid"),
            eabHmac: acme.obj("eab").str("hmacEncoded"),
            dnsResolvers: dns.lines("resolvers"),
            dnsDelay: dns.obj("propagation").str("delayBeforeChecks"),
            dnsDisableChecks: dns.obj("propagation").bool("disableChecks")
        )
    }

    static var challengeOptions: [StaticOption] {
        [
            StaticOption(value: "dnsChallenge", label: String(localized: "DNS Challenge")),
            StaticOption(value: "httpChallenge", label: String(localized: "HTTP Challenge")),
            StaticOption(value: "tlsChallenge", label: String(localized: "TLS Challenge")),
        ]
    }

    static var keyTypeOptions: [StaticOption] {
        [StaticOption(value: "", label: String(localized: "Default (RSA4096)"))]
            + ["EC256", "EC384", "RSA2048", "RSA3072", "RSA4096", "RSA8192"].map { StaticOption(value: $0, label: $0) }
    }
}

struct StaticPluginForm: Hashable {
    var name = ""
    var moduleName = ""
    var version = ""
    var local = false

    var data: JSONObject {
        ["moduleName": .string(moduleName.trimmed), "version": .string(version.trimmed), "local": .bool(local)]
    }
}

struct ProvidersForm: Hashable {
    var docker = false
    var dockerEndpoint = ""
    var dockerExposedByDefault = true
    var dockerWatch = true
    var file = false
    var fileDirectory = ""
    var fileWatch = true
    var throttle = ""

    var data: JSONObject {
        [
            "docker": .bool(docker),
            "dockerEndpoint": .string(dockerEndpoint.trimmed),
            "dockerExposedByDefault": .bool(dockerExposedByDefault),
            "dockerWatch": .bool(dockerWatch),
            "file": .bool(file),
            "fileDirectory": .string(fileDirectory.trimmed),
            "fileWatch": .bool(fileWatch),
            "providers_throttle": .string(throttle.trimmed),
        ]
    }

    static func read(_ providers: JSONObject?) -> ProvidersForm {
        let docker = providers.obj("docker")
        let file = providers.obj("file")
        return ProvidersForm(
            docker: providers.has("docker"),
            dockerEndpoint: docker.str("endpoint"),
            dockerExposedByDefault: docker.bool("exposedByDefault", true),
            dockerWatch: docker.bool("watch", true),
            file: providers.has("file"),
            fileDirectory: file.str("directory"),
            fileWatch: file.bool("watch", true),
            throttle: providers.str("providersThrottleDuration")
        )
    }

    static func others(_ providers: JSONObject?) -> [String] {
        (providers?.keys.map { $0 } ?? []).filter { !["docker", "file", "providersThrottleDuration"].contains($0) }.sorted()
    }
}

struct ApiForm: Hashable {
    var enabled = false
    var dashboard = true
    var insecure = false
    var debug = false

    var data: JSONObject {
        ["enabled": .bool(enabled), "dashboard": .bool(dashboard), "insecure": .bool(insecure), "debug": .bool(debug)]
    }

    static func read(_ root: JSONObject?) -> ApiForm {
        let api = root.obj("api")
        return ApiForm(enabled: root.has("api"), dashboard: api.bool("dashboard", true), insecure: api.bool("insecure"), debug: api.bool("debug"))
    }
}

struct LogForm: Hashable {
    var level = "ERROR"
    var logFormat = ""
    var logFile = ""
    var maxSize = ""
    var maxBackups = ""
    var maxAge = ""
    var compress = false
    var accessLog = false
    var accessLogPath = ""
    var alFormat = ""
    var alStatusCodes = ""
    var alMinDuration = ""
    var alBuffering = ""
    var alHeadersMode = ""
    var alRetry = false

    var data: JSONObject {
        [
            "level": .string(level),
            "log_format": .string(logFormat),
            "log_file": .string(logFile.trimmed),
            "log_max_size": .string(maxSize.trimmed),
            "log_max_backups": .string(maxBackups.trimmed),
            "log_max_age": .string(maxAge.trimmed),
            "log_compress": .bool(compress),
            "accessLog": .bool(accessLog),
            "accessLogPath": .string(accessLogPath.trimmed),
            "al_format": .string(alFormat),
            "al_status_codes": .string(alStatusCodes),
            "al_min_duration": .string(alMinDuration.trimmed),
            "al_buffering": .string(alBuffering.trimmed),
            "al_headers_mode": .string(alHeadersMode),
            "al_retry": .bool(alRetry),
        ]
    }

    static func read(_ root: JSONObject?) -> LogForm {
        let log = root.obj("log")
        let access = root.obj("accessLog")
        let filters = access.obj("filters")
        return LogForm(
            level: log.str("level").uppercased().ifEmpty("ERROR"),
            logFormat: log.str("format").lowercased() == "json" ? "json" : "",
            logFile: log.str("filePath"),
            maxSize: log.str("maxSize"),
            maxBackups: log.str("maxBackups"),
            maxAge: log.str("maxAge"),
            compress: log.bool("compress"),
            accessLog: root.has("accessLog"),
            accessLogPath: access.str("filePath"),
            alFormat: access.str("format").lowercased() == "json" ? "json" : "",
            alStatusCodes: filters.csv("statusCodes"),
            alMinDuration: filters.str("minDuration"),
            alBuffering: access.str("bufferingSize"),
            alHeadersMode: access.obj("fields").obj("headers").str("defaultMode"),
            alRetry: filters.bool("retryAttempts")
        )
    }

    static let levels = ["DEBUG", "INFO", "WARN", "ERROR"].map { StaticOption(value: $0, label: $0) }
    static var formats: [StaticOption] {
        [StaticOption(value: "", label: String(localized: "Text (default)")), StaticOption(value: "json", label: "JSON")]
    }
    static var accessFormats: [StaticOption] {
        [StaticOption(value: "", label: String(localized: "CLF (default)")), StaticOption(value: "json", label: "JSON")]
    }
    static var headerModes: [StaticOption] {
        [
            StaticOption(value: "", label: String(localized: "Drop (default)")),
            StaticOption(value: "keep", label: String(localized: "Keep")),
            StaticOption(value: "redact", label: String(localized: "Redact")),
        ]
    }
}

struct ObservabilityForm: Hashable {
    var ping = false
    var prometheus = false
    var promEpLabels = true
    var promRouterLabels = false
    var promSvcLabels = true
    var tracing = false
    var traceService = ""
    var traceSample = ""
    var traceEndpoint = ""

    var data: JSONObject {
        [
            "ping": .bool(ping),
            "prometheus": .bool(prometheus),
            "prom_ep_labels": .bool(promEpLabels),
            "prom_router_labels": .bool(promRouterLabels),
            "prom_svc_labels": .bool(promSvcLabels),
            "tracing": .bool(tracing),
            "trace_service": .string(traceService.trimmed),
            "trace_sample": .string(traceSample.trimmed),
            "trace_endpoint": .string(traceEndpoint.trimmed),
        ]
    }

    static func read(_ root: JSONObject?) -> ObservabilityForm {
        let prom = root.obj("metrics").obj("prometheus")
        let tracing = root.obj("tracing")
        return ObservabilityForm(
            ping: root.has("ping"),
            prometheus: root.obj("metrics").has("prometheus"),
            promEpLabels: prom.bool("addEntryPointsLabels", true),
            promRouterLabels: prom.bool("addRoutersLabels", false),
            promSvcLabels: prom.bool("addServicesLabels", true),
            tracing: root.has("tracing"),
            traceService: tracing.str("serviceName"),
            traceSample: tracing.str("sampleRate"),
            traceEndpoint: tracing.obj("otlp").obj("http").str("endpoint")
        )
    }
}

struct SystemForm: Hashable {
    var checkNewVersion = true
    var sendUsage = false
    var ruleSyntax = ""
    var stInsecure = false
    var stRootCAs = ""
    var stMaxIdle = ""
    var stDial = ""
    var stRespHeader = ""
    var stIdleConn = ""

    var data: JSONObject {
        [
            "check_new_version": .bool(checkNewVersion),
            "send_usage": .bool(sendUsage),
            "rule_syntax": .string(ruleSyntax),
            "st_insecure": .bool(stInsecure),
            "st_root_cas": .string(stRootCAs),
            "st_max_idle": .string(stMaxIdle.trimmed),
            "st_dial": .string(stDial.trimmed),
            "st_resp_header": .string(stRespHeader.trimmed),
            "st_idle_conn": .string(stIdleConn.trimmed),
        ]
    }

    static func read(_ root: JSONObject?) -> SystemForm {
        let global = root.obj("global")
        let transport = root.obj("serversTransport")
        let timeouts = transport.obj("forwardingTimeouts")
        return SystemForm(
            checkNewVersion: global.bool("checkNewVersion", true),
            sendUsage: global.bool("sendAnonymousUsage", false),
            ruleSyntax: root.obj("core").str("defaultRuleSyntax"),
            stInsecure: transport.bool("insecureSkipVerify"),
            stRootCAs: transport.lines("rootCAs"),
            stMaxIdle: transport.str("maxIdleConnsPerHost"),
            stDial: timeouts.str("dialTimeout"),
            stRespHeader: timeouts.str("responseHeaderTimeout"),
            stIdleConn: timeouts.str("idleConnTimeout")
        )
    }

    static var ruleSyntaxOptions: [StaticOption] {
        [
            StaticOption(value: "", label: String(localized: "v3 (default)")),
            StaticOption(value: "v2", label: String(localized: "v2 (compatibility)")),
        ]
    }
}

struct StaticNamed: Identifiable, Hashable {
    var name: String
    var entry: JSONObject
    var id: String { name }
}

struct StaticPluginRow: Identifiable, Hashable {
    var name: String
    var module: String
    var local: Bool
    var id: String { "\(local ? "local" : "remote"):\(name)" }
}

/// Lists out of the parsed static config, port of `StaticEntries`.
enum StaticEntries {
    static func entrypoints(_ root: JSONObject?) -> [StaticNamed] { named(root.obj("entryPoints")) }
    static func resolvers(_ root: JSONObject?) -> [StaticNamed] { named(root.obj("certificatesResolvers")) }

    static func plugins(_ root: JSONObject?) -> [StaticPluginRow] {
        let experimental = root.obj("experimental")
        let declared = named(experimental.obj("plugins")).map { StaticPluginRow(name: $0.name, module: Optional($0.entry).str("moduleName"), local: false) }
        let local = named(experimental.obj("localPlugins")).map { StaticPluginRow(name: $0.name, module: Optional($0.entry).str("moduleName"), local: true) }
        return declared + local
    }

    /// Kotlin keeps YAML order; Swift dictionaries do not, so sort by name for a stable list.
    private static func named(_ section: JSONObject?) -> [StaticNamed] {
        (section ?? [:]).compactMap { key, value in value.objectValue.map { StaticNamed(name: key, entry: $0) } }
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }
}
