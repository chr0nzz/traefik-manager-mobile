package dev.chr0nzz.traefikmanager.data.model

import dev.chr0nzz.traefikmanager.R

data class ChannelField(
    val key: String,
    val label: UiText,
    val description: UiText,
    val placeholder: String = "",
    val secret: Boolean = false,
)

data class ChannelKind(
    val key: String,
    val label: UiText,
    val pickerLabel: UiText,
    val fields: List<ChannelField>,
    val basicAuth: Boolean = false,
) {
    val required: List<ChannelField> get() = fields
}

object ChannelKinds {

    private val url = ChannelField("url", UiText.Raw("URL"), UiText.Raw(""))

    val all: List<ChannelKind> = listOf(
        ChannelKind(
            key = "discord",
            label = UiText.Raw("Discord"),
            pickerLabel = UiText.Raw("Discord"),
            fields = listOf(
                url.copy(
                    label = UiText.of(R.string.webhook_url),
                    description = UiText.of(R.string.channels_help_where_notifications_are_delivered),
                    placeholder = "https://discord.com/api/webhooks/...",
                ),
            ),
        ),
        ChannelKind(
            key = "slack",
            label = UiText.Raw("Slack"),
            pickerLabel = UiText.Raw("Slack"),
            fields = listOf(
                url.copy(
                    label = UiText.of(R.string.webhook_url),
                    description = UiText.of(R.string.channels_help_incoming_webhook_created_in_your),
                    placeholder = "https://hooks.slack.com/services/...",
                ),
            ),
        ),
        ChannelKind(
            key = "ntfy",
            label = UiText.Raw("ntfy"),
            pickerLabel = UiText.Raw("ntfy.sh / self-hosted ntfy"),
            fields = listOf(
                url.copy(
                    description = UiText.of(R.string.channels_help_full_topic_url_on_ntfy),
                    placeholder = "https://ntfy.sh/my-topic",
                ),
            ),
            basicAuth = true,
        ),
        ChannelKind(
            key = "generic",
            label = UiText.of(R.string.channels_kind_generic),
            pickerLabel = UiText.of(R.string.channels_kind_generic),
            fields = listOf(
                url.copy(
                    description = UiText.of(R.string.channels_help_receives_a_json_body_you),
                    placeholder = "https://example.com/hooks/traefik",
                ),
            ),
            basicAuth = true,
        ),
        ChannelKind(
            key = "gotify",
            label = UiText.Raw("Gotify"),
            pickerLabel = UiText.Raw("Gotify"),
            fields = listOf(
                url.copy(
                    label = UiText.of(R.string.channels_field_server_url),
                    description = UiText.of(R.string.channels_help_base_url_of_your_gotify),
                    placeholder = "https://gotify.example.com",
                ),
                ChannelField(
                    key = "token",
                    label = UiText.of(R.string.channels_field_app_token),
                    description = UiText.of(R.string.channels_help_application_token_from_gotify_stored),
                    secret = true,
                ),
            ),
        ),
        ChannelKind(
            key = "pushover",
            label = UiText.Raw("Pushover"),
            pickerLabel = UiText.Raw("Pushover"),
            fields = listOf(
                ChannelField(
                    key = "token",
                    label = UiText.of(R.string.channels_field_app_token),
                    description = UiText.of(R.string.channels_help_application_token_from_your_pushover),
                    secret = true,
                ),
                ChannelField(
                    key = "token2",
                    label = UiText.of(R.string.channels_field_user_key),
                    description = UiText.of(R.string.channels_help_your_pushover_user_or_group),
                    secret = true,
                ),
            ),
        ),
        ChannelKind(
            key = "pushbullet",
            label = UiText.Raw("Pushbullet"),
            pickerLabel = UiText.Raw("Pushbullet"),
            fields = listOf(
                ChannelField(
                    key = "token",
                    label = UiText.of(R.string.channels_field_access_token),
                    description = UiText.of(R.string.channels_help_access_token_from_your_pushbullet),
                    secret = true,
                ),
            ),
        ),
        ChannelKind(
            key = "unifiedpush",
            label = UiText.of(R.string.channels_kind_mobile),
            pickerLabel = UiText.of(R.string.channels_kind_mobile),
            fields = listOf(
                url.copy(
                    label = UiText.of(R.string.channels_field_endpoint),
                    description = UiText.of(R.string.channels_help_registered_by_the_traefik_manager),
                ),
            ),
        ),
        ChannelKind(
            key = "telegram",
            label = UiText.Raw("Telegram"),
            pickerLabel = UiText.Raw("Telegram"),
            fields = listOf(
                ChannelField(
                    key = "token",
                    label = UiText.of(R.string.channels_field_bot_token),
                    description = UiText.of(R.string.channels_help_token_issued_by_botfather_stored),
                    secret = true,
                ),
                ChannelField(
                    key = "token2",
                    label = UiText.of(R.string.channels_field_chat_id),
                    description = UiText.of(R.string.channels_help_target_chat_group_or_channel),
                    placeholder = "-1001234567890",
                ),
            ),
        ),
    )

    fun of(key: String): ChannelKind? = all.firstOrNull { it.key == key }

    fun label(key: String): UiText = of(key)?.label ?: UiText.Raw(key)

    val categories: List<Pair<String, UiText>> = listOf(
        "config" to UiText.of(R.string.channels_category_config),
        "backup" to UiText.of(R.string.nav_backups),
        "security" to UiText.of(R.string.appearance_security),
        "traefik" to UiText.Raw("Traefik"),
        "certs" to UiText.of(R.string.nav_certificates),
        "crowdsec" to UiText.Raw("CrowdSec"),
        "agent" to UiText.of(R.string.channels_category_agents),
        "update" to UiText.of(R.string.channels_category_updates),
    )

    val severities: List<Pair<String, UiText>> = listOf(
        "info" to UiText.of(R.string.channels_severity_info),
        "success" to UiText.of(R.string.services_health_ok),
        "warning" to UiText.of(R.string.services_health_warning),
        "error" to UiText.of(R.string.common_label_error),
    )

    val digests: List<Pair<String, UiText>> = listOf(
        "immediate" to UiText.of(R.string.channels_digest_immediate),
        "hourly" to UiText.of(R.string.channels_digest_hourly),
        "daily" to UiText.of(R.string.channels_digest_daily),
    )

    fun categoryLabel(key: String): UiText = categories.firstOrNull { it.first == key }?.second ?: UiText.Raw(key)

    fun severityLabel(key: String): UiText = severities.firstOrNull { it.first == key }?.second ?: UiText.Raw(key)
}

fun NotificationChannel.missingFields(): List<UiText> {
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

fun NotificationChannel.summary(): UiText {
    val parts = mutableListOf<UiText>()
    val known = categories.filter { key -> ChannelKinds.categories.any { it.first == key } }
    parts += if (known.isEmpty() || known.size == ChannelKinds.categories.size) {
        UiText.of(R.string.channels_summary_all)
    } else {
        UiText.join(known.map { ChannelKinds.categoryLabel(it) }, ", ")
    }
    if (minSeverity != "info") {
        parts += UiText.of(R.string.channels_summary_severity, ChannelKinds.severityLabel(minSeverity))
    }
    when (digest) {
        "hourly" -> parts += UiText.of(R.string.channels_summary_hourly)
        "daily" -> parts += UiText.of(R.string.channels_summary_daily)
    }
    if (quietHours.isNotBlank()) {
        parts += UiText.of(
            if (breakThrough) R.string.channels_summary_quiet_break else R.string.channels_summary_quiet,
            quietHours,
        )
    }
    return UiText.join(parts)
}
