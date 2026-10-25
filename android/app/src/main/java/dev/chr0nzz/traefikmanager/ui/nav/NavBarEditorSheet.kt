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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.asString
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

private const val BAR_ITEMS = 5

private const val RAIL_ITEMS = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavBarEditorSheet(
    available: List<TmDestination>,
    chosen: List<String>,
    railLayout: Boolean,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val sheetState = rememberModalBottomSheetState()
    val picked = remember(chosen) { mutableStateListOf<String>().apply { addAll(chosen) } }
    val maxItems = if (railLayout) RAIL_ITEMS else BAR_ITEMS

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
                text = stringResource(if (railLayout) R.string.nav_editor_title_rail else R.string.nav_editor_title_bar),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.nav_editor_help, maxItems),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )

            if (picked.isNotEmpty()) {
                SectionLabel(stringResource(R.string.nav_editor_pinned, picked.size))
                TmCard {
                    picked.forEachIndexed { index, route ->
                        val destination = available.firstOrNull { it.route == route } ?: return@forEachIndexed
                        val label = destination.label.asString()
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
                                text = label,
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
                                    contentDescription = stringResource(R.string.nav_editor_move_up, label),
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
                                    contentDescription = stringResource(R.string.nav_editor_move_down, label),
                                )
                            }
                        }
                        if (index < picked.lastIndex) CardDivider()
                    }
                }
            }

            SectionLabel(stringResource(R.string.nav_editor_rest), modifier = Modifier.padding(top = TmSpacing.sm))
            TmCard {
                available.forEachIndexed { index, destination ->
                    val on = destination.route in picked
                    val full = picked.size >= maxItems && !on
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
                            text = destination.label.asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (full) palette.muted else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = TmSpacing.xs),
                        )
                    }
                    if (index < available.lastIndex) CardDivider()
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
                TextButton(onClick = { onSave(emptyList()) }) { Text(stringResource(R.string.common_reset)) }
                Row(modifier = Modifier.weight(1f)) {}
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(
                    onClick = { onSave(picked.toList()) },
                    enabled = picked.isNotEmpty(),
                ) { Text(stringResource(R.string.common_save)) }
            }
        }
    }
}
