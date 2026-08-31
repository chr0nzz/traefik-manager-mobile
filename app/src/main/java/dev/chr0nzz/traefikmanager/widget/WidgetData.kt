package dev.chr0nzz.traefikmanager.widget

import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WidgetCard(
    val key: String = "",
    val title: String = "",
    val hero: String = "",
    val unit: String = "",
    val health: String = "ok",
    val healthLabel: String = "",
    val sub: String = "",
    val cells: List<String> = emptyList(),
    val chips: List<WidgetChip> = emptyList(),
    val rows: List<WidgetRow> = emptyList(),
    val footer: List<WidgetChip> = emptyList(),
    val listTitle: String = "WORST OFFENDERS",
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

@Serializable
data class WidgetServerRow(
    val id: String = "",
    val name: String = "",
    val routers: Int = 0,
    val warn: Int = 0,
    val err: Int = 0,
    val services: Int = 0,
    val cells: List<String> = emptyList(),
    val bans: Int = -1,
    val reachable: Boolean = true,
) {
    val hasBans: Boolean get() = bans >= 0
}

@Serializable
data class WidgetPayloads(val byServer: Map<String, WidgetPayload> = emptyMap()) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(value: WidgetPayloads): String = json.encodeToString(value)

        fun decode(raw: String?): WidgetPayloads {
            if (raw.isNullOrEmpty()) return WidgetPayloads()
            return runCatching { json.decodeFromString<WidgetPayloads>(raw) }.getOrNull()
                ?: WidgetPayload.decode(raw)?.let { WidgetPayloads(mapOf("" to it)) }
                ?: WidgetPayloads()
        }
    }
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
