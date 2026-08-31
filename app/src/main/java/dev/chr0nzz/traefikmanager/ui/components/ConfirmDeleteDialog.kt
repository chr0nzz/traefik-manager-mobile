package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@Composable
fun TypedConfirmDialog(
    title: String,
    consequence: String,
    actionLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    word: String = actionLabel.uppercase(),
) {
    var typed by remember { mutableStateOf("") }
    val palette = LocalTmPalette.current
    val armed = typed.trim() == word

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = "$consequence Type $word to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    placeholder = { Text(word) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TmSpacing.md),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = armed) {
                Text(actionLabel, color = if (armed) palette.red else palette.muted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun ConfirmDeleteDialog(
    routeName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    consequence: String = "This removes the router and its service from the config file.",
) {
    TypedConfirmDialog(
        title = "Delete $routeName?",
        consequence = consequence,
        actionLabel = "Delete",
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
