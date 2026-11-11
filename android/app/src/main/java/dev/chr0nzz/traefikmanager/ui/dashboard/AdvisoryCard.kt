package dev.chr0nzz.traefikmanager.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.AdvisoryNotice
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@Composable
fun AdvisoryCard(notice: AdvisoryNotice, onDetails: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalTmPalette.current
    val advisory = notice.advisory
    TmCard(accent = TmStatus.Error) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            Icon(Icons.Outlined.GppMaybe, contentDescription = null, tint = palette.red, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.advisory_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        val body = buildList {
            add(
                stringResource(
                    R.string.advisory_body,
                    notice.version,
                    advisory.id,
                    stringResource(advisory.severity.label),
                    stringResource(advisory.summary),
                ),
            )
            if (notice.forwardAuthInUse) add(stringResource(R.string.advisory_forward_auth))
            add(
                if (advisory.fixedIn.size > 1) {
                    stringResource(R.string.advisory_update_either, advisory.fixedIn[0], advisory.fixedIn[1])
                } else {
                    stringResource(R.string.advisory_update, advisory.fixedIn.first())
                },
            )
        }.joinToString(" ")
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
            modifier = Modifier.padding(top = TmSpacing.xs),
        )
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.advisory_dismiss)) }
            TextButton(onClick = onDetails) { Text(stringResource(R.string.advisory_details)) }
        }
    }
}
