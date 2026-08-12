package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.DashboardConfig
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.model.RouteIcons
import dev.chr0nzz.traefikmanager.data.model.RouteOverride
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteIconTest {

    private val base = "https://tm.example.com"
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private fun route(name: String, service: String = "", id: String = name) =
        Route(id = id, name = name, serviceName = service)

    @Test
    fun `auto slugs strip the service suffix and go through the server proxy`() {
        assertEquals("jellyfin", RouteIcons.slugFor(route("jellyfin", "jellyfin-service")))
        assertEquals("sonarr", RouteIcons.slugFor(route("sonarr", "sonarr-svc")))
        assertEquals("code", RouteIcons.slugFor(route("code", "code-router")))
        assertEquals(
            "$base/api/dashboard/icon/jellyfin",
            RouteIcons.urlFor(route("jellyfin", "jellyfin-service"), DashboardConfig(), base),
        )
    }

    @Test
    fun `a trailing port and illegal characters are removed`() {
        assertEquals("myapp2", RouteIcons.slugFor(route("My.App2:8080")))
    }

    @Test
    fun `a trailing -app is treated as a suffix, matching the web`() {
        assertEquals("my", RouteIcons.slugFor(route("My_App")))
        assertEquals("photo", RouteIcons.slugFor(route("photo-pods")))
    }

    @Test
    fun `an explicit slug override wins and still uses the proxy`() {
        val config = DashboardConfig(
            routeOverrides = mapOf(
                "dynamic.yml::jellyfin" to RouteOverride(iconType = "slug", iconSlug = "emby"),
            ),
        )
        assertEquals(
            "$base/api/dashboard/icon/emby",
            RouteIcons.urlFor(route("jellyfin", id = "dynamic.yml::jellyfin"), config, base),
        )
    }

    @Test
    fun `a custom url override is used verbatim`() {
        val config = DashboardConfig(
            routeOverrides = mapOf(
                "r1" to RouteOverride(iconType = "url", iconUrl = "https://cdn.example.com/a.png"),
            ),
        )
        assertEquals(
            "https://cdn.example.com/a.png",
            RouteIcons.urlFor(route("anything", id = "r1"), config, base),
        )
    }

    @Test
    fun `the manager's own route uses the bundled icon`() {
        assertEquals(
            "$base/static/icons/icon.png",
            RouteIcons.urlFor(route("traefik-manager"), DashboardConfig(), base),
        )
    }

    @Test
    fun `a name with no usable characters yields no icon`() {
        assertNull(RouteIcons.urlFor(route("___"), DashboardConfig(), base))
    }

    @Test
    fun `the real dashboard config payload parses, extra keys and all`() {
        val payload = """
            {"custom_groups":[{"name":"Media"}],
             "route_overrides":{"dynamic.yml::jellyfin":{
               "icon_type":"slug","icon_slug":"jellyfin","icon_url":"",
               "display_name":"Jellyfin","group":"Media",
               "url":"https://jellyfin.example.com","hidden":false,"link_disabled":false}},
             "tm_route_name":"traefik-manager"}
        """.trimIndent()

        val config = json.decodeFromString<DashboardConfig>(payload)
        val override = config.routeOverrides["dynamic.yml::jellyfin"]
        assertEquals("slug", override?.iconType)
        assertEquals("jellyfin", override?.iconSlug)
        assertEquals("Jellyfin", override?.displayName)
        assertEquals("traefik-manager", config.tmRouteName)
    }
}
