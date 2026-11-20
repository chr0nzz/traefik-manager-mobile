package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass

private val LocalContentWidth = compositionLocalOf<Dp?> { null }

@Composable
fun ProvideContentWidth(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(LocalContentWidth provides maxWidth, content = content)
    }
}

@Composable
fun contentAdaptiveInfo(): WindowAdaptiveInfo {
    val window = currentWindowAdaptiveInfo()
    val width = LocalContentWidth.current ?: return window
    val sizeClass = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(
        widthDp = width.value,
        heightDp = window.windowSizeClass.minHeightDp.toFloat(),
    )
    return WindowAdaptiveInfo(sizeClass, window.windowPosture)
}
