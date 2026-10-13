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
import dev.chr0nzz.traefikmanager.data.model.ApiKeyEntry
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import androidx.compose.ui.res.stringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText
import dev.chr0nzz.traefikmanager.ui.components.TopBarTitle
import dev.chr0nzz.traefikmanager.ui.components.asString
import androidx.compose.ui.platform.LocalResources

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
    val resources = LocalResources.current

    var newKeyName by remember { mutableStateOf("") }
    var revokeTarget by remember { mutableStateOf<ApiKeyEntry?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.auth_sign_out_title)) },
            text = { Text(stringResource(R.string.auth_sign_out_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.revokeSessions()
                        confirmSignOut = false
                    },
                ) { Text(stringResource(R.string.auth_sign_out_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.resolve(resources))
        viewModel.consumeMessage()
        if (message == UiText.of(R.string.auth_key_created)) newKeyName = ""
    }

    state.issuedKey?.let { key ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.auth_key_new_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.servers_key_body))
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
                TextButton(onClick = { clipboard.setText(AnnotatedString(key)) }) { Text(stringResource(R.string.common_copy)) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissKey) { Text(stringResource(R.string.servers_key_saved)) } },
        )
    }

    revokeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text(stringResource(R.string.auth_revoke_title)) },
            text = {
                Text(
                    if (target.name.isEmpty()) {
                        stringResource(R.string.auth_revoke_body)
                    } else {
                        stringResource(R.string.auth_revoke_body_named, target.name)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.revokeKey(target)
                        revokeTarget = null
                    },
                ) { Text(stringResource(R.string.auth_revoke)) }
            },
            dismissButton = { TextButton(onClick = { revokeTarget = null }) { Text(stringResource(R.string.common_cancel)) } },
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
                title = { TopBarTitle(stringResource(R.string.settings_auth_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.settings_back))
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
            item { SectionLabel(stringResource(R.string.auth_section)) }
            item {
                val status = state.status
                TmCard {
                    AuthFact(
                        label = stringResource(R.string.settings_auth_title),
                        value = when {
                            status.noAuth -> stringResource(R.string.auth_state_off)
                            status.authEnabled -> stringResource(R.string.auth_state_on)
                            else -> stringResource(R.string.common_disabled)
                        },
                        tone = when {
                            status.noAuth -> palette.red
                            status.authEnabled -> palette.green
                            else -> palette.yellow
                        },
                        note = when {
                            status.envForced -> stringResource(R.string.auth_note_env)
                            status.noAuth && !status.hasPassword -> stringResource(R.string.auth_note_no_password)
                            status.noAuth -> stringResource(R.string.auth_note_open)
                            else -> stringResource(R.string.auth_note_required)
                        },
                    )
                    CardDivider()
                    AuthFact(
                        label = stringResource(R.string.auth_otp),
                        value = stringResource(if (status.otpEnabled) R.string.common_enabled else R.string.common_disabled),
                        tone = if (status.otpEnabled) palette.green else palette.muted,
                        note = stringResource(R.string.auth_otp_note),
                    )
                    CardDivider()
                    AuthFact(
                        label = "OIDC",
                        value = when {
                            status.oidcActive -> stringResource(R.string.auth_oidc_active)
                            status.oidcEnabled -> stringResource(R.string.auth_oidc_configured)
                            else -> stringResource(R.string.auth_state_off)
                        },
                        tone = if (status.oidcActive) palette.green else palette.muted,
                        note = if (status.oidcEnabled && !status.oidcActive) {
                            stringResource(R.string.auth_oidc_note_down)
                        } else {
                            stringResource(R.string.auth_oidc_note)
                        },
                        last = true,
                    )
                    Text(
                        text = stringResource(R.string.auth_read_only_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    )
                }
            }

            item {
                TmCard {
                    Text(
                        text = stringResource(R.string.auth_sessions_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.auth_sessions_body),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                    Button(
                        onClick = { confirmSignOut = true },
                        enabled = !state.busy,
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    ) {
                        Text(stringResource(R.string.auth_sign_out))
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.auth_keys_title, state.keys.size), modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                Text(
                    text = stringResource(R.string.auth_keys_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
            item {
                TmCard {
                    OutlinedTextField(
                        value = newKeyName,
                        onValueChange = { if (it.length <= 50) newKeyName = it },
                        label = { Text(stringResource(R.string.auth_key_device)) },
                        placeholder = { Text(stringResource(R.string.auth_key_device_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { viewModel.generateKey(newKeyName) },
                        enabled = !state.busy && newKeyName.isNotBlank(),
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    ) {
                        Text(stringResource(R.string.auth_key_create))
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
                                text = key.name.ifEmpty { stringResource(R.string.auth_key_unnamed) },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = key.preview.ifEmpty { stringResource(R.string.auth_key_legacy) },
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                                color = palette.muted,
                            )
                            if (key.createdAt.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.auth_key_created_at, key.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.muted,
                                )
                            }
                        }
                        IconButton(onClick = { revokeTarget = key }, enabled = key.revocable) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.auth_revoke_named, key.name),
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
                        text = message.asString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.red,
                    )
                }
            }
        }
    }
}

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
