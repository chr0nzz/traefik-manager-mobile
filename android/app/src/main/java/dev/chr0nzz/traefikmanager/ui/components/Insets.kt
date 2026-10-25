package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + other.calculateStartPadding(direction),
        top = calculateTopPadding() + other.calculateTopPadding(),
        end = calculateEndPadding(direction) + other.calculateEndPadding(direction),
        bottom = calculateBottomPadding() + other.calculateBottomPadding(),
    )
}

@Composable
fun PaddingValues.only(
    start: Boolean = true,
    top: Boolean = true,
    end: Boolean = true,
    bottom: Boolean = true,
    extraBottom: Dp = 0.dp,
): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = if (start) calculateStartPadding(direction) else 0.dp,
        top = if (top) calculateTopPadding() else 0.dp,
        end = if (end) calculateEndPadding(direction) else 0.dp,
        bottom = (if (bottom) calculateBottomPadding() else 0.dp) + extraBottom,
    )
}
