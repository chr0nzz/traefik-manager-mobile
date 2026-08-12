package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.model.CountryCount
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@Composable
fun CountryStrip(
    countries: List<CountryCount>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    limit: Int = 6,
) {
    val palette = LocalTmPalette.current
    val total = countries.sumOf { it.count }.coerceAtLeast(1)

    TmCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionLabel("Geography", modifier = Modifier.weight(1f))
            Text(
                text = if (countries.size == 1) "1 country" else "${countries.size} countries",
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
        }
        countries.take(limit).forEach { country ->
            val active = selected == country.code
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(country.code) }
                    .padding(vertical = 3.dp),
            ) {
                Text(
                    text = country.flag,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = country.name },
                )
                Text(
                    text = country.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) palette.blue else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = country.count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = palette.muted,
                )
                Text(
                    text = "${country.count * 100 / total}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
        }
        if (countries.size > limit) {
            Text(
                text = "+${countries.size - limit} more ${if (countries.size - limit == 1) "country" else "countries"}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
