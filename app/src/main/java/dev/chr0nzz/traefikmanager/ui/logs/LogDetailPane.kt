package dev.chr0nzz.traefikmanager.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.model.Countries
import dev.chr0nzz.traefikmanager.data.model.LogLine
import dev.chr0nzz.traefikmanager.data.model.LogParser
import dev.chr0nzz.traefikmanager.ui.components.DetailRow
import dev.chr0nzz.traefikmanager.ui.components.MessageState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TooltipIconButton
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LogDetailPane(
    line: LogLine?,
    country: String?,
    showBack: Boolean,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    if (line == null) {
        MessageState(
            icon = Icons.Outlined.TouchApp,
            headline = "Select a request",
            body = "Pick a line from the access log to see its full record.",
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(top = TmSpacing.xxl),
        )
        return
    }

    val palette = LocalTmPalette.current
    val clipboard = LocalClipboardManager.current
    val entry = line.entry
    val statusColor = when {
        entry == null -> palette.muted
        entry.status >= 500 -> palette.red
        entry.status >= 400 -> palette.yellow
        entry.status > 0 -> palette.green
        else -> palette.muted
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(start = TmSpacing.lg, end = TmSpacing.lg, top = TmSpacing.lg, bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to logs")
                    }
                }
                if (entry != null) {
                    Text(
                        text = if (entry.status == 0) "-" else entry.status.toString(),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor,
                    )
                    Text(
                        text = entry.statusName,
                        style = MaterialTheme.typography.titleSmall,
                        color = statusColor,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    Text(
                        text = entry.method,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFamily),
                        color = palette.blue,
                    )
                } else {
                    Text(
                        text = "Unparsed line",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.muted,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                }
            }

            if (entry != null) {
                TmCard {
                    DetailRow("Path", entry.path.ifEmpty { "-" }, mono = true)
                    DetailRow("IP", entry.ip.ifEmpty { "-" }, mono = true)
                    if (entry.date.isNotEmpty()) DetailRow("Date", entry.date, mono = true)
                    if (country != null) {
                        DetailRow("Country", "${Countries.flag(country)} ${Countries.name(country)}")
                    }
                    if (entry.domain.isNotEmpty()) DetailRow("Domain", entry.domain, mono = true)
                    if (entry.scheme.isNotEmpty()) DetailRow("Scheme", entry.scheme, mono = true)
                    if (entry.entryPoint.isNotEmpty()) DetailRow("Entry point", entry.entryPoint, mono = true)
                    if (entry.router.isNotEmpty()) DetailRow("Router", entry.router, mono = true)
                    if (entry.service.isNotEmpty()) DetailRow("Service", entry.service, mono = true)
                    if (entry.serviceUrl.isNotEmpty()) DetailRow("Service URL", entry.serviceUrl, mono = true)
                    if (entry.size.isNotEmpty() && entry.size != "-") DetailRow("Size", entry.size, mono = true)
                    DetailRow(
                        label = "Duration",
                        value = entry.duration.ifEmpty { LogParser.formatMs(entry.durMs) },
                        mono = true,
                    )
                    if (entry.origin != null) {
                        DetailRow(
                            label = "Origin status",
                            value = if (entry.origin == 0) {
                                "0 tunnel (Traefik answered ${entry.status} itself)"
                            } else {
                                entry.origin.toString()
                            },
                            mono = true,
                        )
                    }
                    if (entry.retries > 0) DetailRow("Retries", entry.retries.toString(), mono = true)
                    DetailRow("TLS", entry.tls.ifEmpty { "-" }, mono = true, last = true)
                }
            }

            SectionLabel("Raw line")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.card)
                    .padding(TmSpacing.md),
            ) {
                Text(
                    text = line.raw,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = palette.text,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }

        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(bottom = TmSpacing.lg),
        ) {
            TooltipIconButton(
                label = "Copy raw line",
                icon = Icons.Outlined.ContentCopy,
                onClick = { clipboard.setText(AnnotatedString(line.raw)) },
            )
        }
    }
}
