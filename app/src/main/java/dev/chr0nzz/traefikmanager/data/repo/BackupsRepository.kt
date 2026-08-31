package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.BackupEntry
import dev.chr0nzz.traefikmanager.data.model.BackupKind
import dev.chr0nzz.traefikmanager.data.model.CreateBackupResponse
import dev.chr0nzz.traefikmanager.data.model.GitCommit
import dev.chr0nzz.traefikmanager.data.model.GitDiff
import dev.chr0nzz.traefikmanager.data.model.GitPushRequest
import dev.chr0nzz.traefikmanager.data.model.GitStatus
import javax.inject.Inject
import javax.inject.Singleton

data class BackupsSnapshot(
    val entries: List<BackupEntry> = emptyList(),
    val staticConfigured: Boolean? = null,
    val kindKnown: Boolean = true,
)

@Singleton
class BackupsRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val serverScope: ServerScope,
    private val serversRepository: ServersRepository,
    private val routesRepository: RoutesRepository,
) {

    private val activeAgentId: String? get() = serverScope.activeAgentId.value

    val onHost: Boolean get() = activeAgentId == null

    suspend fun list(): BackupsSnapshot {
        val api = apiProvider.api()
        val agentId = activeAgentId ?: return BackupsSnapshot(
            entries = api.hubBackups().map {
                BackupEntry(it.name, it.size, it.modified, kindOf(it.kind))
            },
        )
        check(agentId.isNotEmpty())
        val response = api.agentBackups()
        val rows = response.backups.orEmpty()
        return BackupsSnapshot(
            entries = rows.map { BackupEntry(it.name, it.size, it.date, kindOf(it.kind)) },
            staticConfigured = response.staticConfigured,
            kindKnown = rows.isEmpty() || rows.any { it.kind.isNotEmpty() },
        )
    }

    suspend fun create(): CreateBackupResponse = apiProvider.api().createBackup()

    suspend fun createStatic(): CreateBackupResponse {
        require(onHost) { "An agent backs up its static config with the ordinary create" }
        return apiProvider.api().createStaticBackup()
    }

    suspend fun delete(name: String) {
        val response = apiProvider.api().deleteBackup(name)
        if (!response.ok) error(response.error ?: response.message ?: "Could not delete the backup")
    }

    suspend fun restore(name: String) {
        val response = apiProvider.api().restoreBackup(name)
        if (!response.worked) error(response.error ?: "Could not restore the backup")
        routesRepository.notifyChangedExternally()
    }

    suspend fun restartTraefik() {
        val response = apiProvider.api().restartTraefik()
        if (!response.ok) error(response.error ?: "Could not restart Traefik")
    }

    private suspend fun gitTarget(): String? {
        val agentId = activeAgentId ?: return null
        val hostBacked = runCatching { serversRepository.config(agentId)?.gitHostBackup }.getOrNull() ?: false
        return if (hostBacked) agentId else null
    }

    suspend fun gitStatus(): GitStatus = apiProvider.api().gitStatus(gitTarget())

    suspend fun gitCommits(): List<GitCommit> = apiProvider.api().gitCommits(gitTarget())

    suspend fun gitDiff(sha: String): GitDiff = apiProvider.api().gitDiff(sha, gitTarget())

    suspend fun gitPush(message: String) {
        val response = apiProvider.api().gitPush(GitPushRequest(message.trim()), gitTarget())
        if (!response.ok) error(response.error ?: response.message ?: "Could not push to Git")
    }

    suspend fun gitRestore(sha: String) {
        val response = apiProvider.api().gitRestore(sha, gitTarget())
        if (!response.ok) error(response.error ?: response.message ?: "Could not restore that commit")
        routesRepository.notifyChangedExternally()
    }

    private fun kindOf(raw: String) = if (raw.equals("static", ignoreCase = true)) {
        BackupKind.Static
    } else {
        BackupKind.Routes
    }
}
