package dev.chr0nzz.traefikmanager.ui.middlewares

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplate
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.YamlEditor
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiddlewareTemplatesScreen(
    onClose: () -> Unit,
    viewModel: MiddlewareTemplatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<MiddlewareTemplate?>(null) }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete template") },
            text = { Text("Delete this template? Middlewares already created from it are not affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target)
                        pendingDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    Text(
                        when {
                            !state.editorOpen -> "Middleware templates"
                            state.editing == null -> "Add template"
                            else -> "Edit template"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (state.editorOpen) viewModel.closeEditor() else onClose() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.editorOpen) {
                        TextButton(onClick = viewModel::save, enabled = !state.saving) {
                            Text("Save")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.editorOpen) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openEditor(null) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Add template") },
                )
            }
        },
    ) { insets ->
        if (state.editorOpen) {
            TemplateEditor(
                state = state,
                viewModel = viewModel,
                contentPadding = insets,
            )
        } else {
            TemplateList(
                state = state,
                onEdit = viewModel::openEditor,
                onDelete = { pendingDelete = it },
                contentPadding = insets,
            )
        }
    }
}

@Composable
private fun TemplateList(
    state: TemplatesUiState,
    onEdit: (MiddlewareTemplate) -> Unit,
    onDelete: (MiddlewareTemplate) -> Unit,
    contentPadding: PaddingValues,
) {
    val palette = LocalTmPalette.current

    if (state.loading) {
        LoadingState(modifier = Modifier.padding(contentPadding))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TmSpacing.lg,
            end = TmSpacing.lg,
            top = contentPadding.calculateTopPadding() + TmSpacing.md,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.blue.copy(alpha = 0.07f))
                    .padding(TmSpacing.md),
            ) {
                Text(
                    text = "Reusable YAML you can start a middleware from. Pick one under Template in the " +
                        "middleware form. Templates are shared across every server, not just this one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )
            }
        }

        if (state.templates.isEmpty()) {
            item {
                EmptyState(
                    headline = "No custom templates yet",
                    body = "Tap Add template to create one.",
                    modifier = Modifier.padding(top = TmSpacing.xl),
                )
            }
        } else {
            items(state.templates, key = { it.id }) { template ->
                TmCard(onClick = { onEdit(template) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Style,
                            contentDescription = null,
                            tint = palette.blue,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onEdit(template) }) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "Edit ${template.name}",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { onDelete(template) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete ${template.name}",
                                tint = palette.red,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Text(
                        text = template.yaml.lineSequence().firstOrNull().orEmpty(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateEditor(
    state: TemplatesUiState,
    viewModel: MiddlewareTemplatesViewModel,
    contentPadding: PaddingValues,
) {
    val palette = LocalTmPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(TmSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(TmSpacing.md),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            label = { Text("Name") },
            placeholder = { Text("e.g. Secure Headers") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.error != null) {
            Text(
                text = state.error.orEmpty(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                color = palette.red,
            )
        }

        SectionLabel("YAML")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            YamlEditor(state = viewModel.content, modifier = Modifier.fillMaxSize())
        }
        Text(
            text = "The middleware body only, without the name above it.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
        )
    }
}
