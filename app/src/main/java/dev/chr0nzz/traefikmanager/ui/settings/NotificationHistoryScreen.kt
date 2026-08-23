package dev.chr0nzz.traefikmanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.ChannelKinds
import dev.chr0nzz.traefikmanager.data.model.NotificationSeverity
import dev.chr0nzz.traefikmanager.data.model.TmNotification
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

/** The bell's destination: what the manager has been reporting, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalTmPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<TmNotification?>(null) }
    var clearOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this notification?") },
            text = { Text(target.msg) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target)
                        pendingDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    if (clearOpen) {
        AlertDialog(
            onDismissRequest = { clearOpen = false },
            title = { Text("Clear the whole history?") },
            text = { Text("Every notification on the server is removed. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        clearOpen = false
                    },
                ) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { clearOpen = false }) { Text("Cancel") } },
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
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.unread > 0) {
                        TextButton(onClick = viewModel::markAllRead) { Text("Mark read") }
                    }
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh notifications")
                    }
                    if (state.notifications.isNotEmpty()) {
                        IconButton(onClick = { clearOpen = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear the history")
                        }
                    }
                },
            )
        },
    ) { insets ->
        if (state.loading) {
            LoadingState(modifier = Modifier.padding(insets))
            return@Scaffold
        }

        state.error?.takeIf { state.notifications.isEmpty() }?.let { message ->
            ErrorState(
                headline = "Could not load notifications",
                body = message,
                onRetry = viewModel::load,
                modifier = Modifier.padding(insets),
            )
            return@Scaffold
        }

        if (state.notifications.isEmpty()) {
            EmptyState(
                headline = "Nothing yet",
                body = "Saves, pings and CrowdSec events show up here.",
                modifier = Modifier.padding(insets),
            )
            return@Scaffold
        }

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
        ) {
            itemsIndexed(
                items = state.notifications,
                key = { index, item -> "${item.ts}|${item.type}|${item.msg}|$index" },
            ) { index, notification ->
                val tint = when (notification.severity) {
                    NotificationSeverity.Success -> palette.green
                    NotificationSeverity.Info -> palette.blue
                    NotificationSeverity.Warning -> palette.yellow
                    NotificationSeverity.Error -> palette.red
                }
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = TmSpacing.sm),
                ) {
                    Icon(
                        imageVector = when (notification.severity) {
                            NotificationSeverity.Success -> Icons.Outlined.CheckCircle
                            NotificationSeverity.Info -> Icons.Outlined.Info
                            NotificationSeverity.Warning -> Icons.Outlined.Warning
                            NotificationSeverity.Error -> Icons.Outlined.ErrorOutline
                        },
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notification.msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = notification.category
                                .takeIf { it.isNotBlank() }
                                ?.let { "${notification.stamp}  ${ChannelKinds.categoryLabel(it)}" }
                                ?: notification.stamp,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                            color = palette.muted,
                        )
                    }
                    IconButton(
                        onClick = { pendingDelete = notification },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Delete this notification",
                            tint = palette.muted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (index < state.notifications.lastIndex) CardDivider()
            }

            item {
                Text(
                    text = "Times are the server's clock.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    modifier = Modifier.padding(top = TmSpacing.md),
                )
            }
        }
    }
}
