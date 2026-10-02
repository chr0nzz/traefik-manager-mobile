package dev.chr0nzz.traefikmanager.ui.logs

import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chr0nzz.traefikmanager.data.model.LogParser
import dev.chr0nzz.traefikmanager.data.model.LogWindow
import dev.chr0nzz.traefikmanager.data.model.RankedGroup
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText

@Composable
fun LogSignalCards(
    window: LogWindow,
    facets: Map<LogFacet, String>,
    onFacet: (LogFacet, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    val buckets = window.statuses
    val latency = window.latency
    val hero = LogParser.heroMs(latency.average)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            DeskCard(
                title = stringResource(R.string.logs_card_status),
                glyph = Icons.Outlined.MonitorHeart,
                accent = palette.blue,
                health = when {
                    buckets.fiveXx > 0 -> palette.red
                    buckets.fourXx > 0 -> palette.yellow
                    else -> null
                },
                hero = LogParser.formatCount(window.parsed),
                flags = {
                    when {
                        buckets.fiveXx > 0 -> Flag(
                            icon = Icons.Outlined.Cancel,
                            count = buckets.fiveXx,
                            label = "5xx",
                            color = palette.red,
                            onClick = { onFacet(LogFacet.Status, "5xx") },
                        )
                        buckets.fourXx > 0 -> Flag(
                            icon = Icons.Outlined.Warning,
                            count = buckets.fourXx,
                            label = "4xx",
                            color = palette.yellow,
                            onClick = { onFacet(LogFacet.Status, "4xx") },
                        )
                        else -> OkPill(stringResource(R.string.logs_ok_all_2xx))
                    }
                },
                sub = window.codeRank.firstOrNull()
                    ?.let { (code, count) ->
                        val name = LogParser.statusName(code).lowercase()
                            .ifEmpty { stringResource(R.string.logs_status_fallback) }
                        val more = window.codeRank.size - 1
                        listOfNotNull(
                            "$code $name x$count",
                            if (more > 0) pluralStringResource(R.plurals.logs_more_codes, more, more) else null,
                        ).joinToString(UiText.SEPARATOR)
                    }
                    ?: listOf(
                        stringResource(R.string.logs_status_ok, buckets.twoXx),
                        pluralStringResource(R.plurals.logs_status_redirects, buckets.threeXx, buckets.threeXx),
                        stringResource(R.string.logs_status_clean),
                    ).joinToString(UiText.SEPARATOR),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                footer = {
                    Counter("2xx", buckets.twoXx, Icons.Outlined.CheckCircle, palette.muted, facets[LogFacet.Status] == "2xx") {
                        onFacet(LogFacet.Status, "2xx")
                    }
                    Counter("3xx", buckets.threeXx, Icons.AutoMirrored.Outlined.CallMade, palette.muted, facets[LogFacet.Status] == "3xx") {
                        onFacet(LogFacet.Status, "3xx")
                    }
                    Counter(
                        label = "4xx",
                        count = buckets.fourXx,
                        icon = Icons.Outlined.Warning,
                        color = if (buckets.fourXx > 0) palette.yellow else palette.muted,
                        active = facets[LogFacet.Status] == "4xx",
                    ) { onFacet(LogFacet.Status, "4xx") }
                    Counter(
                        label = "5xx",
                        count = buckets.fiveXx,
                        icon = Icons.Outlined.Cancel,
                        color = if (buckets.fiveXx > 0) palette.red else palette.muted,
                        active = facets[LogFacet.Status] == "5xx",
                    ) { onFacet(LogFacet.Status, "5xx") }
                    if (buckets.oneXx > 0) {
                        Counter("1xx", buckets.oneXx, Icons.Outlined.SyncAlt, palette.muted, facets[LogFacet.Status] == "1xx") {
                            onFacet(LogFacet.Status, "1xx")
                        }
                    }
                    if (buckets.other > 0) {
                        Counter(
                            label = stringResource(R.string.logs_counter_other),
                            count = buckets.other,
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            color = palette.muted,
                            active = facets[LogFacet.Status] == "other",
                        ) { onFacet(LogFacet.Status, "other") }
                    }
                },
            ) {
                StatusStrip(window)
            }

            DeskCard(
                title = stringResource(R.string.logs_card_latency),
                glyph = Icons.Outlined.Timer,
                accent = palette.teal,
                health = when {
                    latency.verySlow > 0 -> palette.red
                    latency.slow > 0 -> palette.yellow
                    else -> null
                },
                hero = hero.first,
                heroUnit = hero.second,
                flags = {
                    when {
                        latency.verySlow > 0 -> Flag(
                            icon = Icons.Outlined.HourglassBottom,
                            count = latency.verySlow,
                            label = stringResource(R.string.logs_flag_over_2s),
                            color = palette.red,
                            onClick = { onFacet(LogFacet.Duration, "slow") },
                        )
                        latency.slow > 0 -> Flag(
                            icon = Icons.Outlined.HourglassBottom,
                            count = latency.slow,
                            label = stringResource(R.string.logs_flag_over_500ms),
                            color = palette.yellow,
                            onClick = { onFacet(LogFacet.Duration, "slow") },
                        )
                        window.retries > 0 -> Flag(
                            icon = Icons.Outlined.SwapHoriz,
                            count = window.retries,
                            label = pluralStringResource(R.plurals.logs_flag_retries, window.retries),
                            color = palette.yellow,
                            onClick = null,
                        )
                        else -> OkPill(stringResource(R.string.logs_ok_all_fast))
                    }
                },
                sub = if (latency.timed == 0) {
                    stringResource(if (latency.held > 0) R.string.logs_latency_none_completed else R.string.logs_latency_no_duration)
                } else {
                    buildString {
                        append("p50 ${LogParser.formatMs(latency.p50)}")
                        append(" · p95 ${LogParser.formatMs(latency.p95)}")
                        append(" · max ${LogParser.formatMs(latency.max)}")
                        latency.slowest?.path?.takeIf { it.isNotEmpty() }?.let { append(" $it") }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                footer = {
                    Counter(
                        label = stringResource(R.string.logs_counter_fast),
                        count = latency.fast,
                        icon = Icons.Outlined.Bolt,
                        color = palette.muted,
                        active = facets[LogFacet.Duration] == "fast",
                    ) { onFacet(LogFacet.Duration, "fast") }
                    Counter(
                        label = "100-500ms",
                        count = latency.medium,
                        icon = Icons.Outlined.HourglassEmpty,
                        color = if (latency.medium > 0) palette.yellow else palette.muted,
                        active = facets[LogFacet.Duration] == "med",
                    ) { onFacet(LogFacet.Duration, "med") }
                    Counter(
                        label = stringResource(R.string.logs_flag_over_500ms),
                        count = latency.slow,
                        icon = Icons.Outlined.HourglassBottom,
                        color = if (latency.slow > 0) palette.red else palette.muted,
                        active = facets[LogFacet.Duration] == "slow",
                    ) { onFacet(LogFacet.Duration, "slow") }
                    if (latency.held > 0) {
                        Counter(
                            label = pluralStringResource(R.plurals.logs_counter_upgrades, latency.held),
                            count = latency.held,
                            icon = Icons.Outlined.SyncAlt,
                            color = palette.muted,
                            active = facets[LogFacet.Duration] == "held",
                        ) { onFacet(LogFacet.Duration, "held") }
                    }
                },
            ) {
                LatencyStrip(window)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            val writes = window.methods.filter { it.key in WRITE_METHODS }
            DeskCard(
                title = stringResource(R.string.logs_card_methods),
                glyph = Icons.Outlined.SwapHoriz,
                accent = palette.orange,
                hero = window.methods.size.toString(),
                flags = {
                    if (writes.isNotEmpty()) {
                        val risky = writes.any { it.key == "DELETE" || it.key == "PUT" }
                        Flag(
                            icon = Icons.Outlined.SwapHoriz,
                            count = writes.sumOf { it.count },
                            label = stringResource(R.string.logs_flag_writes),
                            color = if (risky) palette.yellow else palette.muted,
                            onClick = { onFacet(LogFacet.Method, writes.first().key) },
                        )
                    } else {
                        OkPill(stringResource(R.string.logs_ok_reads_only))
                    }
                },
                sub = window.methods.maxByOrNull { it.count }?.let { top ->
                    val pct = top.count * 100 / maxOf(1, window.parsed)
                    val risky = window.methods.any { it.key == "PUT" || it.key == "DELETE" }
                    stringResource(if (risky) R.string.logs_methods_risky else R.string.logs_methods_safe, top.key, pct)
                } ?: stringResource(R.string.logs_methods_none),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                RankedRows(window.methods, LogFacet.Method, facets, onFacet, limit = 3, more = R.plurals.logs_more_methods)
            }

            DeskCard(
                title = stringResource(R.string.logs_card_domains),
                glyph = Icons.Outlined.Language,
                accent = palette.purple,
                hero = if (window.domains.isEmpty()) "-" else window.domains.size.toString(),
                flags = {
                    val failing = window.domains.count { it.errors > 0 }
                    if (failing > 0) {
                        Flag(
                            icon = Icons.Outlined.Warning,
                            count = failing,
                            label = stringResource(R.string.logs_flag_failing),
                            color = palette.yellow,
                            onClick = { onFacet(LogFacet.Status, "errors") },
                        )
                    } else if (window.domains.isNotEmpty()) {
                        OkPill(stringResource(R.string.logs_ok_all_served))
                    }
                },
                sub = window.domains.firstOrNull()
                    ?.let { pluralStringResource(R.plurals.logs_top_requests, it.count, it.key, LogParser.formatCount(it.count)) }
                    ?: stringResource(R.string.logs_domains_none),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                RankedRows(window.domains, LogFacet.Domain, facets, onFacet, limit = 3, more = R.plurals.logs_more_domains)
            }
        }

        DeskCard(
            title = stringResource(R.string.logs_card_paths),
            glyph = Icons.Outlined.Route,
            accent = palette.teal,
            hero = window.paths.size.toString(),
            flags = {
                val failing = window.paths.count { it.errors > 0 }
                if (failing > 0) {
                    Flag(
                        icon = Icons.Outlined.Warning,
                        count = failing,
                        label = stringResource(R.string.logs_flag_failing),
                        color = palette.yellow,
                        onClick = { onFacet(LogFacet.Status, "errors") },
                    )
                } else if (window.paths.isNotEmpty()) {
                    OkPill(stringResource(R.string.logs_ok_all_served))
                }
            },
            sub = window.paths.firstOrNull()
                ?.let { top ->
                    val extra = window.paths.count { it.errors > 0 } - 1
                    listOfNotNull(
                        pluralStringResource(R.plurals.logs_top_requests, top.count, top.key, LogParser.formatCount(top.count)),
                        top.worstCode?.let { "${top.worstCount} x $it" },
                        if (extra > 0) stringResource(R.string.logs_more_failing, extra) else null,
                    ).joinToString(", ")
                }
                ?: stringResource(R.string.logs_paths_none),
        ) {
            RankedRows(window.paths, LogFacet.Path, facets, onFacet, limit = 4, more = R.plurals.logs_more_paths, prefix = "~")
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            DeskCard(
                title = stringResource(R.string.logs_card_clients),
                glyph = Icons.Outlined.People,
                accent = palette.blue,
                hero = window.clients.size.toString(),
                flags = {
                    val failing = window.clients.count { it.errors > 0 }
                    if (failing > 0) {
                        Flag(
                            icon = Icons.Outlined.Warning,
                            count = failing,
                            label = stringResource(R.string.logs_flag_failing),
                            color = palette.yellow,
                            onClick = { onFacet(LogFacet.Status, "errors") },
                        )
                    } else if (window.clients.isNotEmpty()) {
                        OkPill(stringResource(R.string.logs_ok_all_served))
                    }
                },
                sub = window.clients.firstOrNull()
                    ?.let { pluralStringResource(R.plurals.logs_top_requests, it.count, it.key, LogParser.formatCount(it.count)) }
                    ?: stringResource(R.string.logs_clients_none),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                RankedRows(window.clients, LogFacet.Ip, facets, onFacet, limit = 3, more = R.plurals.logs_more_clients)
                val byClass = remember(window.clients) {
                    window.clients
                        .groupBy { LogParser.ipClass(it.key) }
                        .map { (kind, rows) -> kind to rows.sumOf { row -> row.count } }
                        .sortedByDescending { it.second }
                        .take(4)
                }
                if (byClass.size > 1) {
                    CardDivider(modifier = Modifier.padding(vertical = TmSpacing.xs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        byClass.forEach { (kind, count) ->
                            Counter(
                                label = ipClassLabel(kind),
                                count = count,
                                icon = if (kind == "public") Icons.Outlined.Public else Icons.Outlined.Router,
                                color = palette.muted,
                                active = facets[LogFacet.IpClass] == kind,
                            ) { onFacet(LogFacet.IpClass, kind) }
                        }
                    }
                }
            }

            DeskCard(
                title = stringResource(R.string.common_label_services),
                glyph = Icons.Outlined.Dns,
                accent = palette.green,
                hero = if (window.services.isEmpty()) "-" else window.services.size.toString(),
                flags = {
                    val failing = window.services.count { it.errors > 0 }
                    if (failing > 0) {
                        Flag(
                            icon = Icons.Outlined.Warning,
                            count = failing,
                            label = stringResource(R.string.logs_flag_failing),
                            color = palette.yellow,
                            onClick = { onFacet(LogFacet.Status, "errors") },
                        )
                    } else if (window.services.isNotEmpty()) {
                        OkPill(stringResource(R.string.logs_ok_all_healthy))
                    }
                },
                sub = window.services.firstOrNull()
                    ?.let {
                        pluralStringResource(
                            R.plurals.logs_top_requests,
                            it.count,
                            LogParser.shortName(it.key),
                            LogParser.formatCount(it.count),
                        )
                    }
                    ?: stringResource(R.string.logs_services_none),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                footer = {
                    val providers = window.services
                        .groupBy { LogParser.providerOf(it.key) }
                        .filterKeys { it.isNotEmpty() }
                    providers.forEach { (provider, rows) ->
                        Counter(
                            label = provider,
                            count = rows.sumOf { it.count },
                            icon = Icons.Outlined.Dns,
                            color = palette.muted,
                            active = facets[LogFacet.Provider] == provider,
                        ) { onFacet(LogFacet.Provider, provider) }
                    }
                },
            ) {
                RankedRows(
                    groups = window.services,
                    facet = LogFacet.Service,
                    facets = facets,
                    onFacet = onFacet,
                    limit = 3,
                    more = R.plurals.logs_more_services,
                    label = { LogParser.shortName(it.key) },
                )
            }
        }

        if (window.failures.isNotEmpty()) {
            TmCard(accentColor = palette.yellow) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = palette.yellow,
                        modifier = Modifier.size(14.dp),
                    )
                    SectionLabel(
                        text = stringResource(R.string.logs_failures_title),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = TmSpacing.xs),
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.logs_requests_of,
                            window.parsed,
                            window.clientErrors + window.serverErrors,
                            window.parsed,
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                        color = palette.muted,
                    )
                }
                window.failures.take(5).forEach { failure ->
                    val active = facets[LogFacet.Status] == failure.status.toString() &&
                        facets[LogFacet.Path] == "~${failure.path}"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                onFacet(LogFacet.Status, failure.status.toString())
                                onFacet(LogFacet.Path, "~${failure.path}")
                            }
                            .background(if (active) palette.blue.copy(alpha = 0.10f) else Color.Transparent)
                            .padding(vertical = TmSpacing.xs, horizontal = 2.dp),
                    ) {
                        Text(
                            text = failure.status.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFamily,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (failure.status >= 500) palette.red else palette.yellow,
                            modifier = Modifier.width(30.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = failure.path,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    R.string.logs_failure_line,
                                    LogParser.statusName(failure.status)
                                        .ifEmpty { stringResource(R.string.logs_failure_fallback) },
                                    failure.path,
                                    failure.share.toString(),
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = palette.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = LogParser.formatCount(failure.count),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFamily,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${failure.share}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.yellow,
                        )
                    }
                }
            }
        }
    }
}

private val WRITE_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")

@Composable
private fun DeskCard(
    title: String,
    glyph: ImageVector,
    accent: Color,
    hero: String,
    sub: String,
    modifier: Modifier = Modifier,
    heroUnit: String? = null,
    health: Color? = null,
    flags: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    val palette = LocalTmPalette.current
    TmCard(modifier = modifier, accentColor = health ?: accent) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionLabel(title, modifier = Modifier.weight(1f))
            Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        ) {
            Text(
                text = hero,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (hero == "-") palette.muted else MaterialTheme.colorScheme.onSurface,
            )
            if (heroUnit != null) {
                Text(
                    text = heroUnit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                )
            }
            Box(modifier = Modifier.weight(1f))
            if (flags != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    flags()
                }
            }
        }
        Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = palette.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.padding(top = TmSpacing.xs)) { body() }
        if (footer != null) {
            CardDivider(modifier = Modifier.padding(top = TmSpacing.xs, bottom = TmSpacing.xs))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                footer()
            }
        }
    }
}

@Composable
private fun Flag(
    icon: ImageVector,
    count: Int,
    label: String,
    color: Color,
    onClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 2.dp, vertical = 1.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(
            text = LogParser.formatCount(count),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Bold,
            ),
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun OkPill(text: String) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.green),
        )
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = palette.muted)
    }
}

@Composable
private fun Counter(
    label: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    active: Boolean,
    onClick: (() -> Unit)?,
) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .then(if (onClick != null && count > 0) Modifier.clickable { onClick() } else Modifier)
            .background(if (active) palette.blue.copy(alpha = 0.14f) else Color.Transparent)
            .padding(horizontal = 3.dp, vertical = 1.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) palette.blue else color,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) palette.blue else palette.muted,
        )
        Text(
            text = LogParser.formatCount(count),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Bold,
            ),
            color = if (active) palette.blue else color,
        )
    }
}

@Composable
private fun StatusStrip(window: LogWindow) {
    val palette = LocalTmPalette.current
    val buckets = window.statuses
    val cells = buildList {
        repeat(buckets.fiveXx) { add(palette.red) }
        repeat(buckets.fourXx) { add(palette.yellow) }
        repeat(buckets.threeXx + buckets.oneXx + buckets.other) { add(palette.muted.copy(alpha = 0.30f)) }
        repeat(buckets.twoXx) { add(palette.muted.copy(alpha = 0.55f)) }
    }
    Cells(cells)
}

@Composable
private fun LatencyStrip(window: LogWindow) {
    val palette = LocalTmPalette.current
    val latency = window.latency
    val cells = buildList {
        repeat(latency.verySlow) { add(palette.red) }
        repeat(latency.slow - latency.verySlow) { add(palette.orange) }
        repeat(latency.medium) { add(palette.yellow) }
        repeat(latency.fast) { add(palette.muted.copy(alpha = 0.55f)) }
    }
    Cells(cells)
}

@Composable
private fun Cells(cells: List<Color>, cap: Int = 120) {
    if (cells.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        cells.take(cap).forEach { color ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun RankedRows(
    groups: List<RankedGroup>,
    facet: LogFacet,
    facets: Map<LogFacet, String>,
    onFacet: (LogFacet, String) -> Unit,
    limit: Int,
    @PluralsRes more: Int,
    prefix: String = "",
    label: (RankedGroup) -> String = { it.key },
) {
    val palette = LocalTmPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        groups.take(limit).forEach { group ->
            val value = "$prefix${group.key}"
            val active = facets[facet] == value
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onFacet(facet, value) }
                    .background(if (active) palette.blue.copy(alpha = 0.10f) else Color.Transparent)
                    .padding(horizontal = 3.dp, vertical = 3.dp),
            ) {
                Text(
                    text = label(group),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = if (active) palette.blue else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (group.errors > 0) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = if (group.serverErrors > 0) palette.red else palette.yellow,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = group.errors.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                        color = if (group.serverErrors > 0) palette.red else palette.yellow,
                    )
                }
                Text(
                    text = LogParser.formatCount(group.count),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (groups.size > limit) {
            val remaining = groups.drop(limit).sumOf { it.count }
            Text(
                text = pluralStringResource(more, groups.size - limit, LogParser.formatCount(remaining), groups.size - limit),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.padding(top = 2.dp, start = 3.dp),
            )
        }
    }
}

@Composable
private fun ipClassLabel(kind: String): String = when (kind) {
    "public" -> stringResource(R.string.logs_ip_public)
    "private" -> stringResource(R.string.logs_ip_private)
    "cgnat" -> "CGNAT"
    "loopback" -> stringResource(R.string.logs_ip_loopback)
    "link-local" -> stringResource(R.string.logs_ip_link_local)
    else -> stringResource(R.string.logs_ip_unknown)
}
