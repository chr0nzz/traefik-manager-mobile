package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.model.LocalizedException
import androidx.annotation.StringRes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import dev.chr0nzz.traefikmanager.data.model.UiText

class InUseException(
    val reason: UiText,
    val routers: List<String> = emptyList(),
    val parents: List<String> = emptyList(),
) : Exception()

object InUse {

    fun from(exception: HttpException, @StringRes fallback: Int): Throwable {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        val parsed = body
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val text = parsed?.let { obj ->
            listOf("error", "message")
                .firstNotNullOfOrNull { (obj[it] as? JsonPrimitive)?.content?.takeIf { m -> m.isNotBlank() } }
        }
        if (exception.code() != 409) {
            return LocalizedException(text?.let(UiText::Raw) ?: UiText.http(fallback, exception.code()))
        }
        return InUseException(
            reason = UiText.messageOr(text, fallback),
            routers = parsed.strings("inUseBy"),
            parents = parsed.strings("parents"),
        )
    }

    private fun JsonObject?.strings(key: String): List<String> =
        (this?.get(key) as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            .orEmpty()
}
