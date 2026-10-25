package dev.chr0nzz.traefikmanager.data.model

import dev.chr0nzz.traefikmanager.R

object CertCleanup {
    private val HOST_RULE = Regex("""(!?)\s*Host(?:SNI)?\(`([^`]+)`\)""")

    fun hostsOf(route: Route): Set<String> {
        if (!route.tlsEnabled) return emptySet()
        return HOST_RULE.findAll(route.rule)
            .filter { it.groupValues[1] != "!" }
            .map { it.groupValues[2].trim().lowercase() }
            .filter { it.isNotEmpty() && it != "*" }
            .toSet()
    }

    fun freedBy(hosts: Set<String>, certs: List<CertEntry>, usage: CertUsage?): List<CertEntry> {
        if (hosts.isEmpty() || usage == null || !usage.unusedKnown) return emptyList()
        val unused = usage.certs.filter { it.unused }.map { it.key }.toSet()
        return certs.filter { cert ->
            cert.resolver.isNotEmpty() && cert.resolver != "file" &&
                "${cert.resolver}|${cert.main}" in unused &&
                (listOf(cert.main) + cert.sans).any { it.trim().lowercase() in hosts }
        }
    }

    fun outcome(response: CertDeleteResponse, asked: Int): UiText {
        val removed = response.removed.takeIf { it > 0 } ?: asked
        return when {
            response.ok && response.restarted -> UiText.plural(R.plurals.certs_removed, removed)
            response.ok -> response.restartError.takeIf { it.isNotBlank() }
                ?.let { UiText.plural(R.plurals.certs_removed_no_restart_reason, removed, removed, it) }
                ?: UiText.plural(R.plurals.certs_removed_no_restart, removed)
            response.partial -> UiText.messageOr(response.error, R.string.certs_error_partial)
            else -> UiText.messageOr(response.error, R.string.certs_error_remove)
        }
    }
}
