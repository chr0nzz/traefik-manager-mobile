package dev.chr0nzz.traefikmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.ChannelKinds
import dev.chr0nzz.traefikmanager.data.model.ChannelPayload
import dev.chr0nzz.traefikmanager.data.model.NotificationChannel
import dev.chr0nzz.traefikmanager.data.model.missingFields
import dev.chr0nzz.traefikmanager.data.repo.NotificationChannelsRepository
import dev.chr0nzz.traefikmanager.push.PushChannels
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChannelDraft(
    val id: String? = null,
    val name: String = "",
    val kind: String = "discord",
    val enabled: Boolean = true,
    val url: String = "",
    val token: String = "",
    val token2: String = "",
    val username: String = "",
    val password: String = "",
    val categories: List<String> = emptyList(),
    val minSeverity: String = "info",
    val digest: String = "immediate",
    val quietStart: String = "",
    val quietEnd: String = "",
    val breakThrough: Boolean = false,
) {
    val adding: Boolean get() = id == null

    fun valueOf(key: String): String = when (key) {
        "url" -> url
        "token" -> token
        "token2" -> token2
        else -> ""
    }

    fun withValue(key: String, value: String): ChannelDraft = when (key) {
        "url" -> copy(url = value)
        "token" -> copy(token = value)
        "token2" -> copy(token2 = value)
        else -> this
    }
}

data class ChannelsUiState(
    val loading: Boolean = true,
    val supported: Boolean? = null,
    val channels: List<NotificationChannel> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val editing: ChannelDraft? = null,
    val editError: String? = null,
    val saving: Boolean = false,
    val test: TestState = TestState.Idle,
    val pendingDelete: NotificationChannel? = null,
    val pushChannelId: String? = null,
)

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val repository: NotificationChannelsRepository,
    private val pushChannels: PushChannels,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelsUiState())
    val state: StateFlow<ChannelsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.channels.isEmpty(), error = null) }
        viewModelScope.launch {
            runCatching { repository.load() }.fold(
                onSuccess = { channels ->
                    val push = runCatching { pushChannels.channelId() }.getOrNull()
                    _state.update {
                        it.copy(
                            loading = false,
                            channels = channels,
                            supported = repository.supported.value,
                            pushChannelId = push,
                            error = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            supported = repository.supported.value,
                            error = throwable.message ?: "Failed to load channels",
                        )
                    }
                },
            )
        }
    }

    fun add() {
        _state.update { it.copy(editing = ChannelDraft(), editError = null, test = TestState.Idle) }
    }

    fun edit(channel: NotificationChannel) {
        val window = channel.quietHours.split('-', limit = 2)
        _state.update {
            it.copy(
                editing = ChannelDraft(
                    id = channel.id,
                    name = channel.name,
                    kind = channel.kind,
                    enabled = channel.enabled,
                    url = channel.url,
                    token = channel.token,
                    token2 = channel.token2,
                    username = channel.username,
                    password = channel.password,
                    categories = channel.categories,
                    minSeverity = channel.minSeverity,
                    digest = channel.digest,
                    quietStart = window.getOrNull(0)?.takeIf { window.size == 2 }.orEmpty(),
                    quietEnd = window.getOrNull(1).orEmpty(),
                    breakThrough = channel.breakThrough,
                ),
                editError = null,
                test = TestState.Idle,
            )
        }
    }

    fun closeEditor() = _state.update { it.copy(editing = null, editError = null, test = TestState.Idle) }

    fun onDraftChange(draft: ChannelDraft) {
        _state.update { it.copy(editing = draft, test = TestState.Idle) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun askDelete(channel: NotificationChannel?) = _state.update { it.copy(pendingDelete = channel) }

    fun toggleEnabled(channel: NotificationChannel) {
        viewModelScope.launch {
            runCatching { repository.update(channel.id, channel.copy(enabled = !channel.enabled).payload()) }
                .fold(
                    onSuccess = { saved -> replace(saved) },
                    onFailure = { throwable ->
                        _state.update {
                            it.copy(message = throwable.message ?: "Failed to update channel")
                        }
                    },
                )
        }
    }

    fun testRow(channel: NotificationChannel) {
        viewModelScope.launch {
            val outcome = repository.test(channel.id)
            _state.update {
                it.copy(
                    message = if (outcome.ok) {
                        "Test message delivered"
                    } else {
                        outcome.message.ifBlank { "Test failed" }
                    },
                )
            }
        }
    }

    fun delete(channel: NotificationChannel) {
        _state.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            runCatching { repository.delete(channel.id) }.fold(
                onSuccess = {
                    _state.update { current ->
                        current.copy(
                            channels = current.channels.filterNot { it.id == channel.id },
                            message = "Channel removed",
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update { it.copy(message = throwable.message ?: "Failed to remove channel") }
                },
            )
        }
    }

    fun save() {
        val draft = _state.value.editing ?: return
        val problem = validate(draft)
        if (problem != null) {
            _state.update { it.copy(editError = problem) }
            return
        }
        _state.update { it.copy(saving = true, editError = null) }
        viewModelScope.launch {
            persist(draft).fold(
                onSuccess = { saved ->
                    replace(saved)
                    _state.update {
                        it.copy(saving = false, editing = null, message = "Channel saved", test = TestState.Idle)
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(saving = false, editError = throwable.message ?: "Failed to save channel")
                    }
                },
            )
        }
    }

    fun test() {
        val draft = _state.value.editing ?: return
        val problem = validate(draft)
        if (problem != null) {
            _state.update { it.copy(editError = problem, test = TestState.Idle) }
            return
        }
        _state.update { it.copy(test = TestState.Running, editError = null) }
        viewModelScope.launch {
            persist(draft).fold(
                onSuccess = { saved ->
                    replace(saved)
                    val outcome = repository.test(saved.id)
                    _state.update { current ->
                        current.copy(
                            editing = current.editing?.copy(id = saved.id),
                            test = if (outcome.ok) {
                                TestState.Ok("Delivered.")
                            } else {
                                TestState.Failed(outcome.message.ifBlank { "Failed." })
                            },
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            test = TestState.Idle,
                            editError = throwable.message ?: "Failed to save channel",
                        )
                    }
                },
            )
        }
    }

    private suspend fun persist(draft: ChannelDraft): Result<NotificationChannel> = runCatching {
        val payload = draft.payload()
        val id = draft.id
        if (id == null) repository.create(payload) else repository.update(id, payload)
    }

    private fun replace(channel: NotificationChannel) {
        _state.update { current ->
            val existing = current.channels.indexOfFirst { it.id == channel.id }
            current.copy(
                channels = if (existing < 0) {
                    current.channels + channel
                } else {
                    current.channels.toMutableList().apply { this[existing] = channel }
                },
            )
        }
    }

    private fun validate(draft: ChannelDraft): String? {
        val missing = draft.toChannel().missingFields()
        if (missing.isNotEmpty()) return "Fill in " + missing.joinToString(" and ") + " first."
        if (draft.quietStart.isBlank() != draft.quietEnd.isBlank()) {
            return "Set both a start and an end time for quiet hours, or clear them both."
        }
        return null
    }
}

private fun ChannelDraft.toChannel() = NotificationChannel(
    id = id.orEmpty(),
    name = name,
    kind = kind,
    enabled = enabled,
    url = url,
    token = token,
    token2 = token2,
    username = username,
    password = password,
    categories = categories,
    minSeverity = minSeverity,
    digest = digest,
    quietHours = quietHours(),
    breakThrough = breakThrough,
)

private fun ChannelDraft.quietHours(): String =
    if (quietStart.isNotBlank() && quietEnd.isNotBlank()) "$quietStart-$quietEnd" else ""

private fun ChannelDraft.payload(): ChannelPayload {
    val spec = ChannelKinds.of(kind)
    val uses = spec?.fields?.map { it.key }.orEmpty()
    return ChannelPayload(
        name = name.trim(),
        kind = kind,
        enabled = enabled,
        url = if ("url" in uses) url.trim() else "",
        token = if ("token" in uses) token.trim() else "",
        token2 = if ("token2" in uses) token2.trim() else "",
        username = if (spec?.basicAuth == true) username.trim() else "",
        password = if (spec?.basicAuth == true) password else "",
        categories = categories,
        minSeverity = minSeverity,
        digest = digest,
        quietHours = quietHours(),
        breakThrough = breakThrough,
    )
}

private fun NotificationChannel.payload() = ChannelPayload(
    name = name,
    kind = kind,
    enabled = enabled,
    url = url,
    token = token,
    token2 = token2,
    username = username,
    password = password,
    categories = categories,
    minSeverity = minSeverity,
    digest = digest,
    quietHours = quietHours,
    breakThrough = breakThrough,
)
