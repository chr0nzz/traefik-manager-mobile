package dev.chr0nzz.traefikmanager.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.chr0nzz.traefikmanager.data.repo.ServerEntry
import dev.chr0nzz.traefikmanager.data.repo.ServersRepository
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import dev.chr0nzz.traefikmanager.ui.theme.TmTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The per-instance setup the launcher shows when a widget is placed, and again when it is
 * reconfigured. Cancelling leaves the widget unplaced, so every path out of here either writes a
 * config or returns RESULT_CANCELED.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var serversRepository: ServersRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        setContent {
            TmTheme {
                WidgetConfigScreen(
                    loadServers = { runCatching { serversRepository.servers(probeHealth = false) }.getOrDefault(emptyList()) },
                    onCancel = { finish() },
                    onSave = { config -> save(appWidgetId, config) },
                )
            }
        }
    }

    private fun save(appWidgetId: Int, config: WidgetConfig) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { prefs ->
                prefs[WidgetConfig.CARDS] = config.cards.joinToString(",") { it.key }
                prefs[WidgetConfig.SERVER_ID] = config.serverId.orEmpty()
                prefs[WidgetConfig.SERVER_NAME] = config.serverName
                prefs[WidgetConfig.INTERVAL] = config.intervalMinutes
                // Force the next run to fetch rather than trust a previous mode's payload.
                prefs[WidgetConfig.UPDATED_AT] = 0L
            }
            StatusWidget().update(this@WidgetConfigActivity, glanceId)
            WidgetUpdateWorker.refreshNow(this@WidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    loadServers: suspend () -> List<ServerEntry>,
    onCancel: () -> Unit,
    onSave: (WidgetConfig) -> Unit,
) {
    val palette = LocalTmPalette.current
    val picked = remember { mutableStateListOf(WidgetCardType.Overview) }
    var server by remember { mutableStateOf<ServerEntry?>(null) }
    var interval by remember { mutableStateOf(WidgetConfig.DEFAULT_INTERVAL_MINUTES) }
    var servers by remember { mutableStateOf<List<ServerEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        servers = loadServers()
        if (server == null) server = servers.firstOrNull()
    }

    // Only the overview reads every server; every other card is about one of them.
    val needsServer = picked.any { it != WidgetCardType.Overview }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("Widget") },
                actions = {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                },
            )
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
            contentPadding = PaddingValues(
                start = TmSpacing.lg,
                end = TmSpacing.lg,
                top = TmSpacing.xs,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            item { SectionLabel("Cards") }
            item {
                Text(
                    text = "Pick up to ${WidgetConfig.MAX_CARDS}. These are the same cards the app " +
                        "shows: one fills a small widget, two or four fill a larger one.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
            item {
                TmCard {
                    WidgetCardType.entries.forEachIndexed { index, option ->
                        val selected = option in picked
                        val full = picked.size >= WidgetConfig.MAX_CARDS && !selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = selected,
                                enabled = !full,
                                onCheckedChange = {
                                    if (selected) picked.remove(option) else picked.add(option)
                                },
                            )
                            Column(modifier = Modifier.padding(start = TmSpacing.xs)) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (full) palette.muted else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = option.blurb,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.muted,
                                )
                            }
                        }
                        if (index < WidgetCardType.entries.lastIndex) CardDivider()
                    }
                }
            }

            if (needsServer) {
                item { SectionLabel("Which server", modifier = Modifier.padding(top = TmSpacing.sm)) }
                item {
                    TmCard {
                        if (servers.isEmpty()) {
                            Text(
                                text = "No servers yet. Connect the app first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.muted,
                            )
                        }
                        servers.forEachIndexed { index, entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                RadioButton(
                                    selected = server?.id == entry.id,
                                    onClick = { server = entry },
                                )
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = TmSpacing.xs),
                                )
                            }
                            if (index < servers.lastIndex) CardDivider()
                        }
                    }
                }
            }

            item { SectionLabel("How often", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
                        WidgetConfig.INTERVAL_CHOICES.forEach { minutes ->
                            FilterChip(
                                selected = interval == minutes,
                                onClick = { interval = minutes },
                                label = { Text("$minutes min") },
                            )
                        }
                    }
                    Text(
                        text = "Android will not run background work more often than every " +
                            "${WidgetConfig.MIN_INTERVAL_MINUTES} minutes. The refresh button on " +
                            "the widget updates it straight away.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        val target = if (needsServer) server else null
                        onSave(
                            WidgetConfig(
                                cards = picked.toList(),
                                serverId = target?.id,
                                serverName = target?.name ?: "Host",
                                intervalMinutes = interval,
                            ),
                        )
                    },
                    enabled = picked.isNotEmpty() && (!needsServer || server != null),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TmSpacing.md),
                ) {
                    Text("Add widget")
                }
            }
        }
    }
}
