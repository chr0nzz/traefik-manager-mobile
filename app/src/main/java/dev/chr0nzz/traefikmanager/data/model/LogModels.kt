package dev.chr0nzz.traefikmanager.data.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
data class LogsResponse(
    val lines: List<String>? = null,
    val error: String? = null,
)

enum class LogFormat { Json, Clf, GenericClf }

data class LogEntry(
    val format: LogFormat,
    val ip: String = "",
    val date: String = "",
    val method: String = "",
    val path: String = "",
    val status: Int = 0,
    val origin: Int? = null,
    val size: String = "",
    val domain: String = "",
    val scheme: String = "",
    val entryPoint: String = "",
    val router: String = "",
    val service: String = "",
    val serviceUrl: String = "",
    val retries: Int = 0,
    val tls: String = "",
    val durMs: Double? = null,
    val duration: String = "",
    val raw: String = "",
) {
    val statusClass: String get() = LogParser.statusClass(status)

    val statusName: String get() = LogParser.statusName(status)

    val heldOpen: Boolean get() = status == 101 || status == 0

    val pattern: String get() = LogParser.pattern(path)

    val timestamp: Long? get() = LogParser.parseTimestamp(date)
}

data class LogLine(
    val index: Int,
    val raw: String,
    val entry: LogEntry?,
)

object LogParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val CLF_FULL = Regex(
        """^(\S+) \S+ \S+ \[([^\]]+)] "(\S+) (\S+)[^"]*" (\d+|-) (\S+) "[^"]*" "[^"]*" \S+ "([^"]*)" "([^"]*)" (\S+)""",
    )

    private val CLF_BASIC = Regex("""^(\S+) \S+ \S+ \[([^\]]+)] "(\S+) (\S+)[^"]*" (\d+|-) (\S+)""")

    private val CLF_DATE = Regex(
        """^(\d{2})/([A-Za-z]{3})/(\d{4}):(\d{2}):(\d{2}):(\d{2})\s*([+-]\d{2})(\d{2})$""",
    )

    private val DURATION = Regex("""(\d+(?:\.\d+)?)(ns|µs|us|ms|h|m|s)""")

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private val DIGITS = Regex("""^\d+$""")
    private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val UUID = Regex("""^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$""", RegexOption.IGNORE_CASE)
    private val LONG_HEX = Regex("""^[0-9a-f]{12,}$""", RegexOption.IGNORE_CASE)

    private val STATUS_NAMES = mapOf(
        100 to "Continue", 101 to "Switching Protocols", 103 to "Early Hints",
        200 to "OK", 201 to "Created", 202 to "Accepted", 204 to "No Content", 206 to "Partial Content",
        301 to "Moved Permanently", 302 to "Found", 303 to "See Other", 304 to "Not Modified",
        307 to "Temporary Redirect", 308 to "Permanent Redirect",
        400 to "Bad Request", 401 to "Unauthorized", 402 to "Payment Required", 403 to "Forbidden",
        404 to "Not Found", 405 to "Method Not Allowed", 406 to "Not Acceptable",
        407 to "Proxy Authentication Required", 408 to "Request Timeout", 409 to "Conflict",
        410 to "Gone", 411 to "Length Required", 413 to "Payload Too Large", 414 to "URI Too Long",
        415 to "Unsupported Media Type", 418 to "I am a teapot", 421 to "Misdirected Request",
        422 to "Unprocessable Entity", 426 to "Upgrade Required", 429 to "Too Many Requests",
        431 to "Request Header Fields Too Large", 451 to "Unavailable For Legal Reasons",
        500 to "Internal Server Error", 501 to "Not Implemented", 502 to "Bad Gateway",
        503 to "Service Unavailable", 504 to "Gateway Timeout", 505 to "HTTP Version Not Supported",
        507 to "Insufficient Storage", 511 to "Network Authentication Required",
    )

    fun parse(raw: String): LogEntry? {
        if (raw.trimStart().startsWith("{")) {
            parseJson(raw)?.let { return it }
        }
        CLF_FULL.find(raw)?.let { match ->
            val g = match.groupValues
            val durationText = g[9]
            return LogEntry(
                format = LogFormat.Clf,
                ip = g[1],
                date = g[2],
                method = g[3],
                path = g[4],
                status = g[5].toIntOrNull() ?: 0,
                origin = null,
                size = g[6],
                router = g[7].takeIf { it != "-" }.orEmpty(),
                serviceUrl = g[8].takeIf { it != "-" }.orEmpty(),
                durMs = parseDuration(durationText),
                duration = durationText,
                raw = raw,
            )
        }
        CLF_BASIC.find(raw)?.let { match ->
            val g = match.groupValues
            return LogEntry(
                format = LogFormat.GenericClf,
                ip = g[1],
                date = g[2],
                method = g[3],
                path = g[4],
                status = g[5].toIntOrNull() ?: 0,
                size = g[6],
                raw = raw,
            )
        }
        return null
    }

    private fun parseJson(raw: String): LogEntry? {
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val method = obj.text("RequestMethod")
        val path = obj.text("RequestPath")
        val downstream = obj.number("DownstreamStatus")
        if (method.isEmpty() && path.isEmpty() && (downstream == null || downstream == 0L)) return null

        val originStatus = obj.number("OriginStatus")
        val durationNs = obj.number("Duration")
        val size = when {
            obj["DownstreamContentSize"] != null && obj["DownstreamContentSize"] !is kotlinx.serialization.json.JsonNull ->
                obj.scalar("DownstreamContentSize")
            obj["OriginContentSize"] != null && obj["OriginContentSize"] !is kotlinx.serialization.json.JsonNull ->
                obj.scalar("OriginContentSize")
            else -> ""
        }
        return LogEntry(
            format = LogFormat.Json,
            ip = obj.text("ClientHost").ifEmpty { hostOnly(obj.text("ClientAddr")) },
            date = obj.text("StartUTC").ifEmpty { obj.text("StartLocal").ifEmpty { obj.text("time") } },
            method = method,
            path = path,
            status = (downstream ?: originStatus ?: 0L).toInt(),
            origin = originStatus?.toInt(),
            size = size,
            domain = obj.text("RequestHost").ifEmpty { hostOnly(obj.text("RequestAddr")) },
            scheme = obj.text("RequestScheme"),
            entryPoint = obj.text("entryPointName").ifEmpty { obj.text("EntryPointName") },
            router = obj.text("RouterName"),
            service = obj.text("ServiceName"),
            serviceUrl = obj.text("ServiceURL").ifEmpty { obj.text("ServiceAddr") },
            retries = obj.number("RetryAttempts")?.toInt() ?: 0,
            tls = obj.text("TLSVersion"),
            durMs = durationNs?.let { it / 1e6 },
            duration = formatDuration(durationNs ?: 0L),
            raw = raw,
        )
    }

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content.orEmpty()

    private fun JsonObject.scalar(key: String): String = (this[key] as? JsonPrimitive)?.content.orEmpty()

    private fun JsonObject.number(key: String): Long? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        primitive.longOrNull?.let { return it }
        primitive.doubleOrNull?.let { return it.toLong() }
        val leading = Regex("""^\s*-?\d+""").find(primitive.content)?.value?.trim()
        return leading?.toLongOrNull()
    }

    fun hostOnly(address: String): String {
        if (address.isEmpty()) return ""
        if (address.startsWith("[")) return address.substring(1, address.indexOf(']').takeIf { it > 0 } ?: address.length)
        val parts = address.split(':')
        return if (parts.size == 2) parts[0] else address
    }

    fun parseDuration(value: String?): Double? {
        if (value.isNullOrEmpty() || value == "-") return null
        var total = 0.0
        var matched = false
        DURATION.findAll(value).forEach { match ->
            val amount = match.groupValues[1].toDoubleOrNull() ?: return@forEach
            val factor = when (match.groupValues[2]) {
                "ns" -> 1e-6
                "µs", "us" -> 1e-3
                "ms" -> 1.0
                "h" -> 3_600_000.0
                "m" -> 60_000.0
                "s" -> 1000.0
                else -> return@forEach
            }
            total += amount * factor
            matched = true
        }
        if (matched) return total
        return value.trim().toDoubleOrNull()
    }

    fun formatDuration(nanos: Long): String = when {
        nanos <= 0L -> ""
        nanos >= 1_000_000_000L -> String.format(Locale.US, "%.2fs", nanos / 1e9)
        nanos >= 1_000_000L -> "${(nanos / 1e6).roundToLong()}ms"
        nanos >= 1_000L -> "${(nanos / 1e3).roundToLong()}µs"
        else -> "${nanos}ns"
    }

    fun parseTimestamp(date: String): Long? {
        if (date.isEmpty()) return null
        CLF_DATE.find(date)?.let { match ->
            val g = match.groupValues
            val monthIndex = MONTHS.indexOf(g[2])
            if (monthIndex < 0) return null
            val base = runCatching {
                OffsetDateTime.of(
                    g[3].toInt(), monthIndex + 1, g[1].toInt(),
                    g[4].toInt(), g[5].toInt(), g[6].toInt(), 0,
                    ZoneOffset.UTC,
                ).toInstant().toEpochMilli()
            }.getOrNull() ?: return null
            val hours = g[7].toInt()
            val minutes = g[8].toInt()
            val sign = if (g[7].startsWith("-")) -1 else 1
            return base - sign * (abs(hours) * 60 + minutes) * 60_000L
        }
        runCatching { return Instant.parse(date).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(date).toInstant().toEpochMilli() }
        return null
    }

    fun statusClass(status: Int): String = when (status) {
        in 500..599 -> "5xx"
        in 400..499 -> "4xx"
        in 300..399 -> "3xx"
        in 200..299 -> "2xx"
        in 100..199 -> "1xx"
        else -> "other"
    }

    fun statusName(status: Int): String = if (status == 0) "tunnel" else STATUS_NAMES[status].orEmpty()

    fun durBand(durMs: Double?): String = when {
        durMs == null -> ""
        durMs < 100 -> "fast"
        durMs < 500 -> "med"
        else -> "slow"
    }

    fun pattern(path: String): String {
        val clean = path.substringBefore('?')
        if (!clean.contains('/')) return clean
        return clean.split('/').joinToString("/") { segment ->
            when {
                segment.isEmpty() -> segment
                DIGITS.matches(segment) -> "<_>"
                ISO_DATE.matches(segment) -> "<_>"
                UUID.matches(segment) -> "<_>"
                LONG_HEX.matches(segment) -> "<_>"
                else -> segment
            }
        }
    }

    fun providerOf(name: String): String = name.substringAfter('@', "")

    /**
     * Which side of the internet an address sits on, matching the web's classifyIp
     * (core.js:843). A log full of private addresses usually means the real client IP is
     * arriving in a header the proxy is not forwarding.
     */
    fun ipClass(ip: String): String {
        val raw = ip.trim()
        if (raw.isEmpty()) return "unknown"
        val v4 = V4.matchEntire(raw)
        if (v4 != null) {
            val octets = v4.groupValues.drop(1).map { it.toIntOrNull() ?: 256 }
            if (octets.any { it > 255 }) return "unknown"
            val (a, b) = octets
            return when {
                a == 127 -> "loopback"
                a == 169 && b == 254 -> "link-local"
                a == 10 -> "private"
                a == 172 && b in 16..31 -> "private"
                a == 192 && b == 168 -> "private"
                a == 100 && b in 64..127 -> "cgnat"
                else -> "public"
            }
        }
        val v6 = raw.removePrefix("[").removeSuffix("]").substringBefore('%').lowercase()
        if (!v6.contains(':')) return "unknown"
        return when {
            v6 == "::1" -> "loopback"
            v6.startsWith("fe80") -> "link-local"
            v6.startsWith("fc") || v6.startsWith("fd") -> "private"
            v6.startsWith("::ffff:") && v6.substring(7).contains('.') -> ipClass(v6.substring(7))
            else -> "public"
        }
    }

    private val V4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    fun shortName(name: String): String = name.substringBefore('@')

    fun formatCount(value: Int): String = String.format(Locale.US, "%,d", value)

    fun formatMs(value: Double?): String = when {
        value == null -> "-"
        value >= 10_000 -> "${(value / 1000).roundToInt()}s"
        value >= 1000 -> String.format(Locale.US, "%.2fs", value / 1000)
        value >= 1 -> "${value.roundToInt()}ms"
        value > 0 -> "${trimZeros(String.format(Locale.US, "%.2f", value))}ms"
        else -> "0ms"
    }

    fun heroMs(value: Double?): Pair<String, String> = when {
        value == null -> "-" to ""
        value >= 1000 -> String.format(Locale.US, "%.1f", value / 1000) to "s"
        value >= 1 -> value.roundToInt().toString() to "ms"
        else -> (trimZeros(String.format(Locale.US, "%.2f", value)).ifEmpty { "0" }) to "ms"
    }

    fun spanText(millis: Long): String {
        val seconds = maxOf(0L, (millis / 1000.0).roundToLong())
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${(seconds % 60).toString().padStart(2, '0')}s"
            seconds < 86_400 -> "${seconds / 3600}h ${((seconds % 3600) / 60).toString().padStart(2, '0')}m"
            else -> "${seconds / 86_400}d ${(seconds % 86_400) / 3600}h"
        }
    }

    private fun trimZeros(value: String): String =
        value.trimEnd('0').trimEnd('.').ifEmpty { "0" }
}
