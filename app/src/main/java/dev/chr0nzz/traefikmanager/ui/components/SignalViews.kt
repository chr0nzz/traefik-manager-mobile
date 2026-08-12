package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@Composable
fun SignalCard(
    label: String,
    hero: String,
    accent: Color,
    subtitle: String,
    modifier: Modifier = Modifier,
    heroUnit: String? = null,
    trailing: String? = null,
    trailingColor: Color? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val palette = LocalTmPalette.current
    TmCard(modifier = modifier, accentColor = accent) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SectionLabel(label, modifier = Modifier.weight(1f))
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = trailingColor ?: palette.muted,
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Text(
                text = hero,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (heroUnit != null) {
                Text(
                    text = heroUnit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                )
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (content != null) {
            CardDivider(modifier = Modifier.padding(top = TmSpacing.sm, bottom = TmSpacing.xs))
            content()
        }
    }
}

@Composable
fun SignalCells(cells: List<Color>, modifier: Modifier = Modifier, cap: Int = 160) {
    if (cells.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        cells.take(cap).forEach { color ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

@Composable
fun RankedRow(
    label: String,
    count: String,
    modifier: Modifier = Modifier,
    warn: String? = null,
    warnSevere: Boolean = false,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (warn != null) {
            Text(
                text = warn,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = if (warnSevere) palette.red else palette.yellow,
            )
        }
        Text(
            text = count,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Bold,
            ),
            color = palette.muted,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
        }
    }
}
