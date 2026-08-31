package dev.chr0nzz.traefikmanager.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

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
    Entrypoints("entrypoints", "Entry points", "Every door in, and what binds to it."),
    Certs("certs", "Certificates", "What expires soonest, and how soon."),
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

enum class WidgetLayout(val label: String, val blurb: String) {
    Mosaic("Mosaic", "Hero number over the signal strip."),
    Numbers("Numbers", "The headline figures, large."),
    Rows("Rows", "What is worst, ranked."),
    ;

    fun next(): WidgetLayout = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(key: String?): WidgetLayout = entries.firstOrNull { it.name == key } ?: Mosaic
    }
}

enum class WidgetPreset(
    val label: String,
    val blurb: String,
    val cards: List<WidgetCardType>,
    val layout: WidgetLayout = WidgetLayout.Mosaic,
) {
    CrowdSecStats(
        label = "CrowdSec stats",
        blurb = "Sources, scenarios, paths and bans in one split card.",
        cards = listOf(
            WidgetCardType.Sources,
            WidgetCardType.Scenarios,
            WidgetCardType.Paths,
            WidgetCardType.Bans,
        ),
        layout = WidgetLayout.Rows,
    ),
    TraefikStats(
        label = "Traefik stats",
        blurb = "Routers, services, middlewares and entry points together.",
        cards = listOf(
            WidgetCardType.Http,
            WidgetCardType.Services,
            WidgetCardType.Middlewares,
            WidgetCardType.Entrypoints,
        ),
        layout = WidgetLayout.Rows,
    ),
    Servers("All servers", "Every server, its health mosaic and what each runs.", listOf(WidgetCardType.Overview)),
    Sources("Attacking sources", "Who is probing, and who is banned.", listOf(WidgetCardType.Sources)),
    Scenarios("Scenarios", "Which buckets are firing.", listOf(WidgetCardType.Scenarios)),
    Paths("Targeted paths", "What they are reaching for.", listOf(WidgetCardType.Paths)),
    Bans("Bans in force", "Decisions holding right now.", listOf(WidgetCardType.Bans)),
    Routers("HTTP routers", "How many are live, and what is not.", listOf(WidgetCardType.Http)),
    Stream("TCP / UDP routers", "Stream routers and how they split.", listOf(WidgetCardType.Stream)),
    ServicesCard("Services", "Backends up against backends configured.", listOf(WidgetCardType.Services)),
    Middlewares("Middlewares", "In use against defined but unused.", listOf(WidgetCardType.Middlewares)),
    Entrypoints("Entry points", "Every door in, and what binds to it.", listOf(WidgetCardType.Entrypoints)),
    Certificates("Certificates", "What expires soonest, and how soon.", listOf(WidgetCardType.Certs)),
    ;

    companion object {
        fun of(cards: List<WidgetCardType>): WidgetPreset =
            entries.firstOrNull { it.cards == cards } ?: Sources

        fun from(name: String?): WidgetPreset? = entries.firstOrNull { it.name == name }
    }
}

data class WidgetSlot(val preset: WidgetPreset, val serverId: String? = null) {
    fun encode(): String = "${preset.name}:${serverId.orEmpty()}"

    companion object {
        const val MAX_SLOTS = 4

        fun decode(raw: String): WidgetSlot? {
            val preset = WidgetPreset.from(raw.substringBefore(':')) ?: return null
            return WidgetSlot(preset, raw.substringAfter(':', "").takeIf { it.isNotEmpty() })
        }

        fun parse(raw: String?): List<WidgetSlot> = raw
            ?.split(',')
            ?.mapNotNull { decode(it.trim()) }
            .orEmpty()
            .take(MAX_SLOTS)
    }
}

data class WidgetConfig(
    val cards: List<WidgetCardType> = listOf(WidgetCardType.Overview),
    val serverId: String? = null,
    val serverName: String = "Host",
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val layout: WidgetLayout = WidgetLayout.Mosaic,
    val slots: List<WidgetSlot> = emptyList(),
) {
    val pages: List<WidgetSlot>
        get() = slots.ifEmpty { listOf(WidgetSlot(WidgetPreset.of(cards), serverId)) }

    val needsCrowdSec: Boolean get() = cards.any { it.crowdsec }

    val familyTitle: String
        get() = when {
            cards.size <= 1 -> cards.firstOrNull()?.label.orEmpty()
            cards.all { it.crowdsec } -> "CrowdSec"
            cards.none { it.crowdsec || it == WidgetCardType.Overview } -> "Traefik"
            else -> "Overview"
        }

    val needsDashboard: Boolean get() = cards.any { !it.crowdsec && it != WidgetCardType.Overview }

    val needsOverview: Boolean get() = cards.contains(WidgetCardType.Overview)

    companion object {
        const val MAX_CARDS = 4

        const val DEFAULT_INTERVAL_MINUTES = 30

        const val MIN_INTERVAL_MINUTES = 15

        val INTERVAL_CHOICES = listOf(15, 30, 60)

        val CARDS = stringPreferencesKey("widget_cards")
        val LAYOUT = stringPreferencesKey("widget_layout")

        val PAGE = intPreferencesKey("widget_page")

        val SLOTS = stringPreferencesKey("widget_slots")
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
            layout = WidgetLayout.from(prefs[LAYOUT]),
            slots = WidgetSlot.parse(prefs[SLOTS]),
        )
    }
}
