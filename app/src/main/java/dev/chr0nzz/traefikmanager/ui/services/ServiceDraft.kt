package dev.chr0nzz.traefikmanager.ui.services

import dev.chr0nzz.traefikmanager.data.model.ServiceChildPayload
import dev.chr0nzz.traefikmanager.data.model.ServicePayload
import dev.chr0nzz.traefikmanager.data.model.TraefikService

data class ServiceChildDraft(
    val kind: String = MANUAL,
    val name: String = "",
    val address: String = "",
    val scheme: String = "http",
    val share: String = "1",
) {
    companion object {
        const val MANUAL = "manual"
        const val SERVICE = "service"
    }
}

data class ServiceDraft(
    val originalName: String = "",
    val name: String = "",
    val type: String = "loadBalancer",
    val configFile: String = "",
    val children: List<ServiceChildDraft> = listOf(ServiceChildDraft()),
) {
    val adding: Boolean get() = originalName.isEmpty()

    fun payload(): ServicePayload = ServicePayload(
        name = name.trim(),
        type = type,
        originalName = originalName,
        configFile = configFile.trim(),
        children = children.mapNotNull { child ->
            val share = child.share.trim().toIntOrNull() ?: if (type == "mirroring") 0 else 1
            when {
                child.kind == ServiceChildDraft.SERVICE && child.name.isBlank() -> null
                child.kind == ServiceChildDraft.MANUAL && child.address.isBlank() -> null
                else -> ServiceChildPayload(
                    kind = child.kind,
                    name = child.name.trim(),
                    address = child.address.trim(),
                    scheme = child.scheme,
                    weight = share,
                    percent = share,
                )
            }
        },
    )

    fun problem(): String? = when {
        name.isBlank() -> "Give the service a name"
        !NAME.matches(name.trim()) -> "Use letters, numbers, dots, dashes or underscores"
        payload().children.isEmpty() -> "Add at least one backend"
        type == "loadBalancer" && payload().children.none { it.kind == ServiceChildDraft.MANUAL } ->
            "A load balancer needs at least one address"
        else -> null
    }

    companion object {
        private val NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,62}$")

        fun of(name: String, service: TraefikService?): ServiceDraft {
            val bare = name.substringBefore('@')
            val weighted = service?.weighted?.services
            val hrw = service?.highestRandomWeight?.services
            val mirroring = service?.mirroring
            val failover = service?.failover
            val type = when {
                weighted != null -> "weighted"
                mirroring != null -> "mirroring"
                failover != null -> "failover"
                hrw != null -> "highestRandomWeight"
                else -> "loadBalancer"
            }
            val children = when {
                weighted != null -> weighted.map { reference(it.name, it.weight ?: 1) }
                hrw != null -> hrw.map { reference(it.name, it.weight ?: 1) }
                mirroring != null -> listOfNotNull(mirroring.service?.let { reference(it, 0) }) +
                    mirroring.mirrors.map { reference(it.name, it.percent ?: 0) }
                failover != null -> listOfNotNull(
                    failover.service?.let { reference(it, 1) },
                    failover.fallback?.let { reference(it, 1) },
                )
                else -> service?.loadBalancer?.servers.orEmpty().map { server ->
                    val url = server.target
                    ServiceChildDraft(
                        kind = ServiceChildDraft.MANUAL,
                        address = url.substringAfter("://", url),
                        scheme = if (url.startsWith("https")) "https" else "http",
                    )
                }
            }
            return ServiceDraft(
                originalName = bare,
                name = bare,
                type = type,
                children = children.ifEmpty { listOf(ServiceChildDraft()) },
            )
        }

        private fun reference(name: String, share: Int) = ServiceChildDraft(
            kind = ServiceChildDraft.SERVICE,
            name = name.substringBefore('@'),
            share = share.toString(),
        )
    }
}
