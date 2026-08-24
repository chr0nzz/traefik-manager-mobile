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

/**
 * One copy of the notification list for the bell and the history screen.
 *
 * They used to fetch it separately, which cost two round trips to show one list. The list is kept
 * so a revisit paints from what was last read while the refresh runs behind it.
 *
 * Unread is counted two ways. From 1.12.0 the server keeps a shared marker and every row has an
 * id, so what is read agrees across the web and every phone. Older servers have neither, and the
 * only handle is the length of the list against a count this device remembers - which quietly
 * stops working once the server's 200 entry cap is reached and the length stops changing.
 */
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

    /** The server's shared marker, or null while unknown or on a server without one. */
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

    /**
     * Draw last night's list while today's is on its way. Only for the server it was written on:
     * every server keeps its own notifications, and showing one server's under another's name
     * would be worse than a spinner.
     */
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

    /** The bell and the history ask at the same moment, so they share one fetch. */
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

    /** Marks everything currently known read, for every client when the server keeps the marker. */
    suspend fun markRead() {
        val list = _items.value.orEmpty()
        val highest = list.maxOfOrNull { it.id } ?: 0
        // The highest id we have actually seen, never "everything": a notification that landed
        // between the last refresh and this tap has not been read by anyone yet.
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

    /** True when the server keeps the shared read marker, for anything that needs to know. */
    suspend fun sharedMarker(): Boolean {
        if (_readUntil.value != null) return true
        return runCatching { apiProvider.api().notificationState() }
            .onSuccess { _readUntil.value = it.readUntil }
            .isSuccess
    }
}
