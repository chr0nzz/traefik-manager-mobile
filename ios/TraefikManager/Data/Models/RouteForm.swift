import Foundation

enum BackendKind: String, Codable, Sendable {
    case address, service
}

struct BackendServer: Hashable, Identifiable, Sendable {
    var id = UUID()
    var scheme = "http"
    var host = ""
    var port = ""
    var kind: BackendKind = .address
    var serviceName = ""
    var share = "1"

    var isService: Bool { kind == .service }
    var filled: Bool { isService ? !serviceName.isBlank : !host.isBlank }
}

struct StickyConfig: Codable, Hashable, Sendable {
    var enabled = false
    var cookieName = ""
    var secure = false
    var httpOnly = false
}

struct HealthCheckConfig: Codable, Hashable, Sendable {
    var enabled = false
    var path = ""
    var interval = ""
    var timeout = ""
}

private struct BackendWire: Encodable {
    var scheme: String
    var host: String
    var port: String
}

private struct BackendChildPayload: Encodable {
    var kind: String
    var name = ""
    var address = ""
    var scheme = "http"
    var weight = 1
    var percent = 0
}

private struct BackendsPayload: Encodable {
    var servers: [BackendWire]
    var children: [BackendChildPayload]?
    var compositeType: String?
    var sticky: StickyConfig?
    var healthCheck: HealthCheckConfig?
    var priority: Int?
}

enum BackendMode: Hashable { case manual, existingService }

enum RouteProtocol: String, CaseIterable, Identifiable, Sendable {
    case http, tcp, udp

    var id: String { rawValue }

    init(wire: String) { self = RouteProtocol(rawValue: wire.lowercased()) ?? .http }
}

enum TCPTLSMode: CaseIterable, Hashable { case none, terminate, passthrough }

enum HeadersPresetDefaults {
    static let features = [
        "geolocation", "camera", "microphone", "fullscreen", "autoplay",
        "payment", "usb", "display-capture", "accelerometer", "gyroscope", "magnetometer",
    ]
    private static let selfByDefault: Set<String> = ["geolocation", "camera", "microphone", "fullscreen", "autoplay"]
    static let referrer = "strict-origin-when-cross-origin"
    static let referrerValues = [
        "no-referrer", "strict-origin-when-cross-origin", "same-origin", "strict-origin", "origin-when-cross-origin",
    ]
    static let permValues = ["self", "all", "block"]
    static let perms: [String: String] = Dictionary(uniqueKeysWithValues: features.map {
        ($0, selfByDefault.contains($0) ? "self" : "block")
    })
}

struct HeadersPresetForm: Hashable, Sendable {
    var present = false
    var enabled = false
    var custom = false
    var perms = HeadersPresetDefaults.perms
    var hsts = true
    var nosniff = true
    var frameDeny = true
    var referrer = HeadersPresetDefaults.referrer
}

/// Everything the add/edit route screen collects. Port of `RouteForm`.
struct RouteForm: Hashable, Sendable {
    static let certResolverDisabled = "__disabled__"
    static let certResolverNone = "__none__"
    static let loadBalancer = "loadBalancer"
    static let compositeTypes = [loadBalancer, "weighted", "mirroring", "failover"]

    var name = ""
    var `protocol`: RouteProtocol = .http
    var subdomain = ""
    var domains: [String] = []
    var advancedRule = false
    var httpRule = ""
    var tcpRule = ""
    var entryPoints: [String] = []
    var middlewares: [String] = []
    var passHostHeader = true
    var insecureSkipVerify = false
    var certResolver = ""
    var tlsEnabled = true
    var tcpTLSMode: TCPTLSMode = .none
    var wildcardEnabled = false
    var tlsMainDomain = ""
    var tlsSans: [String] = []
    var tlsOptionsProfile = ""
    var backends: [BackendServer] = [BackendServer()]
    var sticky = StickyConfig()
    var healthCheck = HealthCheckConfig()
    var priority: Int?
    var streamingPresent = false
    var streamingEnabled = false
    var headersPreset = HeadersPresetForm()
    var backendMode: BackendMode = .manual
    var serviceRef = ""
    var serviceType = RouteForm.loadBalancer
    var serviceOwned = false
    var compositeType = RouteForm.loadBalancer
    var wasComposite = false
    var configFile = ""
    var isEdit = false
    var originalId = ""
    var originalName = ""

    var usesSharedService: Bool { backendMode == .existingService }
    var isManagedService: Bool { serviceType == Self.loadBalancer || serviceOwned }
    var backendsAtCap: Bool { self.protocol == .http && compositeType == "failover" && backends.count >= 2 }
    var effectivePassHostHeader: Bool { streamingEnabled ? true : passHostHeader }

    var validationError: String? {
        if name.isBlank { return String(localized: "A route name is required.") }
        if let problem = EntityNames.problem(name) { return problem }
        if usesSharedService { return serviceRef.isBlank ? String(localized: "Pick the service this route points at.") : nil }
        if !isManagedService { return nil }
        if backends.contains(where: { $0.isService && $0.serviceName.isBlank }) {
            return String(localized: "Pick a service for every service backend.")
        }
        if !backends.contains(where: \.filled) { return String(localized: "A backend host is required.") }
        if self.protocol != .http && backends.contains(where: { !$0.host.isBlank && $0.port.isBlank }) {
            return String(localized: "\(self.protocol.rawValue.uppercased()) routes need a backend port.")
        }
        return nil
    }

    /// The `Host(...)` rule the simple subdomain + domains fields describe.
    var currentHostRule: String {
        let sub = subdomain.trimmingCharacters(in: .whitespaces)
        var seen = Set<String>()
        let hosts = domains.map { domain in
            sub.isEmpty ? domain : sub.contains(".") ? sub : "\(sub).\(domain)"
        }.filter { seen.insert($0).inserted }
        return hosts.map { "Host(`\($0)`)" }.joined(separator: " || ")
    }

    static func combineLabel(_ type: String) -> String {
        switch type {
        case "weighted": String(localized: "Weighted")
        case "mirroring": String(localized: "Mirroring")
        case "failover": String(localized: "Failover")
        default: String(localized: "Load balanced")
        }
    }

    static func combineHint(_ type: String) -> String {
        switch type {
        case "weighted": String(localized: "Traffic is split by weight.")
        case "mirroring": String(localized: "The first backend serves. The rest get a copy.")
        case "failover": String(localized: "The first backend serves. The second takes over if it fails.")
        default: String(localized: "Traffic is load balanced across every backend.")
        }
    }
}

/// Name rules shared by routes, services and middlewares. Port of `EntityNames`.
enum EntityNames {
    static let maxLength = 100

    static func problem(_ name: String) -> String? {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return String(localized: "Give it a name") }
        if trimmed == "." || trimmed == ".." { return String(localized: "That name is reserved") }
        if trimmed.unicodeScalars.count > maxLength {
            return String(localized: "Keep the name to \(maxLength) characters or fewer")
        }
        let forbidden = trimmed.unicodeScalars.contains { scalar in
            "@/,:{}".unicodeScalars.contains(scalar) || scalar.value < 0x20 || scalar.value == 0x7F
        }
        return forbidden ? String(localized: "A name cannot contain @ / , : { or }") : nil
    }
}

/// Builds the `/save` form fields exactly like the web UI posts them. Port of `RouteFormEncoder`.
enum RouteFormEncoder {
    static func fields(_ form: RouteForm, agentId: String?) -> [(String, String)] {
        var fields: [(String, String)] = [
            ("serviceName", form.name.trimmed),
            ("protocol", form.protocol.rawValue),
            ("isEdit", String(form.isEdit)),
            ("originalId", form.originalId),
            ("configFile", form.configFile),
            ("agent_id", agentId ?? ""),
        ]

        let manualBackends = !form.usesSharedService && form.isManagedService
        let first = manualBackends ? form.backends.first : nil
        fields += indexed("targetIp", form.protocol, first?.host ?? "")
        fields += indexed("targetPort", form.protocol, first?.port ?? "")

        fields.append(("entryPoints", form.protocol == .http ? form.entryPoints.joined(separator: ",") : ""))
        fields.append(("entryPoints", form.protocol == .tcp ? form.entryPoints.joined(separator: ",") : ""))
        if form.protocol == .udp {
            fields.append(("udpEntryPoint", form.entryPoints.first ?? ""))
        }

        fields.append(("certResolver", form.protocol == .http ? httpResolver(form) : ""))
        fields.append(("certResolver", form.protocol == .tcp ? tcpResolver(form) : ""))

        if form.usesSharedService { fields.append(("serviceRef", form.serviceRef)) }

        switch form.protocol {
        case .http:
            fields.append(("subdomain", form.advancedRule ? "" : form.subdomain.trimmed))
            if !form.advancedRule {
                fields += form.domains.filter { !$0.isBlank }.map { ("domains", $0.trimmed) }
            }
            fields.append(("httpRule", form.advancedRule ? form.httpRule.trimmed : ""))
            fields.append(("middlewares", form.middlewares.joined(separator: ", ")))
            fields.append(("scheme", form.backends.first?.scheme ?? "http"))
            fields.append(("passHostHeader", String(form.effectivePassHostHeader)))
            fields.append(("insecureSkipVerify", String(form.insecureSkipVerify)))
            fields.append(("tlsOptionsProfile", form.tlsOptionsProfile))

            if form.wildcardEnabled && !form.tlsMainDomain.isBlank {
                fields.append(("tlsWildcardMain", form.tlsMainDomain.trimmed))
                fields.append(("tlsWildcardSans", form.tlsSans.joined(separator: "\n")))
            }

            fields.append(("streamingPresetPresent", String(form.streamingPresent)))
            if form.streamingPresent {
                fields.append(("streamingPresetEnabled", String(form.streamingEnabled)))
            }

            let preset = form.headersPreset
            fields.append(("headersPresetPresent", String(preset.present)))
            if preset.present {
                fields.append(("headersPresetEnabled", String(preset.enabled)))
                fields.append(("headersPresetCustom", String(preset.custom)))
                if preset.enabled && !preset.custom {
                    fields += HeadersPresetDefaults.features.map { ("hp_perm_\($0)", preset.perms[$0] ?? "block") }
                    fields.append(("hp_hsts", String(preset.hsts)))
                    fields.append(("hp_nosniff", String(preset.nosniff)))
                    fields.append(("hp_frameDeny", String(preset.frameDeny)))
                    fields.append(("hp_referrer", preset.referrer))
                }
            }
            if manualBackends { fields.append(("backendsJsonHttp", json(payload(form)))) }
        case .tcp:
            fields.append(("tcpRule", form.tcpRule.trimmed.ifEmpty("HostSNI(`*`)")))
            fields.append(("subdomain", ""))
            fields.append(("middlewaresTcp", form.middlewares.joined(separator: ", ")))
            fields.append(("useTls", String(form.tcpTLSMode != .none)))
            fields.append(("tlsPassthrough", String(form.tcpTLSMode == .passthrough)))
            if manualBackends { fields.append(("backendsJsonTcp", json(payload(form)))) }
        case .udp:
            if manualBackends { fields.append(("backendsJsonUdp", json(payload(form)))) }
        }
        return fields
    }

    private static func httpResolver(_ form: RouteForm) -> String {
        if !form.tlsEnabled { return RouteForm.certResolverDisabled }
        return form.certResolver.isBlank ? RouteForm.certResolverNone : form.certResolver
    }

    private static func tcpResolver(_ form: RouteForm) -> String {
        guard form.tcpTLSMode == .terminate, !form.certResolver.isBlank else { return RouteForm.certResolverNone }
        return form.certResolver
    }

    private static func payload(_ form: RouteForm) -> BackendsPayload {
        let children = compositeChildren(form)
        return BackendsPayload(
            servers: form.backends
                .filter { !$0.isService && !$0.host.isBlank }
                .map { BackendWire(scheme: $0.scheme, host: $0.host, port: $0.port) },
            children: children,
            compositeType: children.flatMap { $0.isEmpty ? nil : (form.compositeType != RouteForm.loadBalancer ? form.compositeType : "weighted") },
            sticky: form.sticky.enabled && form.protocol == .http ? form.sticky : nil,
            healthCheck: form.healthCheck.enabled && form.protocol == .http ? form.healthCheck : nil,
            priority: form.priority.flatMap { $0 != 0 && form.protocol != .udp ? $0 : nil }
        )
    }

    private static func compositeChildren(_ form: RouteForm) -> [BackendChildPayload]? {
        guard form.protocol == .http else { return nil }
        let rows = form.backends.filter(\.filled)
        if form.compositeType == RouteForm.loadBalancer && !rows.contains(where: \.isService) {
            return form.wasComposite ? [] : nil
        }
        guard !rows.isEmpty else { return nil }
        return rows.map { row in
            let share = Int(row.share.trimmed) ?? (form.compositeType == "mirroring" ? 0 : 1)
            if row.isService {
                return BackendChildPayload(kind: "service", name: row.serviceName.trimmed, weight: share, percent: share)
            }
            let address = [row.host.trimmed, row.port.trimmed].filter { !$0.isEmpty }.joined(separator: ":")
            return BackendChildPayload(kind: "manual", address: address, scheme: row.scheme, weight: share, percent: share)
        }
    }

    private static func indexed(_ field: String, _ proto: RouteProtocol, _ value: String) -> [(String, String)] {
        let index = RouteProtocol.allCases.firstIndex(of: proto) ?? 0
        return (0...2).map { (field, $0 == index ? value : "") }
    }

    private static func json(_ payload: BackendsPayload) -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        guard let data = try? encoder.encode(payload) else { return "{}" }
        return String(decoding: data, as: UTF8.self)
    }
}

/// Turning a saved route back into form fields. Port of the parsing helpers in `RouteFormViewModel`.
enum RouteFormParsing {
    struct HostSplit: Equatable {
        var subdomain: String
        var domains: [String]
    }

    static func splitHosts(_ hosts: [String], knownDomains: [String]) -> HostSplit? {
        guard !hosts.isEmpty else { return nil }
        var pairs: [(String, String)] = []
        for host in hosts {
            guard let base = knownDomains.filter({ host == $0 || host.hasSuffix(".\($0)") }).max(by: { $0.count < $1.count })
            else { return nil }
            pairs.append((host == base ? "" : String(host.dropLast(base.count + 1)), base))
        }
        let subdomains = Array(Set(pairs.map(\.0)))
        guard subdomains.count == 1 else { return nil }
        var seen = Set<String>()
        return HostSplit(subdomain: subdomains[0], domains: pairs.map(\.1).filter { seen.insert($0).inserted })
    }

    static func parseBackend(_ raw: String) -> BackendServer? {
        if raw.isBlank || raw == "N/A" || raw == "Unknown" { return nil }
        let scheme = raw.contains("://") ? String(raw.components(separatedBy: "://")[0]) : "http"
        let rest = raw.contains("://") ? String(raw[raw.range(of: "://")!.upperBound...]) : raw
        if rest.contains("/") { return BackendServer(scheme: scheme, host: rest, port: "") }
        if let colon = rest.lastIndex(of: ":") {
            let tail = rest[rest.index(after: colon)...]
            if !tail.isEmpty, tail.allSatisfy(\.isNumber), !rest.hasSuffix("]") {
                return BackendServer(scheme: scheme, host: String(rest[..<colon]), port: String(tail))
            }
        }
        return BackendServer(scheme: scheme, host: rest, port: "")
    }

    static func compositeRows(_ route: Route) -> [BackendServer]? {
        guard route.isComposite, !route.compositeChildren.isEmpty else { return nil }
        return route.compositeChildren.map { child in
            let share = String(route.serviceType == "mirroring" ? child.percent : child.weight)
            if child.url.isBlank {
                return BackendServer(kind: .service, serviceName: RouteDetailView.shortName(child.name), share: share)
            }
            let parts = child.url.components(separatedBy: "://")
            let bare = parts.count > 1 ? parts[1] : child.url
            let scheme = parts.count > 1 ? parts[0].lowercased() : "http"
            let colon = bare.lastIndex(of: ":")
            return BackendServer(
                scheme: scheme,
                host: colon.map { String(bare[..<$0]) } ?? bare,
                port: colon.map { String(bare[bare.index(after: $0)...]) } ?? "",
                share: share
            )
        }
    }

    /// Fills a form from an existing route, port of `applyRoute`.
    static func form(from route: Route, knownDomains: [String]) -> RouteForm {
        let split = splitHosts(route.hosts, knownDomains: knownDomains)
        let plainRule = route.protocol == "http" && route.isPlainHostRule && split != nil
        let shared = !route.serviceName.isEmpty && RouteDetailView.shortName(route.serviceName) != "\(route.name)-service"

        var form = RouteForm()
        form.name = route.name
        form.protocol = RouteProtocol(wire: route.protocol)
        form.backendMode = shared ? .existingService : .manual
        form.serviceRef = shared ? route.serviceName : ""
        form.advancedRule = route.protocol == "http" && !plainRule
        form.httpRule = route.protocol == "http" && !plainRule ? route.rule : ""
        form.subdomain = split?.subdomain ?? ""
        form.domains = split?.domains ?? []
        form.tcpRule = route.protocol == "tcp" ? route.rule : ""
        form.entryPoints = route.entryPointNames
        form.middlewares = route.middlewareNames
        form.passHostHeader = route.passHostHeader ?? true
        form.insecureSkipVerify = route.insecureSkipVerify
        form.certResolver = route.certResolver
        form.tlsEnabled = route.tlsEnabled
        form.tcpTLSMode = route.protocol != "tcp" ? .none : route.tlsPassthrough ? .passthrough : route.tlsEnabled ? .terminate : .none
        form.wildcardEnabled = route.hasWildcard
        form.tlsMainDomain = route.tlsDomains.first?.main ?? ""
        form.tlsSans = route.tlsDomains.first?.sans ?? []
        form.tlsOptionsProfile = route.tlsOptionsProfile
        let parsed = (route.servers.isEmpty ? [route.target] : route.servers).compactMap(parseBackend)
        form.backends = compositeRows(route) ?? (parsed.isEmpty ? [BackendServer()] : parsed)
        form.sticky = StickyConfig(
            enabled: route.stickyEnabled,
            cookieName: route.sticky?.name ?? "",
            secure: route.sticky?.secure ?? false,
            httpOnly: route.sticky?.httpOnly ?? false
        )
        form.healthCheck = HealthCheckConfig(
            enabled: route.healthCheck != nil,
            path: route.healthCheck?.path ?? "",
            interval: route.healthCheck?.interval ?? "",
            timeout: route.healthCheck?.timeout ?? ""
        )
        form.priority = route.priority
        form.streamingPresent = route.protocol == "http" && route.provider == "file"
        form.streamingEnabled = route.streaming
        if let preset = route.headersPreset {
            form.headersPreset = HeadersPresetForm(
                present: true,
                enabled: preset.state != "off",
                custom: preset.state == "custom",
                perms: preset.toggles.perms.isEmpty ? HeadersPresetDefaults.perms : preset.toggles.perms,
                hsts: preset.toggles.hsts,
                nosniff: preset.toggles.nosniff,
                frameDeny: preset.toggles.frameDeny,
                referrer: preset.toggles.referrer.ifEmpty(HeadersPresetDefaults.referrer)
            )
        }
        form.serviceType = route.serviceType
        form.serviceOwned = route.serviceOwned
        form.compositeType = route.isComposite ? route.serviceType : RouteForm.loadBalancer
        form.wasComposite = route.isComposite
        form.configFile = route.configFile
        form.isEdit = true
        form.originalId = route.id
        form.originalName = route.name
        return form
    }

    /// Hosts of a rule, ignoring negated matchers.
    static func hosts(inRule rule: String) -> [String] {
        rule.matches(of: #/(!?)\s*Host\(`([^`]+)`\)/#).filter { $0.output.1 != "!" }.map { String($0.output.2) }
    }
}

extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
