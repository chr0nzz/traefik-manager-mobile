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
