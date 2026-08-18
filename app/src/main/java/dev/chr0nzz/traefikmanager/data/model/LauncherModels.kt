package dev.chr0nzz.traefikmanager.data.model

/** A section of the launcher. Custom groups win over the worked-out ones. */
data class LauncherGroup(
    val name: String,
    val apps: List<LauncherApp>,
    val custom: Boolean = false,
)

/**
 * One card. [url] is where it opens, or null when it cannot be opened - in which case [reason]
 * says why, in the web's own words.
 */
data class LauncherApp(
    val route: Route,
    val name: String,
    val host: String,
    val url: String?,
    val reason: String?,
    val hidden: Boolean,
    val group: String,
) {
    val id: String get() = route.id
}

/**
 * Turns routes plus the saved overrides into the launcher the web draws. The keyword table and the
 * order of it are the web's (static/js/dashboard-tab.js:246-256); a route lands in the first group
 * whose keyword its name or service name contains, once both are stripped of dashes.
 */
object LauncherBuilder {

    private val RULES = listOf(
        "Media" to listOf(
            "plex", "jellyfin", "emby", "navidrome", "kavita", "komga", "audiobookshelf", "sonarr",
            "radarr", "lidarr", "readarr", "whisparr", "prowlarr", "qbittorrent", "transmission",
            "deluge", "sabnzbd", "nzbget", "bazarr", "tautulli", "overseerr", "requestrr", "immich",
            "photoprism", "pigallery", "damselfly",
        ),
        "Monitoring" to listOf(
            "grafana", "prometheus", "alertmanager", "loki", "uptime", "kuma", "glances", "netdata",
            "zabbix", "influx", "telegraf", "speedtest", "myspeed", "healthchecks", "statping",
            "gatus", "scrutiny",
        ),
        "Infrastructure" to listOf(
            "traefik", "portainer", "proxmox", "cockpit", "nginx", "caddy", "haproxy", "watchtower",
            "dozzle", "komodo", "flint", "gitea", "gitlab", "forgejo", "drone", "jenkins", "vault",
            "consul", "nomad", "ansible", "terraform", "penpot", "n8n", "windmill",
        ),
        "Security" to listOf(
            "authentik", "authelia", "vaultwarden", "bitwarden", "crowdsec", "fail2ban", "wireguard",
            "vpn", "keycloak", "zitadel", "casdoor", "lldap", "kanidm",
        ),
        "Home" to listOf(
            "homeassistant", "home-assistant", "nodered", "node-red", "esphome", "zigbee2mqtt",
            "z2m", "frigate", "scrypted", "wyze", "tuya", "matter", "openhabing",
        ),
        "Files & Data" to listOf(
            "nextcloud", "seafile", "filebrowser", "syncthing", "paperless", "mealie", "tandoor",
            "grocy", "bookstack", "wiki", "notion", "obsidian", "miniflux", "freshrss", "wallabag",
            "linkding", "shlink",
        ),
        "Network" to listOf(
            "pihole", "adguard", "unifi", "technitium", "bind", "nginx-proxy", "ddclient",
            "cloudflare", "tailscale", "zerotier", "headscale", "netbird",
        ),
        "Dev" to listOf(
            "gitea", "gitlab", "forgejo", "github", "gogs", "drone", "jenkins", "argocd", "harbor",
            "registry", "sonar", "nexus", "artifactory", "semaphore", "woodpecker", "act",
            "renovate", "dependabot", "code-server", "coder", "vscode", "jupyter", "jupyterlab",
            "mlflow", "airflow", "prefect", "dagster",
        ),
        "Servers" to listOf(
            "proxmox", "cockpit", "idrac", "ilo", "ipmi", "esxi", "xcp", "xen", "hyperv", "kvm",
            "pve", "unraid", "truenas", "freenas", "opnsense", "pfsense", "mikrotik", "synology",
            "qnap", "asustor",
        ),
    )

    private const val OTHER = "Other"

    /** The order sections appear in, custom groups first, exactly as the web orders them. */
    fun order(config: DashboardConfig): List<String> =
        config.customGroups.map { it.name } + RULES.map { it.first } + OTHER

    fun build(routes: List<Route>, config: DashboardConfig, includeHidden: Boolean): List<LauncherGroup> {
        val customNames = config.customGroups.map { it.name }.toSet()
        val apps = routes.map { route -> app(route, config) }
            .filter { includeHidden || !it.hidden }
        val byGroup = apps.groupBy { it.group }
        return order(config).mapNotNull { name ->
            val members = byGroup[name].orEmpty()
            if (members.isEmpty()) {
                null
            } else {
                LauncherGroup(
                    name = name,
                    apps = members.sortedBy { it.name.lowercase() },
                    custom = name in customNames,
                )
            }
        }
    }

    fun app(route: Route, config: DashboardConfig): LauncherApp {
        val override = config.routeOverrides[route.id]
        val launch = launchUrl(route, override)
        return LauncherApp(
            route = route,
            name = override?.displayName?.takeIf { it.isNotEmpty() } ?: route.name,
            host = launch?.removePrefix("https://")?.removePrefix("http://")
                ?: route.target.ifEmpty { route.serviceName },
            url = launch,
            reason = if (launch == null) reason(route, override) else null,
            hidden = override?.hidden == true,
            group = groupFor(route, config),
        )
    }

    private fun launchUrl(route: Route, override: RouteOverride?): String? {
        if (override?.linkDisabled == true) return null
        val custom = override?.url.orEmpty()
        if (custom.isNotEmpty()) {
            return custom.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }
        if (route.protocol.equals("tcp", true) || route.protocol.equals("udp", true)) return null
        val host = route.hosts.firstOrNull { !it.contains('*') } ?: return null
        return "https://$host"
    }

    /** The web's own wording, so the two read the same. */
    private fun reason(route: Route, override: RouteOverride?): String = when {
        override?.linkDisabled == true -> "link disabled for this route"
        override?.url?.isNotEmpty() == true ->
            "the link override is not an http or https URL. Fix it in edit"
        route.protocol.equals("tcp", true) || route.protocol.equals("udp", true) ->
            "stream route, nothing to open"
        route.rule.isEmpty() -> "no rule, nothing to open. Set a link in edit"
        route.hosts.any { it.contains('*') } -> "no launch URL, wildcard host. Set one in edit"
        route.hosts.isEmpty() -> "no launch URL, the rule has no host. Set one in edit"
        else -> "no launch URL, pattern rule. Set one in edit"
    }

    fun groupFor(route: Route, config: DashboardConfig): String {
        val chosen = config.routeOverrides[route.id]?.group.orEmpty()
        if (chosen.isNotEmpty()) {
            if (config.customGroups.any { it.name == chosen }) return chosen
            if (RULES.any { it.first == chosen }) return chosen
        }
        val name = route.name.lowercase().replace("-", "").replace("_", "")
        val service = route.serviceName.lowercase().replace("-", "").replace("_", "")
        RULES.forEach { (group, keywords) ->
            if (keywords.any { keyword ->
                    val k = keyword.replace("-", "").replace("_", "")
                    name.contains(k) || service.contains(k)
                }
            ) {
                return group
            }
        }
        return OTHER
    }

    val builtInGroups: List<String> get() = RULES.map { it.first }
}
