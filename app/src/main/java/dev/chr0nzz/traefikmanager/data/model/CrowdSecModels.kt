package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class CsDecision(
    val id: Long = 0,
    val value: String = "",
    val type: String = "ban",
    val scope: String = "Ip",
    val origin: String = "",
    val scenario: String = "",
    val duration: String = "",
    val simulated: Boolean = false,
) {
    val originKey: String get() = origin.lowercase()

    val own: Boolean get() = originKey !in SUBSCRIBED

    companion object {
        val SUBSCRIBED = setOf("capi", "lists")
    }
}

@Serializable
data class CsSource(
    val ip: String = "",
    val value: String = "",
    val scope: String = "Ip",
    val cn: String = "",
    @SerialName("as_name")
    val asName: String = "",
    @SerialName("as_number")
    @Serializable(with = LenientStringSerializer::class)
    val asNumber: String = "",
    val range: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class CsMetaEntry(
    val key: String = "",
    val value: String = "",
)

@Serializable
data class CsAlert(
    val uuid: String = "",
    val id: Long = 0,
    val scenario: String = "",
    @SerialName("scenario_version")
    val scenarioVersion: String = "",
    @Serializable(with = LenientIntSerializer::class)
    @SerialName("events_count")
    val eventsCount: Int = 0,
    val capacity: Int = 0,
    val leakspeed: String = "",
    val simulated: Boolean = false,
    @SerialName("machine_id")
    val machineId: String = "",
    val message: String = "",
    @SerialName("start_at")
    val startAt: String = "",
    @SerialName("stop_at")
    val stopAt: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    val source: CsSource = CsSource(),
    val meta: List<CsMetaEntry> = emptyList(),
) {
    val ip: String get() = source.ip.ifEmpty { source.value }

    val countryCode: String get() = source.cn.takeIf { CC.matches(it) }?.uppercase().orEmpty()

    val scenarioName: String get() = scenario.ifEmpty { "unknown" }

    val startMillis: Long get() = LogParser.parseTimestamp(startAt.ifEmpty { createdAt }) ?: 0L

    val metaMap: Map<String, List<String>> get() = CrowdSecMeta.parse(meta)

    val uris: List<String> get() = metaMap["target_uri"].orEmpty()

    val users: List<String> get() = metaMap["target_user"].orEmpty()

    val verbs: List<String> get() = metaMap["method"].orEmpty()

    val codes: List<String> get() = metaMap["status"].orEmpty()

    val userAgents: List<String> get() = metaMap["user_agent"].orEmpty()

    fun key(index: Int): String = uuid.ifEmpty { id.takeIf { it != 0L }?.toString() ?: "cs$index" }

    private companion object {
        val CC = Regex("^[A-Za-z]{2}$")
    }
}

@Serializable
data class AddDecisionRequest(
    val value: String,
    val type: String = "ban",
    val duration: String = "24h",
    val reason: String = "manual ban from Traefik Manager",
)

object CrowdSecMeta {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(entries: List<CsMetaEntry>): Map<String, List<String>> {
        val out = mutableMapOf<String, MutableList<String>>()
        entries.forEach { entry ->
            if (entry.key.isEmpty()) return@forEach
            val values = decode(entry.value)
            if (values.isEmpty()) return@forEach
            out.getOrPut(entry.key) { mutableListOf() }.addAll(values)
        }
        return out
    }

    private fun decode(raw: String): List<String> {
        if (raw.isEmpty()) return emptyList()
        val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        if (parsed is JsonArray) {
            return parsed.mapNotNull { element ->
                (element as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }
            }
        }
        return listOf(raw)
    }
}

sealed interface CsRead<out T> {
    data class Loaded<T>(val value: T) : CsRead<T>
    data class Failed(val message: String, val status: Int?) : CsRead<Nothing>
    data object NotConfigured : CsRead<Nothing>

    val ok: Boolean get() = this is Loaded

    fun valueOrNull(): T? = (this as? Loaded)?.value
}

data class CsRanked(
    val key: String,
    val label: String,
    val count: Int,
    val weight: Int,
    val open: Int,
    val extra: String = "",
)

data class CsOriginBreakdown(
    val origin: String,
    val count: Int,
)

data class CrowdSecSnapshot(
    val decisions: CsRead<List<CsDecision>> = CsRead.Loaded(emptyList()),
    val alerts: CsRead<List<CsAlert>> = CsRead.Loaded(emptyList()),
    val alertLimit: Int? = null,
    val alertsCapped: Boolean? = null,
) {
    val decisionList: List<CsDecision> get() = decisions.valueOrNull().orEmpty()

    val alertList: List<CsAlert> get() = alerts.valueOrNull().orEmpty()

    val bannedIps: Set<String>
        get() = decisionList.filter { it.scope == "Ip" || it.scope == "Range" }.map { it.value }.toSet()

    fun handled(alert: CsAlert): Boolean =
        decisions.ok && !alert.simulated && alert.ip in bannedIps
}

object CrowdSecAnalytics {

    private const val ROW_CAP = 6

    fun filterAlerts(alerts: List<CsAlert>): List<CsAlert> =
        alerts.filterNot { it.source.scope.lowercase() in setOf("capi", "lists") }

    fun sources(alerts: List<CsAlert>, banned: Set<String>): List<CsRanked> =
        rank(alerts, banned) { listOf(it.ip) }

    fun networks(alerts: List<CsAlert>, banned: Set<String>): List<CsRanked> =
        rank(
            alerts = alerts,
            banned = banned,
            extraOf = { rows -> rows.firstOrNull()?.countryCode.orEmpty() },
            labelOf = { key, rows -> rows.firstOrNull()?.source?.asName?.ifEmpty { "AS$key" } ?: "AS$key" },
        ) { alert ->
            val number = alert.source.asNumber
            if (number.isEmpty()) emptyList() else listOf(number)
        }

    fun scenarios(alerts: List<CsAlert>, banned: Set<String>): List<CsRanked> =
        rank(alerts, banned) { listOf(it.scenarioName) }

    fun paths(alerts: List<CsAlert>, banned: Set<String>): List<CsRanked> =
        rank(alerts, banned) { it.uris }

    fun accounts(alerts: List<CsAlert>, banned: Set<String>): List<CsRanked> =
        rank(alerts, banned) { it.users }

    fun tooling(alerts: List<CsAlert>, banned: Set<String>): List<CsRanked> =
        rank(alerts, banned) { it.userAgents }

    fun origins(decisions: List<CsDecision>): List<CsOriginBreakdown> =
        decisions.groupingBy { it.originKey.ifEmpty { "other" } }
            .eachCount()
            .map { CsOriginBreakdown(it.key, it.value) }
            .sortedByDescending { it.count }

    fun rank(
        alerts: List<CsAlert>,
        banned: Set<String>,
        extraOf: (List<CsAlert>) -> String = { "" },
        labelOf: (String, List<CsAlert>) -> String = { key, _ -> key },
        keysOf: (CsAlert) -> List<String>,
    ): List<CsRanked> {
        val buckets = mutableMapOf<String, MutableList<CsAlert>>()
        alerts.forEach { alert ->
            keysOf(alert).filter { it.isNotEmpty() }.distinct().forEach { key ->
                buckets.getOrPut(key) { mutableListOf() }.add(alert)
            }
        }
        return buckets.map { (key, rows) ->
            CsRanked(
                key = key,
                label = labelOf(key, rows),
                count = rows.size,
                weight = rows.sumOf { it.eventsCount },
                open = rows.count { it.ip !in banned },
                extra = extraOf(rows),
            )
        }.sortedWith(
            compareByDescending<CsRanked> { it.open }
                .thenByDescending { it.count }
                .thenByDescending { it.weight }
                .thenBy { it.key },
        )
    }

    fun top(rows: List<CsRanked>): List<CsRanked> = rows.take(ROW_CAP)

    fun percent(count: Int, total: Int): String {
        if (total <= 0) return "0%"
        val value = count * 100.0 / total
        return if (value >= 10) "${value.toInt()}%" else String.format(java.util.Locale.US, "%.1f%%", value)
    }

    fun spanMillis(alerts: List<CsAlert>): Long? {
        val stamps = alerts.map { it.startMillis }.filter { it > 0 }
        if (stamps.size < 2) return null
        return stamps.max() - stamps.min()
    }
}
