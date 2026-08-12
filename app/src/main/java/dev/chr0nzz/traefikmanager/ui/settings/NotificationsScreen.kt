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
import androidx.compose.material.icons.Icons
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
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalTmPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    var typeMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
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
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to settings")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.saving) { Text("Save") }
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
            item { SectionLabel("Webhook") }
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
