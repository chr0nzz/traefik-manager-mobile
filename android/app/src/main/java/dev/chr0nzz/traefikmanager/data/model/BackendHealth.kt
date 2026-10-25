package dev.chr0nzz.traefikmanager.data.model

import dev.chr0nzz.traefikmanager.R

object BackendHealth {
    fun key(protocol: String, serviceName: String): String =
        "${protocol.lowercase()}:${serviceName.substringBefore('@')}"

    fun summary(row: ServiceRow?): UiText? {
        if (row == null || !row.healthCheck || row.backendsTotal == 0) return null
        val down = row.backendsTotal - row.backendsUp
        return when {
            down == 0 -> UiText.plural(R.plurals.routes_ping_servers_up, row.backendsTotal, row.backendsUp, row.backendsTotal)
            row.backendsUp == 0 -> UiText.plural(R.plurals.health_all_servers_down, row.backendsTotal)
            else -> UiText.plural(R.plurals.health_servers_down, row.backendsTotal, down, row.backendsTotal)
        }
    }
}
