package dev.chr0nzz.traefikmanager.ui.crowdsec

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

private val DECISION_TYPES = listOf("ban", "captcha", "bypass")

private val DURATIONS = listOf(
    "1h" to "1 hour",
    "4h" to "4 hours",
    "24h" to "24 hours",
    "168h" to "7 days",
    "720h" to "30 days",
    "8760h" to "1 year",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDecisionSheet(
    initialValue: String,
    decisions: List<CsDecision>,
    saving: Boolean,
    onAdd: (String, String, String, String) -> Unit,
    onDelete: (CsDecision) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by remember { mutableStateOf(initialValue) }
    var type by remember { mutableStateOf("ban") }
    var duration by remember { mutableStateOf("24h") }
    var reason by remember { mutableStateOf("") }
    var durationOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = TmSpacing.lg, end = TmSpacing.lg, bottom = TmSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            Text(
                text = "Add decision",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("IP or range") },
                placeholder = { Text("1.2.3.4 or 1.2.3.0/24") },
                supportingText = { Text("Single IP or CIDR range.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, showKeyboardOnFocus = false),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Type")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DECISION_TYPES.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = type == option,
                        onClick = { type = option },
                        shape = SegmentedButtonDefaults.itemShape(index, DECISION_TYPES.size),
                    ) {
                        Text(option.replaceFirstChar { it.uppercase() })
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = durationOpen,
                onExpandedChange = { durationOpen = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = DURATIONS.firstOrNull { it.first == duration }?.second ?: duration,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Duration") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationOpen) },
                    modifier = Modifier
                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = durationOpen, onDismissRequest = { durationOpen = false }) {
                    DURATIONS.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                duration = code
                                durationOpen = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason (optional)") },
                placeholder = { Text("manual ban from Traefik Manager") },
                supportingText = { Text("Stored as the decision scenario.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { onAdd(value, type, duration, reason) },
                enabled = !saving && value.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Adding" else "Add decision")
            }

            if (decisions.isNotEmpty()) {
                CardDivider(modifier = Modifier.padding(vertical = TmSpacing.sm))
                SectionLabel("Your decisions ${decisions.size}")
                decisions.take(20).forEach { decision ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = decision.value,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = decision.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.red,
                        )
                        Text(
                            text = decision.duration,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                            color = palette.muted,
                        )
                        IconButton(onClick = { onDelete(decision) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Unban ${decision.value}",
                                tint = palette.red,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
