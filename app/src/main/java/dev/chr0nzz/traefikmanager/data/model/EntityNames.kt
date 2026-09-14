package dev.chr0nzz.traefikmanager.data.model

object EntityNames {
    const val MAX_LENGTH = 100

    private val FORBIDDEN = Regex("[@/,:{}\\u0000-\\u001f\\u007f]")
    private val RESERVED = setOf(".", "..")

    fun problem(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Give it a name"
            trimmed in RESERVED -> "That name is reserved"
            trimmed.codePointCount(0, trimmed.length) > MAX_LENGTH ->
                "Keep the name to $MAX_LENGTH characters or fewer"
            FORBIDDEN.containsMatchIn(trimmed) -> "A name cannot contain @ / , : { or }"
            else -> null
        }
    }
}
