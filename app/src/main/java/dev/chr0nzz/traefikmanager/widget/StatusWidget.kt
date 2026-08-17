package dev.chr0nzz.traefikmanager.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as dayNight
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
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
import dev.chr0nzz.traefikmanager.MainActivity
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
    val faint = pick { it.border }
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

/** Cell geometry: an 8dp square in a 10dp slot, so a line of N needs 10N dp. */
private const val CELL_SLOT = 10
private const val ROW_HEIGHT = 19
private const val CARD_PADDING = 10

/** As many squares as one widget can draw before the launcher gives up on it. */
private const val MAX_CELLS = 96

/**
 * What this widget's real size can hold. Derived from the actual dp Glance reports, not from a
 * breakpoint, so a card grows continuously as you drag the handles instead of in four jumps.
 */
private data class Fit(
    val rows: Int,
    val cellLines: Int,
    val perLine: Int,
    val compact: Boolean,
    /** Type and cells grow with the card, so a big one reads like the mockup, not a stretched 2x2. */
    val heroSp: Int = 30,
    val labelSp: Int = 9,
    val rowSp: Int = 11,
    val cellDp: Int = 6,
) {
    companion object {
        fun of(size: DpSize, wantsRows: Boolean, hasSub: Boolean, hasFooter: Boolean): Fit {
            val width = size.width.value.toInt() - CARD_PADDING * 2
            val height = size.height.value.toInt() - CARD_PADDING * 2
            val compact = width < 200
            // Head, hero and the lines that always draw come off the top before anything is shared.
            var left = height - 14 - (if (compact) 26 else 30)
            if (hasSub) left -= 14
            if (hasFooter) left -= 13
            // Everything scales off the card's width, the way the mockups were drawn.
            val heroSp = (width / 7).coerceIn(26, 46)
            val labelSp = (width / 26).coerceIn(9, 14)
            val rowSp = (width / 22).coerceIn(11, 15)
            val cellDp = (width / 26).coerceIn(6, 16)
            val slot = cellDp + 2
            val rowHeight = rowSp + 9
            val perLine = ((width / slot).coerceIn(6, 30))
            val rows = if (wantsRows) (left / rowHeight).coerceIn(0, 8) else 0

            left -= rows * rowHeight
            // Whatever survives goes to the mosaic, which is what fills the card out.
            val cellLines = (left / slot).coerceIn(0, 8)
            return Fit(
                rows = rows,
                cellLines = cellLines,
                perLine = perLine,
                compact = compact,
                heroSp = heroSp,
                labelSp = labelSp,
                rowSp = rowSp,
                cellDp = cellDp,
            )
        }
    }
}

class StatusWidget : GlanceAppWidget() {

    // Exact, not Responsive: Responsive reports the breakpoint rather than the widget, which is
    // what left every card padded out with dead space below its content.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val config = WidgetConfig.read(prefs)
            val page = prefs[WidgetConfig.PAGE] ?: 0
            val payloads = WidgetPayloads.decode(prefs[WidgetConfig.PAYLOAD])
            val index = page.coerceIn(0, config.pages.lastIndex)
            val slot = config.pages[index]
            val payload = payloads.byServer[slot.encode()]
            val stale = prefs[WidgetConfig.ERROR] != null
            // Each slot draws as though it were the whole widget; the stack only picks which.
            Desk(
                config.copy(cards = slot.preset.cards, serverId = slot.serverId, layout = slot.preset.layout),
                payload,
                stale,
                index,
            )
        }
    }

    @Composable
    private fun Desk(config: WidgetConfig, payload: WidgetPayload?, stale: Boolean, page: Int) {
        // Every pick is a page of the same widget. A tap turns to the next, which is what the
        // dots count: pick four cards and you get four dots, not three.
        val cards = payload?.cards.orEmpty()
        val serverPages = config.pages.size
        val pages = buildList {
            when {
                cards.size > 1 -> add(Panel.Combined(cards))
                config.needsOverview -> add(Panel.Overview)
                cards.isNotEmpty() -> add(Panel.Card(cards.first()))
                config.slots.isNotEmpty() -> add(Panel.Loading)
                else -> add(Panel.Empty)
            }
        }
        PanelCard(
            panel = pages.first(),
            payload = payload,
            config = config,
            stale = stale,
            // The dots are the servers this widget stacks, never the cards.
            pages = serverPages,
            page = page.coerceIn(0, serverPages - 1),
        )
    }

    @Composable
    private fun PanelCard(
        panel: Panel,
        payload: WidgetPayload?,
        config: WidgetConfig,
        stale: Boolean,
        pages: Int = 1,
        page: Int = 0,
    ) {
        val size = LocalSize.current
        val card = (panel as? Panel.Card)?.card
        val combined = (panel as? Panel.Combined)?.cards
        val fit = Fit.of(
            size = size,
            wantsRows = card?.rows?.isNotEmpty() == true ||
                panel is Panel.Overview ||
                combined?.any { it.rows.isNotEmpty() } == true,
            hasSub = card?.sub?.isNotEmpty() != false,
            hasFooter = card?.footer?.isNotEmpty() == true || combined != null,
        )
        val health = when (panel) {
            is Panel.Card -> panel.card.health
            // A combined card wears the worst state of everything it holds.
            is Panel.Combined -> panel.cards.map { it.health }.let { states ->
                when {
                    states.contains("error") -> "error"
                    states.contains("warn") -> "warn"
                    else -> "ok"
                }
            }
            Panel.Overview -> overviewHealth(payload)
            Panel.Empty, Panel.Loading -> "unknown"
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
                .background(ImageProvider(background))
                // A PendingIntent from the launcher, not startActivity from a callback: Android 12
                // and later refuse a background activity launch, which is why taps did nothing.
                .clickable(actionRunCallback<CycleLayoutAction>()),
        ) {
            Column(modifier = GlanceModifier.fillMaxSize().padding(CARD_PADDING.dp)) {
                when (panel) {
                    is Panel.Combined -> CombinedBody(panel.cards, config, stale, fit, pages, page)
                    is Panel.Card -> CardBody(panel.card, config, stale, fit, pages, page)
                    Panel.Overview -> OverviewBody(payload, config, stale, fit, pages, page)
                    Panel.Loading -> {
                        // Named, so turning to a slot that has not been fetched yet still reads
                        // as this widget rather than an unconfigured one.
                        val slot = config.pages.getOrNull(page)
                        Head(
                            slot?.preset?.label ?: "Loading",
                            slot?.let { accentFor(it.preset.cards.first().key) } ?: P.muted,
                            slot?.let { glyphFor(it.preset.cards.first().key) }
                                ?: R.drawable.ic_widget_servers,
                            stale,
                            true,
                            config,
                            pages,
                            page,
                        )
                        Sub(payload?.note?.ifEmpty { null } ?: "Loading…")
                    }
                    Panel.Empty -> {
                        Head("Widget", P.muted, R.drawable.ic_widget_servers, stale, true)
                        Sub("Tap to set up")
                    }
                }
            }
        }
    }

    @Composable
    private fun ColumnScope.CardBody(
        card: WidgetCard,
        config: WidgetConfig,
        stale: Boolean,
        fit: Fit,
        pages: Int = 1,
        page: Int = 0,
    ) {
        Head(card.title, accentFor(card.key), glyphFor(card.key), stale, true, config, pages, page)
        when (config.layout) {
            WidgetLayout.Numbers -> { NumbersBody(card, fit); return }
            WidgetLayout.Rows -> { RowsBody(card, fit); return }
            WidgetLayout.Mosaic -> Unit
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Hero(card.hero, card.unit, fit.compact, fit)
            Spacer(modifier = GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.Horizontal.End) {
                if (card.healthLabel.isNotEmpty()) {
                    HealthPill(card.healthLabel, P.of(card.health))
                }
                card.chips.take(2).forEach { chip ->
                    Text(
                        text = chip.label,
                        style = TextStyle(
                            color = P.chip(chip.health),
                            fontSize = fit.labelSp.sp,
                            fontFamily = Mono,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        if (card.sub.isNotEmpty()) Sub(card.sub)
        if (card.rows.isNotEmpty() && fit.rows > 0) {
            Spacer(modifier = GlanceModifier.height(3.dp))
            RowsBlock(card.rows.take(fit.rows), rowGlyphFor(card.key), fit)
        }
        if (card.cells.isNotEmpty() && fit.cellLines > 0) {
            Spacer(modifier = GlanceModifier.height(5.dp))
            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                Mosaic(card.cells, fit, fillHeight = fit.cellLines * (fit.cellDp + 2))
            }
        } else {
            Spacer(modifier = GlanceModifier.defaultWeight())
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
    private fun ColumnScope.OverviewBody(
        payload: WidgetPayload?,
        config: WidgetConfig,
        stale: Boolean,
        fit: Fit,
        pages: Int = 1,
        page: Int = 0,
    ) {
        val rows = payload?.servers.orEmpty()
        Head(
            "Servers",
            accentFor("servers"),
            R.drawable.ic_widget_servers,
            stale,
            true,
            config,
            pages,
            page,
        )
        if (rows.isEmpty()) {
            Sub(payload?.note?.ifEmpty { null } ?: "No servers")
            return
        }
        val down = rows.count { !it.reachable || it.err > 0 }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Hero(rows.size.toString(), if (rows.size == 1) "server" else "servers", fit.compact, fit)
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
            Mosaic(cells, fit, fillHeight = (fit.cellLines / 2).coerceAtLeast(2) * (fit.cellDp + 2))
        }
        Spacer(modifier = GlanceModifier.height(4.dp))
        val shown = rows.take(fit.rows)
        Column {
            shown.forEachIndexed { index, row ->
                Column {
                    ServerRow(row)
                    if (index < shown.lastIndex) RowDivider()
                }
            }
        }
        val hidden = rows.size - shown.size
        if (hidden > 0) {
            Text(
                text = "+$hidden more",
                style = TextStyle(color = P.muted, fontSize = 9.sp),
                modifier = GlanceModifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
    }

    /** Numbers: the headline figures, large, for a glance from across the room. */
    @Composable
    private fun ColumnScope.NumbersBody(card: WidgetCard, fit: Fit) {
        Hero(card.hero, card.unit.ifEmpty { card.title.lowercase() }, fit.compact, fit)
        card.chips.firstOrNull()?.let { chip ->
            Text(
                text = chip.label,
                style = TextStyle(color = P.chip(chip.health), fontSize = fit.labelSp.sp, fontFamily = Mono),
                maxLines = 1,
            )
        }
        val figures = card.footer.take(if (fit.compact) 2 else 3)
        if (figures.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                figures.forEach { figure ->
                    Text(
                        text = figure.label,
                        style = TextStyle(
                            color = P.text,
                            fontSize = (fit.heroSp * 2 / 3).sp,
                            fontFamily = Mono,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.padding(top = 6.dp),
                    )
                }
            }
        } else {
            Sub(card.sub)
            Spacer(modifier = GlanceModifier.defaultWeight())
        }
    }

    /** Rows: what is worst, ranked, which is the view you act on. */
    @Composable
    private fun ColumnScope.RowsBody(card: WidgetCard, fit: Fit) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Hero(card.hero, card.unit.ifEmpty { card.title.lowercase() }, true, fit)
            Spacer(modifier = GlanceModifier.defaultWeight())
            card.chips.firstOrNull()?.let { chip ->
                Text(
                    text = chip.label,
                    style = TextStyle(color = P.chip(chip.health), fontSize = fit.labelSp.sp, fontFamily = Mono),
                    maxLines = 1,
                )
            }
        }
        if (card.rows.isEmpty()) {
            Sub(card.sub)
            Spacer(modifier = GlanceModifier.defaultWeight())
            return
        }
        Spacer(modifier = GlanceModifier.height(3.dp))
        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            RowsBlock(card.rows.take((fit.rows + 2).coerceAtMost(8)), rowGlyphFor(card.key), fit)
        }
    }

    /**
     * The split: what the numbers are down the left, what is worst down the right. The mosaic sits
     * under the hero with the figures below it, each measured rather than stacked at fixed offsets,
     * which is what made them collide in the mockup.
     */
    @Composable
    private fun ColumnScope.CombinedBody(
        cards: List<WidgetCard>,
        config: WidgetConfig,
        stale: Boolean,
        fit: Fit,
        pages: Int = 1,
        page: Int = 0,
    ) {
        val lead = cards.first()
        val rest = cards.drop(1)
        // The dots belong to the stack, so a combination in a stack has to be told about it too.
        Head(config.familyTitle, accentFor(lead.key), glyphFor(lead.key), stale, true, config, pages, page)

        // A narrow card has no room for two columns, so it falls back to the figures alone.
        if (fit.compact) {
            NumbersBody(lead.copy(footer = rest.map { WidgetChip("${it.hero} ${it.unit}", 0, "unknown") }), fit)
            return
        }

        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                Hero(lead.hero, lead.unit.ifEmpty { lead.title.lowercase() }, false, fit)
                lead.chips.take(2).forEach { chip ->
                    Text(
                        text = chip.label,
                        style = TextStyle(
                            color = P.chip(chip.health),
                            fontSize = fit.labelSp.sp,
                            fontFamily = Mono,
                        ),
                        maxLines = 1,
                    )
                }
                if (lead.cells.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(5.dp))
                    Mosaic(
                        cells = lead.cells,
                        fit = fit.copy(perLine = (fit.perLine / 2).coerceAtLeast(5)),
                        fillHeight = (fit.cellLines / 2).coerceAtLeast(2) * (fit.cellDp + 2),
                    )
                }
                rest.take(3).forEach { card ->
                    Column(modifier = GlanceModifier.padding(top = 10.dp)) {
                        Text(
                            text = card.hero,
                            style = TextStyle(
                                color = P.text,
                                fontSize = (fit.heroSp / 3).coerceAtLeast(11).sp,
                                fontFamily = Mono,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                        Text(
                            text = card.unit.ifEmpty { card.title.lowercase() },
                            style = TextStyle(color = P.muted, fontSize = fit.labelSp.sp),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                cards.flatMap { it.footer }.take(2).forEach { chip ->
                    Text(
                        text = chip.label,
                        style = TextStyle(
                            color = P.chip(chip.health),
                            fontSize = (fit.labelSp - 1).sp,
                            fontFamily = Mono,
                        ),
                        maxLines = 1,
                    )
                }
            }

            val ranked = cards.filter { it.rows.isNotEmpty() }
            if (ranked.isNotEmpty()) {
                Spacer(
                    modifier = GlanceModifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(P.border),
                )
                Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 9.dp)) {
                    Text(
                        text = ranked.first().listTitle,
                        style = TextStyle(color = P.muted, fontSize = (fit.labelSp - 1).sp, fontFamily = Mono),
                        maxLines = 1,
                    )
                    // Every list that has something to rank shares the column, worst first.
                    val each = ((fit.rows + 3) / ranked.size).coerceAtLeast(1)
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        ranked.forEach { card ->
                            RowsBlock(card.rows.take(each), rowGlyphFor(card.key), fit)
                        }
                    }
                }
            }
        }
    }

    /** Where a tap lands, resolved while composing so the launcher can fire it as a PendingIntent. */
    private fun openIntent(context: Context, config: WidgetConfig): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(OpenAppAction.EXTRA_DESTINATION, config.cards.firstOrNull().destinationRoute)
            config.serverId?.let { putExtra(OpenAppAction.EXTRA_SERVER_ID, it) }
            // Extras alone do not make two PendingIntents distinct, so the data uri keeps a
            // per-widget intent from being reused for a widget watching something else.
            data = android.net.Uri.parse(
                "tmwidget://open/${config.cards.firstOrNull()?.key ?: "home"}/${config.serverId ?: "host"}",
            )
        }

    @Composable
    private fun Head(
        title: String,
        accent: ColorProvider,
        glyph: Int,
        stale: Boolean,
        showRefresh: Boolean,
        config: WidgetConfig = WidgetConfig(),
        pages: Int = 1,
        page: Int = 0,
    ) {
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
            // One dot per card you picked, so the count matches what tapping cycles through.
            if (pages > 1) {
                Row(modifier = GlanceModifier.padding(end = 6.dp)) {
                    repeat(pages.coerceAtMost(6)) { dot ->
                        Box(modifier = GlanceModifier.width(9.dp).height(9.dp)) {
                            Box(
                                modifier = GlanceModifier
                                    .size(5.dp)
                                    .cornerRadius(3.dp)
                                    .background(if (dot == page) accent else P.faint),
                            ) {}
                        }
                    }
                }
            }
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
                contentDescription = "Open in the app",
                colorFilter = ColorFilter.tint(accent),
                modifier = GlanceModifier
                    .size(12.dp)
                    .clickable(actionStartActivity(openIntent(LocalContext.current, config))),
            )
        }
    }

    @Composable
    private fun Hero(value: String, unit: String, compact: Boolean, fit: Fit? = null) {
        val heroSp = fit?.heroSp ?: if (compact) 22 else 28
        Row(verticalAlignment = Alignment.Vertical.Bottom) {
            Text(
                text = value,
                style = TextStyle(color = P.text, fontSize = heroSp.sp, fontWeight = FontWeight.Bold),
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = TextStyle(color = P.muted, fontSize = (fit?.labelSp ?: 10).sp),
                    modifier = GlanceModifier.padding(start = 3.dp, bottom = (heroSp / 8).dp),
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
    private fun Mosaic(cells: List<String>, fit: Fit, fillHeight: Int = 0) {
        val slot = if (fillHeight > 0 && cells.isNotEmpty()) {
            // Grow the squares until the set covers the space, rather than hugging the top of it.
            val area = (fit.perLine * (fit.cellDp + 2)) * fillHeight
            val ideal = kotlin.math.sqrt(area.toDouble() / cells.size).toInt()
            ideal.coerceIn(fit.cellDp + 2, 26)
        } else {
            fit.cellDp + 2
        }
        val perLine = ((fit.perLine * (fit.cellDp + 2)) / slot).coerceAtLeast(4)
        val lines = if (slot > 0) (fillHeight / slot).coerceAtLeast(fit.cellLines) else fit.cellLines
        // Every square is a pair of nested boxes, and RemoteViews stops rendering a widget that
        // carries too many views - which is why some sizes drew nothing at all. Aggregate past a
        // fixed ceiling instead of letting a taller card ask for more squares.
        val cap = (if (fillHeight > 0) lines * perLine else fit.cellLines * fit.perLine)
            .coerceIn(1, MAX_CELLS)
        val per = if (cells.size <= cap) 1 else (cells.size + cap - 1) / cap
        val shown = if (per == 1) cells else cells.chunked(per).map { bucket -> worstOf(bucket) }
        Column {
            shown.chunked(if (fillHeight > 0) perLine else fit.perLine).forEach { line ->
                Row {
                    line.chunked(8).forEach { group ->
                        Row {
                            group.forEach { cell ->
                                Box(
                                    modifier = GlanceModifier.width(slot.dp).height(slot.dp),
                                    contentAlignment = Alignment.TopStart,
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .size((slot - 2).dp)
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
    private fun RowsBlock(rows: List<WidgetRow>, glyph: Int?, fit: Fit? = null) {
        Column {
            rows.forEachIndexed { index, row ->
                Column {
                    RankRow(row, glyph, fit)
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
    private fun RankRow(row: WidgetRow, glyph: Int?, fit: Fit? = null) {
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
                style = TextStyle(color = P.text, fontSize = (fit?.rowSp ?: 10).sp, fontFamily = Mono),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().padding(start = 5.dp),
            )
            if (row.flag.isNotEmpty()) {
                Text(
                    text = row.flag,
                    style = TextStyle(
                        color = P.chip(row.health),
                        fontSize = ((fit?.rowSp ?: 10) - 1).sp,
                        fontFamily = Mono,
                    ),
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
                style = TextStyle(color = P.text, fontSize = 11.sp, fontFamily = Mono),
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
        data object Loading : Panel
        data class Card(val card: WidgetCard) : Panel
        data class Combined(val cards: List<WidgetCard>) : Panel
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

/** The 4x4, which only earns its size with a combination in it. */
class StatusWidgetLargeReceiver : BaseStatusWidgetReceiver()

