package dev.chr0nzz.traefikmanager.data.model

/** One credential slot on a channel, and how it is asked for. */
data class ChannelField(
    val key: String,
    val label: String,
    val description: String,
    val placeholder: String = "",
    val secret: Boolean = false,
)

/**
 * What each kind of channel needs. The labels follow the web's Settings - Notifications, so a
 * channel set up on a phone reads the same as one set up in a browser.
 */
data class ChannelKind(
    val key: String,
    val label: String,
    val pickerLabel: String,
    val fields: List<ChannelField>,
    val basicAuth: Boolean = false,
) {
    val required: List<ChannelField> get() = fields
}

object ChannelKinds {

    private val url = ChannelField("url", "URL", "")

    val all: List<ChannelKind> = listOf(
        ChannelKind(
            key = "discord",
            label = "Discord",
            pickerLabel = "Discord",
            fields = listOf(
                url.copy(
                    label = "Webhook URL",
                    description = "Where notifications are delivered.",
                    placeholder = "https://discord.com/api/webhooks/...",
                ),
            ),
        ),
        ChannelKind(
            key = "slack",
            label = "Slack",
            pickerLabel = "Slack",
            fields = listOf(
                url.copy(
                    label = "Webhook URL",
                    description = "Incoming webhook created in your Slack workspace.",
                    placeholder = "https://hooks.slack.com/services/...",
                ),
            ),
        ),
        ChannelKind(
            key = "ntfy",
            label = "ntfy",
            pickerLabel = "ntfy.sh / self-hosted ntfy",
            fields = listOf(
                url.copy(
                    description = "Full topic URL on ntfy.sh or your own server.",
                    placeholder = "https://ntfy.sh/my-topic",
                ),
            ),
            basicAuth = true,
        ),
        ChannelKind(
            key = "generic",
            label = "Generic JSON",
            pickerLabel = "Generic JSON",
            fields = listOf(
                url.copy(
                    description = "Receives a JSON body you can shape downstream.",
                    placeholder = "https://example.com/hooks/traefik",
                ),
            ),
            basicAuth = true,
        ),
        ChannelKind(
            key = "gotify",
            label = "Gotify",
            pickerLabel = "Gotify",
            fields = listOf(
                url.copy(
                    label = "Server URL",
                    description = "Base URL of your Gotify server.",
                    placeholder = "https://gotify.example.com",
                ),
                ChannelField(
                    key = "token",
                    label = "App Token",
                    description = "Application token from Gotify. Stored encrypted.",
                    secret = true,
                ),
            ),
        ),
        ChannelKind(
            key = "pushover",
            label = "Pushover",
            pickerLabel = "Pushover",
            fields = listOf(
                ChannelField(
                    key = "token",
                    label = "App Token",
                    description = "Application token from your Pushover app. Stored encrypted.",
                    secret = true,
                ),
                ChannelField(
                    key = "token2",
                    label = "User Key",
                    description = "Your Pushover user or group key. Stored encrypted.",
                    secret = true,
                ),
            ),
        ),
        ChannelKind(
            key = "pushbullet",
            label = "Pushbullet",
            pickerLabel = "Pushbullet",
            fields = listOf(
                ChannelField(
                    key = "token",
                    label = "Access Token",
                    description = "Access token from your Pushbullet account. Stored encrypted.",
                    secret = true,
                ),
            ),
        ),
        ChannelKind(
            key = "unifiedpush",
            label = "Mobile app",
            pickerLabel = "Mobile app",
            fields = listOf(
                url.copy(
                    label = "Device endpoint",
                    description = "Registered by the Traefik Manager app on your phone. " +
                        "Editing it stops push to that device.",
                ),
            ),
        ),
        ChannelKind(
            key = "telegram",
            label = "Telegram",
            pickerLabel = "Telegram",
            fields = listOf(
                ChannelField(
                    key = "token",
                    label = "Bot Token",
                    description = "Token issued by BotFather. Stored encrypted.",
                    secret = true,
                ),
                ChannelField(
                    key = "token2",
                    label = "Chat ID",
                    description = "Target chat, group or channel to post into.",
                    placeholder = "-1001234567890",
                ),
            ),
        ),
    )

    fun of(key: String): ChannelKind? = all.firstOrNull { it.key == key }

    fun label(key: String): String = of(key)?.label ?: key

    val categories: List<Pair<String, String>> = listOf(
        "config" to "Config",
        "backup" to "Backups",
        "security" to "Security",
        "traefik" to "Traefik",
        "certs" to "Certificates",
        "crowdsec" to "CrowdSec",
        "agent" to "Agents",
        "update" to "Updates",
    )

    val severities: List<Pair<String, String>> = listOf(
        "info" to "Info",
        "success" to "Success",
        "warning" to "Warning",
        "error" to "Error",
    )

    val digests: List<Pair<String, String>> = listOf(
        "immediate" to "Immediate",
        "hourly" to "Hourly",
        "daily" to "Daily",
    )

    fun categoryLabel(key: String): String = categories.firstOrNull { it.first == key }?.second ?: key

    fun severityLabel(key: String): String = severities.firstOrNull { it.first == key }?.second ?: key
}

/** The labels of the credential slots this channel leaves blank. */
fun NotificationChannel.missingFields(): List<String> {
    val kind = ChannelKinds.of(kind) ?: return emptyList()
    return kind.required.filter { field -> valueOf(field.key).isBlank() }.map { it.label }
}

fun NotificationChannel.valueOf(key: String): String = when (key) {
    "url" -> url
    "token" -> token
    "token2" -> token2
    "username" -> username
    "password" -> password
    else -> ""
}

/**
 * The one line under a channel's name: what it takes, how loudly, and when. Mirrors the web's
 * summary so the two read alike.
 */
fun NotificationChannel.summary(): String {
    val parts = mutableListOf<String>()
    val known = categories.filter { key -> ChannelKinds.categories.any { it.first == key } }
    parts += if (known.isEmpty() || known.size == ChannelKinds.categories.size) {
        "All categories"
    } else {
        known.joinToString(", ") { ChannelKinds.categoryLabel(it) }
    }
    if (minSeverity != "info") parts += "${ChannelKinds.severityLabel(minSeverity)} and above"
    when (digest) {
        "hourly" -> parts += "Hourly digest"
        "daily" -> parts += "Daily digest"
    }
    if (quietHours.isNotBlank()) {
        parts += "Quiet $quietHours" + if (breakThrough) ", errors break through" else ""
    }
    return parts.joinToString(" · ")
}
