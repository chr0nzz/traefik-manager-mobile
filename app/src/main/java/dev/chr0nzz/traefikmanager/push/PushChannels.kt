package dev.chr0nzz.traefikmanager.push

import android.os.Build
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.ChannelPayload
import dev.chr0nzz.traefikmanager.data.model.NotificationChannel
import dev.chr0nzz.traefikmanager.data.repo.NotificationChannelsRepository
import dev.chr0nzz.traefikmanager.data.store.PreferencesStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * This device's own channel on the server.
 *
 * Push works by pointing a Mobile app channel at the endpoint a UnifiedPush distributor handed
 * out. The channel is created through the same API the channel list uses, and its id is remembered
 * per server so the list can mark it and turning push off can remove it.
 */
@Singleton
class PushChannels @Inject constructor(
    private val apiProvider: ApiProvider,
    private val channels: NotificationChannelsRepository,
    private val preferencesStore: PreferencesStore,
) {

    /** What the channel is called on the server. The server trims this to 60 characters. */
    val deviceName: String
        get() = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replaceFirstChar { it.uppercase() }
            .ifBlank { "Android device" }

    /**
     * Point the active server at [endpoint], creating this device's channel or moving the existing
     * one. Returns the channel, or null when the server is too old to have channels at all.
     */
    suspend fun sync(endpoint: String): NotificationChannel? {
        if (endpoint.isBlank()) return null
        val server = apiProvider.ready().baseUrl
        val known = preferencesStore.preferences.first().pushChannels[server]
        val existing = known?.let { id -> channels.load().firstOrNull { it.id == id } }
        val payload = payload(endpoint, existing)
        val saved = if (existing == null) {
            channels.create(payload)
        } else {
            channels.update(existing.id, payload)
        }
        preferencesStore.setPushChannel(server, saved.id)
        preferencesStore.setPushEndpoint(endpoint)
        return saved
    }

    /** Remove this device's channel from the active server. */
    suspend fun remove() {
        val server = apiProvider.ready().baseUrl
        val id = preferencesStore.preferences.first().pushChannels[server] ?: return
        runCatching { channels.delete(id) }
        preferencesStore.setPushChannel(server, null)
    }

    /** The id of this device's channel on the active server, for the badge in the list. */
    suspend fun channelId(): String? {
        val server = runCatching { apiProvider.ready().baseUrl }.getOrNull() ?: return null
        return preferencesStore.preferences.first().pushChannels[server]
    }

    /**
     * A new channel takes every category at info, which is what someone turning on push expects.
     * An existing one keeps whatever filters the user has since set on it, and only its URL moves.
     */
    private fun payload(endpoint: String, existing: NotificationChannel?) = ChannelPayload(
        name = existing?.name?.takeIf { it.isNotBlank() } ?: deviceName,
        kind = "unifiedpush",
        enabled = existing?.enabled ?: true,
        url = endpoint,
        token = "",
        token2 = "",
        username = "",
        password = "",
        categories = existing?.categories ?: emptyList(),
        minSeverity = existing?.minSeverity ?: "info",
        digest = existing?.digest ?: "immediate",
        quietHours = existing?.quietHours.orEmpty(),
        breakThrough = existing?.breakThrough ?: false,
    )
}
