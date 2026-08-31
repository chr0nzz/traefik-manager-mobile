package dev.chr0nzz.traefikmanager.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dev.chr0nzz.traefikmanager.R
import java.io.File

object LauncherWidgetConfig {
    val SERVERS = stringPreferencesKey("launcher_servers")
    val HIDE_NAMES = booleanPreferencesKey("launcher_hide_names")
    val PAYLOAD = stringPreferencesKey("launcher_payload")

    fun servers(prefs: Preferences): List<String?> = prefs[SERVERS]
        ?.split(',')
        ?.map { it.trim().takeIf { id -> id.isNotEmpty() } }
        ?: listOf(null)
}

private const val MAX_GRID_COLUMNS = 5

private fun openIntent(url: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

class LauncherWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val payload = LauncherWidgetPayload.decode(prefs[LauncherWidgetConfig.PAYLOAD])
            Body(payload, prefs[LauncherWidgetConfig.HIDE_NAMES] ?: false)
        }
    }

    @Composable
    private fun Body(payload: LauncherWidgetPayload?, hideNames: Boolean) {
        val apps = payload?.apps.orEmpty()
        val width = LocalSize.current.width.value.toInt()
        val cell = if (hideNames) 56 else 68
        val columns = ((width - 12) / cell).coerceIn(1, MAX_GRID_COLUMNS)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg))
                .padding(6.dp),
        ) {
            if (apps.isEmpty()) {
                Text(
                    text = payload?.note?.ifEmpty { null } ?: "Tap to set up",
                    style = TextStyle(color = P.muted, fontSize = 12.sp),
                )
            } else {
                LazyVerticalGrid(
                    gridCells = GridCells.Fixed(columns),
                    modifier = GlanceModifier.fillMaxSize(),
                ) {
                    items(apps, itemId = { it.id.hashCode().toLong() }) { app ->
                        Tile(app, hideNames)
                    }
                }
            }
        }
    }

    @Composable
    private fun Tile(app: LauncherEntry, hideNames: Boolean) {
        Column(
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(actionStartActivity(openIntent(app.url))),
        ) {
            Icon(app.icon, app.name, 44)
            if (!hideNames) {
                Text(
                    text = app.name,
                    style = TextStyle(color = P.text, fontSize = 10.sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun Icon(icon: String, name: String, size: Int) {
    val bitmap = icon.takeIf { it.isNotEmpty() }
        ?.let { File(it) }
        ?.takeIf { it.exists() }
        ?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
    Box(modifier = GlanceModifier.size(size.dp), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.size(size.dp),
            )
        } else {
            Text(
                text = name.take(1).uppercase(),
                style = TextStyle(color = P.muted, fontSize = 15.sp),
            )
        }
    }
}

class LauncherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LauncherWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdateWorker.refreshNow(context)
    }
}
