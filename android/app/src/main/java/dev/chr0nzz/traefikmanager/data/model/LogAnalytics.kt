package dev.chr0nzz.traefikmanager.data.model

import kotlin.math.floor
import kotlin.math.roundToInt

enum class LogHealth { Up, Warn, Down }

data class StatusBuckets(
    val oneXx: Int = 0,
    val twoXx: Int = 0,
    val threeXx: Int = 0,
    val fourXx: Int = 0,
    val fiveXx: Int = 0,
    val other: Int = 0,
) {
    val total: Int get() = oneXx + twoXx + threeXx + fourXx + fiveXx + other
}

data class LatencyStats(
    val p50: Double? = null,
    val p95: Double? = null,
    val max: Double? = null,
    val average: Double? = null,
    val timed: Int = 0,
    val fast: Int = 0,
    val medium: Int = 0,
    val slow: Int = 0,
    val verySlow: Int = 0,
    val held: Int = 0,
    val heldMax: Double? = null,
    val untimed: Int = 0,
    val slowest: LogEntry? = null,
)

data class RankedGroup(
    val key: String,
    val count: Int,
    val clientErrors: Int,
    val serverErrors: Int,
    val worstCode: Int?,
    val worstCount: Int,
    val kind: String?,
) {
    val errors: Int get() = clientErrors + serverErrors

    val share: Double get() = if (count == 0) 0.0 else errors.toDouble() / count
}

data class FailureRow(
    val status: Int,
    val path: String,
    val count: Int,
    val pathTotal: Int,
) {
    val share: Int get() = if (pathTotal == 0) 0 else ((count.toDouble() / pathTotal) * 100).roundToInt()
}

data class LogWindow(
    val fetched: Int = 0,
    val parsed: Int = 0,
    val unparsed: Int = 0,
    val spanMillis: Long? = null,
    val requestsPerMinute: Int? = null,
    val statuses: StatusBuckets = StatusBuckets(),
    val codeRank: List<Pair<Int, Int>> = emptyList(),
    val latency: LatencyStats = LatencyStats(),
    val retries: Int = 0,
    val methods: List<RankedGroup> = emptyList(),
    val domains: List<RankedGroup> = emptyList(),
    val paths: List<RankedGroup> = emptyList(),
    val clients: List<RankedGroup> = emptyList(),
    val services: List<RankedGroup> = emptyList(),
    val failures: List<FailureRow> = emptyList(),
    val jsonFormat: Boolean = false,
) {
    val health: LogHealth
        get() = when {
            statuses.fiveXx > 0 -> LogHealth.Down
            statuses.fourXx > 0 || latency.slow > 0 -> LogHealth.Warn
            else -> LogHealth.Up
        }

    val clientErrors: Int get() = statuses.fourXx

    val serverErrors: Int get() = statuses.fiveXx
}

object LogAnalytics {

    fun build(entries: List<LogEntry>, fetched: Int, unparsed: Int): LogWindow {
        if (entries.isEmpty()) {
            return LogWindow(fetched = fetched, parsed = 0, unparsed = unparsed)
        }

        val statuses = buckets(entries)
        val codes = entries.filter { it.status >= 400 }
            .groupingBy { it.status }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

        return LogWindow(
            fetched = fetched,
            parsed = entries.size,
            unparsed = unparsed,
            spanMillis = span(entries),
            requestsPerMinute = rate(entries),
            statuses = statuses,
            codeRank = codes,
            latency = latency(entries),
            retries = entries.sumOf { it.retries },
            methods = rank(entries) { it.method },
            domains = rank(entries) { it.domain },
            paths = rank(entries) { it.pattern },
            clients = rank(entries) { it.ip },
            services = rank(entries) { it.service.ifEmpty { it.router } },
            failures = failures(entries),
            jsonFormat = entries.any { it.format == LogFormat.Json },
        )
    }

    fun buckets(entries: List<LogEntry>): StatusBuckets {
        var one = 0
        var two = 0
        var three = 0
        var four = 0
        var five = 0
        var other = 0
        entries.forEach {
            when (it.statusClass) {
                "1xx" -> one++
                "2xx" -> two++
                "3xx" -> three++
                "4xx" -> four++
                "5xx" -> five++
                else -> other++
            }
        }
        return StatusBuckets(one, two, three, four, five, other)
    }

    fun span(entries: List<LogEntry>): Long? {
        val stamps = entries.mapNotNull { it.timestamp }
        if (stamps.size < 2) return null
        return (stamps.max() - stamps.min()).takeIf { it >= 0 }
    }

    private fun rate(entries: List<LogEntry>): Int? {
        val spanMillis = span(entries) ?: return null
        if (spanMillis <= 1000) return null
        return (entries.size / (spanMillis / 60_000.0)).roundToInt()
    }

    fun latency(entries: List<LogEntry>): LatencyStats {
        val held = entries.filter { it.durMs != null && it.heldOpen }
        val timed = entries.filter { it.durMs != null && !it.heldOpen }
        val durations = timed.mapNotNull { it.durMs }.sorted()

        fun pick(quantile: Double): Double? {
            if (durations.isEmpty()) return null
            val index = minOf(durations.size - 1, floor(durations.size * quantile).toInt())
            return durations[index]
        }

        return LatencyStats(
            p50 = pick(0.5),
            p95 = pick(0.95),
            max = durations.lastOrNull(),
            average = durations.takeIf { it.isNotEmpty() }?.average(),
            timed = timed.size,
            fast = durations.count { it < 100 },
            medium = durations.count { it >= 100 && it < 500 },
            slow = durations.count { it >= 500 },
            verySlow = durations.count { it >= 2000 },
            held = held.size,
            heldMax = held.mapNotNull { it.durMs }.maxOrNull(),
            untimed = entries.size - timed.size - held.size,
            slowest = timed.maxByOrNull { it.durMs ?: 0.0 },
        )
    }

    fun rank(entries: List<LogEntry>, kindOf: ((LogEntry) -> String)? = null, keyOf: (LogEntry) -> String): List<RankedGroup> =
        entries.filter { keyOf(it).isNotEmpty() && keyOf(it) != "-" }
            .groupBy(keyOf)
            .map { (key, rows) ->
                val codes = rows.filter { it.status >= 400 }.groupingBy { it.status }.eachCount()
                val worst = codes.entries.sortedWith(
                    compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key },
                ).firstOrNull()
                RankedGroup(
                    key = key,
                    count = rows.size,
                    clientErrors = rows.count { it.status in 400..499 },
                    serverErrors = rows.count { it.status in 500..599 },
                    worstCode = worst?.key,
                    worstCount = worst?.value ?: 0,
                    kind = kindOf?.let { fn -> rows.groupingBy(fn).eachCount().maxByOrNull { it.value }?.key },
                )
            }
            .sortedWith(
                compareByDescending<RankedGroup> { it.serverErrors }
                    .thenByDescending { it.clientErrors }
                    .thenByDescending { it.share }
                    .thenByDescending { it.count }
                    .thenBy { it.key },
            )

    private fun rank(entries: List<LogEntry>, keyOf: (LogEntry) -> String): List<RankedGroup> =
        rank(entries, null, keyOf)

    fun failures(entries: List<LogEntry>): List<FailureRow> {
        val failing = entries.filter { it.status >= 400 }
        if (failing.isEmpty()) return emptyList()
        val totals = entries.filter { it.path.isNotEmpty() }.groupingBy { it.pattern }.eachCount()
        return failing.groupBy { it.status to it.pattern }
            .map { (key, rows) ->
                FailureRow(
                    status = key.first,
                    path = key.second,
                    count = rows.size,
                    pathTotal = totals[key.second] ?: rows.size,
                )
            }
            .sortedWith(compareByDescending<FailureRow> { it.count }.thenBy { it.status })
    }
}
