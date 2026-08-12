package dev.chr0nzz.traefikmanager.ui.backups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.BackupEntry
import dev.chr0nzz.traefikmanager.data.model.BackupKind
import dev.chr0nzz.traefikmanager.data.model.GitCommit
import dev.chr0nzz.traefikmanager.data.model.GitDiff
import dev.chr0nzz.traefikmanager.data.model.GitStatus
import dev.chr0nzz.traefikmanager.data.repo.BackupsRepository
import dev.chr0nzz.traefikmanager.data.repo.ServerScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BackupsTab { Dynamic, Static, Git }

data class BackupsUiState(
    val tab: BackupsTab = BackupsTab.Dynamic,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val busy: Boolean = false,
    val onHost: Boolean = true,
    val entries: List<BackupEntry> = emptyList(),
    val staticConfigured: Boolean = true,
    val kindKnown: Boolean = true,
    val error: String? = null,
    val gitLoading: Boolean = true,
    val gitStatus: GitStatus? = null,
    val gitCommits: List<GitCommit> = emptyList(),
    val gitError: String? = null,
    val diffFor: GitCommit? = null,
    val diff: GitDiff? = null,
    val diffLoading: Boolean = false,
    /** Set after restoring a static backup: it only takes effect once Traefik restarts. */
    val restartPending: Boolean = false,
    val message: String? = null,
) {
    val dynamic: List<BackupEntry> get() = entries.filter { it.kind == BackupKind.Routes }

    val static: List<BackupEntry> get() = entries.filter { it.kind == BackupKind.Static }

    val totalSize: Long get() = entries.sumOf { it.size }
}

@HiltViewModel
class BackupsViewModel @Inject constructor(
    private val repository: BackupsRepository,
    serverScope: ServerScope,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupsUiState())
    val state: StateFlow<BackupsUiState> = _state.asStateFlow()

    init {
        load(initial = true)
        loadGit()
        viewModelScope.launch {
            serverScope.generation.drop(1).collect {
                _state.update { BackupsUiState(tab = it.tab) }
                load(initial = true)
                loadGit()
            }
        }
    }

    fun onTabChange(tab: BackupsTab) {
        _state.update { it.copy(tab = tab) }
        if (tab == BackupsTab.Git && _state.value.gitStatus == null) loadGit()
    }

    fun refresh() {
        load(initial = false)
        loadGit()
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun dismissRestartNotice() = _state.update { it.copy(restartPending = false) }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.entries.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            runCatching { repository.list() }.fold(
                onSuccess = { snapshot ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            onHost = repository.onHost,
                            entries = snapshot.entries,
                            // The host answers for its own static config through its settings, so
                            // only an agent's explicit answer can hide the tab.
                            staticConfigured = snapshot.staticConfigured ?: true,
                            kindKnown = snapshot.kindKnown,
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = throwable.readable("Could not read the backups"),
                        )
                    }
                },
            )
        }
    }

    private fun loadGit() {
        _state.update { it.copy(gitLoading = it.gitStatus == null, gitError = null) }
        viewModelScope.launch {
            runCatching {
                val status = repository.gitStatus()
                val commits = if (status.configured) repository.gitCommits() else emptyList()
                status to commits
            }.fold(
                onSuccess = { (status, commits) ->
                    _state.update { it.copy(gitLoading = false, gitStatus = status, gitCommits = commits) }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(gitLoading = false, gitError = throwable.readable("Could not read Git backup"))
                    }
                },
            )
        }
    }

    fun createBackup() = run("Backup created") {
        val response = repository.create()
        if (!response.worked) error(response.error ?: "Could not create the backup")
        val created = response.created
        when {
            created.isEmpty() -> "Backup created"
            created.size == 1 -> "Backed up ${created.first()}"
            else -> "Backed up ${created.size} files"
        }
    }

    fun createStaticBackup() = run("Static config backed up") {
        val response = repository.createStatic()
        if (!response.worked) error(response.error ?: "Could not back up the static config")
        "Static config backed up"
    }

    fun delete(entry: BackupEntry) = run("Backup deleted") {
        repository.delete(entry.name)
        "Deleted ${entry.name}"
    }

    fun restore(entry: BackupEntry) = run("Backup restored") {
        repository.restore(entry.name)
        if (entry.kind == BackupKind.Static) {
            _state.update { it.copy(restartPending = true) }
            "Static config restored - Traefik needs a restart"
        } else {
            "Restored ${entry.name}"
        }
    }

    fun restartTraefik() = run("Traefik restarted") {
        repository.restartTraefik()
        _state.update { it.copy(restartPending = false) }
        "Traefik restarted"
    }

    fun push(message: String) = run("Pushed to Git") {
        repository.gitPush(message)
        // A push with nothing to commit still answers ok, so the log is the only honest report.
        "Pushed to Git"
    }

    fun restoreCommit(commit: GitCommit) = run("Commit restored") {
        repository.gitRestore(commit.sha)
        "Restored ${commit.shaShort}"
    }

    fun openDiff(commit: GitCommit) {
        _state.update { it.copy(diffFor = commit, diff = null, diffLoading = true) }
        viewModelScope.launch {
            runCatching { repository.gitDiff(commit.sha) }.fold(
                onSuccess = { diff -> _state.update { it.copy(diff = diff, diffLoading = false) } },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            diffLoading = false,
                            message = throwable.readable("Could not read that commit"),
                        )
                    }
                },
            )
        }
    }

    fun closeDiff() = _state.update { it.copy(diffFor = null, diff = null, diffLoading = false) }

    private fun run(fallback: String, block: suspend () -> String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = runCatching { block() }
            _state.update {
                it.copy(busy = false, message = result.getOrNull() ?: result.readableFailure(fallback))
            }
            load(initial = false)
            loadGit()
        }
    }
}

/** Server failures can arrive as HTML, so never show a raw body as if it were a message. */
private fun Throwable.readable(fallback: String): String {
    val message = message?.trim().orEmpty()
    return when {
        message.isEmpty() -> fallback
        message.startsWith("<") || message.contains("<html", ignoreCase = true) -> fallback
        message.length > 200 -> fallback
        else -> message
    }
}

private fun Result<String>.readableFailure(fallback: String): String =
    exceptionOrNull()?.readable(fallback) ?: fallback
