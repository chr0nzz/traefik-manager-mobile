package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConfigFile(
    val label: String = "",
    val path: String = "",
    val name: String = "",
)

@Serializable
data class ConfigsResponse(
    val files: List<ConfigFile> = emptyList(),
    @SerialName("configDirSet") val configDirSet: Boolean = false,
) {
    fun normalized(onAgent: Boolean): ConfigsResponse {
        val named = files
            .map { file ->
                val label = file.label.ifEmpty { file.name }
                ConfigFile(label = label, path = file.path.ifEmpty { label })
            }
            .filter { it.label.isNotEmpty() }
            .distinctBy { it.path }
        return if (onAgent) {
            ConfigsResponse(files = named.sortedBy { it.label }, configDirSet = true)
        } else {
            copy(files = named)
        }
    }
}

@Serializable
data class TlsOptionProfile(
    val name: String = "",
    val configFile: String = "",
    val minVersion: String = "",
    val maxVersion: String = "",
)
