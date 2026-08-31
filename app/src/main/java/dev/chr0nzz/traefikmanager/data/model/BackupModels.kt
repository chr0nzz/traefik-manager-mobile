package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class BackupKind { Routes, Static }

data class BackupEntry(
    val name: String,
    val size: Long,
    val stamp: String,
    val kind: BackupKind,
)

@Serializable
data class HubBackup(
    val name: String = "",
    val size: Long = 0,
    val modified: String = "",
    val kind: String = "routes",
)

@Serializable
data class AgentBackup(
    val name: String = "",
    val size: Long = 0,
    val date: String = "",
    val kind: String = "routes",
)

@Serializable
data class AgentBackupsResponse(
    val backups: List<AgentBackup>? = null,
    @SerialName("static_configured") val staticConfigured: Boolean = false,
)

@Serializable
data class CreateBackupResponse(
    val success: Boolean = false,
    val ok: Boolean = false,
    val names: List<String> = emptyList(),
    val name: String = "",
    val count: Int = 0,
    val error: String? = null,
) {
    val worked: Boolean get() = success || ok
    val created: List<String> get() = names.ifEmpty { listOfNotNull(name.takeIf { it.isNotEmpty() }) }
}

@Serializable
data class RestoreResponse(
    val success: Boolean = false,
    val ok: Boolean = false,
    val error: String? = null,
) {
    val worked: Boolean get() = success || ok
}

@Serializable
data class GitStatus(
    val enabled: Boolean = false,
    val configured: Boolean = false,
    @SerialName("last_sha") val lastSha: String = "",
    @SerialName("last_push") val lastPush: String = "",
    val branch: String = "",
)

@Serializable
data class GitCommit(
    val sha: String = "",
    @SerialName("sha_short") val shaShort: String = "",
    val timestamp: String = "",
    val message: String = "",
)

@Serializable
data class GitDiffFile(
    val filename: String = "",
    val status: String = "",
    val old: String = "",
    val new: String = "",
)

@Serializable
data class GitDiff(
    val stat: String = "",
    val files: List<GitDiffFile> = emptyList(),
    val error: String? = null,
)

@Serializable
data class GitPushRequest(val message: String = "")
