package dev.chr0nzz.traefikmanager.data.model

/** Turns a distributor's refusal into something worth reading. */
object PushFailure {

    fun describe(reason: String): String = when (reason.uppercase()) {
        "NETWORK" -> "No network when registering for push. It will be retried."
        "ACTION_REQUIRED" -> "The distributor app needs attention before it will register this app."
        "VAPID_REQUIRED" -> "This distributor requires a VAPID key, which this app does not use."
        else -> "The distributor could not register this app for push."
    }
}
