package dev.chr0nzz.traefikmanager.data.model

import dev.chr0nzz.traefikmanager.R

object PushFailure {

    const val NOTIFICATIONS_BLOCKED = "notifications_blocked"

    const val OLD_SERVER = "OLD_SERVER"

    private const val PREFIX = "push_failure:"

    fun code(reason: String): String = PREFIX + reason.uppercase()

    fun text(stored: String): UiText = when {
        stored == NOTIFICATIONS_BLOCKED -> UiText.of(R.string.push_error_blocked)
        !stored.startsWith(PREFIX) -> UiText.Raw(stored)
        else -> UiText.of(
            when (stored.removePrefix(PREFIX)) {
                "NETWORK" -> R.string.push_error_network
                "ACTION_REQUIRED" -> R.string.push_error_action
                "VAPID_REQUIRED" -> R.string.push_error_vapid
                OLD_SERVER -> R.string.push_error_old_server
                else -> R.string.push_error_register
            },
        )
    }
}
