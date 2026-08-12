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

/** What a listing told us, including the one capability the payload carries. */
data class BackupsSnapshot(
    val entries: List<BackupEntry> = emptyList(),
    /** The agent reports this; the host answers through its own settings instead. */
    val staticConfigured: Boolean? = null,
    /** An agent that predates the kind field cannot tell routes from static. */
    val kindKnown: Boolean = true,
)

/**
 * Backups, which the servers disagree about in three separate ways.
 *
 * The listing has two wire shapes. The static file is backed up by its own call on the host and
 * folded into the ordinary create on an agent. And Git backup has three targets: the host's own
 * repo, an agent that rides the host's repo on its own branch, and an agent with a repo of its
 * own. The first two are calls to the hub, the third is proxied to the agent.
 */
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
        // Named only to make it obvious the interceptor is what reaches the agent here.
        check(agentId.isNotEmpty())
        val response = api.agentBackups()
        val rows = response.backups.orEmpty()
        return BackupsSnapshot(
            entries = rows.map { BackupEntry(it.name, it.size, it.date, kindOf(it.kind)) },
            staticConfigured = response.staticConfigured,
            // An agent old enough to omit kind labels everything routes; say so rather than lie.
            kindKnown = rows.isEmpty() || rows.any { it.kind.isNotEmpty() },
        )
    }

    suspend fun create(): CreateBackupResponse = apiProvider.api().createBackup()

    /** Host only. On an agent the ordinary create already includes the static file. */
    suspend fun createStatic(): CreateBackupResponse {
        require(onHost) { "An agent backs up its static config with the ordinary create" }
        return apiProvider.api().createStaticBackup()
    }

    suspend fun delete(name: String) {
        val response = apiProvider.api().deleteBackup(name)
        if (!response.ok) error(response.error ?: response.message ?: "Could not delete the backup")
    }

    /**
     * Overwrites live config with the backup, after the server takes one of what is there now.
     * Every cached page is stale afterwards, so the caches are told before this returns.
     */
    suspend fun restore(name: String) {
        val response = apiProvider.api().restoreBackup(name)
        if (!response.worked) error(response.error ?: "Could not restore the backup")
        routesRepository.notifyChangedExternally()
    }

    suspend fun restartTraefik() {
        val response = apiProvider.api().restartTraefik()
        if (!response.ok) error(response.error ?: "Could not restart Traefik")
    }

    /**
     * Which server answers for Git, and how it must be asked.
     *
     * An agent that rides the host repo is a question for the hub, tagged with the agent's id -
     * asking the agent itself would reach a different repo or none at all. Anything else goes to
     * whichever server is selected.
     */
    private suspend fun gitTarget(): String? {
        val agentId = activeAgentId ?: return null
        val hostBacked = runCatching { serversRepository.config(agentId)?.gitHostBackup }.getOrNull() ?: false
        return if (hostBacked) agentId else null
    }

    suspend fun gitStatus(): GitStatus = apiProvider.api().gitStatus(gitTarget())

    suspend fun gitCommits(): List<GitCommit> = apiProvider.api().gitCommits(gitTarget())

    suspend fun gitDiff(sha: String): GitDiff = apiProvider.api().gitDiff(sha, gitTarget())

    /** Answers ok even when there was nothing to commit, so callers must re-read the log. */
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
