package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.ChannelPayload
import dev.chr0nzz.traefikmanager.data.model.NotificationChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException

/** What a test delivery reported, whichever way the server phrased it. */
data class ChannelTestOutcome(val ok: Boolean, val message: String)

/**
 * Notification channels, which only exist from manager 1.12.0. Older servers answer the listing
 * with a 404, and [supported] carries that so the settings screen can offer the single webhook
 * it used to instead of an empty list.
 */
@Singleton
class NotificationChannelsRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    serverScope: ServerScope,
) {

    private val _supported = MutableStateFlow<Boolean?>(null)
    val supported: StateFlow<Boolean?> = _supported.asStateFlow()

    init {
        serverScope.onServerChanged { _supported.value = null }
    }

    suspend fun load(): List<NotificationChannel> = try {
        apiProvider.api().notificationChannels().channels.also { _supported.value = true }
    } catch (exception: HttpException) {
        if (exception.code() == 404) {
            _supported.value = false
            emptyList()
        } else {
            error(message(exception) ?: "Could not read the channels (HTTP ${exception.code()})")
        }
    }

    suspend fun create(payload: ChannelPayload): NotificationChannel = try {
        val response = apiProvider.api().createNotificationChannel(payload)
        response.channel ?: error(response.error ?: "Could not save the channel")
    } catch (exception: HttpException) {
        error(message(exception) ?: "Could not save the channel (HTTP ${exception.code()})")
    }

    suspend fun update(id: String, payload: ChannelPayload): NotificationChannel = try {
        val response = apiProvider.api().updateNotificationChannel(id, payload)
        response.channel ?: error(response.error ?: "Could not save the channel")
    } catch (exception: HttpException) {
        error(message(exception) ?: "Could not save the channel (HTTP ${exception.code()})")
    }

    suspend fun delete(id: String) {
        try {
            apiProvider.api().deleteNotificationChannel(id)
        } catch (exception: HttpException) {
            error(message(exception) ?: "Could not remove the channel (HTTP ${exception.code()})")
        }
    }

    /** A refused delivery is an answer, not a failure, so a 400 reads the same as `ok: false`. */
    suspend fun test(id: String): ChannelTestOutcome = try {
        val result = apiProvider.api().testNotificationChannel(id)
        ChannelTestOutcome(
            ok = result.ok && result.error == null,
            message = result.error ?: result.detail,
        )
    } catch (exception: HttpException) {
        ChannelTestOutcome(
            ok = false,
            message = message(exception) ?: "The test failed (HTTP ${exception.code()})",
        )
    }

    private fun message(exception: HttpException): String? {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        if (body.isNullOrBlank()) return null
        val parsed = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        return (parsed["error"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    }
}
