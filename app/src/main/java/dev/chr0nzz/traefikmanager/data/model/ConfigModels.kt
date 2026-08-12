package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConfigFile(
    val label: String = "",
    val path: String = "",
)

@Serializable
data class ConfigsResponse(
    val files: List<ConfigFile> = emptyList(),
    @SerialName("configDirSet") val configDirSet: Boolean = false,
)

@Serializable
data class TlsOptionProfile(
    val name: String = "",
    val configFile: String = "",
    val minVersion: String = "",
    val maxVersion: String = "",
)
