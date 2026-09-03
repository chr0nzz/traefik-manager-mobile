package dev.chr0nzz.traefikmanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.model.ChannelKinds
import dev.chr0nzz.traefikmanager.data.model.NotificationChannel
import dev.chr0nzz.traefikmanager.data.model.missingFields
import dev.chr0nzz.traefikmanager.data.model.summary
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import dev.chr0nzz.traefikmanager.push.PushNotifier
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = hiltViewModel(),
    channelsViewModel: ChannelsViewModel = hiltViewModel(),
    pushViewModel: PushViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val channels by channelsViewModel.state.collectAsStateWithLifecycle()
    val push by pushViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) pushViewModel.enable() else pushViewModel.onPermissionDenied() }
    val palette = LocalTmPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    var typeMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    LaunchedEffect(channels.message) {
        val message = channels.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        channelsViewModel.consumeMessage()
    }

    channels.editing?.let { draft ->
        ChannelEditorSheet(
            draft = draft,
            saving = channels.saving,
            test = channels.test,
            error = channels.editError,
            onChange = channelsViewModel::onDraftChange,
            onSave = channelsViewModel::save,
            onTest = channelsViewModel::test,
            onDismiss = channelsViewModel::closeEditor,
        )
    }

    if (push.picking) {
        AlertDialog(
            onDismissRequest = pushViewModel::cancelPicking,
            title = { Text("Deliver push through") },
            text = {
                Column {
                    push.distributors.forEach { distributor ->
                        Text(
                            text = distributor.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pushViewModel.choose(distributor.packageName) }
                                .padding(vertical = TmSpacing.sm),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = pushViewModel::cancelPicking) { Text("Cancel") } },
        )
    }

    channels.pendingDelete?.let { channel ->
        AlertDialog(
            onDismissRequest = { channelsViewModel.askDelete(null) },
            title = { Text("Remove channel") },
            text = {
                Text(
                    if (channel.kind == "unifiedpush") {
                        "Remove \"${channel.name}\"? Push to that phone stops until the app registers again."
                    } else {
                        "Remove channel \"${channel.name}\"? Events will stop being delivered to it."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { channelsViewModel.delete(channel) }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { channelsViewModel.askDelete(null) }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (channels.supported == true) {
                FloatingActionButton(onClick = channelsViewModel::add) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add a channel")
                }
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to settings")
                    }
                },
                actions = {
                    if (channels.supported == false) {
                        TextButton(onClick = viewModel::save, enabled = !state.saving) { Text("Save") }
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
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            if (channels.supported == true) {
                item { SectionLabel("This device") }
                item {
                    TmCard {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Push to this device",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = when {
                                        push.noDistributor ->
                                            "Needs a UnifiedPush app such as ntfy, NextPush, Sunup or " +
                                                "Conversations. Install one and it appears here."
                                        push.registered ->
                                            "Delivered through ${push.currentLabel ?: "your distributor"}, " +
                                                "as its own channel below."
                                        push.enabled -> "Waiting for the distributor to hand out an endpoint."
                                        else -> "Get events on this phone as they happen."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.muted,
                                )
                            }
                            Switch(
                                checked = push.enabled,
                                enabled = !push.noDistributor,
                                onCheckedChange = { on ->
                                    if (!on) {
                                        pushViewModel.disable()
                                    } else if (PushNotifier.allowed(context)) {
                                        pushViewModel.enable()
                                    } else {
                                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                            )
                        }
                        if (push.error.isNotBlank()) {
                            Text(
                                text = push.error,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.yellow,
                                modifier = Modifier.padding(top = TmSpacing.xs),
                            )
                        }
                    }
                }
            }

            if (channels.supported != false) {
                item { SectionLabel("Channels ${channels.channels.size}") }
                item {
                    Text(
                        text = "Every enabled channel gets its own copy of an event, filtered by the " +
                            "categories, severity, digest and quiet hours set on it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                }
                if (channels.channels.isEmpty() && !channels.loading) {
                    item {
                        EmptyState(
                            headline = "No channels configured",
                            body = "Add a channel to get a message when routes change, backups run " +
                                "or certificates expire.",
                        )
                    }
                }
                itemsIndexed(channels.channels, key = { index, channel -> channel.id.ifEmpty { "ch-$index" } }) { _, channel ->
                    ChannelRow(
                        channel = channel,
                        thisDevice = channel.id == channels.pushChannelId,
                        onToggle = { channelsViewModel.toggleEnabled(channel) },
                        onTest = { channelsViewModel.testRow(channel) },
                        onEdit = { channelsViewModel.edit(channel) },
                        onDelete = { channelsViewModel.askDelete(channel) },
                    )
                }
                channels.error?.let { message ->
                    item {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.red,
                        )
                    }
                }
            } else {
            item { SectionLabel("Webhook") }
            item {
                Text(
                    text = "Channels arrived in Traefik Manager 1.12.0. This server is older, so it " +
                        "still takes the one webhook.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
            item {
                TmCard {
                    OutlinedTextField(
                        value = state.webhookUrl,
                        onValueChange = viewModel::onUrlChange,
                        label = { Text("Webhook URL") },
                        placeholder = { Text("https://discord.com/api/webhooks/…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExposedDropdownMenuBox(
                        expanded = typeMenuOpen,
                        onExpandedChange = { typeMenuOpen = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TmSpacing.sm),
                    ) {
                        OutlinedTextField(
                            value = state.webhookType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Provider") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuOpen) },
                            modifier = Modifier
                                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                            WEBHOOK_TYPES.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        viewModel.onTypeChange(type)
                                        typeMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = state.webhookUsername,
                        onValueChange = viewModel::onUsernameChange,
                        label = { Text("Username (optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TmSpacing.sm),
                    )
                    OutlinedTextField(
                        value = state.webhookPassword,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text("Password or token (optional)") },
                        supportingText = { Text("The server never returns this, so it always looks empty.") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TmSpacing.sm),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::test,
                            enabled = state.test != TestState.Running && state.webhookUrl.isNotBlank(),
                        ) {
                            Text("Send a test")
                        }
                        when (val test = state.test) {
                            TestState.Idle -> Unit
                            TestState.Running -> LoadingIndicator(modifier = Modifier.size(24.dp))
                            is TestState.Ok -> Text(
                                text = "Request accepted",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.green,
                            )
                            is TestState.Failed -> Text(
                                text = test.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.red,
                            )
                        }
                    }
                    if (state.test is TestState.Ok) {
                        Text(
                            text = "The endpoint received the request. Check the destination to confirm it arrived.",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                            modifier = Modifier.padding(top = TmSpacing.xs),
                        )
                    }
                }
            }

            }

            item {
                Text(
                    text = "The bell on Home shows what the manager has reported.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    modifier = Modifier.padding(top = TmSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: NotificationChannel,
    thisDevice: Boolean,
    onToggle: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val missing = channel.missingFields()
    TmCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = ChannelKinds.label(channel.kind),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (thisDevice) {
                        Text(
                            text = "this device",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.blue,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                Text(
                    text = if (missing.isEmpty()) channel.summary() else "Needs " + missing.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (missing.isEmpty()) palette.muted else palette.yellow,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = channel.enabled, onCheckedChange = { onToggle() })
        }
        CardDivider(modifier = Modifier.padding(vertical = TmSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
            IconButton(onClick = onTest) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "Send a test to ${channel.name}",
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${channel.name}", modifier = Modifier.size(18.dp))
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove ${channel.name}",
                    tint = palette.red,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
