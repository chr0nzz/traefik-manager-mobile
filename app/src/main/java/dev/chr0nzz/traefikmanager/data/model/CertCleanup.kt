package dev.chr0nzz.traefikmanager.data.model

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

    fun outcome(response: CertDeleteResponse, asked: Int): String {
        val removed = response.removed.takeIf { it > 0 } ?: asked
        val noun = if (removed == 1) "certificate" else "certificates"
        return when {
            response.ok && response.restarted -> "Removed $removed $noun"
            response.ok -> buildString {
                append("Removed $removed $noun, but Traefik did not restart")
                response.restartError.takeIf { it.isNotBlank() }?.let { append(": $it") }
                append(". The change is undone until it does.")
            }
            response.partial -> response.error ?: "Certificate removal stopped partway"
            else -> response.error ?: "Could not remove the certificate"
        }
    }
}
