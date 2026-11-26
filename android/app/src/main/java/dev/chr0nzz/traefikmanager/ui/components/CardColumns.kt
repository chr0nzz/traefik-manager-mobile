package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

val MinCardWidth = 280.dp

fun cardColumnCount(width: Dp, sidePadding: Dp = TmSpacing.lg, gap: Dp = TmSpacing.sm): Int {
    val usable = width - sidePadding * 2
    return maxOf(1, ((usable + gap) / (MinCardWidth + gap)).toInt())
}

@Composable
fun CardColumns(modifier: Modifier = Modifier, content: @Composable (columns: Int) -> Unit) {
    BoxWithConstraints(modifier) { content(cardColumnCount(maxWidth)) }
}

inline fun <T> LazyListScope.cardRows(
    items: List<T>,
    columns: Int,
    noinline key: (T) -> String,
    crossinline itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    if (columns <= 1) {
        distinctItems(items, key) { item -> itemContent(item, Modifier.animateItem()) }
        return
    }
    val keys = distinctKeys(items, key)
    val rows = items.indices.chunked(columns)
    items(rows.size, key = { "row:${keys[rows[it].first()]}" }) { index ->
        val row = rows[index]
        Row(
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .animateItem(),
        ) {
            row.forEach { position ->
                itemContent(items[position], Modifier.weight(1f).fillMaxHeight())
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
