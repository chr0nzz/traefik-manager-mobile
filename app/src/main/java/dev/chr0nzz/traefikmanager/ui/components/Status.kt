package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSize

enum class TmStatus { Ok, Warn, Error, Unknown, Disabled }

fun TmStatus.severity(): Int = when (this) {
    TmStatus.Error -> 0
    TmStatus.Warn -> 1
    TmStatus.Unknown -> 2
    TmStatus.Disabled -> 3
    TmStatus.Ok -> 4
}

@Composable
fun statusColor(status: TmStatus): Color {
    val palette = LocalTmPalette.current
    return when (status) {
        TmStatus.Ok -> palette.green
        TmStatus.Warn -> palette.yellow
        TmStatus.Error -> palette.red
        TmStatus.Unknown -> palette.muted
        TmStatus.Disabled -> palette.muted
    }
}

@Composable
fun StatusDot(
    status: TmStatus,
    modifier: Modifier = Modifier,
    size: Dp = TmSize.statusDot,
) {
    Spacer(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(statusColor(status)),
    )
}

@Composable
fun HealthLabel(
    status: TmStatus,
    text: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        StatusDot(status = status, size = 6.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (status == TmStatus.Ok) palette.green else statusColor(status),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignalStrip(
    cells: List<TmStatus>,
    modifier: Modifier = Modifier,
    cellSize: Dp = 6.dp,
    maxCells: Int = 150,
    emptyLabel: String? = null,
) {
    val palette = LocalTmPalette.current
    if (cells.isEmpty()) {
        if (emptyLabel != null) {
            Text(
                text = emptyLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
                modifier = modifier,
            )
        }
        return
    }
    val sorted = cells.sortedBy { it.severity() }
    val shown = sorted.take(maxCells)
    val overflow = sorted.size - shown.size
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        shown.forEach { cell -> StripCell(cell, cellSize) }
        if (overflow > 0) {
            Text(
                text = "+$overflow",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 3.dp),
            )
        }
    }
}

@Composable
private fun StripCell(status: TmStatus, size: Dp) {
    val palette = LocalTmPalette.current
    val shape = RoundedCornerShape(1.5.dp)
    val base = Modifier.size(size).clip(shape)
    when (status) {
        TmStatus.Ok -> Spacer(base.background(palette.muted.copy(alpha = 0.30f)))
        TmStatus.Warn -> Spacer(base.background(palette.yellow))
        TmStatus.Error -> Spacer(base.background(palette.red))
        TmStatus.Unknown, TmStatus.Disabled ->
            Spacer(Modifier.size(size).border(1.dp, palette.muted.copy(alpha = 0.55f), shape))
    }
}

@Composable
fun SegmentBar(
    ok: Int,
    warn: Int,
    error: Int,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    val total = ok + warn + error
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TmSize.stripHeight)
            .clip(RoundedCornerShape(TmSize.stripHeight / 2))
            .background(palette.border),
    ) {
        if (total == 0) return@Row
        Segment(weight = error, color = palette.red)
        Segment(weight = warn, color = palette.yellow)
        Segment(weight = ok, color = palette.muted.copy(alpha = 0.30f))
    }
}

@Composable
private fun RowScope.Segment(weight: Int, color: Color) {
    if (weight <= 0) return
    Spacer(
        modifier = Modifier
            .weight(weight.toFloat())
            .fillMaxHeight()
            .background(color),
    )
}
