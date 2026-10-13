package dev.chr0nzz.traefikmanager.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.store.LanguageOption

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
    val density by viewModel.density.collectAsStateWithLifecycle()
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val languages = viewModel.languages
    val configuration = LocalConfiguration.current
    val selectedLanguage = remember(configuration) { viewModel.selectedLanguage() }
    var languageSheet by rememberSaveable { mutableStateOf(false) }

    if (languageSheet) {
        LanguageSheet(
            languages = languages,
            selected = selectedLanguage,
            onSelect = { tag ->
                languageSheet = false
                viewModel.setLanguage(tag)
            },
            onDismiss = { languageSheet = false },
        )
    }

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
                title = { Text(stringResource(R.string.settings_appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.settings_back))
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
            item { SectionLabel(stringResource(R.string.appearance_theme)) }
            item {
                TmCard {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    ) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = preferences.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                modifier = Modifier.fillMaxHeight(),
                            ) {
                                Text(
                                    stringResource(
                                        when (mode) {
                                            ThemeMode.Light -> R.string.appearance_theme_light
                                            ThemeMode.Dark -> R.string.appearance_theme_dark
                                            ThemeMode.System -> R.string.appearance_theme_system
                                        },
                                    ),
                                )
                            }
                        }
                    }
                    CardDivider(modifier = Modifier.padding(vertical = TmSpacing.sm))
                    ToggleRow(
                        title = stringResource(R.string.appearance_dynamic),
                        subtitle = if (dynamicColorSupported) {
                            stringResource(R.string.appearance_dynamic_body)
                        } else {
                            stringResource(R.string.appearance_dynamic_unsupported)
                        },
                        checked = preferences.dynamicColor && dynamicColorSupported,
                        enabled = dynamicColorSupported,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
            }

            if (languages.size > 1) {
                item {
                    SectionLabel(
                        stringResource(R.string.settings_language_section),
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    )
                }
                item {
                    TmCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { languageSheet = true },
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_language_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = languages.firstOrNull { it.tag == selectedLanguage }?.name
                                        ?: stringResource(R.string.settings_language_system),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.muted,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = palette.muted,
                            )
                        }
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.appearance_navigation), modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.appearance_hide_nav),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.appearance_hide_nav_body),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.muted,
                            )
                        }
                        Switch(
                            checked = preferences.hideNavBar,
                            onCheckedChange = viewModel::setHideNavBar,
                        )
                    }
                    CardDivider(modifier = Modifier.padding(vertical = TmSpacing.sm))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.editNavBar() },
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.appearance_nav_items),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.appearance_nav_items_body),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.muted,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = palette.muted,
                        )
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.nav_dashboard), modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Text(
                        text = stringResource(R.string.appearance_density_body),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    ) {
                        listOf(
                            "list" to stringResource(R.string.appearance_density_rows),
                            "icons" to stringResource(R.string.appearance_density_icons),
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = density == key,
                                onClick = { viewModel.setDensity(key) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.appearance_security), modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    ToggleRow(
                        title = stringResource(R.string.appearance_lock),
                        subtitle = when {
                            !biometricAvailable ->
                                stringResource(R.string.appearance_lock_unavailable)
                            preferences.appLock ->
                                stringResource(R.string.appearance_lock_on)
                            else ->
                                stringResource(R.string.appearance_lock_off)
                        },
                        checked = preferences.appLock && biometricAvailable,
                        enabled = biometricAvailable,
                        onCheckedChange = viewModel::setAppLock,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.appearance_local_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    modifier = Modifier.padding(top = TmSpacing.xs),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(
    languages: List<LanguageOption>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val choices = listOf<Pair<String?, String>>(null to stringResource(R.string.settings_language_system)) +
        languages.map { it.tag to it.name }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TmSpacing.lg)
                .padding(bottom = TmSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TmCard {
                choices.forEachIndexed { index, (tag, name) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(tag) }
                            .padding(vertical = TmSpacing.sm),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (tag == selected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = palette.blue,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (index < choices.lastIndex) CardDivider()
                }
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
