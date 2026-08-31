package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientIpDiagnostic(
    @SerialName("effective_ip") val effectiveIp: String = "",
    @SerialName("effective_class") val effectiveClass: String = "",
    @SerialName("socket_peer") val socketPeer: String = "",
    @SerialName("socket_peer_class") val socketPeerClass: String = "",
    val headers: Map<String, String> = emptyMap(),
    @SerialName("forwarded_for_chain") val forwardedForChain: List<String> = emptyList(),
    @SerialName("proxy_hops") val proxyHops: Int = 0,
    val classes: Map<String, String> = emptyMap(),
) {
    companion object {
        val HEADER_ORDER = listOf(
            "X-Forwarded-For",
            "X-Real-IP",
            "CF-Connecting-IP",
            "X-Forwarded-Proto",
            "X-Forwarded-Host",
        )
    }
}
