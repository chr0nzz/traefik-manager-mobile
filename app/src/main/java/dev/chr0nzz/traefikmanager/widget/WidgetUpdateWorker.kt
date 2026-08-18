package dev.chr0nzz.traefikmanager.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps every placed widget's cached payload current.
 *
 * One periodic job serves all of them, running at the shortest interval any widget asked for;
 * each widget is only refetched once its own interval has elapsed. That keeps a single 15 minute
 * widget from dragging every other widget onto a 15 minute cadence.
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun widgetDataSource(): WidgetDataSource

        fun launcherWidgetSource(): LauncherWidgetSource
    }

    /**
     * Launcher widgets keep their own list. Each one is refreshed for the servers it was set to,
     * and the icons it needs are downloaded once and cached as files Glance can draw.
     */
    private suspend fun refreshLaunchers(
        manager: GlanceAppWidgetManager,
        ids: List<androidx.glance.GlanceId>,
    ) {
        if (ids.isEmpty()) return
        val source = EntryPointAccessors
            .fromApplication(applicationContext, Deps::class.java)
            .launcherWidgetSource()
        ids.forEach { glanceId ->
            val prefs = runCatching { getAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, glanceId) }
                .getOrNull() ?: return@forEach
            val payload = runCatching { source.load(LauncherWidgetConfig.servers(prefs)) }.getOrNull()
                ?: return@forEach
            runCatching {
                updateAppWidgetState(applicationContext, glanceId) { store ->
                    store[LauncherWidgetConfig.PAYLOAD] = LauncherWidgetPayload.encode(payload)
                }
                LauncherWidget().update(applicationContext, glanceId)
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val manager = GlanceAppWidgetManager(applicationContext)
        val ids = runCatching { manager.getGlanceIds(StatusWidget::class.java) }.getOrDefault(emptyList())
        val launcherIds = runCatching { manager.getGlanceIds(LauncherWidget::class.java) }
            .getOrDefault(emptyList())
        refreshLaunchers(manager, launcherIds)
        if (ids.isEmpty() && launcherIds.isEmpty()) {
            // Nothing is placed any more, so stop waking up for it.
            cancelPeriodic(applicationContext)
            return@withContext Result.success()
        }

        val source = EntryPointAccessors
            .fromApplication(applicationContext, Deps::class.java)
            .widgetDataSource()
        val forced = inputData.getBoolean(KEY_FORCE, false)
        val onlyId = inputData.getString(KEY_ONLY_ID)
        val now = System.currentTimeMillis()
        var failures = 0

        val configs = ids.associateWith { readConfig(it) }

        configs.forEach { (glanceId, config) ->
            if (onlyId != null && glanceId.toString() != onlyId) return@forEach
            val updatedAt = readUpdatedAt(glanceId)
            val due = forced || updatedAt == 0L ||
                now - updatedAt >= TimeUnit.MINUTES.toMillis(config.intervalMinutes.toLong())
            if (!due) return@forEach

            // Only the server on screen is fetched; the rest arrive when the user turns to them.
            val page = readPage(glanceId).coerceIn(0, config.pages.lastIndex)
            val slot = config.pages[page]
            val result = runCatching {
                source.load(config.copy(cards = slot.preset.cards, serverId = slot.serverId))
            }
            updateAppWidgetState(applicationContext, glanceId) { prefs ->
                result.fold(
                    onSuccess = { payload ->
                        val held = WidgetPayloads.decode(prefs[WidgetConfig.PAYLOAD])
                        prefs[WidgetConfig.PAYLOAD] = WidgetPayloads.encode(
                            held.copy(byServer = held.byServer + (slot.encode() to payload)),
                        )
                        prefs[WidgetConfig.UPDATED_AT] = now
                        prefs.remove(WidgetConfig.ERROR)
                    },
                    onFailure = {
                        failures++
                        // Keep the last good payload: a stale number beats an empty widget.
                        prefs[WidgetConfig.ERROR] = "offline"
                    },
                )
            }
            StatusWidget().update(applicationContext, glanceId)
        }

        // Whoever wants the shortest interval sets the cadence for the job that serves them all.
        schedulePeriodic(applicationContext, configs.values.minOf { it.intervalMinutes })
        if (failures > 0 && failures == ids.size) Result.retry() else Result.success()
    }

    private suspend fun readConfig(glanceId: GlanceId): WidgetConfig {
        var config = WidgetConfig()
        updateAppWidgetState(applicationContext, glanceId) { prefs: MutablePreferences ->
            config = WidgetConfig.read(prefs)
        }
        return config
    }

    private suspend fun readPage(glanceId: GlanceId): Int {
        var page = 0
        updateAppWidgetState(applicationContext, glanceId) { prefs -> page = prefs[WidgetConfig.PAGE] ?: 0 }
        return page
    }

    private suspend fun readUpdatedAt(glanceId: GlanceId): Long {
        var stamp = 0L
        updateAppWidgetState(applicationContext, glanceId) { prefs: MutablePreferences ->
            stamp = prefs[WidgetConfig.UPDATED_AT] ?: 0L
        }
        return stamp
    }

    companion object {
        private const val PERIODIC = "tm-widget-periodic"
        private const val IMMEDIATE = "tm-widget-immediate"
        private const val KEY_FORCE = "force"
        private const val KEY_ONLY_ID = "only"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Refresh now, either everything or a single widget that was just placed or tapped. */
        fun refreshNow(context: Context, glanceId: GlanceId? = null) {
            val data = Data.Builder()
                .putBoolean(KEY_FORCE, true)
                .apply { glanceId?.let { putString(KEY_ONLY_ID, it.toString()) } }
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                    .setConstraints(constraints)
                    .setInputData(data)
                    .build(),
            )
        }

        /**
         * Runs at the shortest interval any placed widget wants, which is the only way a 15 minute
         * widget can be served at all: WorkManager will not schedule periodic work below that.
         */
        fun schedulePeriodic(context: Context, minutes: Int = WidgetConfig.DEFAULT_INTERVAL_MINUTES) {
            val interval = minutes.coerceAtLeast(WidgetConfig.MIN_INTERVAL_MINUTES).toLong()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<WidgetUpdateWorker>(interval, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build(),
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
