package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackendServer(
    val scheme: String = "http",
    val host: String = "",
    val port: String = "",
    val kind: String = BackendKind.ADDRESS,
    val serviceName: String = "",
    val share: String = "1",
) {
    val isService: Boolean get() = kind == BackendKind.SERVICE

    val filled: Boolean get() = if (isService) serviceName.isNotBlank() else host.isNotBlank()
}

@Serializable
data class BackendWire(
    val scheme: String = "http",
    val host: String = "",
    val port: String = "",
)

object BackendKind {
    const val ADDRESS = "address"
    const val SERVICE = "service"
}

@Serializable
data class BackendChildPayload(
    val kind: String,
    val name: String = "",
    val address: String = "",
    val scheme: String = "http",
    val weight: Int = 1,
    val percent: Int = 0,
)

enum class BackendMode { Manual, ExistingService }

@Serializable
data class StickyConfig(
    val enabled: Boolean = false,
    val cookieName: String = "",
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
)

@Serializable
data class HealthCheckConfig(
    val enabled: Boolean = false,
    val path: String = "",
    val interval: String = "",
    val timeout: String = "",
)

@Serializable
data class BackendsPayload(
    val servers: List<BackendWire> = emptyList(),
    val children: List<BackendChildPayload>? = null,
    val compositeType: String? = null,
    val sticky: StickyConfig? = null,
    val healthCheck: HealthCheckConfig? = null,
    val priority: Int? = null,
)

enum class RouteProtocol(val wire: String) {
    Http("http"),
    Tcp("tcp"),
    Udp("udp"),
    ;

    companion object {
        fun from(value: String): RouteProtocol =
            entries.firstOrNull { it.wire == value.lowercase() } ?: Http
    }
}

enum class TcpTlsMode { None, Terminate, Passthrough }

data class HeadersPresetForm(
    val present: Boolean = false,
    val enabled: Boolean = false,
    val custom: Boolean = false,
    val perms: Map<String, String> = HeadersPresetDefaults.perms,
    val hsts: Boolean = true,
    val nosniff: Boolean = true,
    val frameDeny: Boolean = true,
    val referrer: String = HeadersPresetDefaults.REFERRER,
)

object HeadersPresetDefaults {
    val FEATURES = listOf(
        "geolocation", "camera", "microphone", "fullscreen", "autoplay",
        "payment", "usb", "display-capture", "accelerometer", "gyroscope", "magnetometer",
    )
    private val SELF_BY_DEFAULT = setOf("geolocation", "camera", "microphone", "fullscreen", "autoplay")
    const val REFERRER = "strict-origin-when-cross-origin"
    val REFERRER_VALUES = listOf(
        "no-referrer",
        "strict-origin-when-cross-origin",
        "same-origin",
        "strict-origin",
        "origin-when-cross-origin",
    )
    val PERM_VALUES = listOf("self", "all", "block")
    val perms: Map<String, String> =
        FEATURES.associateWith { if (it in SELF_BY_DEFAULT) "self" else "block" }
}

data class RouteForm(
    val name: String = "",
    val protocol: RouteProtocol = RouteProtocol.Http,
    val subdomain: String = "",
    val domains: List<String> = emptyList(),
    val advancedRule: Boolean = false,
    val httpRule: String = "",
    val tcpRule: String = "",
    val entryPoints: List<String> = emptyList(),
    val middlewares: List<String> = emptyList(),
    val passHostHeader: Boolean = true,
    val insecureSkipVerify: Boolean = false,
    val certResolver: String = "",
    val tlsEnabled: Boolean = true,
    val tcpTlsMode: TcpTlsMode = TcpTlsMode.None,
    val wildcardEnabled: Boolean = false,
    val tlsMainDomain: String = "",
    val tlsSans: List<String> = emptyList(),
    val tlsOptionsProfile: String = "",
    val backends: List<BackendServer> = listOf(BackendServer()),
    val sticky: StickyConfig = StickyConfig(),
    val healthCheck: HealthCheckConfig = HealthCheckConfig(),
    val priority: Int? = null,
    val streamingPresent: Boolean = false,
    val streamingEnabled: Boolean = false,
    val headersPreset: HeadersPresetForm = HeadersPresetForm(),
    val backendMode: BackendMode = BackendMode.Manual,
    val serviceRef: String = "",
    val serviceType: String = "loadBalancer",
    val serviceOwned: Boolean = false,
    val compositeType: String = LOAD_BALANCER,
    val wasComposite: Boolean = false,
    val configFile: String = "",
    val isEdit: Boolean = false,
    val originalId: String = "",
    val originalName: String = "",
) {
    val usesSharedService: Boolean get() = backendMode == BackendMode.ExistingService

    val isManagedService: Boolean get() = serviceType == LOAD_BALANCER || serviceOwned

    val effectivePassHostHeader: Boolean get() = if (streamingEnabled) true else passHostHeader

    val validationError: String?
        get() = when {
            name.isBlank() -> "A route name is required."
            usesSharedService && serviceRef.isBlank() ->
                "Pick the service this route points at."
            usesSharedService -> null
            !isManagedService -> null
            backends.any { it.isService && it.serviceName.isBlank() } ->
                "Pick a service for every service backend."
            backends.none { it.filled } ->
                "A backend host is required."
            protocol != RouteProtocol.Http && backends.any { it.host.isNotBlank() && it.port.isBlank() } ->
                "${protocol.wire.uppercase()} routes need a backend port."
            else -> null
        }

    val isValid: Boolean get() = validationError == null

    companion object {
        const val CERT_RESOLVER_DISABLED = "__disabled__"
        const val CERT_RESOLVER_NONE = "__none__"
        const val LOAD_BALANCER = "loadBalancer"

        val compositeTypes: List<Pair<String, String>> = listOf(
            LOAD_BALANCER to "Load balanced",
            "weighted" to "Weighted",
            "mirroring" to "Mirroring",
            "failover" to "Failover",
        )

        fun combineHint(type: String): String = when (type) {
            "weighted" -> "Traffic is split by weight."
            "mirroring" -> "The first backend serves. The rest get a copy."
            "failover" -> "The first backend serves. The second takes over if it fails."
            else -> "Traffic is load balanced across every backend."
        }
    }
}

object RouteFormEncoder {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    fun fields(form: RouteForm, agentId: String?): List<Pair<String, String>> = buildList {
        add("serviceName" to form.name.trim())
        add("protocol" to form.protocol.wire)
        add("isEdit" to form.isEdit.toString())
        add("originalId" to form.originalId)
        add("configFile" to form.configFile)
        add("agent_id" to agentId.orEmpty())

        val manualBackends = !form.usesSharedService && form.isManagedService
        val first = form.backends.firstOrNull().takeIf { manualBackends }
        addAll(indexed("targetIp", form.protocol, first?.host.orEmpty()))
        addAll(indexed("targetPort", form.protocol, first?.port.orEmpty()))

        add("entryPoints" to if (form.protocol == RouteProtocol.Http) form.entryPoints.joinToString(",") else "")
        add("entryPoints" to if (form.protocol == RouteProtocol.Tcp) form.entryPoints.joinToString(",") else "")
        if (form.protocol == RouteProtocol.Udp) {
            add("udpEntryPoint" to form.entryPoints.firstOrNull().orEmpty())
        }

        add("certResolver" to if (form.protocol == RouteProtocol.Http) httpResolver(form) else "")
        add("certResolver" to if (form.protocol == RouteProtocol.Tcp) tcpResolver(form) else "")

        if (form.usesSharedService) add("serviceRef" to form.serviceRef)

        when (form.protocol) {
            RouteProtocol.Http -> {
                add("subdomain" to if (form.advancedRule) "" else form.subdomain.trim())
                if (!form.advancedRule) {
                    form.domains.filter { it.isNotBlank() }.forEach { add("domains" to it.trim()) }
                }
                add("httpRule" to if (form.advancedRule) form.httpRule.trim() else "")
                add("middlewares" to form.middlewares.joinToString(", "))
                add("scheme" to (form.backends.firstOrNull()?.scheme ?: "http"))
                add("passHostHeader" to form.effectivePassHostHeader.toString())
                add("insecureSkipVerify" to form.insecureSkipVerify.toString())
                add("tlsOptionsProfile" to form.tlsOptionsProfile)

                if (form.wildcardEnabled && form.tlsMainDomain.isNotBlank()) {
                    add("tlsWildcardMain" to form.tlsMainDomain.trim())
                    add("tlsWildcardSans" to form.tlsSans.joinToString("\n"))
                }

                add("streamingPresetPresent" to form.streamingPresent.toString())
                if (form.streamingPresent) {
                    add("streamingPresetEnabled" to form.streamingEnabled.toString())
                }

                add("headersPresetPresent" to form.headersPreset.present.toString())
                if (form.headersPreset.present) {
                    add("headersPresetEnabled" to form.headersPreset.enabled.toString())
                    add("headersPresetCustom" to form.headersPreset.custom.toString())
                    if (form.headersPreset.enabled && !form.headersPreset.custom) {
                        HeadersPresetDefaults.FEATURES.forEach { feature ->
                            add("hp_perm_$feature" to (form.headersPreset.perms[feature] ?: "block"))
                        }
                        add("hp_hsts" to form.headersPreset.hsts.toString())
                        add("hp_nosniff" to form.headersPreset.nosniff.toString())
                        add("hp_frameDeny" to form.headersPreset.frameDeny.toString())
                        add("hp_referrer" to form.headersPreset.referrer)
                    }
                }

                if (manualBackends) {
                    add("backendsJsonHttp" to json.encodeToString(backendsPayload(form)))
                }
            }
            RouteProtocol.Tcp -> {
                add("tcpRule" to form.tcpRule.trim().ifEmpty { "HostSNI(`*`)" })
                add("subdomain" to "")
                add("middlewaresTcp" to form.middlewares.joinToString(", "))
                add("useTls" to (form.tcpTlsMode != TcpTlsMode.None).toString())
                add("tlsPassthrough" to (form.tcpTlsMode == TcpTlsMode.Passthrough).toString())
                if (manualBackends) {
                    add("backendsJsonTcp" to json.encodeToString(backendsPayload(form)))
                }
            }
            RouteProtocol.Udp -> {
                if (manualBackends) {
                    add("backendsJsonUdp" to json.encodeToString(backendsPayload(form)))
                }
            }
        }
    }

    private fun httpResolver(form: RouteForm): String = when {
        !form.tlsEnabled -> RouteForm.CERT_RESOLVER_DISABLED
        form.certResolver.isBlank() -> RouteForm.CERT_RESOLVER_NONE
        else -> form.certResolver
    }

    private fun tcpResolver(form: RouteForm): String = when {
        form.tcpTlsMode != TcpTlsMode.Terminate -> RouteForm.CERT_RESOLVER_NONE
        form.certResolver.isBlank() -> RouteForm.CERT_RESOLVER_NONE
        else -> form.certResolver
    }

    private fun backendsPayload(form: RouteForm) = BackendsPayload(
        servers = form.backends
            .filter { !it.isService && it.host.isNotBlank() }
            .map { BackendWire(scheme = it.scheme, host = it.host, port = it.port) },
        children = compositeChildren(form),
        compositeType = compositeChildren(form)
            ?.takeIf { it.isNotEmpty() }
            ?.let { form.compositeType.takeIf { type -> type != RouteForm.LOAD_BALANCER } ?: "weighted" },
        sticky = form.sticky.takeIf { it.enabled && form.protocol == RouteProtocol.Http },
        healthCheck = form.healthCheck.takeIf { it.enabled && form.protocol == RouteProtocol.Http },
        priority = form.priority?.takeIf { it != 0 && form.protocol != RouteProtocol.Udp },
    )

    private fun compositeChildren(form: RouteForm): List<BackendChildPayload>? {
        if (form.protocol != RouteProtocol.Http) return null
        val rows = form.backends.filter { it.filled }
        if (form.compositeType == RouteForm.LOAD_BALANCER && !rows.any { it.isService }) {
            return if (form.wasComposite) emptyList() else null
        }
        if (rows.isEmpty()) return null
        return rows.map { row ->
            val share = row.share.trim().toIntOrNull()
                ?: if (form.compositeType == "mirroring") 0 else 1
            if (row.isService) {
                BackendChildPayload(
                    kind = "service",
                    name = row.serviceName.trim(),
                    weight = share,
                    percent = share,
                )
            } else {
                BackendChildPayload(
                    kind = "manual",
                    address = listOf(row.host.trim(), row.port.trim())
                        .filter { it.isNotBlank() }
                        .joinToString(":"),
                    scheme = row.scheme,
                    weight = share,
                    percent = share,
                )
            }
        }
    }

    private fun indexed(
        field: String,
        protocol: RouteProtocol,
        value: String,
    ): List<Pair<String, String>> {
        val index = when (protocol) {
            RouteProtocol.Http -> 0
            RouteProtocol.Tcp -> 1
            RouteProtocol.Udp -> 2
        }
        return (0..2).map { position -> field to if (position == index) value else "" }
    }
}
