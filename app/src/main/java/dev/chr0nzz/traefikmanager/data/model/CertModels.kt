package dev.chr0nzz.traefikmanager.data.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CertEntry(
    @Serializable(with = LenientStringSerializer::class)
    val resolver: String = "",
    @Serializable(with = LenientStringSerializer::class)
    val main: String = "",
    val sans: List<@Serializable(with = LenientStringSerializer::class) String> = emptyList(),
    @SerialName("not_after")
    val notAfter: String? = null,
    val source: String? = null,
    val certFile: String? = null,
)

@Serializable
data class CertsResponse(
    val certs: List<CertEntry> = emptyList(),
    val error: String? = null,
)

enum class CertHealth { Healthy, Expiring, Critical, Unknown }

data class CertRow(
    val key: String,
    val main: String,
    val resolver: String,
    val extraDomains: List<String>,
    val domainCount: Int,
    val daysLeft: Int?,
    val expiresOn: String?,
    val health: CertHealth,
    val origin: String?,
) {
    val title: String get() = main.ifEmpty { "Unknown" }

    val resolverLabel: String get() = resolver.ifEmpty { "-" }

    val daysLeftLabel: String?
        get() = when {
            daysLeft == null -> null
            daysLeft <= 0 -> "expired"
            else -> "${daysLeft}d left"
        }
}

object CertRows {

    private val dateFormat: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

    fun from(certs: List<CertEntry>, nowMillis: Long): List<CertRow> =
        certs.mapIndexed { index, entry -> row(entry, nowMillis, index) }
            .sortedWith(
                compareBy<CertRow> { it.daysLeft == null }
                    .thenBy { it.daysLeft ?: Int.MAX_VALUE }
                    .thenBy { it.main.lowercase() },
            )

    fun row(entry: CertEntry, nowMillis: Long, index: Int = 0): CertRow {
        val expiry = parseExpiry(entry.notAfter)
        val daysLeft = expiry?.let { ceil((it - nowMillis) / 86_400_000.0).toInt() }
        val sans = entry.sans.filter { it.isNotBlank() }
        val extras = sans.filter { it != entry.main }
        return CertRow(
            key = "$index:${entry.resolver}:${entry.main}",
            main = entry.main,
            resolver = entry.resolver,
            extraDomains = extras,
            domainCount = if (extras.isEmpty()) 1 else extras.size + 1,
            daysLeft = daysLeft,
            expiresOn = expiry?.let { dateFormat.format(Instant.ofEpochMilli(it)) },
            health = healthOf(daysLeft),
            origin = entry.source?.takeIf { it.isNotBlank() }
                ?: entry.certFile?.takeIf { it.isNotBlank() }?.substringAfterLast('/'),
        )
    }

    fun healthOf(daysLeft: Int?): CertHealth = when {
        daysLeft == null -> CertHealth.Unknown
        daysLeft < 7 -> CertHealth.Critical
        daysLeft < 30 -> CertHealth.Expiring
        else -> CertHealth.Healthy
    }

    fun parseExpiry(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }
}
