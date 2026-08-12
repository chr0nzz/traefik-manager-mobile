package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

/** An icon-and-number chip, the web's sig-flag. */
data class SignalChip(val icon: ImageVector?, val text: String, val color: Color? = null)

@Composable
fun SignalCard(
    label: String,
    hero: String,
    accent: Color,
    subtitle: String,
    modifier: Modifier = Modifier,
    heroUnit: String? = null,
    /** Chips beside the hero, right aligned, as the web puts its sig-flags. */
    flags: List<SignalChip> = emptyList(),
    /** The card's foot: quiet counts that break the hero down. */
    footer: List<SignalChip> = emptyList(),
    trailing: String? = null,
    trailingColor: Color? = null,
    /** Tinted glyph in the card head, as the web renders one per card. */
    glyph: ImageVector? = null,
    /** Overrides the rail when the card is reporting a problem. */
    health: Color? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val palette = LocalTmPalette.current
    TmCard(modifier = modifier, accentColor = health) {
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
            if (glyph != null) {
                Icon(
                    imageVector = glyph,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .padding(start = TmSpacing.xs)
                        .size(14.dp),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        ) {
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
            if (flags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = TmSpacing.sm, bottom = 3.dp),
                ) {
                    flags.forEach { ChipView(it) }
                }
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
        if (footer.isNotEmpty()) {
            CardDivider(modifier = Modifier.padding(top = TmSpacing.xs, bottom = TmSpacing.xs))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                footer.forEach { ChipView(it) }
            }
        }
    }
}

@Composable
private fun ChipView(chip: SignalChip) {
    val palette = LocalTmPalette.current
    val tint = chip.color ?: palette.muted
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (chip.icon != null) {
            Icon(
                imageVector = chip.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(11.dp),
            )
        }
        Text(
            text = chip.text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = tint,
        )
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
                    .clip(RoundedCornerShape(1.5.dp))
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
    warnIcon: ImageVector? = null,
    trailing: String? = null,
    /** A coloured rail on the leading edge, as the web puts on every ranked row. */
    rail: Color? = null,
    leading: (@Composable () -> Unit)? = null,
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
        if (rail != null) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(rail),
            )
        }
        if (leading != null) leading()
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (warn != null) {
            val tint = if (warnSevere) palette.red else palette.yellow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (warnIcon != null) {
                    Icon(
                        imageVector = warnIcon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Text(
                    text = warn,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = tint,
                )
            }
        }
        Text(
            text = count,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
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
