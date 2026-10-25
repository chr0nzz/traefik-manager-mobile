import SwiftUI

enum DestinationSection: CaseIterable, Identifiable {
    case traffic, observability, infrastructure, providers, system

    var id: Self { self }

    var label: LocalizedStringKey {
        switch self {
        case .traffic: "Traffic"
        case .observability: "Observability"
        case .infrastructure: "Infrastructure"
        case .providers: "Providers"
        case .system: "System"
        }
    }
}

/// Every top-level screen, port of `TmDestination`.
enum Destination: String, CaseIterable, Identifiable, Hashable {
    case home, routes, middlewares, services, routeMap
    case logs, crowdsec
    case certificates, plugins
    case docker, kubernetes, swarm, nomad, ecs, consulCatalog, redis, etcd, consul, zookeeper
    case httpProvider, fileExternal, `internal`
    case backups, settings

    var id: String { rawValue }

    var label: LocalizedStringKey {
        switch self {
        case .home: "Dashboard"
        case .routes: "Routes"
        case .middlewares: "Middleware"
        case .services: "Services"
        case .routeMap: "Route map"
        case .logs: "Logs"
        case .crowdsec: "CrowdSec"
        case .certificates: "Certificates"
        case .plugins: "Plugins"
        case .docker: "Docker"
        case .kubernetes: "Kubernetes"
        case .swarm: "Swarm"
        case .nomad: "Nomad"
        case .ecs: "ECS"
        case .consulCatalog: "Consul Catalog"
        case .redis: "Redis"
        case .etcd: "etcd"
        case .consul: "Consul KV"
        case .zookeeper: "ZooKeeper"
        case .httpProvider: "HTTP Provider"
        case .fileExternal: "File (external)"
        case .internal: "Internal"
        case .backups: "Backups"
        case .settings: "Settings"
        }
    }

    var systemImage: String {
        switch self {
        case .home: "house"
        case .routes: "arrow.triangle.branch"
        case .middlewares: "square.3.layers.3d"
        case .services: "server.rack"
        case .routeMap: "point.3.connected.trianglepath.dotted"
        case .logs: "doc.text"
        case .crowdsec: "shield"
        case .certificates: "checkmark.seal"
        case .plugins: "puzzlepiece.extension"
        case .docker: "shippingbox"
        case .kubernetes: "circle.hexagongrid"
        case .swarm: "bubbles.and.sparkles"
        case .nomad: "square.grid.3x3"
        case .ecs: "cloud"
        case .consulCatalog: "person.text.rectangle"
        case .redis, .etcd, .consul, .zookeeper: "cylinder.split.1x2"
        case .httpProvider: "link"
        case .fileExternal: "doc"
        case .internal: "arrow.left.arrow.right"
        case .backups: "archivebox"
        case .settings: "gearshape"
        }
    }

    var section: DestinationSection {
        switch self {
        case .home, .routes, .middlewares, .services, .routeMap: .traffic
        case .logs, .crowdsec: .observability
        case .certificates, .plugins: .infrastructure
        case .backups, .settings: .system
        default: .providers
        }
    }

    /// Shown in the iPhone tab bar by default; the rest live under "More".
    var primary: Bool {
        [.home, .routes, .middlewares, .logs].contains(self)
    }

    /// The server-side tab key that controls visibility, if any.
    var serverTab: String? {
        switch self {
        case .routeMap: "routemap"
        case .logs: "logs"
        case .crowdsec: "crowdsec"
        case .certificates: "certs"
        case .plugins: "plugins"
        case .docker: "docker"
        case .kubernetes: "kubernetes"
        case .swarm: "swarm"
        case .nomad: "nomad"
        case .ecs: "ecs"
        case .consulCatalog: "consulcatalog"
        case .redis: "redis"
        case .etcd: "etcd"
        case .consul: "consul"
        case .zookeeper: "zookeeper"
        case .httpProvider: "http_provider"
        case .fileExternal: "file_external"
        case .internal: "internal"
        default: nil
        }
    }

    var isProvider: Bool { section == .providers }

    func visible(with capabilities: ServerCapabilities) -> Bool {
        guard let tab = serverTab else { return true }
        let visible = isProvider ? capabilities.tabEnabled(tab) : capabilities.tabVisible(tab)
        return self == .crowdsec ? visible && capabilities.crowdsecConfigured : visible
    }
}
