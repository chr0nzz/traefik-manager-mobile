import Foundation

struct UIPrefs: Codable, Hashable, Sendable {
    var showRouteIcons = false
    var dashPodDensity = "list"

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        showRouteIcons = c.value(.showRouteIcons, default: false)
        dashPodDensity = c.value(.dashPodDensity, default: "list")
    }
}

struct UIPrefsRequest: Encodable {
    var uiPrefs: UIPrefs

    private enum CodingKeys: String, CodingKey { case uiPrefs = "ui_prefs" }
}

struct UIPrefsResponse: Decodable, Sendable {
    var ok = true
    var uiPrefs = UIPrefs()

    private enum CodingKeys: String, CodingKey {
        case ok
        case uiPrefs = "ui_prefs"
    }

    init(uiPrefs: UIPrefs) { self.uiPrefs = uiPrefs }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: true)
        uiPrefs = c.value(.uiPrefs, default: UIPrefs())
    }
}

/// Per-route dashboard customisation. Empty fields mean "use the default".
struct RouteOverride: Codable, Hashable, Sendable {
    var iconType = ""
    var iconUrl = ""
    var iconSlug = ""
    var displayName = ""
    var hidden = false
    var url = ""
    var linkDisabled = false
    var group = ""

    private enum CodingKeys: String, CodingKey {
        case hidden, url, group
        case iconType = "icon_type"
        case iconUrl = "icon_url"
        case iconSlug = "icon_slug"
        case displayName = "display_name"
        case linkDisabled = "link_disabled"
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        iconType = c.value(.iconType, default: "")
        iconUrl = c.value(.iconUrl, default: "")
        iconSlug = c.value(.iconSlug, default: "")
        displayName = c.value(.displayName, default: "")
        hidden = c.value(.hidden, default: false)
        url = c.value(.url, default: "")
        linkDisabled = c.value(.linkDisabled, default: false)
        group = c.value(.group, default: "")
    }
}

struct CustomGroup: Codable, Hashable, Sendable {
    var name = ""
}

struct DashboardConfig: Codable, Hashable, Sendable {
    var routeOverrides: [String: RouteOverride] = [:]
    var customGroups: [CustomGroup] = []
    var tmRouteName = "traefik-manager"
    var server = ""

    private enum CodingKeys: String, CodingKey {
        case server
        case routeOverrides = "route_overrides"
        case customGroups = "custom_groups"
        case tmRouteName = "tm_route_name"
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        routeOverrides = c.value(.routeOverrides, default: [:])
        customGroups = c.value(.customGroups, default: [])
        tmRouteName = c.value(.tmRouteName, default: "traefik-manager")
        server = c.value(.server, default: "")
    }
}

struct LauncherApp: Identifiable, Hashable {
    var route: Route
    var name: String
    var host: String
    var url: String?
    var reason: String?
    var hidden: Bool
    var group: String

    var id: String { route.id }
}

struct LauncherGroup: Identifiable, Hashable {
    var name: String
    var apps: [LauncherApp]
    var custom = false

    var id: String { name }
}

/// Groups routes into launcher tiles by keyword. Port of `LauncherBuilder`.
enum LauncherBuilder {
    static let rules: [(String, [String])] = [
        ("Media", [
            "plex", "jellyfin", "emby", "navidrome", "kavita", "komga", "audiobookshelf", "sonarr",
            "radarr", "lidarr", "readarr", "whisparr", "prowlarr", "qbittorrent", "transmission",
            "deluge", "sabnzbd", "nzbget", "bazarr", "tautulli", "overseerr", "requestrr", "immich",
            "photoprism", "pigallery", "damselfly",
        ]),
        ("Monitoring", [
            "grafana", "prometheus", "alertmanager", "loki", "uptime", "kuma", "glances", "netdata",
            "zabbix", "influx", "telegraf", "speedtest", "myspeed", "healthchecks", "statping",
            "gatus", "scrutiny",
        ]),
        ("Infrastructure", [
            "traefik", "portainer", "proxmox", "cockpit", "nginx", "caddy", "haproxy", "watchtower",
            "dozzle", "komodo", "flint", "gitea", "gitlab", "forgejo", "drone", "jenkins", "vault",
            "consul", "nomad", "ansible", "terraform", "penpot", "n8n", "windmill",
        ]),
        ("Security", [
            "authentik", "authelia", "vaultwarden", "bitwarden", "crowdsec", "fail2ban", "wireguard",
            "vpn", "keycloak", "zitadel", "casdoor", "lldap", "kanidm",
        ]),
        ("Home", [
            "homeassistant", "home-assistant", "nodered", "node-red", "esphome", "zigbee2mqtt",
            "z2m", "frigate", "scrypted", "wyze", "tuya", "matter", "openhabing",
        ]),
        ("Files & Data", [
            "nextcloud", "seafile", "filebrowser", "syncthing", "paperless", "mealie", "tandoor",
            "grocy", "bookstack", "wiki", "notion", "obsidian", "miniflux", "freshrss", "wallabag",
            "linkding", "shlink",
        ]),
        ("Network", [
            "pihole", "adguard", "unifi", "technitium", "bind", "nginx-proxy", "ddclient",
            "cloudflare", "tailscale", "zerotier", "headscale", "netbird",
        ]),
        ("Dev", [
            "gitea", "gitlab", "forgejo", "github", "gogs", "drone", "jenkins", "argocd", "harbor",
            "registry", "sonar", "nexus", "artifactory", "semaphore", "woodpecker", "act",
            "renovate", "dependabot", "code-server", "coder", "vscode", "jupyter", "jupyterlab",
            "mlflow", "airflow", "prefect", "dagster",
        ]),
        ("Servers", [
            "proxmox", "cockpit", "idrac", "ilo", "ipmi", "esxi", "xcp", "xen", "hyperv", "kvm",
            "pve", "unraid", "truenas", "freenas", "opnsense", "pfsense", "mikrotik", "synology",
            "qnap", "asustor",
        ]),
    ]

    static let other = "Other"
    static var builtInGroups: [String] { rules.map(\.0) }

    static func order(_ config: DashboardConfig) -> [String] {
        config.customGroups.map(\.name) + builtInGroups + [other]
    }

    static func build(routes: [Route], config: DashboardConfig, includeHidden: Bool) -> [LauncherGroup] {
        let custom = Set(config.customGroups.map(\.name))
        let apps = routes.map { app($0, config: config) }.filter { includeHidden || !$0.hidden }
        let byGroup = Dictionary(grouping: apps, by: \.group)
        return order(config).compactMap { name in
            guard let members = byGroup[name], !members.isEmpty else { return nil }
            return LauncherGroup(
                name: name,
                apps: members.sorted { $0.name.lowercased() < $1.name.lowercased() },
                custom: custom.contains(name)
            )
        }
    }

    static func app(_ route: Route, config: DashboardConfig) -> LauncherApp {
        let override = config.routeOverrides[route.id]
        let launch = launchURL(route, override: override)
        let host = launch.map { $0.replacingOccurrences(of: "https://", with: "").replacingOccurrences(of: "http://", with: "") }
        return LauncherApp(
            route: route,
            name: override?.displayName.isEmpty == false ? override!.displayName : route.name,
            host: host ?? route.target.ifEmpty(route.serviceName),
            url: launch,
            reason: launch == nil ? reason(route, override: override) : nil,
            hidden: override?.hidden == true,
            group: groupFor(route, config: config)
        )
    }

    private static func isStream(_ route: Route) -> Bool {
        ["tcp", "udp"].contains(route.protocol.lowercased())
    }

    private static func launchURL(_ route: Route, override: RouteOverride?) -> String? {
        if override?.linkDisabled == true { return nil }
        let custom = override?.url ?? ""
        if !custom.isEmpty {
            return custom.hasPrefix("http://") || custom.hasPrefix("https://") ? custom : nil
        }
        if isStream(route) { return nil }
        guard let host = route.hosts.first(where: { !$0.contains("*") }) else { return nil }
        return "https://\(host)"
    }

    private static func reason(_ route: Route, override: RouteOverride?) -> String {
        if override?.linkDisabled == true { return String(localized: "link disabled for this route") }
        if override?.url.isEmpty == false { return String(localized: "the link override is not an http or https URL. Fix it in edit") }
        if isStream(route) { return String(localized: "stream route, nothing to open") }
        if route.rule.isEmpty { return String(localized: "no rule, nothing to open. Set a link in edit") }
        if route.hosts.contains(where: { $0.contains("*") }) { return String(localized: "no launch URL, wildcard host. Set one in edit") }
        if route.hosts.isEmpty { return String(localized: "no launch URL, the rule has no host. Set one in edit") }
        return String(localized: "no launch URL, pattern rule. Set one in edit")
    }

    static func groupFor(_ route: Route, config: DashboardConfig) -> String {
        let chosen = config.routeOverrides[route.id]?.group ?? ""
        if !chosen.isEmpty, config.customGroups.contains(where: { $0.name == chosen }) || builtInGroups.contains(chosen) {
            return chosen
        }
        let squash = { (text: String) in text.lowercased().replacingOccurrences(of: "-", with: "").replacingOccurrences(of: "_", with: "") }
        let name = squash(route.name)
        let service = squash(route.serviceName)
        for (group, keywords) in rules where keywords.contains(where: { name.contains(squash($0)) || service.contains(squash($0)) }) {
            return group
        }
        return other
    }

    static func label(for group: String) -> String {
        switch group {
        case "Media": String(localized: "Media")
        case "Monitoring": String(localized: "Monitoring")
        case "Infrastructure": String(localized: "Infrastructure")
        case "Security": String(localized: "Security")
        case "Home": String(localized: "Home")
        case "Files & Data": String(localized: "Files & Data")
        case "Network": String(localized: "Network")
        case "Dev": String(localized: "Dev")
        case "Servers": String(localized: "Servers")
        case "Other": String(localized: "Other")
        default: group
        }
    }

    static func icon(for group: LauncherGroup) -> String {
        if group.custom { return "tag" }
        switch group.name {
        case "Media": return "film"
        case "Monitoring": return "chart.xyaxis.line"
        case "Infrastructure": return "wrench.and.screwdriver"
        case "Security": return "shield"
        case "Home": return "house"
        case "Files & Data": return "folder"
        case "Network": return "network"
        case "Dev": return "chevron.left.forwardslash.chevron.right"
        case "Servers": return "server.rack"
        default: return "square.grid.2x2"
        }
    }
}

/// Where a route's icon comes from: override URL, selfh.st slug via the server proxy, or auto-detected slug.
enum RouteIcons {
    static func slug(for route: Route) -> String {
        var base = route.serviceName.ifEmpty(route.name)
        base = base.components(separatedBy: "@").first ?? base
        base = base.replacingOccurrences(of: #":\d+$"#, with: "", options: .regularExpression)
        base = base.replacingOccurrences(
            of: #"[-_](service|svc|router|app|container|pod)s?$"#,
            with: "",
            options: [.regularExpression, .caseInsensitive]
        )
        return base.lowercased().replacingOccurrences(of: "[^a-z0-9-]", with: "", options: .regularExpression)
    }

    static func url(for route: Route, config: DashboardConfig, baseURL: URL) -> URL? {
        let root = baseURL.absoluteString.hasSuffix("/") ? String(baseURL.absoluteString.dropLast()) : baseURL.absoluteString
        let override = config.routeOverrides[route.id]
        if let override, override.iconType == "url", !override.iconUrl.isEmpty {
            return URL(string: override.iconUrl)
        }
        let slug: String
        if let override, override.iconType == "slug", !override.iconSlug.isEmpty {
            slug = override.iconSlug
        } else if route.name.caseInsensitiveCompare(config.tmRouteName) == .orderedSame {
            return URL(string: "\(root)/static/icons/icon.png")
        } else {
            slug = self.slug(for: route)
        }
        return slug.isEmpty ? nil : URL(string: "\(root)/api/dashboard/icon/\(slug)")
    }
}
