package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable

fun <T> distinctKeys(items: List<T>, key: (T) -> String): List<String> {
    val used = HashSet<String>(items.size * 2)
    return items.map { item ->
        val base = key(item)
        var candidate = base
        var count = 1
        while (!used.add(candidate)) {
            count += 1
            candidate = "$base#$count"
        }
        candidate
    }
}

inline fun <T> LazyListScope.distinctItems(
    items: List<T>,
    noinline key: (T) -> String,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit,
) {
    val keys = distinctKeys(items, key)
    items(items.size, key = { keys[it] }) { index -> itemContent(items[index]) }
}
