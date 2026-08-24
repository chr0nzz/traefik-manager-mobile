package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
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

@Serializable
data class StaticAvailable(val available: Boolean = false)

/** What an agent reports about its own static config, which is how the host's probe is answered. */
@Serializable
data class AgentStaticStatus(
    val configured: Boolean = false,
    val path: String = "",
    @SerialName("restart_method") val restartMethod: String = "",
)

@Serializable
data class PluginInstallRequest(
    @SerialName("static_yaml") val staticYaml: String,
    @SerialName("middleware_yaml") val middlewareYaml: String = "",
    @SerialName("middleware_file") val middlewareFile: String = "",
    /** The agent this is for, or empty for the host. The call itself always goes to the host. */
    val server: String = "",
)

@Serializable
data class PluginInstallResponse(
    val ok: Boolean = false,
    val plugins: List<String> = emptyList(),
    @SerialName("middleware_file") val middlewareFile: String? = null,
    val warning: String? = null,
    val error: String? = null,
)

/** A pure YAML transform: the server hands back a new document and writes nothing. */
@Serializable
data class StaticSectionRequest(
    val section: String,
    val action: String,
    val name: String,
    @SerialName("old_name") val oldName: String = "",
    val data: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
    @SerialName("current_raw") val currentRaw: String? = null,
)

@Serializable
data class StaticSectionResponse(
    val ok: Boolean = false,
    val raw: String = "",
    val error: String? = null,
)

@Serializable
data class StaticSaveRequest(val content: String)
