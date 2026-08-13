package dev.chr0nzz.traefikmanager.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as dayNight
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.ui.theme.TmDarkPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmLightPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmPalette

/**
 * The app's palette, read from the same objects the screens use rather than copied, so the home
 * screen cannot drift from the app.
 */
private object P {
    val text = pick { it.text }
    val muted = pick { it.muted }
    val border = pick { it.border }
    val green = pick { it.green }
    val yellow = pick { it.yellow }
    val red = pick { it.red }
    val blue = pick { it.blue }
    val purple = pick { it.purple }
    val teal = pick { it.teal }
    val orange = pick { it.orange }
    val idle = dayNight(
        day = TmLightPalette.muted.copy(alpha = 0.35f),
        night = TmDarkPalette.muted.copy(alpha = 0.35f),
    )

    private inline fun pick(get: (TmPalette) -> androidx.compose.ui.graphics.Color) =
        dayNight(day = get(TmLightPalette), night = get(TmDarkPalette))

    fun of(wire: String): ColorProvider = when (wire) {
        "ok" -> green
        "warn" -> yellow
        "error" -> red
        "idle" -> idle
        else -> muted
    }

    /** Footer and chip text: quiet unless something is wrong, the way the desk prints them. */
    fun chip(wire: String): ColorProvider = when (wire) {
        "warn" -> yellow
        "error" -> red
        else -> muted
    }
}

private val Mono = FontFamily("monospace")

private val SMALL = DpSize(110.dp, 100.dp)
private val WIDE = DpSize(250.dp, 100.dp)
private val TALL = DpSize(110.dp, 220.dp)
private val LARGE = DpSize(250.dp, 220.dp)

/** How much of each card this widget size can hold. */
private data class Fit(val rows: Int, val cellLines: Int, val perLine: Int, val compact: Boolean)

class StatusWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val config = WidgetConfig.read(prefs)
            val payload = WidgetPayload.decode(prefs[WidgetConfig.PAYLOAD])
            val stale = prefs[WidgetConfig.ERROR] != null
            Desk(config, payload, stale)
        }
    }

    @Composable
    private fun Desk(config: WidgetConfig, payload: WidgetPayload?, stale: Boolean) {
        val size = LocalSize.current
        val wide = size.width >= WIDE.width
        val tall = size.height >= TALL.height
        val slots = when {
            wide && tall -> 4
            wide || tall -> 2
            else -> 1
        }

        val panels = buildList {
            if (config.needsOverview) add(Panel.Overview)
            payload?.cards?.forEach { add(Panel.Card(it)) }
            if (isEmpty()) add(Panel.Empty)
        }.take(slots)

        val fit = when {
            panels.size == 1 && tall -> Fit(rows = 8, cellLines = 3, perLine = if (wide) 24 else 12, compact = !wide)
            panels.size == 1 -> Fit(rows = 3, cellLines = 1, perLine = if (wide) 24 else 12, compact = !wide)
            tall && !wide -> Fit(rows = 3, cellLines = 1, perLine = 12, compact = true)
            else -> Fit(rows = 2, cellLines = 1, perLine = 12, compact = true)
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionRunCallback<OpenAppAction>()),
        ) {
            val grid = panels.chunked(if (wide && panels.size > 1) 2 else 1)
            grid.forEachIndexed { index, rowPanels ->
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    rowPanels.forEachIndexed { column, panel ->
                        Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                            PanelCard(
                                panel = panel,
                                payload = payload,
                                config = config,
                                stale = stale && index == 0 && column == 0,
                                showRefresh = index == 0 && column == rowPanels.lastIndex,
                                fit = fit,
                            )
                        }
                        if (column < rowPanels.lastIndex) Spacer(modifier = GlanceModifier.width(6.dp))
                    }
                }
                if (index < grid.lastIndex) Spacer(modifier = GlanceModifier.height(6.dp))
            }
        }
    }

    @Composable
    private fun PanelCard(
        panel: Panel,
        payload: WidgetPayload?,
        config: WidgetConfig,
        stale: Boolean,
        showRefresh: Boolean,
        fit: Fit,
    ) {
        val health = when (panel) {
            is Panel.Card -> panel.card.health
            Panel.Overview -> overviewHealth(payload)
            Panel.Empty -> "unknown"
        }

        // Trouble tints the whole card border, so the state reads at any corner radius.
        val background = when (health) {
            "error" -> R.drawable.widget_bg_error
            "warn" -> R.drawable.widget_bg_warn
            else -> R.drawable.widget_bg
        }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(background)),
        ) {
            Column(modifier = GlanceModifier.fillMaxSize().padding(10.dp)) {
                when (panel) {
                    is Panel.Card -> CardBody(panel.card, stale, showRefresh, fit)
                    Panel.Overview -> OverviewBody(payload, stale, showRefresh, fit)
                    Panel.Empty -> {
                        Head("Widget", P.muted, R.drawable.ic_widget_servers, stale, showRefresh)
                        Sub(payload?.note?.ifEmpty { null } ?: "Tap to set up")
                    }
                }
            }
        }
    }

    @Composable
    private fun CardBody(card: WidgetCard, stale: Boolean, showRefresh: Boolean, fit: Fit) {
        Head(card.title, accentFor(card.key), glyphFor(card.key), stale, showRefresh)
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Hero(card.hero, card.unit, fit.compact)
            Spacer(modifier = GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.Horizontal.End) {
                if (card.healthLabel.isNotEmpty()) {
                    HealthPill(card.healthLabel, P.of(card.health))
                }
                card.chips.take(2).forEach { chip ->
                    Text(
                        text = chip.label,
                        style = TextStyle(color = P.chip(chip.health), fontSize = 9.sp, fontFamily = Mono),
                        maxLines = 1,
                    )
                }
            }
        }
        if (card.sub.isNotEmpty()) Sub(card.sub)
        if (card.cells.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(5.dp))
            Mosaic(card.cells, fit)
        }
        if (card.rows.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(3.dp))
            RowsBlock(card.rows.take(fit.rows), rowGlyphFor(card.key))
        }
        card.footer.firstOrNull()?.let { first ->
            Spacer(modifier = GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                card.footer.take(3).forEach { chip ->
                    Text(
                        text = chip.label,
                        style = TextStyle(color = P.chip(chip.health), fontSize = 9.sp, fontFamily = Mono),
                        maxLines = 1,
                        modifier = GlanceModifier.padding(end = 8.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun OverviewBody(payload: WidgetPayload?, stale: Boolean, showRefresh: Boolean, fit: Fit) {
        val rows = payload?.servers.orEmpty()
        Head("Servers", accentFor("servers"), R.drawable.ic_widget_servers, stale, showRefresh)
        if (rows.isEmpty()) {
            Sub(payload?.note?.ifEmpty { null } ?: "No servers")
            return
        }
        val down = rows.count { !it.reachable || it.err > 0 }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Hero(rows.size.toString(), if (rows.size == 1) "server" else "servers", fit.compact)
            Spacer(modifier = GlanceModifier.defaultWeight())
            HealthPill(
                if (down == 0) "all up" else "$down down",
                if (down == 0) P.green else P.red,
            )
        }
        Sub(
            buildString {
                append("${rows.sumOf { it.routers }} routers")
                val services = rows.sumOf { it.services }
                if (services > 0) append(" · $services services")
                val bans = rows.filter { it.hasBans }.sumOf { it.bans }
                if (bans > 0) append(" · ${compactCount(bans)} bans")
                val warns = rows.sumOf { it.warn }
                if (warns > 0) append(" · $warns warning")
            },
        )
        val cells = rows.flatMap { it.cells }
        if (cells.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(5.dp))
            Mosaic(cells, fit)
        }
        Spacer(modifier = GlanceModifier.height(3.dp))
        val shown = rows.take(fit.rows)
        Column {
            shown.forEachIndexed { index, row ->
                Column {
                    ServerRow(row)
                    if (index < shown.lastIndex) RowDivider()
                }
            }
        }
        val hidden = rows.size - fit.rows
        if (hidden > 0) {
            Text(
                text = "+$hidden more",
                style = TextStyle(color = P.muted, fontSize = 9.sp),
                modifier = GlanceModifier.padding(top = 2.dp),
            )
        }
    }

    @Composable
    private fun Head(title: String, accent: ColorProvider, glyph: Int, stale: Boolean, showRefresh: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = title.uppercase(),
                style = TextStyle(
                    color = P.muted,
                    fontSize = 9.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (stale) {
                Text(
                    text = "stale",
                    style = TextStyle(color = P.yellow, fontSize = 9.sp, fontFamily = Mono),
                    modifier = GlanceModifier.padding(end = 5.dp),
                )
            }
            if (showRefresh) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = "Refresh",
                    colorFilter = ColorFilter.tint(P.muted),
                    modifier = GlanceModifier
                        .size(12.dp)
                        .padding(end = 5.dp)
                        .clickable(actionRunCallback<RefreshAction>()),
                )
            }
            Image(
                provider = ImageProvider(glyph),
                contentDescription = null,
                colorFilter = ColorFilter.tint(accent),
                modifier = GlanceModifier.size(12.dp),
            )
        }
    }

    @Composable
    private fun Hero(value: String, unit: String, compact: Boolean) {
        Row(verticalAlignment = Alignment.Vertical.Bottom) {
            Text(
                text = value,
                style = TextStyle(
                    color = P.text,
                    fontSize = if (compact) 22.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = TextStyle(color = P.muted, fontSize = 10.sp),
                    modifier = GlanceModifier.padding(start = 2.dp, bottom = 3.dp),
                )
            }
        }
    }

    @Composable
    private fun HealthPill(label: String, colour: ColorProvider) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Box(modifier = GlanceModifier.size(6.dp).cornerRadius(3.dp).background(colour)) {}
            Text(
                text = label,
                style = TextStyle(color = colour, fontSize = 9.sp),
                maxLines = 1,
                modifier = GlanceModifier.padding(start = 3.dp),
            )
        }
    }

    @Composable
    private fun Sub(text: String) {
        Text(
            text = text,
            style = TextStyle(color = P.muted, fontSize = 10.sp),
            maxLines = 1,
        )
    }

    /**
     * The desk's mosaic: fixed little squares, wrapping, one per object. Glance caps a Row at ten
     * children, so cells nest as lines of groups rather than one long row.
     */
    @Composable
    private fun Mosaic(cells: List<String>, fit: Fit) {
        val cap = fit.cellLines * fit.perLine
        val per = if (cells.size <= cap) 1 else (cells.size + cap - 1) / cap
        val shown = if (per == 1) cells else cells.chunked(per).map { bucket -> worstOf(bucket) }
        Column {
            shown.chunked(fit.perLine).forEach { line ->
                Row {
                    line.chunked(8).forEach { group ->
                        Row {
                            group.forEach { cell ->
                                Box(
                                    modifier = GlanceModifier.width(8.dp).height(8.dp),
                                    contentAlignment = Alignment.TopStart,
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .size(6.dp)
                                            .cornerRadius(1.dp)
                                            .background(P.of(cell)),
                                    ) {}
                                }
                            }
                        }
                    }
                }
            }
            if (per > 1) {
                Text(
                    text = "1 cell = $per",
                    style = TextStyle(color = P.muted, fontSize = 8.sp, fontFamily = Mono),
                    modifier = GlanceModifier.padding(top = 1.dp),
                )
            }
        }
    }

    /** When one square stands for several objects, it wears the worst of them. */
    private fun worstOf(bucket: List<String>): String = when {
        bucket.contains("error") -> "error"
        bucket.contains("warn") -> "warn"
        bucket.contains("ok") -> "ok"
        else -> bucket.firstOrNull() ?: "idle"
    }

    @Composable
    private fun RowsBlock(rows: List<WidgetRow>, glyph: Int?) {
        Column {
            rows.forEachIndexed { index, row ->
                Column {
                    RankRow(row, glyph)
                    if (index < rows.lastIndex) RowDivider()
                }
            }
        }
    }

    @Composable
    private fun RowDivider() {
        Spacer(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(P.border),
        )
    }

    @Composable
    private fun RankRow(row: WidgetRow, glyph: Int?) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Spacer(modifier = GlanceModifier.width(2.dp).height(11.dp).background(P.of(row.health)))
            if (glyph != null) {
                Image(
                    provider = ImageProvider(glyph),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(P.muted),
                    modifier = GlanceModifier.size(10.dp).padding(start = 4.dp),
                )
            }
            Text(
                text = row.name,
                style = TextStyle(color = P.text, fontSize = 10.sp, fontFamily = Mono),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().padding(start = 5.dp),
            )
            if (row.flag.isNotEmpty()) {
                Text(
                    text = row.flag,
                    style = TextStyle(color = P.chip(row.health), fontSize = 9.sp, fontFamily = Mono),
                    modifier = GlanceModifier.padding(end = 4.dp),
                )
            }
            Text(
                text = row.count,
                style = TextStyle(
                    color = P.text,
                    fontSize = 10.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    @Composable
    private fun ServerRow(row: WidgetServerRow) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Spacer(
                modifier = GlanceModifier
                    .width(2.dp)
                    .height(11.dp)
                    .background(
                        when {
                            !row.reachable || row.err > 0 -> P.red
                            row.warn > 0 -> P.yellow
                            else -> P.green
                        },
                    ),
            )
            Text(
                text = row.name,
                style = TextStyle(color = P.text, fontSize = 10.sp, fontFamily = Mono),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().padding(start = 5.dp),
            )
            if (!row.reachable) {
                Text(text = "offline", style = TextStyle(color = P.red, fontSize = 9.sp, fontFamily = Mono))
                return@Row
            }
            if (row.warn > 0) {
                Text(
                    text = "${row.warn} warn",
                    style = TextStyle(color = P.yellow, fontSize = 9.sp, fontFamily = Mono),
                    modifier = GlanceModifier.padding(end = 5.dp),
                )
            }
            if (row.err > 0) {
                Text(
                    text = "${row.err} down",
                    style = TextStyle(color = P.red, fontSize = 9.sp, fontFamily = Mono),
                    modifier = GlanceModifier.padding(end = 5.dp),
                )
            }
            if (row.hasBans) {
                Text(
                    text = "${compactCount(row.bans)} bans",
                    style = TextStyle(color = P.blue, fontSize = 9.sp, fontFamily = Mono),
                    modifier = GlanceModifier.padding(end = 5.dp),
                )
            }
            Text(
                text = row.routers.toString(),
                style = TextStyle(
                    color = P.text,
                    fontSize = 10.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    private fun overviewHealth(payload: WidgetPayload?): String {
        val rows = payload?.servers.orEmpty()
        return when {
            rows.isEmpty() -> "unknown"
            rows.any { !it.reachable || it.err > 0 } -> "error"
            rows.any { it.warn > 0 } -> "warn"
            else -> "ok"
        }
    }

    /** Each card's own accent, as the desk assigns them, independent of health. */
    private fun accentFor(key: String): ColorProvider = when (key) {
        WidgetCardType.Http.key -> P.blue
        WidgetCardType.Stream.key -> P.teal
        WidgetCardType.Services.key -> P.green
        WidgetCardType.Middlewares.key -> P.purple
        WidgetCardType.Sources.key -> P.red
        WidgetCardType.Scenarios.key -> P.orange
        WidgetCardType.Paths.key -> P.blue
        WidgetCardType.Bans.key -> P.green
        else -> P.blue
    }

    private fun glyphFor(key: String): Int = when (key) {
        WidgetCardType.Sources.key, WidgetCardType.Bans.key -> R.drawable.ic_widget_shield
        WidgetCardType.Scenarios.key -> R.drawable.ic_widget_bolt
        WidgetCardType.Paths.key -> R.drawable.ic_widget_file
        WidgetCardType.Services.key, WidgetCardType.Middlewares.key -> R.drawable.ic_widget_servers
        else -> R.drawable.ic_widget_routes
    }

    private fun rowGlyphFor(key: String): Int? = when (key) {
        WidgetCardType.Scenarios.key -> R.drawable.ic_widget_bolt
        WidgetCardType.Paths.key -> R.drawable.ic_widget_file
        else -> null
    }

    private fun compactCount(value: Int): String = when {
        value >= 10_000 -> "${value / 1000}k"
        value >= 1000 -> "${value / 1000}.${(value % 1000) / 100}k"
        else -> value.toString()
    }

    private sealed interface Panel {
        data object Overview : Panel
        data object Empty : Panel
        data class Card(val card: WidgetCard) : Panel
    }
}

open class BaseStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdateWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Another size's widgets may still be placed; the worker cancels itself when none are.
        WidgetUpdateWorker.refreshNow(context)
    }
}

/** The 2x2. Keeps v1's exact class name so widgets placed before the rewrite survive it. */
class StatusWidgetReceiver : BaseStatusWidgetReceiver()

class StatusWidgetWideReceiver : BaseStatusWidgetReceiver()

class StatusWidgetLargeReceiver : BaseStatusWidgetReceiver()
