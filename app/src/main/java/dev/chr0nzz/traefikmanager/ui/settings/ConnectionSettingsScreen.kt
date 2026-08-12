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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectionSettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectionSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalTmPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbarHostState.showSnackbar("Connection settings saved")
            viewModel.consumeSaved()
        }
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
                title = { Text("Traefik connection") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to settings")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text("Save")
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
            item { SectionLabel("Traefik API") }
            item {
                TmCard {
                    OutlinedTextField(
                        value = state.url,
                        onValueChange = viewModel::onUrlChange,
                        label = { Text("API URL") },
                        placeholder = { Text("http://traefik:8080") },
                        isError = state.url.isNotEmpty() && !state.urlValid,
                        supportingText = {
                            Text(
                                if (state.url.isNotEmpty() && !state.urlValid) {
                                    "Must start with http:// or https://"
                                } else {
                                    "Where this server reaches the Traefik API, not where you reach Traefik."
                                },
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                            showKeyboardOnFocus = false,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.user,
                        onValueChange = viewModel::onUserChange,
                        label = { Text("Username (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, showKeyboardOnFocus = false),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TmSpacing.sm),
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text(if (state.passwordStored) "New password" else "Password (optional)") },
                        supportingText = {
                            Text(
                                if (state.passwordStored) {
                                    "A password is already saved. Leave this empty to keep it."
                                } else {
                                    "Only needed if the Traefik API is behind basic auth."
                                },
                            )
                        },
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                )
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            showKeyboardOnFocus = false,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TmSpacing.sm),
                    )
                }
            }

            item {
                TmCard {
                    Text(
                        text = "Verify the API URL and credentials against your Traefik instance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::testConnection,
                            enabled = state.test != TestState.Running && state.urlValid,
                        ) {
                            Text("Test connection")
                        }
                        when (val test = state.test) {
                            TestState.Idle -> Unit
                            TestState.Running -> {
                                LoadingIndicator(modifier = Modifier.size(24.dp))
                                Text(
                                    text = "Testing…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.muted,
                                )
                            }
                            is TestState.Ok -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = palette.green,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Connected - Traefik v${test.version}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.green,
                                )
                            }
                            is TestState.Failed -> Unit
                        }
                    }
                    (state.test as? TestState.Failed)?.let { failed ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = TmSpacing.sm),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = palette.red,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = failed.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.red,
                            )
                        }
                    }
                }
            }

            item { SectionLabel("Routing defaults", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Text(
                        text = "Domains",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                    )
                    Text(
                        text = state.domains.joinToString(", ").ifEmpty { "none" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Certificate resolvers",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    )
                    Text(
                        text = state.certResolver.ifEmpty { "none" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Edit these in the web UI. Saving here keeps them exactly as they are.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    )
                }
            }

            state.error?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                        color = palette.red,
                    )
                }
            }
        }
    }
}
