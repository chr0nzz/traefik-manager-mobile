package dev.chr0nzz.traefikmanager.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

/** The bar holds five, so the sixth tick has to wait for one to be freed. */
private const val MAX_ITEMS = 5

/**
 * Which destinations sit in the bottom bar, and in what order. Everything else stays reachable
 * from the drawer, so unticking something hides it from the bar rather than from the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavBarEditorSheet(
    available: List<TmDestination>,
    chosen: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val sheetState = rememberModalBottomSheetState()
    val picked = remember { mutableStateListOf<String>().apply { addAll(chosen) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TmSpacing.lg)
                .padding(bottom = TmSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            Text(
                text = "Navigation bar",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Pick up to $MAX_ITEMS and order them with the arrows. Everything else stays " +
                    "in the drawer.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )

            if (picked.isNotEmpty()) {
                SectionLabel("In the bar ${picked.size}")
                TmCard {
                    picked.forEachIndexed { index, route ->
                        val destination = available.firstOrNull { it.route == route } ?: return@forEachIndexed
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                                tint = palette.muted,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = TmSpacing.sm),
                            )
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        picked.add(index - 1, picked.removeAt(index))
                                    }
                                },
                                enabled = index > 0,
                            ) {
                                Icon(
                                    Icons.Outlined.KeyboardArrowUp,
                                    contentDescription = "Move ${destination.label} up",
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (index < picked.lastIndex) {
                                        picked.add(index + 1, picked.removeAt(index))
                                    }
                                },
                                enabled = index < picked.lastIndex,
                            ) {
                                Icon(
                                    Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Move ${destination.label} down",
                                )
                            }
                        }
                        if (index < picked.lastIndex) CardDivider()
                    }
                }
            }

            SectionLabel("Everything else", modifier = Modifier.padding(top = TmSpacing.sm))
            TmCard {
                available.forEachIndexed { index, destination ->
                    val on = destination.route in picked
                    val full = picked.size >= MAX_ITEMS && !on
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !full) {
                                if (on) picked.remove(destination.route) else picked.add(destination.route)
                            },
                    ) {
                        Checkbox(checked = on, enabled = !full, onCheckedChange = null)
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (full) palette.muted else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = TmSpacing.xs),
                        )
                    }
                    if (index < available.lastIndex) CardDivider()
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
                TextButton(onClick = { onSave(emptyList()) }) { Text("Reset") }
                Row(modifier = Modifier.weight(1f)) {}
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    onClick = { onSave(picked.toList()) },
                    enabled = picked.isNotEmpty(),
                ) { Text("Save") }
            }
        }
    }
}
