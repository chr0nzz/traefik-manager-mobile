package dev.chr0nzz.traefikmanager.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.repo.ServerEntry
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    onClose: () -> Unit,
    onConfigure: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ServersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalTmPalette.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var addOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ServerEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<ServerEntry?>(null) }
    var rotateTarget by remember { mutableStateOf<ServerEntry?>(null) }
    var composeTarget by remember { mutableStateOf<ServerEntry?>(null) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    if (addOpen) {
        AddServerDialog(
            onDismiss = { addOpen = false },
            onAdd = { name, url ->
                viewModel.add(name, url)
                addOpen = false
            },
        )
    }

    renameTarget?.let { target ->
        TextFieldDialog(
            title = "Rename ${target.name}",
            label = "Name",
            initial = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { value ->
                viewModel.rename(target.id.orEmpty(), value)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove ${target.name}?") },
            text = {
                Text(
                    "This only removes it from this Traefik Manager. The agent keeps running on that " +
                        "machine with the same key.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target)
                        deleteTarget = null
                    },
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    rotateTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { rotateTarget = null },
            title = { Text("Rotate the key for ${target.name}?") },
            text = {
                Text(
                    "The old key stops working immediately and every request to this server fails " +
                        "until you update its compose file and recreate the container. Health checks " +
                        "will keep looking green, because they are unauthenticated.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rotateKey(target)
                        rotateTarget = null
                    },
                ) { Text("Rotate key") }
            },
            dismissButton = { TextButton(onClick = { rotateTarget = null }) { Text("Cancel") } },
        )
    }

    state.issuedKey?.let { issued ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(if (issued.rotated) "New key for ${issued.agentName}" else "Key for ${issued.agentName}") },
            text = {
                Column {
                    Text(
                        text = "Save this key - it will not be shown again." +
                            if (issued.rotated) " The agent stops working until you update it." else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TmSpacing.sm)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.card)
                            .padding(TmSpacing.sm),
                    ) {
                        Text(
                            text = issued.key,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                            color = palette.text,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(issued.key)) }) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissKey) { Text("I have saved it") }
            },
        )
    }

    composeTarget?.let { target ->
        val snippet = viewModel.composeFor(target, null)
        AlertDialog(
            onDismissRequest = { composeTarget = null },
            title = { Text("Install ${target.name}") },
            text = {
                Column {
                    Text(
                        text = "Run this on the remote machine, with the key you saved when the server " +
                            "was added.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TmSpacing.sm)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.card)
                            .padding(TmSpacing.sm)
                            .heightIn(max = 320.dp),
                    ) {
                        Text(
                            text = snippet,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                            color = palette.text,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(snippet)) }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { composeTarget = null }) { Text("Close") } },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { addOpen = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add a server")
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("Servers") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to settings")
                    }
                },
            )
        },
    ) { insets ->
        if (state.loading) {
            LoadingState(modifier = Modifier.padding(insets))
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
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            item {
                Text(
                    text = "Agents let one Traefik Manager reach Traefik on other machines. " +
                        "The host itself is always available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )
            }
            item { SectionLabel("Agents ${state.agents.size}") }

            if (state.agents.isEmpty()) {
                item {
                    EmptyState(
                        headline = "No agents yet",
                        body = "Add one to manage Traefik on another machine from this app.",
                    )
                }
            }

            items(state.agents, key = { it.id.orEmpty() }) { entry ->
                TmCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.url,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                                color = palette.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.reachable) palette.green else palette.red,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (entry.id == state.activeId) {
                            Text(
                                text = "active",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.blue,
                            )
                        }
                    }
                    CardDivider(modifier = Modifier.padding(vertical = TmSpacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
                        IconButton(onClick = { renameTarget = entry }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Rename ${entry.name}", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { entry.id?.let(onConfigure) }) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Configure ${entry.name}",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { composeTarget = entry }) {
                            Icon(Icons.Outlined.Terminal, contentDescription = "Install snippet", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { rotateTarget = entry }) {
                            Icon(
                                Icons.Outlined.Key,
                                contentDescription = "Rotate the key",
                                tint = palette.yellow,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Box(modifier = Modifier.weight(1f))
                        IconButton(onClick = { deleteTarget = entry }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Remove ${entry.name}",
                                tint = palette.red,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddServerDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a server") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("edge-01") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Agent URL") },
                    placeholder = { Text("http://10.0.0.5:8090") },
                    supportingText = { Text("Where this Traefik Manager reaches the agent.") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TmSpacing.sm),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, url) },
                enabled = name.isNotBlank() && url.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TextFieldDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                isError = value.isBlank(),
                supportingText = if (value.isBlank()) {
                    { Text("This cannot be empty - the server would be dropped.") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank() && value != initial) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
