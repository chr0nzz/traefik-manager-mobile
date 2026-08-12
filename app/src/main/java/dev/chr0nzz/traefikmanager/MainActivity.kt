package dev.chr0nzz.traefikmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.chr0nzz.traefikmanager.data.store.ThemeMode
import dev.chr0nzz.traefikmanager.ui.nav.RootViewModel
import dev.chr0nzz.traefikmanager.ui.nav.TmApp
import dev.chr0nzz.traefikmanager.ui.theme.TmTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: RootViewModel = hiltViewModel()
            val preferences by viewModel.preferences.collectAsState(initial = RootViewModel.DefaultPreferences)
            val apiState by viewModel.apiState.collectAsStateWithLifecycle()
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }
            TmTheme(darkTheme = darkTheme, dynamicColor = preferences.dynamicColor) {
                TmApp(
                    apiState = apiState,
                    migrationNotice = preferences.migrationNotice,
                    onNoticeShown = viewModel::onMigrationNoticeShown,
                )
            }
        }
    }
}
