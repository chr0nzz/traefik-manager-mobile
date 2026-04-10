package dev.chr0nzz.traefikmanager.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val (baseUrl, apiKey) = readCredentials()
            val (ok, warn, error) = fetchOverview(baseUrl, apiKey)
            pushSuccess(ok, warn, error)
            Result.success()
        } catch (e: Exception) {
            pushOffline(e.message ?: e.javaClass.simpleName)
            Result.retry()
        }
    }

    private fun readCredentials(): Pair<String, String> {
        val file = java.io.File(applicationContext.filesDir, "tm_widget_creds.json")
        if (!file.exists()) throw Exception("not connected - open the app and connect first")
        val json = JSONObject(file.readText())
        return Pair(json.getString("baseUrl"), json.optString("apiKey", ""))
    }

    private fun fetchOverview(baseUrl: String, apiKey: String): Triple<Int, Int, Int> {
        val conn = URL("$baseUrl/api/traefik/overview").openConnection() as HttpURLConnection
        conn.setRequestProperty("X-Api-Key", apiKey)
        conn.setRequestProperty("X-Requested-With", "fetch")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val root = JSONObject(body)
        var ok = 0; var warn = 0; var error = 0
        for (proto in listOf("http", "tcp", "udp")) {
            if (!root.has(proto)) continue
            val services = root.getJSONObject(proto).optJSONObject("services") ?: continue
            val total = services.optInt("total", 0)
            val w = services.optInt("warnings", 0)
            val e = services.optInt("errors", 0)
            warn += w
            error += e
            ok += (total - w - e).coerceAtLeast(0)
        }
        return Triple(ok, warn, error)
    }

    private suspend fun pushSuccess(ok: Int, warn: Int, error: Int) {
        val ctx = applicationContext
        val ids = GlanceAppWidgetManager(ctx).getGlanceIds(StatusWidget::class.java)
        ids.forEach { id ->
            updateAppWidgetState(ctx, id) { prefs ->
                prefs[StatusWidget.okKey] = ok
                prefs[StatusWidget.warnKey] = warn
                prefs[StatusWidget.errorCountKey] = error
                prefs[StatusWidget.offlineKey] = false
                prefs[StatusWidget.updatedAtKey] = System.currentTimeMillis()
            }
        }
        StatusWidget().updateAll(ctx)
    }

    private suspend fun pushOffline(msg: String) {
        val ctx = applicationContext
        val ids = GlanceAppWidgetManager(ctx).getGlanceIds(StatusWidget::class.java)
        ids.forEach { id ->
            updateAppWidgetState(ctx, id) { prefs ->
                prefs[StatusWidget.offlineKey] = true
                prefs[StatusWidget.updatedAtKey] = System.currentTimeMillis()
            }
        }
        StatusWidget().updateAll(ctx)
    }

    companion object {
        private const val WORK_NAME = "tm_widget_update"

        fun enqueuePeriodicWork(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
