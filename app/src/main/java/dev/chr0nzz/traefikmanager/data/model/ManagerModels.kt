package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ManagerVersion(
    val version: String = "",
    val repo: String = "",
    @SerialName("static_config_configured") val staticConfigConfigured: Boolean = false,
)

@Serializable
data class ApiKeyStatus(
    val enabled: Boolean = false,
    val count: Int = 0,
    val keys: List<ApiKeyEntry> = emptyList(),
)

@Serializable
data class ApiKeyEntry(
    val name: String = "",
    val preview: String = "",
    @SerialName("created_at") val createdAt: String = "",
) {
    val revocable: Boolean get() = preview.isNotEmpty()
}

@Serializable
data class GenerateKeyRequest(@SerialName("device_name") val deviceName: String)

@Serializable
data class GenerateKeyResponse(
    val ok: Boolean = false,
    val key: String? = null,
    val error: String? = null,
)

@Serializable
data class RevokeKeyRequest(val preview: String)

@Serializable
data class OtpStatus(@SerialName("otp_enabled") val otpEnabled: Boolean = false)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
    @SerialName("confirm_password") val confirmPassword: String,
)

@Serializable
data class AuthActionResponse(
    val success: Boolean = false,
    val ok: Boolean = false,
    val error: String? = null,
) {
    val worked: Boolean get() = success || ok
}

@Serializable
data class TraefikVersion(
    @SerialName("Version") val version: String? = null,
    @SerialName("Codename") val codename: String? = null,
)

@Serializable
data class OkResponse(
    val ok: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)
