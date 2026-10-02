package dev.chr0nzz.traefikmanager.data.model

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

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

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())

        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = Plural(id, count, args.toList())

        private fun resolved(args: List<Any>, resources: Resources): Array<Any> =
            args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()
    }
}
