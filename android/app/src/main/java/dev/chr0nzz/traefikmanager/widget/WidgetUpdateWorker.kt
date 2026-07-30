package dev.chr0nzz.traefikmanager.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class ServerStatus(
    val name: String,
    val ok: Int,
    val warn: Int,
    val err: Int,
    val reachable: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("ok", ok)
        .put("warn", warn)
        .put("err", err)
        .put("reachable", reachable)
}

class WidgetUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val creds = readCredentials() ?: run {
            pushOffline()
            return@withContext Result.failure()
        }
        try {
            val servers = fetchAllServers(creds.first, creds.second)
            if (servers.none { it.reachable }) {
                pushOffline()
                Result.retry()
            } else {
                pushSuccess(servers)
                Result.success()
            }
        } catch (e: Exception) {
            pushOffline()
            Result.retry()
        }
    }

    private fun readCredentials(): Pair<String, String>? {
        val file = java.io.File(applicationContext.filesDir, "tm_widget_creds.json")
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            Pair(json.getString("baseUrl").trimEnd('/'), json.optString("apiKey", ""))
        } catch (e: Exception) {
            null
        }
    }

    private class HttpResult(val code: Int, val body: String)

    private fun get(url: String, apiKey: String): HttpResult {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("X-Api-Key", apiKey)
            conn.setRequestProperty("X-Requested-With", "fetch")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: return HttpResult(code, ""))
            return HttpResult(code, stream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    private fun parseOverview(body: String): Triple<Int, Int, Int> {
        val root = JSONObject(body)
        var ok = 0; var warn = 0; var err = 0
        for (proto in listOf("http", "tcp", "udp")) {
            if (!root.has(proto)) continue
            val services = root.optJSONObject(proto)?.optJSONObject("services") ?: continue
            val total = services.optInt("total", 0)
            val w = services.optInt("warnings", 0)
            val e = services.optInt("errors", 0)
            warn += w
            err += e
            ok += (total - w - e).coerceAtLeast(0)
        }
        return Triple(ok, warn, err)
    }

    private fun fetchAgentList(baseUrl: String, apiKey: String): List<Pair<String, String>>? {
        val res = get("$baseUrl/api/agents", apiKey)
        if (res.code == 404) return null
        if (res.code !in 200..299) throw Exception("agents list failed: HTTP ${res.code}")
        val arr: JSONArray = JSONObject(res.body).optJSONArray("agents") ?: JSONArray()
        val out = ArrayList<Pair<String, String>>(arr.length())
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            val id = a.optString("id", "")
            if (id.isEmpty()) continue
            out.add(Pair(id, a.optString("name", id)))
        }
        return out
    }

    private fun fetchServer(name: String, url: String, apiKey: String): ServerStatus {
        return try {
            val res = get(url, apiKey)
            if (res.code !in 200..299) throw Exception("HTTP ${res.code}")
            val (ok, warn, err) = parseOverview(res.body)
            ServerStatus(name, ok, warn, err, reachable = true)
        } catch (e: Exception) {
            ServerStatus(name, 0, 0, 0, reachable = false)
        }
    }

    private suspend fun fetchAllServers(baseUrl: String, apiKey: String): List<ServerStatus> {
        val agents = fetchAgentList(baseUrl, apiKey)
        return coroutineScope {
            val gate = Semaphore(4)
            val host = async {
                gate.withPermit { fetchServer("Host", "$baseUrl/api/traefik/overview", apiKey) }
            }
            val rest = (agents ?: emptyList()).map { (id, name) ->
                async {
                    gate.withPermit {
                        fetchServer(name, "$baseUrl/api/agents/proxy/$id/traefik/overview", apiKey)
                    }
                }
            }
            listOf(host.await()) + rest.awaitAll()
        }
    }

    private suspend fun pushSuccess(servers: List<ServerStatus>) {
        val ctx = applicationContext
        val ids = GlanceAppWidgetManager(ctx).getGlanceIds(StatusWidget::class.java)
        val json = JSONArray().also { arr -> servers.forEach { arr.put(it.toJson()) } }.toString()
        ids.forEach { id ->
            updateAppWidgetState(ctx, id) { prefs ->
                prefs[StatusWidget.serversJsonKey] = json
                prefs[StatusWidget.offlineKey] = false
                prefs[StatusWidget.updatedAtKey] = System.currentTimeMillis()
            }
        }
        StatusWidget().updateAll(ctx)
    }

    private suspend fun pushOffline() {
        val ctx = applicationContext
        val ids = GlanceAppWidgetManager(ctx).getGlanceIds(StatusWidget::class.java)
        ids.forEach { id ->
            updateAppWidgetState(ctx, id) { prefs ->
                prefs[StatusWidget.offlineKey] = true
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
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "tm_widget_refresh", ExistingWorkPolicy.KEEP, request
            )
        }
    }
}
