package dev.chr0nzz.traefikmanager.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.AndroidEntryPoint
import dev.chr0nzz.traefikmanager.data.repo.ServerEntry
import dev.chr0nzz.traefikmanager.data.repo.ServersRepository
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import androidx.lifecycle.lifecycleScope
import dev.chr0nzz.traefikmanager.ui.theme.TmTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LauncherConfigActivity : ComponentActivity() {

    @Inject
    lateinit var serversRepository: ServersRepository

    @Inject
    lateinit var source: LauncherWidgetSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            TmTheme {
                LauncherSetup(
                    loadServers = { serversRepository.servers(probeHealth = false) },
                    onDone = { servers, hideNames ->
                        lifecycleScope.launch {
                            val glanceId = GlanceAppWidgetManager(this@LauncherConfigActivity)
                                .getGlanceIdBy(appWidgetId)
                            updateAppWidgetState(this@LauncherConfigActivity, glanceId) { prefs ->
                                prefs[LauncherWidgetConfig.SERVERS] = servers.joinToString(",")
                                prefs[LauncherWidgetConfig.HIDE_NAMES] = hideNames
                            }
                            LauncherWidget().updateAll(this@LauncherConfigActivity)
                            WidgetUpdateWorker.refreshNow(this@LauncherConfigActivity)
                            setResult(
                                RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                            )
                            finish()
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherSetup(
    loadServers: suspend () -> List<ServerEntry>,
    onDone: (List<String>, Boolean) -> Unit,
) {
    val palette = LocalTmPalette.current
    val servers = remember { mutableStateListOf<ServerEntry>() }
    val picked = remember { mutableStateListOf<String>() }
    var hideNames by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val list = runCatching { loadServers() }.getOrDefault(emptyList())
        servers.addAll(list)
        if (picked.isEmpty()) list.firstOrNull()?.let { picked.add(it.id.orEmpty()) }
        loading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text("App launcher") }) },
    ) { insets ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
            contentPadding = PaddingValues(TmSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            item { SectionLabel("Servers") }
            item {
                Text(
                    text = "Tick as many as you like. Their apps are merged into one grid.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
            item {
                TmCard {
                    servers.forEachIndexed { index, entry ->
                        val id = entry.id.orEmpty()
                        val on = id in picked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (on) {
                                        if (picked.size > 1) picked.remove(id)
                                    } else {
                                        picked.add(id)
                                    }
                                },
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = TmSpacing.xs),
                            )
                        }
                        if (index < servers.lastIndex) CardDivider()
                    }
                }
            }

            item { SectionLabel("Names", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hide names", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Icons only, which fits more in.",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.muted,
                            )
                        }
                        Switch(checked = hideNames, onCheckedChange = { hideNames = it })
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        onDone(picked.toList(), hideNames)
                    },
                    enabled = !loading && picked.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TmSpacing.sm),
                ) { Text("Done") }
            }
        }
    }
}
