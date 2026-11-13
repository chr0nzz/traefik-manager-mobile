package dev.chr0nzz.traefikmanager.data.model

import androidx.annotation.StringRes
import dev.chr0nzz.traefikmanager.R

enum class AdvisorySeverity(@param:StringRes val label: Int) {
    Critical(R.string.advisory_severity_critical),
    High(R.string.advisory_severity_high),
    Moderate(R.string.advisory_severity_moderate),
}

data class TraefikAdvisory(
    val id: String,
    val severity: AdvisorySeverity,
    val url: String,
    val forwardAuthRelated: Boolean,
    @param:StringRes val summary: Int,
    val fixedIn: List<String>,
    val affected: (major: Int, minor: Int, patch: Int) -> Boolean,
)

data class AdvisoryNotice(
    val advisory: TraefikAdvisory,
    val version: String,
    val forwardAuthInUse: Boolean,
)

object TraefikAdvisories {

    private const val BASE = "https://github.com/traefik/traefik/security/advisories/"

    val all: List<TraefikAdvisory> = listOf(
        TraefikAdvisory("CVE-2026-88007", AdvisorySeverity.Critical, BASE + "GHSA-qqjf-53cj-pwvv", false, R.string.advisory_http3_identity_leak, listOf("v3.7.13", "v2.11.57")) { maj, min, pat ->
            when (maj) {
                0, 1 -> true
                2 -> min == 11 && pat <= 56
                3 -> min < 7 || (min == 7 && pat <= 12)
                else -> false
            }
        },
        TraefikAdvisory("CVE-2026-88009", AdvisorySeverity.High, BASE + "GHSA-f52w-8j3h-j724", true, R.string.advisory_rootless_target, listOf("v3.7.13", "v2.11.57")) { maj, min, pat ->
            when (maj) {
                0, 1 -> true
                2 -> min < 11 || (min == 11 && pat <= 56)
                3 -> min < 7 || (min == 7 && pat <= 12)
                else -> false
            }
        },
        TraefikAdvisory("CVE-2026-88008", AdvisorySeverity.High, BASE + "GHSA-w4v4-9rw7-5326", true, R.string.advisory_h2c_upgrade, listOf("v3.7.13", "v2.11.57")) { maj, min, pat ->
            when (maj) {
                2 -> min == 11 && pat in 26..56
                3 -> (min in 5..6) || (min == 4 && pat >= 2) || (min == 7 && pat <= 12)
                else -> false
            }
        },
        TraefikAdvisory("CVE-2026-88004", AdvisorySeverity.High, BASE + "GHSA-v67p-phpq-fc8x", true, R.string.advisory_trailer_sanitization, listOf("v3.7.13")) { maj, min, pat ->
            maj == 3 && ((min in 2..6) || (min == 7 && pat <= 12))
        },
        TraefikAdvisory("GHSA-qm9f-w54v-q2qh", AdvisorySeverity.High, BASE + "GHSA-qm9f-w54v-q2qh", false, R.string.advisory_negotiate_pool, listOf("v3.7.14", "v2.11.58")) { maj, min, pat ->
            when (maj) {
                2 -> min == 11 && pat <= 57
                3 -> min < 7 || (min == 7 && pat <= 13)
                else -> false
            }
        },
        TraefikAdvisory("GHSA-cw35-4q88-3rmp", AdvisorySeverity.High, BASE + "GHSA-cw35-4q88-3rmp", false, R.string.advisory_sticky_tls_config, listOf("v3.7.14", "v2.11.58")) { maj, min, pat ->
            when (maj) {
                2 -> min == 11 && pat <= 57
                3 -> min < 7 || (min == 7 && pat <= 13)
                else -> false
            }
        },
        TraefikAdvisory("GHSA-53qr-784g-cj35", AdvisorySeverity.High, BASE + "GHSA-53qr-784g-cj35", false, R.string.advisory_fastproxy_identity_leak, listOf("v3.7.14")) { maj, min, pat ->
            maj == 3 && ((min in 2..6) || (min == 7 && pat <= 13))
        },
        TraefikAdvisory("CVE-2026-39858", AdvisorySeverity.High, BASE + "GHSA-5m6w-wvh7-57vm", true, R.string.advisory_forwardauth_alias_bypass, listOf("v3.6.14", "v2.11.43")) { maj, min, pat ->
            when (maj) {
                0, 1 -> true
                2 -> min < 11 || (min == 11 && pat < 43)
                3 -> min < 6 || (min == 6 && pat < 14)
                else -> false
            }
        },
        TraefikAdvisory("GHSA-rf44-j88r-hh8c", AdvisorySeverity.Moderate, BASE + "GHSA-rf44-j88r-hh8c", true, R.string.advisory_forwardauth_dot_alias, listOf("v3.7.12", "v2.11.56")) { maj, min, pat ->
            when (maj) {
                0, 1 -> true
                2 -> min < 11 || (min == 11 && pat <= 55)
                3 -> min < 7 || (min == 7 && pat <= 11)
                else -> false
            }
        },
        TraefikAdvisory("CVE-2026-88010", AdvisorySeverity.Moderate, BASE + "GHSA-8fcf-v89g-xpg6", false, R.string.advisory_basicauth_timing, listOf("v3.7.13")) { maj, min, pat ->
            maj == 3 && ((min == 6 && pat >= 11) || (min == 7 && pat <= 12))
        },
        TraefikAdvisory("GHSA-2272-4vv8-65ww", AdvisorySeverity.Moderate, BASE + "GHSA-2272-4vv8-65ww", false, R.string.advisory_negated_host_mtls, listOf("v3.7.13", "v2.11.58")) { maj, min, pat ->
            when (maj) {
                0, 1 -> true
                2 -> min < 11 || (min == 11 && pat <= 57)
                3 -> min < 7 || (min == 7 && pat <= 12)
                else -> false
            }
        },
        TraefikAdvisory("GHSA-mhjw-hmjv-p99x", AdvisorySeverity.Moderate, BASE + "GHSA-mhjw-hmjv-p99x", false, R.string.advisory_sticky_h2_race, listOf("v3.7.14", "v2.11.58")) { maj, min, pat ->
            when (maj) {
                2 -> min == 11 && pat <= 57
                3 -> min < 7 || (min == 7 && pat <= 13)
                else -> false
            }
        },
        TraefikAdvisory("GHSA-hrpp-p4fc-wwf8", AdvisorySeverity.High, BASE + "GHSA-hrpp-p4fc-wwf8", true, R.string.advisory_forwardauth_connection_leak, listOf("v3.7.16", "v2.11.60")) { maj, min, pat ->
            when (maj) {
                0, 1 -> true
                2 -> min < 11 || (min == 11 && pat <= 59)
                3 -> min < 7 || (min == 7 && pat <= 15)
                else -> false
            }
        },
        TraefikAdvisory("GHSA-ww7c-q3mv-xx22", AdvisorySeverity.High, BASE + "GHSA-ww7c-q3mv-xx22", false, R.string.advisory_fastproxy_upgrade_race, listOf("v3.7.15")) { maj, min, pat ->
            maj == 3 && ((min in 2..6) || (min == 7 && pat <= 14))
        },
    )

    private val SEMVER = Regex("""(\d+)\.(\d+)\.(\d+)""")

    fun parse(version: String?): Triple<Int, Int, Int>? {
        val parts = SEMVER.find(version.orEmpty())?.destructured ?: return null
        val (maj, min, pat) = parts
        return Triple(maj.toInt(), min.toInt(), pat.toInt())
    }

    fun firstAffecting(version: String?): TraefikAdvisory? {
        val (maj, min, pat) = parse(version) ?: return null
        return all.firstOrNull { it.affected(maj, min, pat) }
    }

    private val FORWARD_AUTH = Regex("forward[_-]?auth", RegexOption.IGNORE_CASE)

    fun forwardAuthInUse(middlewares: List<TraefikObject>): Boolean =
        middlewares.any { FORWARD_AUTH.containsMatchIn(it.type.orEmpty()) || FORWARD_AUTH.containsMatchIn(it.name) }

    fun notice(version: String?, middlewares: List<TraefikObject>, dismissed: Map<String, String>): AdvisoryNotice? {
        val advisory = firstAffecting(version) ?: return null
        val clean = parse(version)?.let { (maj, min, pat) -> "$maj.$min.$pat" } ?: return null
        if (dismissed[advisory.id] == clean) return null
        return AdvisoryNotice(advisory, clean, advisory.forwardAuthRelated && forwardAuthInUse(middlewares))
    }
}
