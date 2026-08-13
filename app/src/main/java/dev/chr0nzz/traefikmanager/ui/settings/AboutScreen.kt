package dev.chr0nzz.traefikmanager.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.BuildConfig
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.DetailRow
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

private data class Attribution(val name: String, val licence: String)

/**
 * Every third-party component shipped in the APK. Apache-2.0 is satisfied by attribution here;
 * MIT and OFL require their notice text, which lives in [OSS_NOTICES].
 */
private val ATTRIBUTIONS = listOf(
    Attribution("AndroidX (core, activity, lifecycle, navigation, datastore, biometric, window, glance, work)", "Apache-2.0"),
    Attribution("Jetpack Compose and Material 3", "Apache-2.0"),
    Attribution("Kotlin standard library, coroutines and kotlinx.serialization", "Apache-2.0"),
    Attribution("Dagger Hilt", "Apache-2.0"),
    Attribution("Retrofit", "Apache-2.0"),
    Attribution("OkHttp and Okio", "Apache-2.0"),
    Attribution("Coil", "Apache-2.0"),
    Attribution("MaterialKolor", "MIT"),
    Attribution("Google material-color-utilities", "Apache-2.0"),
    Attribution("Inter", "SIL Open Font License 1.1"),
    Attribution("JetBrains Mono", "SIL Open Font License 1.1"),
)

private val OSS_NOTICES = listOf(
    "MaterialKolor is distributed under the MIT License. Copyright (c) Jordon de Hoog. " +
        "Permission is hereby granted, free of charge, to any person obtaining a copy of this " +
        "software and associated documentation files (the \"Software\"), to deal in the Software " +
        "without restriction, including without limitation the rights to use, copy, modify, merge, " +
        "publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons " +
        "to whom the Software is furnished to do so, subject to the following conditions: the above " +
        "copyright notice and this permission notice shall be included in all copies or substantial " +
        "portions of the Software. THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND.",
    "Inter is Copyright (c) The Inter Project Authors, and JetBrains Mono is Copyright (c) " +
        "The JetBrains Mono Project Authors. Both are licensed under the SIL Open Font License, " +
        "Version 1.1. The fonts and derivatives may be bundled, embedded, redistributed and sold " +
        "with any software provided that reserved names are not used, and that the licence text " +
        "accompanies the fonts. The full OFL 1.1 text is shipped with this app.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalTmPalette.current
    val uriHandler = LocalUriHandler.current

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
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to settings")
                    }
                },
            )
        },
    ) { insets ->
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
                TmCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_tm_logo),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            text = "Traefik Manager",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "GPL-3.0 licence · xyzlab.dev",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.blue,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { uriHandler.openUri("https://xyzlab.dev") },
                    )
                    CardDivider(modifier = Modifier.padding(vertical = TmSpacing.sm))
                    DetailRow("App", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", mono = true)
                    DetailRow("Manager", state.managerVersion, mono = true)
                    DetailRow(
                        label = "Traefik",
                        value = state.traefikVersion,
                        mono = true,
                        last = true,
                    )
                }
            }

            item { SectionLabel("Open source licences", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    ATTRIBUTIONS.forEachIndexed { index, attribution ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = TmSpacing.xs),
                        ) {
                            Text(
                                text = attribution.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = attribution.licence,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                                color = palette.muted,
                            )
                        }
                        if (index < ATTRIBUTIONS.lastIndex) CardDivider()
                    }
                }
            }

            items(OSS_NOTICES) { notice ->
                TmCard {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                }
            }
        }
    }
}
