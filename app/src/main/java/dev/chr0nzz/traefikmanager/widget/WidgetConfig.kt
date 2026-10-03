package dev.chr0nzz.traefikmanager.widget

import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText

enum class WidgetCardType(
    val key: String,
    @StringRes val label: Int,
    @StringRes val blurb: Int,
    val crowdsec: Boolean = false,
) {
    Overview("overview", R.string.widget_card_all_servers, R.string.widget_blurb_overview),
    Http("http", R.string.widget_card_http, R.string.widget_blurb_http),
    Stream("stream", R.string.widget_card_stream, R.string.widget_blurb_stream),
    Services("services", R.string.common_label_services, R.string.widget_blurb_services),
    Middlewares("middlewares", R.string.common_label_middlewares, R.string.widget_blurb_middlewares),
    Sources("sources", R.string.crowdsec_card_sources, R.string.widget_blurb_sources, crowdsec = true),
    Scenarios("scenarios", R.string.crowdsec_card_scenarios, R.string.widget_blurb_scenarios, crowdsec = true),
    Paths("paths", R.string.crowdsec_card_paths, R.string.widget_blurb_paths, crowdsec = true),
    Bans("bans", R.string.crowdsec_card_bans, R.string.widget_blurb_bans, crowdsec = true),
    Entrypoints("entrypoints", R.string.dashboard_entry_points, R.string.widget_blurb_entrypoints),
    Certs("certs", R.string.nav_certificates, R.string.widget_blurb_certs),
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

enum class WidgetLayout(@StringRes val label: Int, @StringRes val blurb: Int) {
    Mosaic(R.string.widget_layout_mosaic, R.string.widget_layout_mosaic_blurb),
    Numbers(R.string.widget_layout_numbers, R.string.widget_layout_numbers_blurb),
    Rows(R.string.appearance_density_rows, R.string.widget_layout_rows_blurb),
    ;

    fun next(): WidgetLayout = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(key: String?): WidgetLayout = entries.firstOrNull { it.name == key } ?: Mosaic
    }
}

enum class WidgetPreset(
    @StringRes val label: Int,
    @StringRes val blurb: Int,
    val cards: List<WidgetCardType>,
    val layout: WidgetLayout = WidgetLayout.Mosaic,
) {
    CrowdSecStats(
        label = R.string.widget_preset_crowdsec,
        blurb = R.string.widget_preset_crowdsec_blurb,
        cards = listOf(
            WidgetCardType.Sources,
            WidgetCardType.Scenarios,
            WidgetCardType.Paths,
            WidgetCardType.Bans,
        ),
        layout = WidgetLayout.Rows,
    ),
    TraefikStats(
        label = R.string.widget_preset_traefik,
        blurb = R.string.widget_preset_traefik_blurb,
        cards = listOf(
            WidgetCardType.Http,
            WidgetCardType.Services,
            WidgetCardType.Middlewares,
            WidgetCardType.Entrypoints,
        ),
        layout = WidgetLayout.Rows,
    ),
Servers(R.string.widget_card_all_servers, R.string.widget_blurb_servers_preset, listOf(WidgetCardType.Overview)),
Sources(R.string.crowdsec_card_sources, R.string.widget_blurb_sources, listOf(WidgetCardType.Sources)),
Scenarios(R.string.crowdsec_card_scenarios, R.string.widget_blurb_scenarios, listOf(WidgetCardType.Scenarios)),
Paths(R.string.crowdsec_card_paths, R.string.widget_blurb_paths, listOf(WidgetCardType.Paths)),
Bans(R.string.crowdsec_card_bans, R.string.widget_blurb_bans, listOf(WidgetCardType.Bans)),
Routers(R.string.widget_card_http, R.string.widget_blurb_http, listOf(WidgetCardType.Http)),
Stream(R.string.widget_card_stream, R.string.widget_blurb_stream, listOf(WidgetCardType.Stream)),
ServicesCard(R.string.common_label_services, R.string.widget_blurb_services, listOf(WidgetCardType.Services)),
Middlewares(R.string.common_label_middlewares, R.string.widget_blurb_middlewares, listOf(WidgetCardType.Middlewares)),
Entrypoints(R.string.dashboard_entry_points, R.string.widget_blurb_entrypoints, listOf(WidgetCardType.Entrypoints)),
Certificates(R.string.nav_certificates, R.string.widget_blurb_certs, listOf(WidgetCardType.Certs)),
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

    val familyTitle: UiText
        get() = when {
            cards.size <= 1 -> cards.firstOrNull()?.let { UiText.of(it.label) } ?: UiText.Raw("")
            cards.all { it.crowdsec } -> UiText.Raw("CrowdSec")
            cards.none { it.crowdsec || it == WidgetCardType.Overview } -> UiText.Raw("Traefik")
            else -> UiText.of(R.string.widget_overview)
        }

    val needsDashboard: Boolean get() = cards.any { !it.crowdsec && it != WidgetCardType.Overview }

    val needsOverview: Boolean get() = cards.contains(WidgetCardType.Overview)

    companion object {

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
