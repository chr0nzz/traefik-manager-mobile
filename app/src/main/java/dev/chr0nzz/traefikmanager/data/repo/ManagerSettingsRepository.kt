package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.TestConnectionRequest
import dev.chr0nzz.traefikmanager.data.model.TestConnectionResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class ManagerSettingsRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val serverSettingsRepository: ServerSettingsRepository,
) {

    suspend fun raw(): JsonObject = apiProvider.api().settingsRaw()

    suspend fun patch(changes: Map<String, JsonElement>): JsonObject {
        val api = apiProvider.api()
        val current = api.settingsRaw()
        val merged = JsonObject(current.toMutableMap().apply { putAll(changes) })
        val response = api.saveSettings(merged)
        if (!response.success) error(response.error ?: "Could not save the settings")
        serverSettingsRepository.refresh()
        return merged
    }

    suspend fun patch(vararg changes: Pair<String, JsonElement>): JsonObject = patch(changes.toMap())

    suspend fun testConnection(url: String, user: String, password: String): TestConnectionResult =
        apiProvider.api().testTraefikConnection(TestConnectionRequest(url.trim(), user.trim(), password))

    companion object {
        fun string(value: String): JsonElement = JsonPrimitive(value)

        fun flag(value: Boolean): JsonElement = JsonPrimitive(value)

        fun JsonObject.text(key: String): String =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

        fun JsonObject.bool(key: String): Boolean =
            (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
    }
}
