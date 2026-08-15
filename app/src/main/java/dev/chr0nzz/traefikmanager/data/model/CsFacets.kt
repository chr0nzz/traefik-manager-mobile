package dev.chr0nzz.traefikmanager.data.model

/**
 * The eleven things the desk can be filtered by, exactly as the web names them
 * (crowdsec.js:26). Two of them read decisions, the rest read alerts, and scenario and ip read
 * both - which is what decides the view a click lands you in.
 */
enum class CsFacet(val key: String, val label: String, val reads: CsReads) {
    Scenario("scenario", "scenario", CsReads.Both),
    Ip("ip", "IP", CsReads.Both),
    Asn("asn", "network", CsReads.Alerts),
    Country("cc", "country", CsReads.Alerts),
    Uri("uri", "path", CsReads.Alerts),
    User("user", "account", CsReads.Alerts),
    Agent("agent", "tool", CsReads.Alerts),
    Verb("verb", "method", CsReads.Alerts),
    Outcome("outcome", "outcome", CsReads.Alerts),
    Origin("origin", "origin", CsReads.Decisions),
    Type("type", "type", CsReads.Decisions),
}

enum class CsReads { Alerts, Decisions, Both }

/**
 * Every filter on the window row applies together, and clicking a value that is already set
 * clears it (crowdsec.js:372-373).
 */
data class CsFacets(val values: Map<CsFacet, String> = emptyMap()) {

    operator fun get(facet: CsFacet): String? = values[facet]?.takeIf { it.isNotEmpty() }

    val active: List<Pair<CsFacet, String>>
        get() = CsFacet.entries.mapNotNull { facet -> get(facet)?.let { facet to it } }

    val isEmpty: Boolean get() = active.isEmpty()

    fun toggle(facet: CsFacet, value: String): CsFacets = when {
        value.isEmpty() -> this
        get(facet) == value -> CsFacets(values - facet)
        else -> CsFacets(values + (facet to value))
    }

    fun without(facet: CsFacet): CsFacets = CsFacets(values - facet)

    fun clear(): CsFacets = CsFacets()

    /** Mirrors `_atkMatchAlert` (crowdsec.js:431). [skip] leaves one facet out, as the map does. */
    fun matches(
        alert: CsAlert,
        countryOf: (CsAlert) -> String,
        handled: (CsAlert) -> Boolean,
        skip: CsFacet? = null,
    ): Boolean {
        fun on(facet: CsFacet) = if (facet == skip) null else get(facet)
        on(CsFacet.Scenario)?.let { if (alert.scenarioName != it) return false }
        on(CsFacet.Ip)?.let { if (alert.ip != it) return false }
        on(CsFacet.Asn)?.let { if (alert.source.asNumber != it) return false }
        on(CsFacet.Country)?.let { if (countryOf(alert) != it) return false }
        on(CsFacet.Uri)?.let { if (it !in alert.uris) return false }
        on(CsFacet.User)?.let { if (it !in alert.users) return false }
        on(CsFacet.Verb)?.let { if (it !in alert.verbs) return false }
        on(CsFacet.Agent)?.let { if (it !in alert.userAgents) return false }
        on(CsFacet.Outcome)?.let { outcome ->
            when (outcome) {
                "sim" -> if (!alert.simulated) return false
                "banned" -> if (!handled(alert)) return false
                "loose" -> if (handled(alert)) return false
            }
        }
        return true
    }

    /** Mirrors `_atkMatchDec` (crowdsec.js:455), including the two synthetic origins. */
    fun matches(decision: CsDecision): Boolean {
        get(CsFacet.Type)?.let { if (decision.type != it) return false }
        get(CsFacet.Origin)?.let { origin ->
            when (origin) {
                "subscribed" -> if (decision.own) return false
                "own" -> if (!decision.own) return false
                else -> if (decision.originKey != origin) return false
            }
        }
        get(CsFacet.Ip)?.let { if (decision.value != it) return false }
        get(CsFacet.Scenario)?.let { if (decision.scenario != it) return false }
        return true
    }

    /**
     * Which view a click should land in: a decision-only facet has nothing to say about alerts,
     * and the other way round (crowdsec.js:374-379).
     */
    fun viewFor(facet: CsFacet): CsReads = facet.reads
}
