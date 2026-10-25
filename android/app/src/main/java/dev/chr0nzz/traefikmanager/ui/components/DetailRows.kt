package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@Composable
fun DetailRow(
    label: String,
    value: String,
    mono: Boolean = false,
    valueColor: Color? = null,
    last: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TmSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(label, modifier = Modifier.weight(0.42f))
        Text(
            text = value,
            style = if (mono) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f),
        )
    }
    if (!last) CardDivider()
}

@Composable
fun ValueRow(
    icon: ImageVector,
    value: String,
    color: Color,
    extra: String? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        modifier = modifier.padding(top = TmSpacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (extra != null) {
            Text(
                text = extra,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
            )
        }
    }
}
