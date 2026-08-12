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

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newKeyName by remember { mutableStateOf("") }
    var revokeTarget by remember { mutableStateOf<ApiKeyEntry?>(null) }
    var disableOtpOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
        if (message == "Password changed") {
            currentPassword = ""
            newPassword = ""
            confirmPassword = ""
        }
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

    if (disableOtpOpen) {
        AlertDialog(
            onDismissRequest = { disableOtpOpen = false },
            title = { Text("Turn off two-factor authentication?") },
            text = { Text("The web login will stop asking for a code. This app is unaffected either way.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disableOtp()
                        disableOtpOpen = false
                    },
                ) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { disableOtpOpen = false }) { Text("Cancel") } },
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
            item { SectionLabel("Web password") }
            item {
                TmCard {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New password") },
                        supportingText = { Text("At least 8 characters.") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TmSpacing.sm),
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm new password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TmSpacing.sm),
                    )
                    Button(
                        onClick = { viewModel.changePassword(currentPassword, newPassword, confirmPassword) },
                        enabled = !state.busy,
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    ) {
                        Text("Change password")
                    }
                    Text(
                        text = "This is the browser login. The app authenticates with its API key.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    )
                }
            }

            item { SectionLabel("Two-factor authentication", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Text(
                        text = if (state.otpEnabled) "Enabled for the web login" else "Not enabled",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (state.otpEnabled) {
                            "It protects the browser login only - this app authenticates with its API key."
                        } else {
                            "Set it up in the web UI. Enrolment needs a browser session, and it protects " +
                                "the browser login only."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    )
                    if (state.otpEnabled) {
                        OutlinedButton(
                            onClick = { disableOtpOpen = true },
                            enabled = !state.busy,
                            modifier = Modifier.padding(top = TmSpacing.sm),
                        ) {
                            Text("Turn off")
                        }
                    }
                }
            }

            item { SectionLabel("API keys ${state.keys.size}", modifier = Modifier.padding(top = TmSpacing.sm)) }
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
