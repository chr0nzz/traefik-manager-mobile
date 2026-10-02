package dev.chr0nzz.traefikmanager.data.store

import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText

object MigrationNotice {
    const val CARRIED_OVER = "migration:carried_over"
    const val UNREADABLE = "migration:unreadable"

    fun text(stored: String): UiText = when (stored) {
        CARRIED_OVER -> UiText.of(R.string.migration_carried_over)
        UNREADABLE -> UiText.of(R.string.migration_unreadable)
        else -> UiText.Raw(stored)
    }
}
