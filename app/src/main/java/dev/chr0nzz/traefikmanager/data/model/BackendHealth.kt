package dev.chr0nzz.traefikmanager.data.model

object BackendHealth {
    fun key(protocol: String, serviceName: String): String =
        "${protocol.lowercase()}:${serviceName.substringBefore('@')}"

    fun summary(row: ServiceRow?): String? {
        if (row == null || !row.healthCheck || row.backendsTotal == 0) return null
        val down = row.backendsTotal - row.backendsUp
        return when {
            down == 0 -> "${row.backendsUp} of ${row.backendsTotal} servers up"
            row.backendsUp == 0 -> "all ${row.backendsTotal} servers down"
            else -> "$down of ${row.backendsTotal} servers down"
        }
    }
}
