package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * The default directive adds a 24dp spacer between panes on top of the 16dp each pane already
 * pads its own content by, which reads as a 56dp trench on a tablet. Drop the spacer and let the
 * panes' own padding do the separating, so the gutter matches the gap between cards.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun tmPaneScaffoldDirective(): PaneScaffoldDirective =
    calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
        .copy(horizontalPartitionSpacerSize = 0.dp)
