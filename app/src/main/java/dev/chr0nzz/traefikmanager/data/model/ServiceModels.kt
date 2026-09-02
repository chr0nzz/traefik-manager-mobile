package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ServiceServer(
    val url: String? = null,
    val address: String? = null,
) {
    val target: String get() = url ?: address ?: ""
}

@Serializable
data class ServiceLoadBalancer(
    val servers: List<ServiceServer> = emptyList(),
    val passHostHeader: Boolean? = null,
    val sticky: JsonElement? = null,
    val healthCheck: JsonElement? = null,
)

@Serializable
data class WeightedChild(
    val name: String = "",
    val weight: Int? = null,
)

@Serializable
data class WeightedService(
    val services: List<WeightedChild> = emptyList(),
)

@Serializable
data class MirrorChild(
    val name: String = "",
    val percent: Int? = null,
)

@Serializable
data class MirroringService(
    val service: String? = null,
    val mirrors: List<MirrorChild> = emptyList(),
)

@Serializable
data class FailoverService(
    val service: String? = null,
    val fallback: String? = null,
)

@Serializable
data class TraefikService(
    val name: String = "",
    val provider: String = "",
    val status: String? = null,
    val type: String? = null,
    val usedBy: List<String>? = null,
    val serverStatus: Map<String, String>? = null,
    val error: JsonElement? = null,
    val loadBalancer: ServiceLoadBalancer? = null,
    val weighted: WeightedService? = null,
    val mirroring: MirroringService? = null,
    val failover: FailoverService? = null,
    val highestRandomWeight: WeightedService? = null,
)

@Serializable
data class ServiceEnvelope(
    val http: List<TraefikService> = emptyList(),
    val tcp: List<TraefikService> = emptyList(),
    val udp: List<TraefikService> = emptyList(),
    val reachable: Boolean = true,
    val ownedServices: List<String> = emptyList(),
    val ownedChildren: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = http.isEmpty() && tcp.isEmpty() && udp.isEmpty()
}

fun ServiceEnvelope.toProtoEnvelope(): ProtoEnvelope = ProtoEnvelope(
    http = http.map { it.asTraefikObject() },
    tcp = tcp.map { it.asTraefikObject() },
    udp = udp.map { it.asTraefikObject() },
    reachable = reachable,
)

private fun TraefikService.asTraefikObject(): TraefikObject = TraefikObject(
    name = name,
    provider = provider,
    status = status,
    usedBy = usedBy,
    serverStatus = serverStatus,
    type = type,
)

enum class ServiceProtocol(val label: String) {
    Http("HTTP"),
    Tcp("TCP"),
    Udp("UDP"),
}

enum class ServiceKind(val label: String) {
    LoadBalancer("loadbalancer"),
    Weighted("weighted"),
    Mirroring("mirroring"),
    Failover("failover"),
    HighestRandomWeight("highestrandomweight"),
}

enum class ServiceHealth { Ok, Warning, Error }

enum class ServerHealth { Up, Down, Unknown }

data class ServiceServerRow(
    val target: String,
    val health: ServerHealth,
)

data class ServiceRow(
    val key: String,
    val proto: ServiceProtocol,
    val name: String,
    val shortName: String,
    val provider: String,
    val kind: ServiceKind?,
    val health: ServiceHealth,
    val backendsUp: Int,
    val backendsTotal: Int,
    val servers: List<ServiceServerRow>,
    val composite: List<String>,
    val sticky: Boolean,
    val healthCheck: Boolean,
    val passHostHeader: Boolean?,
    val usedBy: List<String>,
    val errors: List<String>,
    val owned: Boolean = false,
) {
    val kindLabel: String get() = kind?.label ?: "service"

    val authorable: Boolean get() = proto == ServiceProtocol.Http

    val usedByLabel: String?
        get() = when (usedBy.size) {
            0 -> null
            1 -> "used by 1 route"
            else -> "used by ${usedBy.size} routes"
        }

    val backendSummary: String? get() = if (backendsTotal > 0) "$backendsUp/$backendsTotal active" else null

    val allBackendsUp: Boolean get() = backendsTotal > 0 && backendsUp == backendsTotal

    val metaParts: List<String>
        get() = buildList {
            if (composite.isEmpty() && servers.isNotEmpty()) {
                add(if (servers.size == 1) "1 server" else "${servers.size} servers")
            }
            backendSummary?.let(::add)
            if (sticky) add("sticky")
            if (healthCheck) add("health check")
        }
}

object ServiceRows {

    fun from(envelope: ServiceEnvelope): List<ServiceRow> {
        val owned = envelope.ownedServices.toSet()
        val generated = envelope.ownedChildren.toSet()
        return (envelope.http.map { it to ServiceProtocol.Http } +
            envelope.tcp.map { it to ServiceProtocol.Tcp } +
            envelope.udp.map { it to ServiceProtocol.Udp })
            .map { (service, proto) -> row(service, proto) }
            .filterNot { it.shortName in generated }
            .map { if (it.shortName in owned) it.copy(owned = true) else it }
            .sortedBy { it.name.lowercase() }
    }

    fun row(service: TraefikService, proto: ServiceProtocol): ServiceRow {
        val statuses = service.serverStatus.orEmpty()
        val up = statuses.count { it.value.uppercase() == "UP" }
        val lb = service.loadBalancer
        val targets = lb?.servers.orEmpty().map { it.target }.filter { it.isNotEmpty() }
        val fallbackTargets = if (targets.isEmpty()) statuses.keys.toList() else targets
        return ServiceRow(
            key = "${proto.name}:${service.name}",
            proto = proto,
            name = service.name,
            shortName = service.name.substringBefore('@'),
            provider = service.provider.ifEmpty { service.name.substringAfterLast('@', "file") },
            kind = kindOf(service),
            health = healthOf(service),
            backendsUp = up,
            backendsTotal = statuses.size,
            servers = fallbackTargets.map { target ->
                ServiceServerRow(
                    target = target,
                    health = when (statuses[target]?.uppercase()) {
                        null -> ServerHealth.Unknown
                        "UP" -> ServerHealth.Up
                        else -> ServerHealth.Down
                    },
                )
            },
            composite = compositeTargets(service),
            sticky = lb?.sticky != null,
            healthCheck = lb?.healthCheck != null,
            passHostHeader = if (lb == null) null else lb.passHostHeader ?: true,
            usedBy = service.usedBy.orEmpty(),
            errors = errorsOf(service.error),
        )
    }

    fun kindOf(service: TraefikService): ServiceKind? {
        when (service.type?.lowercase()) {
            "loadbalancer" -> return ServiceKind.LoadBalancer
            "weighted" -> return ServiceKind.Weighted
            "mirroring" -> return ServiceKind.Mirroring
            "failover" -> return ServiceKind.Failover
            "highestrandomweight" -> return ServiceKind.HighestRandomWeight
        }
        return when {
            service.loadBalancer != null -> ServiceKind.LoadBalancer
            service.mirroring != null -> ServiceKind.Mirroring
            service.failover != null -> ServiceKind.Failover
            service.weighted != null -> ServiceKind.Weighted
            service.highestRandomWeight != null -> ServiceKind.HighestRandomWeight
            else -> null
        }
    }

    fun healthOf(service: TraefikService): ServiceHealth {
        val anyDown = service.serverStatus.orEmpty().any { it.value.uppercase() != "UP" }
        return when (service.status?.lowercase()) {
            "disabled", "error" -> ServiceHealth.Error
            "enabled" -> if (anyDown) ServiceHealth.Warning else ServiceHealth.Ok
            else -> ServiceHealth.Warning
        }
    }

    private fun compositeTargets(service: TraefikService): List<String> = buildList {
        service.weighted?.services?.forEach { child ->
            add(if (child.weight != null) "${child.name} (${child.weight})" else child.name)
        }
        service.mirroring?.service?.let(::add)
        service.mirroring?.mirrors?.forEach { mirror ->
            add("${mirror.name} mirror (${mirror.percent ?: 0}%)")
        }
        service.failover?.service?.let(::add)
        service.failover?.fallback?.let { add("$it fallback") }
        service.highestRandomWeight?.services?.forEach { child ->
            add(if (child.weight != null) "${child.name} (${child.weight})" else child.name)
        }
    }

    fun errorsOf(element: JsonElement?): List<String> = when (element) {
        null -> emptyList()
        is JsonArray -> element.mapNotNull(::errorText)
        else -> listOfNotNull(errorText(element))
    }

    private fun errorText(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }
        is JsonObject -> (element["message"] as? JsonPrimitive)?.content ?: element.toString()
        else -> element.toString()
    }
}

@Serializable
data class ServiceChildPayload(
    val kind: String,
    val name: String = "",
    val address: String = "",
    val scheme: String = "http",
    val weight: Int = 1,
    val percent: Int = 0,
)

@Serializable
data class ServicePayload(
    val name: String,
    val type: String,
    val originalName: String = "",
    val configFile: String = "",
    val children: List<ServiceChildPayload> = emptyList(),
)

@Serializable
data class ServiceSaveResponse(
    val ok: Boolean = false,
    val name: String = "",
    val error: String? = null,
)

@Serializable
data class ServiceOwnershipRequest(val adopt: Boolean)

@Serializable
data class ServiceOwnershipResponse(
    val ok: Boolean = false,
    val owned: Boolean = false,
    val error: String? = null,
)

object ServiceTypes {

    val authorable: List<Pair<String, String>> = listOf(
        "loadBalancer" to "Load balancer",
        "weighted" to "Weighted",
        "mirroring" to "Mirroring",
        "failover" to "Failover",
    )

    fun label(type: String): String =
        authorable.firstOrNull { it.first == type }?.second
            ?: if (type == "highestRandomWeight") "Highest random weight" else type

    fun shareLabel(type: String): String = when (type) {
        "mirroring" -> "Percent"
        "failover" -> ""
        else -> "Weight"
    }

    fun rowHint(type: String, index: Int): String? = when {
        type == "mirroring" && index == 0 -> "serves traffic"
        type == "mirroring" -> "mirror"
        type == "failover" && index == 0 -> "primary"
        type == "failover" && index == 1 -> "fallback"
        else -> null
    }

    fun usesShare(type: String): Boolean = type == "weighted" || type == "mirroring"

    fun maxRows(type: String): Int = if (type == "failover") 2 else Int.MAX_VALUE
}
