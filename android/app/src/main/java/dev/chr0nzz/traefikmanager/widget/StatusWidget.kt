package dev.chr0nzz.traefikmanager.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as dayNight
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.chr0nzz.traefikmanager.R

private object P {
    val text   = dayNight(day = Color(0xFF1F2328), night = Color(0xFFE6EDF3))
    val muted  = dayNight(day = Color(0xFF636E7B), night = Color(0xFF7D8590))
    val border = dayNight(day = Color(0xFFD0D7DE), night = Color(0xFF30363D))
    val green  = dayNight(day = Color(0xFF1A7F37), night = Color(0xFF22C55E))
    val yellow = dayNight(day = Color(0xFF9A6700), night = Color(0xFFF59E0B))
    val red    = dayNight(day = Color(0xFFCF222E), night = Color(0xFFEF4444))
    val blue   = dayNight(day = Color(0xFF0969DA), night = Color(0xFF24A1DE))

}

private class BarInts(
    val track: Int, val ok: Int, val warn: Int, val err: Int,
)

private fun barInts(context: Context): BarInts {
    val isNight = context.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    return if (isNight) {
        BarInts(0xFF30363D.toInt(), 0xFF22C55E.toInt(), 0xFFF59E0B.toInt(), 0xFFEF4444.toInt())
    } else {
        BarInts(0xFFD0D7DE.toInt(), 0xFF1A7F37.toInt(), 0xFF9A6700.toInt(), 0xFFCF222E.toInt())
    }
}

class StatusWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(SMALL, MEDIUM, WIDE, TALL, WIDE_TALL, XWIDE, XWIDE_TALL)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }

    @Composable
    private fun WidgetContent() {
        val prefs     = currentState<Preferences>()
        val servers   = parseServers(prefs[serversJsonKey])
        val offline   = prefs[offlineKey] ?: false
        val updatedAt = prefs[updatedAtKey]
        val size      = LocalSize.current

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg), contentScale = ContentScale.FillBounds)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when {
                servers == null              -> EmptyState(offline)
                size.width >= XWIDE.width    -> ServerList(servers, offline, updatedAt, size, showBars = true, fullChips = true)
                size.width >= WIDE.width     -> ServerList(servers, offline, updatedAt, size, showBars = true, fullChips = false)
                size.height >= MEDIUM.height -> ServerList(servers, offline, updatedAt, size, showBars = false, fullChips = false)
                else                         -> Aggregate(servers, offline, updatedAt, size)
            }
        }
    }

    @Composable
    private fun EmptyState(offline: Boolean) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Header(dotColor = P.muted, title = "Traefik Manager")
            Spacer(GlanceModifier.height(6.dp))
            Text(
                if (offline) "Could not connect.\nOpen app to reconnect." else "Loading...",
                style = TextStyle(color = P.muted, fontSize = 11.sp),
                maxLines = 3,
            )
        }
    }

    @Composable
    private fun Aggregate(servers: List<ServerStatus>, offline: Boolean, updatedAt: Long?, size: DpSize) {
        val agg = aggregate(servers)
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Header(dotColor = agg.stateColor(offline), title = "Traefik Manager")
            Spacer(GlanceModifier.height(6.dp))
            SegmentBar(agg.ok, agg.warn, agg.err, widthDp = size.width.value - 28f, stretch = true)
            Spacer(GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                CountText(agg.ok, "ok", P.green)
                if (agg.warn > 0) CountText(agg.warn, "warn", P.yellow)
                if (agg.err > 0) CountText(agg.err, "err", P.red)
                if (agg.down > 0) CountText(agg.down, "down", P.muted)
                Spacer(GlanceModifier.defaultWeight())
                Footer(servers.size, 0, offline, updatedAt, compact = true)
            }
        }
    }

    @Composable
    private fun ServerList(
        servers: List<ServerStatus>,
        offline: Boolean,
        updatedAt: Long?,
        size: DpSize,
        showBars: Boolean,
        fullChips: Boolean,
    ) {
        val agg     = aggregate(servers)
        val maxRows = if (size.height >= TALL.height) 6 else 3
        val shown   = servers.take(maxRows)
        val more    = servers.size - shown.size

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Header(dotColor = agg.stateColor(offline), title = "Servers")
            Spacer(GlanceModifier.height(5.dp))
            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(P.border)) {}
            Spacer(GlanceModifier.height(4.dp))
            shown.forEach { s ->
                ServerRow(s, showBars, fullChips)
                Spacer(GlanceModifier.height(2.dp))
            }
            Spacer(GlanceModifier.defaultWeight())
            Footer(servers.size, more, offline, updatedAt, compact = false)
        }
    }

    @Composable
    private fun ServerRow(s: ServerStatus, showBar: Boolean, fullChips: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(actionRunCallback<OpenAppAction>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(if (!s.reachable) P.muted else if (s.err > 0) P.red else if (s.warn > 0) P.yellow else P.green)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                s.name,
                style = TextStyle(color = P.text, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (!s.reachable) {
                Text("unreachable", style = TextStyle(color = P.muted, fontSize = 10.sp))
            } else if (showBar) {
                SegmentBar(s.ok, s.warn, s.err, widthDp = if (fullChips) 80f else 56f)
                Spacer(GlanceModifier.width(8.dp))
                Chip(if (fullChips) "${s.ok} ok" else "${s.ok}", P.green, R.drawable.chip_green)
                if (s.warn > 0) { Spacer(GlanceModifier.width(4.dp)); Chip(if (fullChips) "${s.warn} warn" else "${s.warn}", P.yellow, R.drawable.chip_yellow) }
                if (s.err > 0) { Spacer(GlanceModifier.width(4.dp)); Chip(if (fullChips) "${s.err} err" else "${s.err}", P.red, R.drawable.chip_red) }
            } else {
                CountPlain(s.ok, P.green)
                if (s.warn > 0) { SepDot(); CountPlain(s.warn, P.yellow) }
                if (s.err > 0) { SepDot(); CountPlain(s.err, P.red) }
            }
        }
    }

    @Composable
    private fun Header(dotColor: ColorProvider, title: String) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(dotColor)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                title,
                style = TextStyle(color = P.text, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight().clickable(actionRunCallback<OpenAppAction>()),
            )
            Box(
                modifier = GlanceModifier.size(40.dp).clickable(actionRunCallback<RefreshAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = "Refresh",
                    colorFilter = ColorFilter.tint(P.blue),
                    modifier = GlanceModifier.size(15.dp),
                )
            }
        }
    }

    @Composable
    private fun Footer(total: Int, more: Int, offline: Boolean, updatedAt: Long?, compact: Boolean) {
        val parts = mutableListOf<String>()
        if (offline) parts.add("offline")
        if (more > 0) parts.add("+$more more")
        if (!compact && total > 1) parts.add("$total servers")
        if (updatedAt != null) {
            parts.add(
                android.text.format.DateUtils.getRelativeTimeSpanString(
                    updatedAt,
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS
                ).toString()
            )
        }
        if (parts.isEmpty()) return
        Text(
            parts.joinToString(" · "),
            style = TextStyle(
                color = if (offline) P.yellow else P.muted,
                fontSize = 10.sp,
            ),
            maxLines = 1,
        )
    }

    @Composable
    private fun SegmentBar(ok: Int, warn: Int, err: Int, widthDp: Float, stretch: Boolean = false) {
        val ctx     = LocalContext.current
        val density = ctx.resources.displayMetrics.density
        val bi      = barInts(ctx)
        val bmp = BarBitmap.create(
            widthPx = (widthDp * density).toInt(),
            heightPx = (6 * density).toInt(),
            ok = ok, warn = warn, err = err,
            trackColor = bi.track, okColor = bi.ok, warnColor = bi.warn, errColor = bi.err,
        )
        Image(
            provider = ImageProvider(bmp),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = if (stretch) GlanceModifier.fillMaxWidth().height(6.dp)
                       else GlanceModifier.width(widthDp.dp).height(6.dp),
        )
    }

    @Composable
    private fun Dot(color: ColorProvider) {
        Image(
            provider = ImageProvider(R.drawable.widget_dot),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = GlanceModifier.size(8.dp),
        )
    }

    @Composable
    private fun Chip(label: String, fg: ColorProvider, bg: Int) {
        Box(
            modifier = GlanceModifier
                .background(ImageProvider(bg), contentScale = ContentScale.FillBounds)
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(label, style = TextStyle(color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium))
        }
    }

    @Composable
    private fun CountText(n: Int, label: String, color: ColorProvider) {
        Text(
            "$n $label",
            style = TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.padding(end = 6.dp),
        )
    }

    @Composable
    private fun CountPlain(n: Int, color: ColorProvider) {
        Text(
            n.toString(),
            style = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
    }

    @Composable
    private fun SepDot() {
        Text(" · ", style = TextStyle(color = P.muted, fontSize = 11.sp))
    }

    private class Agg(val ok: Int, val warn: Int, val err: Int, val down: Int) {
        fun stateColor(offline: Boolean): ColorProvider = when {
            offline  -> P.muted
            err > 0  -> P.red
            down > 0 -> P.muted
            warn > 0 -> P.yellow
            else     -> P.green
        }
    }

    private fun aggregate(servers: List<ServerStatus>): Agg {
        val reachable = servers.filter { it.reachable }
        return Agg(
            ok   = reachable.sumOf { it.ok },
            warn = reachable.sumOf { it.warn },
            err  = reachable.sumOf { it.err },
            down = servers.count { !it.reachable },
        )
    }

    companion object {
        val SMALL      = DpSize(110.dp, 40.dp)
        val MEDIUM     = DpSize(110.dp, 100.dp)
        val WIDE       = DpSize(215.dp, 100.dp)
        val TALL       = DpSize(110.dp, 170.dp)
        val WIDE_TALL  = DpSize(215.dp, 170.dp)
        val XWIDE      = DpSize(270.dp, 100.dp)
        val XWIDE_TALL = DpSize(270.dp, 170.dp)

        val serversJsonKey = stringPreferencesKey("servers_json")
        val offlineKey     = booleanPreferencesKey("offline")
        val updatedAtKey   = longPreferencesKey("updated_at")

        fun parseServers(json: String?): List<ServerStatus>? {
            if (json.isNullOrEmpty()) return null
            return try {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    ServerStatus(
                        name      = o.optString("name", "?"),
                        ok        = o.optInt("ok", 0),
                        warn      = o.optInt("warn", 0),
                        err       = o.optInt("err", 0),
                        reachable = o.optBoolean("reachable", false),
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
