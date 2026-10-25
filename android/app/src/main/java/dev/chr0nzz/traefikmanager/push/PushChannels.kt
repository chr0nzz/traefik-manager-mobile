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

@Singleton
class PushChannels @Inject constructor(
    private val apiProvider: ApiProvider,
    private val channels: NotificationChannelsRepository,
    private val preferencesStore: PreferencesStore,
) {

    val deviceName: String
        get() = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replaceFirstChar { it.uppercase() }
            .ifBlank { "Android device" }

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

    suspend fun remove() {
        val server = apiProvider.ready().baseUrl
        val id = preferencesStore.preferences.first().pushChannels[server] ?: return
        runCatching { channels.delete(id) }
        preferencesStore.setPushChannel(server, null)
    }

    suspend fun channelId(): String? {
        val server = runCatching { apiProvider.ready().baseUrl }.getOrNull() ?: return null
        return preferencesStore.preferences.first().pushChannels[server]
    }

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
