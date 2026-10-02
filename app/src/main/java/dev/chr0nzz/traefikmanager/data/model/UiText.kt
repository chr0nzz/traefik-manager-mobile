package dev.chr0nzz.traefikmanager.data.model

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import dev.chr0nzz.traefikmanager.R

sealed interface UiText {

    fun resolve(resources: Resources): String

    data class Raw(val value: String) : UiText {
        override fun resolve(resources: Resources): String = value
    }

    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        override fun resolve(resources: Resources): String =
            if (args.isEmpty()) resources.getString(id) else resources.getString(id, *resolved(args, resources))
    }

    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText {
        override fun resolve(resources: Resources): String =
            resources.getQuantityString(id, count, *resolved(args.ifEmpty { listOf(count) }, resources))
    }

    data class Join(val parts: List<UiText>, val separator: String = SEPARATOR) : UiText {
        override fun resolve(resources: Resources): String = parts.joinToString(separator) { it.resolve(resources) }
    }

    companion object {
        const val SEPARATOR = " · "

        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())

        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = Plural(id, count, args.toList())

        fun join(parts: List<UiText>, separator: String = SEPARATOR): UiText = Join(parts, separator)

        fun messageOr(message: String?, @StringRes fallback: Int): UiText =
            message?.takeIf { it.isNotBlank() }?.let(::Raw) ?: Res(fallback)

        fun http(@StringRes what: Int, code: Int): UiText = Res(R.string.common_error_http, listOf(Res(what), code))

        fun failure(throwable: Throwable, @StringRes fallback: Int): UiText =
            (throwable as? LocalizedException)?.text ?: messageOr(throwable.message, fallback)

        private fun resolved(args: List<Any>, resources: Resources): Array<Any> =
            args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()
    }
}

class LocalizedException(val text: UiText) : Exception()

fun fail(message: String?, @StringRes fallback: Int): Nothing =
    throw LocalizedException(UiText.messageOr(message, fallback))

fun failHttp(message: String?, @StringRes fallback: Int, code: Int): Nothing =
    throw LocalizedException(message?.takeIf { it.isNotBlank() }?.let(UiText::Raw) ?: UiText.http(fallback, code))
