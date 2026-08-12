package dev.chr0nzz.traefikmanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.ClientIpDiagnostic
import dev.chr0nzz.traefikmanager.ui.components.DetailRow
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalTmPalette.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to settings")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { insets ->
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(insets))

            state.error != null -> ErrorState(
                headline = "Diagnostic unavailable",
                body = state.error,
                onRetry = viewModel::load,
                modifier = Modifier.padding(insets),
            )

            else -> {
                val diagnostic = state.diagnostic ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(insets),
                    contentPadding = PaddingValues(
                        start = TmSpacing.lg,
                        end = TmSpacing.lg,
                        top = TmSpacing.xs,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                ) {
                    item {
                        Text(
                            text = "What the manager sees when this device calls it. This is the address " +
                                "that feeds rate limits, the audit log, ipAllowList and CrowdSec matching.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.muted,
                        )
                    }
                    item { SectionLabel("Address") }
                    item {
                        TmCard {
                            DetailRow("Effective IP", diagnostic.effectiveIp.ifEmpty { "-" }, mono = true)
                            DetailRow("Class", diagnostic.effectiveClass.ifEmpty { "-" })
                            DetailRow("Socket peer", diagnostic.socketPeer.ifEmpty { "-" }, mono = true)
                            DetailRow(
                                label = "Peer class",
                                value = diagnostic.socketPeerClass.ifEmpty { "-" },
                                last = true,
                            )
                        }
                    }

                    item { SectionLabel("Proxy chain", modifier = Modifier.padding(top = TmSpacing.sm)) }
                    item {
                        TmCard {
                            Text(
                                text = if (diagnostic.proxyHops == 1) {
                                    "1 proxy hop"
                                } else {
                                    "${diagnostic.proxyHops} proxy hops"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (diagnostic.forwardedForChain.isEmpty()) {
                                Text(
                                    text = "No X-Forwarded-For chain",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.muted,
                                )
                            } else {
                                diagnostic.forwardedForChain.forEach { hop ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp),
                                    ) {
                                        Text(
                                            text = hop,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = diagnostic.classes[hop].orEmpty(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.muted,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { SectionLabel("Headers", modifier = Modifier.padding(top = TmSpacing.sm)) }
                    item {
                        TmCard {
                            ClientIpDiagnostic.HEADER_ORDER.forEachIndexed { index, header ->
                                DetailRow(
                                    label = header,
                                    value = diagnostic.headers[header].orEmpty().ifEmpty { "-" },
                                    mono = true,
                                    last = index == ClientIpDiagnostic.HEADER_ORDER.lastIndex,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
