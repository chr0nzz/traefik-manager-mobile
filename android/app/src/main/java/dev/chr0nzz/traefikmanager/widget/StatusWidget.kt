package dev.chr0nzz.traefikmanager.widget

import android.content.Context
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class StatusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetUpdateWorker.enqueueImmediate(context)

        val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ColorProviders(
                light = dynamicLightColorScheme(context),
                dark = dynamicDarkColorScheme(context)
            )
        } else {
            ColorProviders(light = lightColorScheme(), dark = darkColorScheme())
        }

        provideContent {
            GlanceTheme(colors = colors) {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        val prefs = currentState<Preferences>()
        val ok = prefs[okKey] ?: -1
        val warn = prefs[warnKey] ?: -1
        val errorCount = prefs[errorCountKey] ?: -1
        val offline = prefs[offlineKey] ?: false
        val updatedAt = prefs[updatedAtKey]
        val hasData = ok >= 0
        val total = if (hasData) (ok + warn + errorCount).coerceAtLeast(0) else 0

        fun pct(n: Int) = if (total > 0) "${n * 100 / total}%" else "0%"

        val ctx2 = LocalContext.current
        val isNight2 = ctx2.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bgScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isNight2) dynamicDarkColorScheme(ctx2) else dynamicLightColorScheme(ctx2)
        } else {
            if (isNight2) darkColorScheme() else lightColorScheme()
        }
        val bgColor = ColorProvider(bgScheme.surfaceContainer)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgColor)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = GlanceModifier.defaultWeight().clickable(
                            actionRunCallback<OpenAppAction>()
                        )
                    ) {
                        Text(
                            "Services",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        if (offline && hasData) {
                            Text(
                                "offline",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFFf59e0b)),
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                    Box(
                        modifier = GlanceModifier
                            .size(28.dp)
                            .clickable(actionRunCallback<RefreshAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "↻",
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp)
                        )
                    }
                }

                Spacer(GlanceModifier.height(8.dp))

                val ctx = LocalContext.current
                val isNight = ctx.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isNight) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
                } else {
                    if (isNight) darkColorScheme() else lightColorScheme()
                }
                fun toArgb(c: androidx.compose.ui.graphics.Color) = android.graphics.Color.argb(
                    (c.alpha * 255).toInt(), (c.red * 255).toInt(),
                    (c.green * 255).toInt(), (c.blue * 255).toInt()
                )

                if (!hasData) {
                    Text(
                        if (offline) "Could not connect.\nOpen the app and connect first." else "Loading...",
                        style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 11.sp),
                        maxLines = 3
                    )
                } else {
                    val bmp = RingBitmap.create(ok, warn, errorCount, 130, toArgb(scheme.onSurface), toArgb(scheme.surfaceVariant))

                    Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(
                            provider = ImageProvider(bmp),
                            contentDescription = "service ring",
                            contentScale = ContentScale.Fit,
                            modifier = GlanceModifier.size(65.dp)
                        )
                    }

                    Spacer(GlanceModifier.height(8.dp))

                    StatRow(dotArgb = 0xFF22c55e.toInt(), label = "OK",   pct = pct(ok),         count = ok)
                    Spacer(GlanceModifier.height(4.dp))
                    StatRow(dotArgb = 0xFFf59e0b.toInt(), label = "Warn", pct = pct(warn),       count = warn)
                    Spacer(GlanceModifier.height(4.dp))
                    StatRow(dotArgb = 0xFFef4444.toInt(), label = "Err",  pct = pct(errorCount), count = errorCount)
                }

                Spacer(GlanceModifier.defaultWeight())

                if (updatedAt != null) {
                    Text(
                        android.text.format.DateUtils.getRelativeTimeSpanString(
                            updatedAt,
                            System.currentTimeMillis(),
                            android.text.format.DateUtils.MINUTE_IN_MILLIS
                        ).toString(),
                        style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 9.sp)
                    )
                }
            }
        }
    }

    @Composable
    private fun StatRow(dotArgb: Int, label: String, pct: String, count: Int) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(RingBitmap.circle(dotArgb, 24)),
                contentDescription = null,
                modifier = GlanceModifier.size(10.dp)
            )
            Spacer(GlanceModifier.width(5.dp))
            Text(
                label,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 11.sp),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                pct,
                style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 10.sp)
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                count.toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    companion object {
        val okKey = intPreferencesKey("ok")
        val warnKey = intPreferencesKey("warn")
        val errorCountKey = intPreferencesKey("error_count")
        val offlineKey = booleanPreferencesKey("offline")
        val updatedAtKey = longPreferencesKey("updated_at")
    }
}
