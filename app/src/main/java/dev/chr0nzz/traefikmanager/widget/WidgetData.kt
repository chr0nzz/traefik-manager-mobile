package dev.chr0nzz.traefikmanager.widget

import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A card the app already draws, flattened so it survives a trip through widget state. */
@Serializable
data class WidgetCard(
    val key: String = "",
    val title: String = "",
    val hero: String = "",
    val unit: String = "",
    val health: String = "ok",
    val healthLabel: String = "",
    val sub: String = "",
    /** The signal strip, one entry per object, exactly as the desk shades it. */
    val cells: List<String> = emptyList(),
    /** Chips beside the hero, as the desk places its sig-flags: "101 loose", "6 idle". */
    val chips: List<WidgetChip> = emptyList(),
    /** Ranked rows for the cards that list rather than count. */
    val rows: List<WidgetRow> = emptyList(),
    /** The card's foot: providers on a Traefik card, origins on a CrowdSec one. */
    val footer: List<WidgetChip> = emptyList(),
)

@Serializable
data class WidgetRow(
    val name: String = "",
    val count: String = "",
    val flag: String = "",
    val health: String = "ok",
)

@Serializable
data class WidgetChip(
    val label: String = "",
    val count: Int = 0,
    val health: String = "ok",
)

/** One server on the overview: what it is running and what is wrong with it. */
@Serializable
data class WidgetServerRow(
    val id: String = "",
    val name: String = "",
    val routers: Int = 0,
    val warn: Int = 0,
    val err: Int = 0,
    val services: Int = 0,
    /** One entry per service on this server, shaded the way the services card shades them. */
    val cells: List<String> = emptyList(),
    val bans: Int = -1,
    val reachable: Boolean = true,
) {
    val hasBans: Boolean get() = bans >= 0
}

@Serializable
data class WidgetPayload(
    val cards: List<WidgetCard> = emptyList(),
    val servers: List<WidgetServerRow> = emptyList(),
    val note: String = "",
) {
    val isBlank: Boolean get() = cards.isEmpty() && servers.isEmpty()

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(payload: WidgetPayload): String = json.encodeToString(payload)

        fun decode(raw: String?): WidgetPayload? {
            if (raw.isNullOrEmpty()) return null
            return runCatching { json.decodeFromString<WidgetPayload>(raw) }.getOrNull()
        }
    }
}

fun TmStatus.wire(): String = when (this) {
    TmStatus.Ok -> "ok"
    TmStatus.Warn -> "warn"
    TmStatus.Error -> "error"
    TmStatus.Disabled -> "idle"
    TmStatus.Unknown -> "unknown"
}
