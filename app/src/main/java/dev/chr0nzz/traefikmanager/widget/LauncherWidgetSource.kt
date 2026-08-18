package dev.chr0nzz.traefikmanager.widget

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.DashboardConfig
import dev.chr0nzz.traefikmanager.data.model.LauncherBuilder
import dev.chr0nzz.traefikmanager.data.model.RouteIcons
import dev.chr0nzz.traefikmanager.data.repo.RoutesRepository
import dev.chr0nzz.traefikmanager.data.repo.ServersRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One app on a launcher widget. [icon] is a file we already downloaded, or empty. */
@Serializable
data class LauncherEntry(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val icon: String = "",
    val serverId: String = "",
    val serverName: String = "",
)

@Serializable
data class LauncherWidgetPayload(
    val apps: List<LauncherEntry> = emptyList(),
    val note: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(value: LauncherWidgetPayload): String = json.encodeToString(value)

        fun decode(raw: String?): LauncherWidgetPayload? {
            if (raw.isNullOrEmpty()) return null
            return runCatching { json.decodeFromString<LauncherWidgetPayload>(raw) }.getOrNull()
        }
    }
}

/**
 * Builds the app list a launcher widget draws, across however many servers it watches.
 *
 * Glance cannot load a URL, so every icon is fetched here and written to the cache as a PNG the
 * widget can point at. The files are keyed by icon URL and reused, so a refresh costs nothing when
 * nothing changed.
 */
@Singleton
class LauncherWidgetSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apiProvider: ApiProvider,
    private val routesRepository: RoutesRepository,
    private val serversRepository: ServersRepository,
    private val imageLoader: ImageLoader,
) {

    suspend fun load(serverIds: List<String?>): LauncherWidgetPayload = coroutineScope {
        val known = runCatching { serversRepository.servers(probeHealth = false) }.getOrDefault(emptyList())
        val targets = serverIds.ifEmpty { listOf(null) }
        val perServer = targets.map { id ->
            async {
                runCatching { appsFor(id, known.firstOrNull { it.id == id }?.name ?: "Host") }
                    .getOrDefault(emptyList())
            }
        }.flatMap { it.await() }

        if (perServer.isEmpty()) {
            LauncherWidgetPayload(note = "Nothing to launch")
        } else {
            LauncherWidgetPayload(apps = perServer.map { entry -> entry.copy(icon = cache(entry.icon)) })
        }
    }

    private suspend fun appsFor(serverId: String?, serverName: String): List<LauncherEntry> {
        val ready = apiProvider.ready()
        val api = apiProvider.apiFor(serverId)
        val config = runCatching { api.dashboardConfig(serverId) }.getOrDefault(DashboardConfig())
        val routes = runCatching { routesRepository.loadFor(serverId).routes }.getOrDefault(emptyList())
        return LauncherBuilder.build(routes, config, includeHidden = false)
            .flatMap { group -> group.apps }
            .filter { it.url != null }
            .map { app ->
                LauncherEntry(
                    id = "${serverId.orEmpty()}|${app.id}",
                    name = app.name,
                    url = app.url.orEmpty(),
                    icon = RouteIcons.urlFor(app.route, config, ready.baseUrl).orEmpty(),
                    serverId = serverId.orEmpty(),
                    serverName = serverName,
                )
            }
    }

    /** Downloads an icon once and hands back a file path Glance can draw. */
    private suspend fun cache(url: String): String = withContext(Dispatchers.IO) {
        if (url.isEmpty()) return@withContext ""
        // The earlier release cropped icons at fetch time, and the cache keys by URL with no
        // expiry - so the bad pixels would be served forever. A new directory retires them all.
        File(context.cacheDir, "widget-icons").takeIf { it.exists() }?.deleteRecursively()
        val dir = File(context.cacheDir, "widget-icons-v2").apply { mkdirs() }
        val file = File(dir, url.hashCode().toString() + ".png")
        if (file.exists() && file.length() > 0) return@withContext file.absolutePath
        val result = runCatching {
            imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build(),
            )
        }.getOrNull() as? SuccessResult ?: return@withContext ""
        // toBitmap(w, h) draws into a canvas of that size rather than scaling to it, which cropped
        // every icon to its top-left corner. Take the whole bitmap, then scale it ourselves.
        val image = result.image
        val full: Bitmap = runCatching {
            (image as? coil3.BitmapImage)?.bitmap ?: image.toBitmap(image.width, image.height)
        }.getOrNull() ?: return@withContext ""
        val longest = maxOf(full.width, full.height).coerceAtLeast(1)
        val bitmap = if (longest <= 192) {
            full
        } else {
            val scale = 192f / longest
            Bitmap.createScaledBitmap(
                full,
                (full.width * scale).toInt().coerceAtLeast(1),
                (full.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        }
        runCatching {
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            file.absolutePath
        }.getOrDefault("")
    }
}
