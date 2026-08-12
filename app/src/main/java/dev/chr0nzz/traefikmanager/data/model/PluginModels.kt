package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PluginEntry(
    @Serializable(with = LenientStringSerializer::class)
    val name: String = "",
    @Serializable(with = LenientStringSerializer::class)
    val moduleName: String = "",
    @Serializable(with = LenientStringSerializer::class)
    val version: String = "",
    val settings: JsonElement? = null,
) {
    val repoUrl: String? get() = if (moduleName.startsWith("github.com/")) "https://$moduleName" else null

    val displayVersion: String get() = version.ifBlank { "-" }

    val displayModule: String get() = moduleName.ifBlank { "-" }
}

@Serializable
data class PluginsResponse(
    val plugins: List<PluginEntry> = emptyList(),
    val error: String? = null,
)

object PluginUsage {

    fun countsFor(plugins: List<PluginEntry>, middlewares: List<MiddlewareDef>): Map<String, Int> =
        plugins.associate { plugin -> plugin.name to usersOf(plugin.name, middlewares).size }

    fun usersOf(pluginName: String, middlewares: List<MiddlewareDef>): List<MiddlewareDef> {
        if (pluginName.isEmpty()) return emptyList()
        val alias = Regex("""^\s*${Regex.escape(pluginName)}\s*:""", RegexOption.MULTILINE)
        return middlewares.filter { middleware ->
            val yaml = middleware.yaml
            PLUGIN_KEY.containsMatchIn(yaml) && alias.containsMatchIn(yaml)
        }
    }

    private val PLUGIN_KEY = Regex("""(^|\n)\s*plugin\s*:""")
}
