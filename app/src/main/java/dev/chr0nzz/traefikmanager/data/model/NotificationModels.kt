package dev.chr0nzz.traefikmanager.data.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A manager notification. There is no id and no read state: the only handle is [ts], which is
 * the server's local clock formatted without a timezone, and which can repeat within a second.
 */
@Serializable
data class DeleteNotificationRequest(val ts: String)

@Serializable
data class TmNotification(
    val ts: String = "",
    val type: String = "info",
    val msg: String = "",
    val category: String = "",
) {
    val severity: NotificationSeverity
        get() = when (type.lowercase()) {
            "success" -> NotificationSeverity.Success
            "warning" -> NotificationSeverity.Warning
            "error" -> NotificationSeverity.Error
            else -> NotificationSeverity.Info
        }

    /** Rendered verbatim in server time: there is no endpoint that reports the server's zone. */
    val stamp: String
        get() = runCatching {
            LocalDateTime.parse(ts, PARSER).format(DISPLAY)
        }.getOrDefault(ts)

    private companion object {
        val PARSER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val DISPLAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm:ss")
    }
}

enum class NotificationSeverity { Success, Info, Warning, Error }

@Serializable
data class WebhookTestRequest(
    val url: String,
    @SerialName("webhook_type") val webhookType: String = "discord",
    val username: String = "",
    val password: String = "",
)

@Serializable
data class WebhookTestResult(
    val ok: Boolean = false,
    val error: String? = null,
)

/**
 * A notification destination. Secrets read back as `***` and the URL of a kind that carries its
 * token in the path reads back masked, so a channel round-tripped unchanged keeps what is stored.
 */
@Serializable
data class NotificationChannel(
    val id: String = "",
    val name: String = "",
    val kind: String = "",
    val enabled: Boolean = true,
    val url: String = "",
    val token: String = "",
    val token2: String = "",
    val username: String = "",
    val password: String = "",
    val categories: List<String> = emptyList(),
    @SerialName("min_severity") val minSeverity: String = "info",
    val digest: String = "immediate",
    @SerialName("quiet_hours") val quietHours: String = "",
    @SerialName("break_through") val breakThrough: Boolean = false,
)

/**
 * What a save sends. No property has a default, so every field is on the wire: the server treats
 * an absent key as "keep what you had", which would silently drop a cleared field.
 */
@Serializable
data class ChannelPayload(
    val name: String,
    val kind: String,
    val enabled: Boolean,
    val url: String,
    val token: String,
    val token2: String,
    val username: String,
    val password: String,
    val categories: List<String>,
    @SerialName("min_severity") val minSeverity: String,
    val digest: String,
    @SerialName("quiet_hours") val quietHours: String,
    @SerialName("break_through") val breakThrough: Boolean,
)

fun NotificationChannel.toPayload() = ChannelPayload(
    name = name,
    kind = kind,
    enabled = enabled,
    url = url,
    token = token,
    token2 = token2,
    username = username,
    password = password,
    categories = categories,
    minSeverity = minSeverity,
    digest = digest,
    quietHours = quietHours,
    breakThrough = breakThrough,
)

@Serializable
data class ChannelListResponse(val channels: List<NotificationChannel> = emptyList())

@Serializable
data class ChannelSaveResponse(
    val ok: Boolean = false,
    val channel: NotificationChannel? = null,
    val error: String? = null,
)

@Serializable
data class ChannelTestResult(
    val ok: Boolean = false,
    val detail: String = "",
    val error: String? = null,
)
