package dev.chr0nzz.traefikmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.push.PushNotifier
import dev.chr0nzz.traefikmanager.data.model.TmNotification
import dev.chr0nzz.traefikmanager.data.model.WebhookTestRequest
import dev.chr0nzz.traefikmanager.data.repo.ManagerSettingsRepository
import dev.chr0nzz.traefikmanager.data.repo.NotificationsRepository
import dev.chr0nzz.traefikmanager.data.repo.RouteHealthRepository
import dev.chr0nzz.traefikmanager.data.repo.ManagerSettingsRepository.Companion.text
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText

val WEBHOOK_TYPES = listOf("discord", "slack", "ntfy", "gotify", "generic")

data class NotificationsUiState(
    val loading: Boolean = true,
    val notifications: List<TmNotification> = emptyList(),
    val webhookUrl: String = "",
    val webhookType: String = "discord",
    val webhookUsername: String = "",
    val webhookPassword: String = "",
    val saving: Boolean = false,
    val test: TestState = TestState.Idle,
    val message: UiText? = null,
    val error: UiText? = null,
    val unread: Int = 0,
    val routeCheckSupported: Boolean? = null,
    val routeCheckEnabled: Boolean = true,
    val routeCheckInterval: Int = 300,
    val savingRouteCheck: Boolean = false,
)

object RouteCheckIntervals {
    val options: List<Pair<Int, UiText>> = listOf(60, 300, 900, 1800)
        .map { it to UiText.plural(R.plurals.common_minutes, it / 60) }

    fun label(seconds: Int): UiText =
        options.firstOrNull { it.first == seconds }?.second ?: UiText.plural(R.plurals.common_seconds, seconds)
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    @param:ApplicationContext private val context: android.content.Context,
    private val apiProvider: ApiProvider,
    private val notifications: NotificationsRepository,
    private val routeHealth: RouteHealthRepository,
    private val settingsRepository: ManagerSettingsRepository,
    private val preferencesStore: dev.chr0nzz.traefikmanager.data.store.PreferencesStore,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            notifications.items.collect { list ->
                if (list != null) _state.update { it.copy(notifications = list) }
            }
        }
        viewModelScope.launch {
            notifications.unread.collect { count -> _state.update { it.copy(unread = count) } }
        }
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.notifications.isEmpty(), error = null) }
        viewModelScope.launch {
            loadRouteChecks()
            val history = runCatching { notifications.refresh() }
            val settings = runCatching { settingsRepository.raw() }
            _state.update { current ->
                current.copy(
                    loading = false,
                    webhookUrl = settings.getOrNull()?.text("webhook_url") ?: current.webhookUrl,
                    webhookType = settings.getOrNull()?.text("webhook_type")?.ifEmpty { "discord" }
                        ?: current.webhookType,
                    webhookUsername = settings.getOrNull()?.text("webhook_username") ?: current.webhookUsername,
                    error = history.exceptionOrNull()?.message?.let(UiText::Raw),
                )
            }
        }
    }

    private fun loadRouteChecks() {
        viewModelScope.launch {
            val snapshot = routeHealth.refresh()
            _state.update {
                it.copy(
                    routeCheckSupported = routeHealth.supported.value,
                    routeCheckEnabled = snapshot?.enabled ?: it.routeCheckEnabled,
                    routeCheckInterval = snapshot?.interval ?: it.routeCheckInterval,
                )
            }
        }
    }

    fun setRouteChecks(enabled: Boolean = _state.value.routeCheckEnabled, interval: Int = _state.value.routeCheckInterval) {
        _state.update { it.copy(savingRouteCheck = true) }
        viewModelScope.launch {
            runCatching { routeHealth.save(enabled, interval) }.fold(
                onSuccess = { saved ->
                    _state.update {
                        it.copy(
                            savingRouteCheck = false,
                            routeCheckEnabled = saved.enabled,
                            routeCheckInterval = saved.interval,
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            savingRouteCheck = false,
                            message = UiText.failure(throwable, R.string.route_checks_error_save),
                        )
                    }
                },
            )
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            notifications.markRead()
            PushNotifier.clear(context)
        }
    }

    fun delete(notification: TmNotification) {
        viewModelScope.launch {
            runCatching { notifications.delete(notification) }.fold(
                onSuccess = { _state.update { it.copy(message = UiText.of(R.string.notifications_deleted)) } },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(message = UiText.failure(throwable, R.string.notifications_error_delete))
                    }
                },
            )
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            runCatching { notifications.clear() }.fold(
                onSuccess = {
                    PushNotifier.clear(context)
                    _state.update { it.copy(message = UiText.of(R.string.notifications_cleared)) }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(message = UiText.failure(throwable, R.string.notifications_error_clear))
                    }
                },
            )
        }
    }

    fun onUrlChange(value: String) = _state.update { it.copy(webhookUrl = value, test = TestState.Idle) }

    fun onTypeChange(value: String) = _state.update { it.copy(webhookType = value, test = TestState.Idle) }

    fun onUsernameChange(value: String) = _state.update { it.copy(webhookUsername = value) }

    fun onPasswordChange(value: String) = _state.update { it.copy(webhookPassword = value) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun test() {
        val current = _state.value
        _state.update { it.copy(test = TestState.Running) }
        viewModelScope.launch {
            runCatching {
                apiProvider.api().testWebhook(
                    WebhookTestRequest(
                        url = current.webhookUrl.trim(),
                        webhookType = current.webhookType,
                        username = current.webhookUsername.trim(),
                        password = current.webhookPassword,
                    ),
                )
            }.fold(
                onSuccess = { result ->
                    _state.update {
                        it.copy(
                            test = if (result.ok) {
                                TestState.Ok("sent")
                            } else {
                                TestState.Failed(UiText.messageOr(result.error, R.string.webhook_error_not_delivered))
                            },
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update { it.copy(test = TestState.Failed(UiText.failure(throwable, R.string.webhook_error_delivery))) }
                },
            )
        }
    }

    fun save() {
        val current = _state.value
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val changes = buildMap {
                put("webhook_url", JsonPrimitive(current.webhookUrl.trim()))
                put("webhook_type", JsonPrimitive(current.webhookType))
                put("webhook_username", JsonPrimitive(current.webhookUsername.trim()))
                if (current.webhookPassword.isNotEmpty()) {
                    put("webhook_password", JsonPrimitive(current.webhookPassword))
                }
            }
            runCatching { settingsRepository.patch(changes) }.fold(
                onSuccess = {
                    _state.update { it.copy(saving = false, message = UiText.of(R.string.webhook_saved), webhookPassword = "") }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(saving = false, message = UiText.failure(throwable, R.string.webhook_error_save))
                    }
                },
            )
        }
    }
}
