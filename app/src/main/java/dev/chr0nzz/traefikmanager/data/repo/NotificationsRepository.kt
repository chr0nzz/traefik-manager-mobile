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
import kotlinx.coroutines.flow.stateIn

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
    serverScope: ServerScope,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val _items = MutableStateFlow<List<TmNotification>?>(null)
    val items: StateFlow<List<TmNotification>?> = _items.asStateFlow()

    /** The server's shared marker, or null while unknown or on a server without one. */
    private val _readUntil = MutableStateFlow<Int?>(null)

    private val inFlightLock = Any()
    private var inFlight: Deferred<List<TmNotification>>? = null

    init {
        serverScope.onServerChanged {
            _items.value = null
            _readUntil.value = null
        }
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
                .onSuccess { _readUntil.value = highest }
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
        if (_readUntil.value == null) {
            preferencesStore.setNotificationsRead(_items.value.orEmpty().size)
        }
    }

    suspend fun clear() {
        apiProvider.api().clearNotifications()
        _items.value = emptyList()
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
