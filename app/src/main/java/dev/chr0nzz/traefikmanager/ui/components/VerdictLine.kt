package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.repo.Verdict
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerdictLine(verdict: Verdict, info: String? = null) {
    val palette = LocalTmPalette.current
    TmCard(accent = verdict.status) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            StatusDot(verdict.status)
            Text(
                text = verdict.headline,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (info != null) {
                val tooltipState = rememberTooltipState(isPersistent = true)
                val scope = rememberCoroutineScope()
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(info) } },
                    state = tooltipState,
                ) {
                    IconButton(onClick = { scope.launch { tooltipState.show() } }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = info,
                            tint = palette.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = verdict.detail,
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
