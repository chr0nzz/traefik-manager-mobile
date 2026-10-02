package dev.chr0nzz.traefikmanager.ui.backups

import dev.chr0nzz.traefikmanager.data.model.LocalizedException
import androidx.annotation.StringRes
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
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText

enum class BackupsTab { Dynamic, Static, Certs, Git }

data class BackupsUiState(
    val tab: BackupsTab = BackupsTab.Dynamic,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val busy: Boolean = false,
    val onHost: Boolean = true,
    val entries: List<BackupEntry> = emptyList(),
    val staticConfigured: Boolean = true,
    val kindKnown: Boolean = true,
    val error: UiText? = null,
    val gitLoading: Boolean = true,
    val gitStatus: GitStatus? = null,
    val gitCommits: List<GitCommit> = emptyList(),
    val gitError: UiText? = null,
    val diffFor: GitCommit? = null,
    val diff: GitDiff? = null,
    val diffLoading: Boolean = false,
    val restartPending: BackupKind? = null,
    val message: UiText? = null,
) {
    val dynamic: List<BackupEntry> get() = entries.filter { it.kind == BackupKind.Routes }

    val static: List<BackupEntry> get() = entries.filter { it.kind == BackupKind.Static }

    val certs: List<BackupEntry> get() = entries.filter { it.kind == BackupKind.Certs }
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

    fun dismissRestartNotice() = _state.update { it.copy(restartPending = null) }

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
                            error = throwable.readable(R.string.backups_error_load),
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
                        it.copy(gitLoading = false, gitError = throwable.readable(R.string.backups_git_error))
                    }
                },
            )
        }
    }

    fun createBackup() = run(R.string.backups_error_create) {
        val response = repository.create()
        if (!response.worked) error(response.error.orEmpty())
        val created = response.created
        when {
            created.isEmpty() -> UiText.of(R.string.backups_created)
            created.size == 1 -> UiText.of(R.string.backups_created_file, created.first())
            else -> UiText.plural(R.plurals.backups_created_files, created.size)
        }
    }

    fun createStaticBackup() = run(R.string.backups_error_create_static) {
        val response = repository.createStatic()
        if (!response.worked) error(response.error.orEmpty())
        UiText.of(R.string.backups_created_static)
    }

    fun delete(entry: BackupEntry) = run(R.string.backups_error_delete) {
        repository.delete(entry.name)
        UiText.of(R.string.backups_deleted, entry.name)
    }

    fun restore(entry: BackupEntry) = run(R.string.backups_error_restore) {
        val response = repository.restore(entry.name)
        when {
            entry.kind == BackupKind.Static -> {
                _state.update { it.copy(restartPending = BackupKind.Static) }
                UiText.of(R.string.backups_restored_static)
            }
            entry.kind != BackupKind.Certs -> UiText.of(R.string.backups_restored, entry.name)
            response.restarted == true -> UiText.of(R.string.backups_restored_certs)
            else -> {
                _state.update { it.copy(restartPending = BackupKind.Certs) }
                response.restartError.ifBlank { null }
                    ?.let { UiText.of(R.string.backups_restored_certs_no_restart_reason, it) }
                    ?: UiText.of(R.string.backups_restored_certs_no_restart)
            }
        }
    }

    fun restartTraefik() = run(R.string.common_error_restart) {
        repository.restartTraefik()
        _state.update { it.copy(restartPending = null) }
        UiText.of(R.string.common_traefik_restarted)
    }

    fun push(message: String) = run(R.string.backups_git_error_push) {
        repository.gitPush(message)
        UiText.of(R.string.backups_git_pushed)
    }

    fun restoreCommit(commit: GitCommit) = run(R.string.backups_git_error_restore) {
        repository.gitRestore(commit.sha)
        UiText.of(R.string.backups_restored, commit.shaShort)
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
                            message = throwable.readable(R.string.backups_git_error_diff),
                        )
                    }
                },
            )
        }
    }

    fun closeDiff() = _state.update { it.copy(diffFor = null, diff = null, diffLoading = false) }

    private fun run(@StringRes failure: Int, block: suspend () -> UiText) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = runCatching { block() }
            _state.update {
                it.copy(busy = false, message = result.getOrNull() ?: result.readableFailure(failure))
            }
            load(initial = false)
            loadGit()
        }
    }
}

private fun Throwable.readable(@StringRes fallback: Int): UiText {
    (this as? LocalizedException)?.let { return it.text }
    val message = message?.trim().orEmpty()
    return when {
        message.isEmpty() -> UiText.of(fallback)
        message.startsWith("<") || message.contains("<html", ignoreCase = true) -> UiText.of(fallback)
        message.length > 200 -> UiText.of(fallback)
        else -> UiText.Raw(message)
    }
}

private fun Result<UiText>.readableFailure(@StringRes fallback: Int): UiText =
    exceptionOrNull()?.readable(fallback) ?: UiText.of(fallback)
