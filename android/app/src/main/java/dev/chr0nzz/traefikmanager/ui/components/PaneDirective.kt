package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun tmPaneScaffoldDirective(): PaneScaffoldDirective =
    calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
        .copy(horizontalPartitionSpacerSize = 0.dp)
