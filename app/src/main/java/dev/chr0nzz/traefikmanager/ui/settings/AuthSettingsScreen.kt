package dev.chr0nzz.traefikmanager.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.ApiKeyEntry
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthSettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalTmPalette.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var newKeyName by remember { mutableStateOf("") }
    var revokeTarget by remember { mutableStateOf<ApiKeyEntry?>(null) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
        if (message == "API key created") newKeyName = ""
    }

    state.issuedKey?.let { key ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("New API key") },
            text = {
                Column {
                    Text("Save this key - it will not be shown again.")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TmSpacing.sm)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.card)
                            .padding(TmSpacing.sm),
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                            color = palette.text,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(key)) }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissKey) { Text("I have saved it") } },
        )
    }

    revokeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("Revoke this API key?") },
            text = {
                Text(
                    "Anything signed in with ${target.name.ifEmpty { "this key" }} stops working " +
                        "immediately. If it is the key this app uses, you will have to reconnect.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.revokeKey(target)
                        revokeTarget = null
                    },
                ) { Text("Revoke") }
            },
            dismissButton = { TextButton(onClick = { revokeTarget = null }) { Text("Cancel") } },
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
                title = { Text("Authentication") },
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
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            item { SectionLabel("How this server lets people in") }
            item {
                val status = state.status
                TmCard {
                    AuthFact(
                        label = "Authentication",
                        value = when {
                            status.noAuth -> "Off"
                            status.authEnabled -> "On"
                            else -> "Disabled"
                        },
                        tone = when {
                            status.noAuth -> palette.red
                            status.authEnabled -> palette.green
                            else -> palette.yellow
                        },
                        note = when {
                            status.envForced -> "Forced off by AUTH_ENABLED in the environment"
                            status.noAuth && !status.hasPassword -> "No password is set, so the web UI is open"
                            status.noAuth -> "Anyone who can reach the web UI is let straight in"
                            else -> "The browser login is required"
                        },
                    )
                    CardDivider()
                    AuthFact(
                        label = "Two-factor",
                        value = if (status.otpEnabled) "Enabled" else "Disabled",
                        tone = if (status.otpEnabled) palette.green else palette.muted,
                        note = "Covers the browser login. This app authenticates with its API key.",
                    )
                    CardDivider()
                    AuthFact(
                        label = "OIDC",
                        value = when {
                            status.oidcActive -> "Active"
                            status.oidcEnabled -> "Configured"
                            else -> "Off"
                        },
                        tone = if (status.oidcActive) palette.green else palette.muted,
                        note = if (status.oidcEnabled && !status.oidcActive) {
                            "Switched on but not answering, so the password login still applies"
                        } else {
                            "Single sign-on for the browser login"
                        },
                        last = true,
                    )
                    Text(
                        text = "Read-only here. Passwords, two-factor and OIDC are changed in the web UI.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    )
                }
            }

            item { SectionLabel("API keys ${state.keys.size}", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                Text(
                    text = "Each device that talks to this server needs its own key. Revoking one only " +
                        "locks out that device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
            item {
                TmCard {
                    OutlinedTextField(
                        value = newKeyName,
                        onValueChange = { if (it.length <= 50) newKeyName = it },
                        label = { Text("Device name") },
                        placeholder = { Text("My phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { viewModel.generateKey(newKeyName) },
                        enabled = !state.busy && newKeyName.isNotBlank(),
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    ) {
                        Text("Create key")
                    }
                }
            }

            itemsIndexed(
                items = state.keys,
                key = { index, key -> "${key.preview}|$index" },
            ) { _, key ->
                TmCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = key.name.ifEmpty { "Unnamed key" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = key.preview.ifEmpty { "legacy key - revoke it from manager.yml" },
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                                color = palette.muted,
                            )
                            if (key.createdAt.isNotEmpty()) {
                                Text(
                                    text = "created ${key.createdAt} UTC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.muted,
                                )
                            }
                        }
                        IconButton(onClick = { revokeTarget = key }, enabled = key.revocable) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Revoke ${key.name}",
                                tint = if (key.revocable) palette.red else palette.muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            state.error?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.red,
                    )
                }
            }
        }
    }
}

/** One read-only line of auth state: what it is, and what that means. */
@Composable
private fun AuthFact(
    label: String,
    value: String,
    tone: Color,
    note: String,
    last: Boolean = false,
) {
    val palette = LocalTmPalette.current
    Column(modifier = Modifier.padding(vertical = TmSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFamily),
                color = tone,
            )
        }
        Text(
            text = note,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
