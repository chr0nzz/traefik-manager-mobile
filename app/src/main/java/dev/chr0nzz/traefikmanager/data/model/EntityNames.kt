package dev.chr0nzz.traefikmanager.data.model

import dev.chr0nzz.traefikmanager.R

object EntityNames {
    const val MAX_LENGTH = 100

    private val FORBIDDEN = Regex("[@/,:{}\\u0000-\\u001f\\u007f]")
    private val RESERVED = setOf(".", "..")

    fun problem(name: String): UiText? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> UiText.of(R.string.names_error_empty)
            trimmed in RESERVED -> UiText.of(R.string.names_error_reserved)
            trimmed.codePointCount(0, trimmed.length) > MAX_LENGTH ->
                UiText.of(R.string.names_error_long, MAX_LENGTH)
            FORBIDDEN.containsMatchIn(trimmed) -> UiText.of(R.string.names_error_characters)
            else -> null
        }
    }
}
