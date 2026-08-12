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

private const val CONFIRM_WORD = "DELETE"

@Composable
fun ConfirmDeleteDialog(
    routeName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val palette = LocalTmPalette.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $routeName?") },
        text = {
            Column {
                Text(
                    text = "This removes the router and its service from the config file. " +
                        "Type $CONFIRM_WORD to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    placeholder = { Text(CONFIRM_WORD) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TmSpacing.md),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = typed.trim() == CONFIRM_WORD,
            ) {
                Text("Delete", color = if (typed.trim() == CONFIRM_WORD) palette.red else palette.muted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
