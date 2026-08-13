package dev.chr0nzz.traefikmanager.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * The cards a widget can hold. These are the desk's own cards, not widget-only inventions: the
 * Traefik ones come straight out of DashboardBuilder, the CrowdSec ones out of CrowdSecAnalytics.
 */
enum class WidgetCardType(
    val key: String,
    val label: String,
    val blurb: String,
    val crowdsec: Boolean = false,
) {
    Overview("overview", "All servers", "Every server, with routers, warnings and bans."),
    Http("http", "HTTP routers", "How many are live, and what is not."),
    Stream("stream", "TCP / UDP routers", "Stream routers and how they split."),
    Services("services", "Services", "Backends up against backends configured."),
    Middlewares("middlewares", "Middlewares", "In use against defined but unused."),
    Sources("sources", "Attacking sources", "Who is probing, and who is banned.", crowdsec = true),
    Scenarios("scenarios", "Scenarios", "Which buckets are firing.", crowdsec = true),
    Paths("paths", "Targeted paths", "What they are reaching for.", crowdsec = true),
    Bans("bans", "Bans in force", "Decisions holding right now.", crowdsec = true),
    ;

    companion object {
        fun from(key: String?): WidgetCardType? = entries.firstOrNull { it.key == key }

        fun parse(raw: String?): List<WidgetCardType> = raw
            ?.split(',')
            ?.mapNotNull { from(it.trim()) }
            ?.distinct()
            .orEmpty()
            .ifEmpty { listOf(Overview) }
    }
}

/**
 * Per-widget settings, held in that widget's own Glance state.
 *
 * A widget placed before v2 has none of these keys, so every default is what an unconfigured
 * widget should show: the overview, on the host, at the ordinary cadence.
 */
data class WidgetConfig(
    val cards: List<WidgetCardType> = listOf(WidgetCardType.Overview),
    /** null means the host. */
    val serverId: String? = null,
    val serverName: String = "Host",
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
) {
    val needsCrowdSec: Boolean get() = cards.any { it.crowdsec }

    val needsDashboard: Boolean get() = cards.any { !it.crowdsec && it != WidgetCardType.Overview }

    val needsOverview: Boolean get() = cards.contains(WidgetCardType.Overview)

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 30

        /** WorkManager will not run periodic work more often than this. */
        const val MIN_INTERVAL_MINUTES = 15

        /** A 2x2 grid is as much as a home screen widget can hold and stay readable. */
        const val MAX_CARDS = 4

        val INTERVAL_CHOICES = listOf(15, 30, 60)

        val CARDS = stringPreferencesKey("widget_cards")
        val SERVER_ID = stringPreferencesKey("widget_server_id")
        val SERVER_NAME = stringPreferencesKey("widget_server_name")
        val INTERVAL = intPreferencesKey("widget_interval")
        val PAYLOAD = stringPreferencesKey("widget_payload")
        val UPDATED_AT = longPreferencesKey("widget_updated_at")
        val ERROR = stringPreferencesKey("widget_error")

        fun read(prefs: Preferences): WidgetConfig = WidgetConfig(
            cards = WidgetCardType.parse(prefs[CARDS]),
            serverId = prefs[SERVER_ID]?.takeIf { it.isNotEmpty() },
            serverName = prefs[SERVER_NAME] ?: "Host",
            intervalMinutes = prefs[INTERVAL] ?: DEFAULT_INTERVAL_MINUTES,
        )
    }
}
