package dev.chr0nzz.traefikmanager.data.store

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class LanguageOption(val tag: String, val name: String)

@Singleton
class AppLanguage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val localeManager: LocaleManager? get() = context.getSystemService(LocaleManager::class.java)

    private val shipped: List<Locale> by lazy {
        val locales = runCatching { LocaleConfig(context).supportedLocales }.getOrNull()
        if (locales == null) emptyList() else (0 until locales.size()).map { locales[it] }
    }

    fun options(): List<LanguageOption> {
        val collator = Collator.getInstance()
        return shipped
            .filterNot { it.country in PSEUDO_REGIONS }
            .map { LanguageOption(it.toLanguageTag(), displayName(it)) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    fun selected(): String? = localeManager?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()

    fun select(tag: String?) {
        localeManager?.applicationLocales =
            if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    fun serverTag(): String? {
        val locale = context.resources.configuration.locales[0]
        return serverTagFor(locale).takeIf { shipped.any { it.language == locale.language } }
    }

    companion object {
        private const val SIMPLIFIED = "zh-Hans"
        private val PSEUDO_REGIONS = setOf("XA", "XB")
        private val SIMPLIFIED_REGIONS = setOf("", "CN", "SG")

        fun displayName(locale: Locale): String =
            locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }

        fun serverTagFor(locale: Locale): String? = when (locale.language) {
            "" -> null
            "en" -> when (locale.country) {
                "GB" -> "en-GB"
                "US" -> "en-US"
                else -> null
            }
            "fr" -> if (locale.country == "CA") "fr-CA" else "fr"
            "pt" -> if (locale.country == "BR") "pt-BR" else "pt"
            "zh" -> when {
                locale.toLanguageTag().startsWith(SIMPLIFIED) -> SIMPLIFIED
                locale.script.isEmpty() && locale.country in SIMPLIFIED_REGIONS -> SIMPLIFIED
                else -> null
            }
            else -> locale.language
        }
    }
}
