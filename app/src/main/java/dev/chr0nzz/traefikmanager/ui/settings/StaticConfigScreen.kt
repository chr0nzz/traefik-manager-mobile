package dev.chr0nzz.traefikmanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.YamlEditor
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

/**
 * Traefik's own configuration file, as text.
 *
 * This is the one screen in the app that can stop Traefik from starting, so it says so, keeps the
 * unsaved state visible, and never writes without being asked. The server backs the file up before
 * every write, which is the safety net when a save turns out to be wrong.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaticConfigScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StaticConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalTmPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes") },
            text = { Text("Reload the file from the server and lose what you have typed?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        viewModel.discard()
                    },
                ) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("Static config") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to settings")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.saving && viewModel.dirty) {
                        Text("Save")
                    }
                },
            )
        },
    ) { insets ->
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(insets))
            state.loadError != null -> ErrorState(
                headline = "Could not read the static config",
                body = state.loadError,
                onRetry = viewModel::load,
                modifier = Modifier.padding(insets),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .imePadding(),
            ) {
                TmCard(
                    accentColor = palette.yellow,
                    modifier = Modifier
                        .padding(horizontal = TmSpacing.lg, vertical = TmSpacing.xs)
                        .height(IntrinsicSize.Min),
                ) {
                    Text(
                        text = "Changes here can break Traefik",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "A misconfigured static config will stop Traefik from starting. " +
                            "A backup is taken before every save, and Backups can restore it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                    if (state.path.isNotBlank()) {
                        Text(
                            text = state.path,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                            color = palette.muted,
                            modifier = Modifier.padding(top = TmSpacing.xs),
                        )
                    }
                }

                if (state.restartPending) {
                    TmCard(
                        accentColor = palette.red,
                        modifier = Modifier
                            .padding(horizontal = TmSpacing.lg, vertical = TmSpacing.xs)
                            .height(IntrinsicSize.Min),
                    ) {
                        Text(
                            text = "Saved. Traefik is still running the previous config.",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                            modifier = Modifier.padding(top = TmSpacing.sm),
                        ) {
                            Button(onClick = viewModel::restart, enabled = !state.restarting) {
                                Icon(
                                    Icons.Outlined.RestartAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text("Restart Traefik", modifier = Modifier.padding(start = TmSpacing.xs))
                            }
                            TextButton(onClick = viewModel::dismissRestart) { Text("Later") }
                        }
                    }
                }

                if (viewModel.dirty) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
                    ) {
                        Text(
                            text = "Unsaved changes - nothing is written until you save.",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.yellow,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { confirmDiscard = true }) { Text("Discard") }
                    }
                }

                state.saveError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                        color = palette.red,
                        modifier = Modifier.padding(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
                    )
                }

                YamlEditor(
                    state = viewModel.content,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = TmSpacing.xs),
                )
            }
        }
    }
}
