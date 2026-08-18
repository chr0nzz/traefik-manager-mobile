package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UiPrefs(
    @SerialName("showRouteIcons") val showRouteIcons: Boolean = false,
    /** The web's launcher density: "list" or "icons". */
    @SerialName("dashPodDensity") val dashPodDensity: String = "list",
)

@Serializable
data class UiPrefsRequest(@SerialName("ui_prefs") val uiPrefs: UiPrefs)

@Serializable
data class UiPrefsResponse(
    val ok: Boolean = true,
    @SerialName("ui_prefs") val uiPrefs: UiPrefs = UiPrefs(),
)

@Serializable
data class RouteOverride(
    @SerialName("icon_type") val iconType: String = "",
    @SerialName("icon_url") val iconUrl: String = "",
    @SerialName("icon_slug") val iconSlug: String = "",
    @SerialName("display_name") val displayName: String = "",
    val hidden: Boolean = false,
    /** Overrides where the card opens; blank falls back to the rule's host. */
    val url: String = "",
    @SerialName("link_disabled") val linkDisabled: Boolean = false,
    /** Blank means the group is worked out from the name. */
    val group: String = "",
)

@Serializable
data class CustomGroup(val name: String = "")

@Serializable
data class DashboardConfig(
    @SerialName("route_overrides") val routeOverrides: Map<String, RouteOverride> = emptyMap(),
    @SerialName("custom_groups") val customGroups: List<CustomGroup> = emptyList(),
    @SerialName("tm_route_name") val tmRouteName: String = "traefik-manager",
    /** Echoed back on save so the hub knows which server the config belongs to. */
    val server: String = "",
)

object RouteIcons {

    private val TRAILING = Regex("[-_](service|svc|router|app|container|pod)s?$", RegexOption.IGNORE_CASE)
    private val PORT = Regex(":\\d+$")
    private val ILLEGAL = Regex("[^a-z0-9-]")

    fun slugFor(route: Route): String {
        val base = route.serviceName.ifEmpty { route.name }.substringBefore('@')
        return base
            .replace(PORT, "")
            .replace(TRAILING, "")
            .lowercase()
            .replace(ILLEGAL, "")
    }

    fun urlFor(route: Route, config: DashboardConfig, baseUrl: String): String? {
        val root = baseUrl.trimEnd('/')
        val override = config.routeOverrides[route.id]

        if (override != null && override.iconType == "url" && override.iconUrl.isNotEmpty()) {
            return override.iconUrl
        }

        val slug = when {
            override != null && override.iconType == "slug" && override.iconSlug.isNotEmpty() ->
                override.iconSlug
            route.name.equals(config.tmRouteName, ignoreCase = true) ->
                return "$root/static/icons/icon.png"
            else -> slugFor(route)
        }
        return if (slug.isEmpty()) null else "$root/api/dashboard/icon/$slug"
    }
}
