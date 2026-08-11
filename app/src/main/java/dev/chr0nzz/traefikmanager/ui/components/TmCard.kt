package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmRadius
import dev.chr0nzz.traefikmanager.ui.theme.TmSize
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@Composable
fun TmCard(
    modifier: Modifier = Modifier,
    accent: TmStatus? = null,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalTmPalette.current
    val shape = RoundedCornerShape(TmRadius.md)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.5f else 1f),
        shape = shape,
        color = palette.card,
        border = BorderStroke(1.dp, palette.border),
    ) {
        Row(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        ) {
            if (accent != null) {
                Spacer(
                    modifier = Modifier
                        .width(TmSize.accentBar)
                        .fillMaxHeight()
                        .background(statusColor(accent)),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(TmSpacing.md),
                content = content,
            )
        }
    }
}

@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    status: TmStatus? = null,
) {
    val palette = LocalTmPalette.current
    Box(modifier = modifier.size(TmSize.iconTile)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(TmRadius.sm))
                .background(palette.bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.muted,
                modifier = Modifier.size(16.dp),
            )
        }
        if (status != null) {
            StatusDot(
                status = status,
                size = 7.dp,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
fun CardTitleRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    status: TmStatus? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
    ) {
        if (icon != null) {
            IconTile(icon = icon, status = status)
        } else if (status != null) {
            StatusDot(status = status)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    val palette = LocalTmPalette.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
        color = palette.muted,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = MonoFamily,
            letterSpacing = androidx.compose.ui.unit.TextUnit(0.1f, androidx.compose.ui.unit.TextUnitType.Em),
        ),
        color = palette.muted,
        modifier = modifier,
    )
}

@Composable
fun CountChip(
    count: Int,
    label: String,
    status: TmStatus,
    modifier: Modifier = Modifier,
) {
    val color = statusColor(status)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(TmRadius.sm))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = TmSpacing.sm, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFamily),
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    val palette = LocalTmPalette.current
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.border),
    )
}
