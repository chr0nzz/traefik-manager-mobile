package dev.chr0nzz.traefikmanager.ui.settings

import android.os.Build
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.store.ThemeMode
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onClose: () -> Unit,
    biometricAvailable: Boolean,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val palette = LocalTmPalette.current
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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
                title = { Text("Appearance and security") },
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
            item { SectionLabel("Theme") }
            item {
                TmCard {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = preferences.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            ) {
                                Text(mode.name)
                            }
                        }
                    }
                    CardDivider(modifier = Modifier.padding(vertical = TmSpacing.sm))
                    ToggleRow(
                        title = "Dynamic colour",
                        subtitle = if (dynamicColorSupported) {
                            "Take the palette from your wallpaper"
                        } else {
                            "Needs Android 12 or newer"
                        },
                        checked = preferences.dynamicColor && dynamicColorSupported,
                        enabled = dynamicColorSupported,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
            }

            item { SectionLabel("Security", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    ToggleRow(
                        title = "Require unlock",
                        subtitle = when {
                            !biometricAvailable ->
                                "No biometric or device credential is enrolled on this device"
                            preferences.appLock ->
                                "Asked for on launch and whenever the app returns from the background"
                            else ->
                                "Ask for biometrics or your device PIN before showing anything"
                        },
                        checked = preferences.appLock && biometricAvailable,
                        enabled = biometricAvailable,
                        onCheckedChange = viewModel::setAppLock,
                    )
                }
            }

            item {
                Text(
                    text = "These settings live on this device only. They are not sent to the server.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    modifier = Modifier.padding(top = TmSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TmSpacing.xs),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else palette.muted,
            )
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = palette.muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
