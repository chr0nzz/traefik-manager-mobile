package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CertVerdict(
    val main: String = "",
    val resolver: String = "",
    val source: String = "",
    val expired: Boolean = false,
    val unused: Boolean = false,
    val orphaned: Boolean = false,
    val why: String = "",
) {
    val key: String get() = "$resolver|$main"
}

@Serializable
data class CertUsage(
    val certs: List<CertVerdict> = emptyList(),
    @SerialName("unused_known") val unusedKnown: Boolean = false,
    val why: String = "",
    @SerialName("resolvers_known") val resolversKnown: Boolean = false,
) {
    fun verdict(resolver: String, main: String): CertVerdict? = certs.firstOrNull { it.key == "$resolver|$main" }
}

@Serializable
data class CertManageState(
    val available: Boolean = false,
    val writable: Boolean = false,
    @SerialName("restart_method") val restartMethod: String = "",
    val reason: String = "",
    val paths: List<String> = emptyList(),
)

@Serializable
data class CertRef(
    val resolver: String,
    val main: String,
)

@Serializable
data class CertDeleteRequest(
    val server: String = "",
    val certs: List<CertRef>,
)

@Serializable
data class CertDeleteResponse(
    val ok: Boolean = false,
    val removed: Int = 0,
    val backup: String = "",
    val restarted: Boolean = false,
    @SerialName("restart_error") val restartError: String = "",
    val partial: Boolean = false,
    val error: String? = null,
)
