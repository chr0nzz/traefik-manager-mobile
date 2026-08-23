package dev.chr0nzz.traefikmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.push.PushNotifier
import dev.chr0nzz.traefikmanager.data.model.DeleteNotificationRequest
import dev.chr0nzz.traefikmanager.data.model.TmNotification
import dev.chr0nzz.traefikmanager.data.model.WebhookTestRequest
import dev.chr0nzz.traefikmanager.data.repo.ManagerSettingsRepository
import dev.chr0nzz.traefikmanager.data.repo.ManagerSettingsRepository.Companion.text
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

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
    val message: String? = null,
    val error: String? = null,
    val unread: Int = 0,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    @param:ApplicationContext private val context: android.content.Context,
    private val apiProvider: ApiProvider,
    private val settingsRepository: ManagerSettingsRepository,
    private val preferencesStore: dev.chr0nzz.traefikmanager.data.store.PreferencesStore,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.notifications.isEmpty(), error = null) }
        viewModelScope.launch {
            val history = runCatching { apiProvider.api().notifications() }
            val settings = runCatching { settingsRepository.raw() }
            _state.update { current ->
                current.copy(
                    loading = false,
                    notifications = history.getOrDefault(current.notifications),
                    webhookUrl = settings.getOrNull()?.text("webhook_url") ?: current.webhookUrl,
                    webhookType = settings.getOrNull()?.text("webhook_type")?.ifEmpty { "discord" }
                        ?: current.webhookType,
                    webhookUsername = settings.getOrNull()?.text("webhook_username") ?: current.webhookUsername,
                    error = history.exceptionOrNull()?.message,
                )
            }
            markUnread()
        }
    }

    /** Unread is the count the web keeps too: how many arrived since the list was last opened. */
    private suspend fun markUnread() {
        val seen = preferencesStore.preferences.first().notificationsRead
        _state.update { it.copy(unread = (it.notifications.size - seen).coerceAtLeast(0)) }
    }

    fun markAllRead() {
        viewModelScope.launch {
            preferencesStore.setNotificationsRead(_state.value.notifications.size)
            _state.update { it.copy(unread = 0) }
            PushNotifier.clear(context)
        }
    }

    fun delete(notification: TmNotification) {
        viewModelScope.launch {
            val result = runCatching { apiProvider.api().deleteNotification(DeleteNotificationRequest(notification.ts)) }
            val ok = result.getOrNull()?.ok == true
            if (ok) {
                _state.update { current ->
                    current.copy(
                        notifications = current.notifications.filterNot { it.ts == notification.ts },
                        message = "Notification deleted",
                    )
                }
                preferencesStore.setNotificationsRead(_state.value.notifications.size)
                markUnread()
            } else {
                _state.update {
                    it.copy(message = result.exceptionOrNull()?.message ?: "Could not delete the notification")
                }
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val result = runCatching { apiProvider.api().clearNotifications() }
            if (result.getOrNull()?.ok == true) {
                preferencesStore.setNotificationsRead(0)
                PushNotifier.clear(context)
                _state.update { it.copy(notifications = emptyList(), unread = 0, message = "History cleared") }
            } else {
                _state.update {
                    it.copy(message = result.exceptionOrNull()?.message ?: "Could not clear the history")
                }
            }
        }
    }

    fun onUrlChange(value: String) = _state.update { it.copy(webhookUrl = value, test = TestState.Idle) }

    fun onTypeChange(value: String) = _state.update { it.copy(webhookType = value, test = TestState.Idle) }

    fun onUsernameChange(value: String) = _state.update { it.copy(webhookUsername = value) }

    fun onPasswordChange(value: String) = _state.update { it.copy(webhookPassword = value) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Uses the endpoint's own field names, which differ from the ones the settings document uses. */
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
                                TestState.Failed(result.error ?: "The webhook was not delivered")
                            },
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update { it.copy(test = TestState.Failed(throwable.message ?: "Delivery failed")) }
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
                    _state.update { it.copy(saving = false, message = "Webhook saved", webhookPassword = "") }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(saving = false, message = throwable.message ?: "Could not save the webhook")
                    }
                },
            )
        }
    }
}
