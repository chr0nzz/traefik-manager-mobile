import Foundation
import Observation

/// State and options for the add/edit route screen. Port of `RouteFormViewModel`.
@MainActor
@Observable
final class RouteFormModel {
    var form = RouteForm()
    private(set) var loading = true
    private(set) var configFiles: [ConfigFile] = []
    private(set) var canCreateConfigFile = false
    private(set) var tlsProfiles: [TLSOptionProfile] = []
    private(set) var entryPointOptions: [String] = []
    private(set) var domainOptions: [String] = []
    private(set) var certResolverOptions: [String] = []
    private(set) var serviceOptions: [String] = []
    private(set) var middlewareOptions: [MiddlewareDef] = []
    private(set) var saving = false
    var error: String?

    private var ownServices = ServicesByProtocol()
    private var providerServices = ServicesByProtocol()

    var entryPointsUnavailable: Bool { !loading && entryPointOptions.isEmpty }

    var protocolMiddlewares: [MiddlewareDef] {
        middlewareOptions.filter { $0.type.ifEmpty("http") == form.protocol.rawValue }
    }

    func load(service: TMService, editing routeId: String?) async {
        let host = service.scoped(to: nil)
        async let settings = (try? host.settings()) ?? ServerSettings()
        async let configs = (try? service.configs()) ?? ConfigsResponse()
        async let profiles = (try? service.tlsOptions()) ?? []
        async let snapshot = try? RoutesStore.fetch(service: service)
        async let resolvers = Self.certResolvers(service: service)
        async let entryPointList = (try? service.entrypoints()) ?? []
        async let provider = try? service.services()

        let loadedSettings = await settings
        let loadedSnapshot = await snapshot
        let loadedResolvers = await resolvers
        let loadedEntryList = await entryPointList
        let loadedEntryPoints = loadedEntryList.map(\.name).filter { !$0.isEmpty }
        configFiles = await configs.normalized(onAgent: service.agentId != nil).files
        canCreateConfigFile = await configs.normalized(onAgent: service.agentId != nil).configDirSet
        tlsProfiles = await profiles
        entryPointOptions = loadedEntryPoints
        certResolverOptions = loadedResolvers
        ownServices = loadedSnapshot?.services ?? ServicesByProtocol()
        providerServices = await provider.map(Self.providerServices) ?? ServicesByProtocol()
        middlewareOptions = loadedSnapshot?.middlewares ?? []
        domainOptions = Self.mergedDomains(loadedSettings.domains, routes: loadedSnapshot?.routes ?? [])

        if let routeId, let route = loadedSnapshot?.routes.first(where: { $0.id == routeId }) {
            form = RouteFormParsing.form(from: route, knownDomains: domainOptions)
        } else if !form.isEdit {
            if form.configFile.isEmpty, configFiles.count == 1 { form.configFile = configFiles[0].label }
            if form.entryPoints.isEmpty, let entry = Self.defaultEntryPoint(loadedEntryList) {
                form.entryPoints = [entry]
            }
            if form.domains.isEmpty, let domain = loadedSettings.domains.first { form.domains = [domain] }
            if form.certResolver.isEmpty { form.certResolver = loadedResolvers.first ?? "" }
            form.tlsEnabled = !loadedResolvers.isEmpty
        }
        refreshServiceOptions()
        loading = false
    }

    func setProtocol(_ proto: RouteProtocol) {
        form.protocol = proto
        refreshServiceOptions()
    }

    func setAdvancedRule(_ enabled: Bool) {
        if enabled {
            if form.httpRule.isEmpty { form.httpRule = form.currentHostRule }
            form.advancedRule = true
            return
        }
        let split = RouteFormParsing.splitHosts(RouteFormParsing.hosts(inRule: form.httpRule), knownDomains: domainOptions)
        form.advancedRule = false
        form.httpRule = ""
        if let split {
            form.subdomain = split.subdomain
            form.domains = split.domains
        }
    }

    func setPresetEnabled(_ enabled: Bool) {
        form.headersPreset.present = true
        form.headersPreset.enabled = enabled
        if !enabled {
            form.middlewares.removeAll { RouteDetailView.shortName($0) == "\(form.name)-headers" }
        }
    }

    func setWildcard(_ enabled: Bool) {
        form.wildcardEnabled = enabled
        if enabled && form.tlsMainDomain.isEmpty { form.tlsMainDomain = form.domains.first ?? "" }
        if enabled && form.tlsSans.isEmpty, let domain = form.domains.first { form.tlsSans = ["*.\(domain)"] }
    }

    func addBackend() {
        guard !form.backendsAtCap else { return }
        form.backends.append(BackendServer())
    }

    func removeBackend(_ id: BackendServer.ID) {
        form.backends.removeAll { $0.id == id }
        if form.backends.isEmpty { form.backends = [BackendServer()] }
    }

    func save(service: TMService) async -> Bool {
        if let problem = form.validationError {
            error = problem
            return false
        }
        saving = true
        error = nil
        defer { saving = false }
        do {
            let result = try await service.saveRoute(form)
            guard result.worked else {
                error = result.message ?? result.error ?? String(localized: "Could not save the route")
                return false
            }
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    // MARK: - Helpers

    private func refreshServiceOptions() {
        var seen = Set<String>()
        serviceOptions = (ownServices.forProtocol(form.protocol.rawValue) + providerServices.forProtocol(form.protocol.rawValue))
            .filter { seen.insert($0).inserted }
    }

    /// The entry point a new HTTP route starts on: the one marked `asDefault`, then the one on port 443,
    /// then a websecure / https name, then anything with TLS, then the first TCP one.
    nonisolated static func defaultEntryPoint(_ entryPoints: [Entrypoint]) -> String? {
        let tcp = entryPoints.filter { !$0.isUDP && !$0.name.isEmpty }
        func port(_ e: Entrypoint) -> String { e.address.components(separatedBy: ":").last?.components(separatedBy: "/").first ?? "" }
        let secureNames = ["websecure", "https", "web-secure", "secure"]
        return tcp.first(where: \.asDefault)?.name
            ?? tcp.first(where: { port($0) == "443" })?.name
            ?? tcp.first(where: { secureNames.contains($0.name.lowercased()) })?.name
            ?? tcp.first(where: { $0.name.lowercased().contains("sec") || $0.name.lowercased().contains("https") })?.name
            ?? tcp.first(where: \.hasTLS)?.name
            ?? tcp.first?.name
            ?? entryPoints.first?.name
    }

    private static func mergedDomains(_ configured: [String], routes: [Route]) -> [String] {
        let derived = routes.flatMap(\.hosts).compactMap { host in
            configured.first { host == $0 || host.hasSuffix(".\($0)") }
        }
        var seen = Set<String>()
        return (configured + derived).filter { seen.insert($0).inserted }
    }

    /// Agents report their own resolvers; the host merges settings with `certificatesResolvers` from traefik.yml.
    private static func certResolvers(service: TMService) async -> [String] {
        if let agentId = service.agentId {
            return (try? await service.scoped(to: nil).agentCertResolvers(agentId).resolvers) ?? []
        }
        async let configured = (try? service.settings().certResolvers) ?? []
        async let fromStatic = (try? service.staticConfig().parsed?["certificatesResolvers"]?.objectValue?.keys.sorted()) ?? []
        var seen = Set<String>()
        return await (configured + fromStatic).filter { seen.insert($0).inserted }
    }

    /// Non-file services Traefik already knows, offered when pointing a route at an existing service. Port of `ServicePicker`.
    private static func providerServices(_ envelope: ProtoEnvelope) -> ServicesByProtocol {
        func names(_ objects: [TraefikObject], extra: [String] = []) -> [String] {
            let named = objects.compactMap { object -> String? in
                let provider = object.provider.ifEmpty(object.name.components(separatedBy: "@").dropFirst().first ?? "")
                return !object.name.isEmpty && !provider.isEmpty && provider != "file" ? object.name : nil
            }
            var seen = Set<String>()
            return (named + extra).filter { seen.insert($0).inserted }.sorted {
                let left = ($0.components(separatedBy: "@").last ?? "", $0)
                let right = ($1.components(separatedBy: "@").last ?? "", $1)
                return left < right
            }
        }
        var result = ServicesByProtocol()
        result.http = names(envelope.http, extra: ["noop@internal"])
        result.tcp = names(envelope.tcp)
        result.udp = names(envelope.udp)
        return result
    }
}
