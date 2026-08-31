package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.DeleteNotificationRequest
import dev.chr0nzz.traefikmanager.data.model.MarkReadRequest
import dev.chr0nzz.traefikmanager.data.model.TmNotification
import dev.chr0nzz.traefikmanager.data.store.PreferencesStore
import dev.chr0nzz.traefikmanager.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Singleton
class NotificationsRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val preferencesStore: PreferencesStore,
    private val serverScope: ServerScope,
    private val json: Json,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val _items = MutableStateFlow<List<TmNotification>?>(null)
    val items: StateFlow<List<TmNotification>?> = _items.asStateFlow()

    private val _readUntil = MutableStateFlow<Int?>(null)

    private val inFlightLock = Any()

    private companion object {
        const val HOST = "host"
    }

    private var inFlight: Deferred<List<TmNotification>>? = null

    init {
        scope.launch { restore() }
        serverScope.onServerChanged {
            _items.value = null
            _readUntil.value = null
        }
    }

    private fun key(): String = serverScope.activeAgentId.value ?: HOST

    private suspend fun restore() {
        val prefs = preferencesStore.preferences.first()
        if (prefs.notificationsCache.isBlank() || prefs.notificationsCacheServer != key()) return
        val cached = runCatching {
            json.decodeFromString<List<TmNotification>>(prefs.notificationsCache)
        }.getOrNull() ?: return
        if (_items.value == null) {
            _items.value = cached
            if (prefs.notificationsReadUntil >= 0) _readUntil.value = prefs.notificationsReadUntil
        }
    }

    private suspend fun persist(list: List<TmNotification>) {
        val payload = runCatching { json.encodeToString(list) }.getOrNull() ?: return
        preferencesStore.setNotificationsCache(key(), payload, _readUntil.value ?: -1)
    }

    val unread: StateFlow<Int> =
        combine(_items, _readUntil, preferencesStore.preferences) { items, marker, prefs ->
            val list = items.orEmpty()
            if (marker != null) {
                list.count { it.id > marker }
            } else {
                (list.size - prefs.notificationsRead).coerceAtLeast(0)
            }
        }.stateIn(scope, SharingStarted.Eagerly, 0)

    suspend fun refresh(): List<TmNotification> {
        val job = synchronized(inFlightLock) {
            inFlight?.takeIf { it.isActive } ?: scope.async { fetch() }.also { inFlight = it }
        }
        return job.await()
    }

    private suspend fun fetch(): List<TmNotification> {
        val api = apiProvider.api()
        val list = api.notifications()
        _items.value = list
        _readUntil.value = runCatching { api.notificationState().readUntil }.getOrNull()
        persist(list)
        return list
    }

    suspend fun markRead() {
        val list = _items.value.orEmpty()
        val highest = list.maxOfOrNull { it.id } ?: 0
        if (_readUntil.value != null && highest > 0) {
            runCatching { apiProvider.api().markNotificationsRead(MarkReadRequest(highest)) }
                .onSuccess {
                    _readUntil.value = highest
                    persist(list)
                }
        } else {
            preferencesStore.setNotificationsRead(list.size)
        }
    }

    suspend fun delete(notification: TmNotification) {
        apiProvider.api().deleteNotification(
            DeleteNotificationRequest(
                ts = notification.ts,
                id = notification.id.takeIf { it > 0 },
            ),
        )
        _items.value = _items.value?.filterNot {
            if (notification.id > 0) it.id == notification.id else it.ts == notification.ts
        }
        persist(_items.value.orEmpty())
        if (_readUntil.value == null) {
            preferencesStore.setNotificationsRead(_items.value.orEmpty().size)
        }
    }

    suspend fun clear() {
        apiProvider.api().clearNotifications()
        _items.value = emptyList()
        persist(emptyList())
        if (_readUntil.value == null) preferencesStore.setNotificationsRead(0)
    }

    suspend fun sharedMarker(): Boolean {
        if (_readUntil.value != null) return true
        return runCatching { apiProvider.api().notificationState() }
            .onSuccess { _readUntil.value = it.readUntil }
            .isSuccess
    }
}
