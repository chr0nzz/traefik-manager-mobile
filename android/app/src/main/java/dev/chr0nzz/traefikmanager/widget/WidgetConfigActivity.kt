package dev.chr0nzz.traefikmanager.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.chr0nzz.traefikmanager.data.repo.ServerEntry
import dev.chr0nzz.traefikmanager.data.repo.ServersRepository
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import dev.chr0nzz.traefikmanager.ui.theme.TmTheme
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.ui.components.asString

@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var serversRepository: ServersRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                    loadExisting = { readConfig(appWidgetId) },
                    onCancel = { finish() },
                    onSave = { config -> save(appWidgetId, config) },
                )
            }
        }
    }

    private suspend fun readConfig(appWidgetId: Int): WidgetConfig? = runCatching {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        WidgetConfig.read(getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId))
            .takeIf { it.slots.isNotEmpty() }
    }.getOrNull()

    private fun save(appWidgetId: Int, config: WidgetConfig) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { prefs ->
                prefs[WidgetConfig.CARDS] = config.cards.joinToString(",") { it.key }
                prefs[WidgetConfig.LAYOUT] = config.layout.name
                prefs[WidgetConfig.SLOTS] = config.slots.joinToString(",") { it.encode() }
                prefs[WidgetConfig.PAGE] = 0
                prefs[WidgetConfig.SERVER_ID] = config.serverId.orEmpty()
                prefs[WidgetConfig.SERVER_NAME] = config.serverName
                prefs[WidgetConfig.INTERVAL] = config.intervalMinutes
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
    loadExisting: suspend () -> WidgetConfig?,
    onCancel: () -> Unit,
    onSave: (WidgetConfig) -> Unit,
) {
    val palette = LocalTmPalette.current

    var server by remember { mutableStateOf<ServerEntry?>(null) }
    var interval by remember { mutableStateOf(WidgetConfig.DEFAULT_INTERVAL_MINUTES) }
    var layout by remember { mutableStateOf(WidgetLayout.Mosaic) }
    val slots = remember { mutableStateListOf(WidgetSlot(WidgetPreset.CrowdSecStats)) }
    var servers by remember { mutableStateOf<List<ServerEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        servers = loadServers()
        loadExisting()?.let { saved ->
            interval = saved.intervalMinutes
            layout = saved.layout
            slots.clear()
            slots.addAll(saved.pages)
        }
        if (server == null) {
            server = servers.firstOrNull { it.id == slots.firstOrNull()?.serverId }
                ?: servers.firstOrNull()
        }
    }

    val needsServer = slots.any { slot -> slot.preset.cards.any { it != WidgetCardType.Overview } }

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
                title = { Text(stringResource(R.string.widget_title)) },
                actions = {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
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
            item { SectionLabel(stringResource(R.string.widget_config_shows)) }
            item {
                Text(
                    text = stringResource(R.string.widget_config_stack_help, WidgetSlot.MAX_SLOTS),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
            itemsIndexed(slots) { index, slot ->
                TmCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                            color = palette.muted,
                        )
                        Text(
                            text = stringResource(slot.preset.label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = TmSpacing.sm),
                        )
                        if (slots.size > 1) {
                            IconButton(onClick = { slots.removeAt(index) }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_remove))
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(top = TmSpacing.xs),
                    ) {
                        WidgetPreset.entries.forEach { option ->
                            FilterChip(
                                selected = slot.preset == option,
                                onClick = { slots[index] = slot.copy(preset = option) },
                                label = { Text(stringResource(option.label)) },
                            )
                        }
                    }
                    if (servers.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(top = TmSpacing.xs),
                        ) {
                            servers.forEach { entry ->
                                FilterChip(
                                    selected = slot.serverId == entry.id,
                                    onClick = { slots[index] = slot.copy(serverId = entry.id) },
                                    label = { Text(entry.name) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                if (slots.size < WidgetSlot.MAX_SLOTS) {
                    OutlinedButton(
                        onClick = { slots.add(slots.last().copy()) },
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.widget_config_add_slot), modifier = Modifier.padding(start = TmSpacing.xs))
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.widget_config_interval), modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
                        WidgetConfig.INTERVAL_CHOICES.forEach { minutes ->
                            FilterChip(
                                selected = interval == minutes,
                                onClick = { interval = minutes },
                                label = { Text(stringResource(R.string.widget_config_minutes, minutes)) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.widget_config_interval_help, WidgetConfig.MIN_INTERVAL_MINUTES),
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
                                cards = slots.first().preset.cards,
                                serverId = slots.first().serverId ?: target?.id,
                                serverName = target?.name ?: "Host",
                                intervalMinutes = interval,
                                layout = slots.first().preset.layout,
                                slots = slots.toList(),
                            ),
                        )
                    },
                    enabled = !needsServer || server != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TmSpacing.md),
                ) {
                    Text(stringResource(R.string.widget_config_add))
                }
            }
        }
    }
}
