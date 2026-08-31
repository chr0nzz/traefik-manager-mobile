package dev.chr0nzz.traefikmanager

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
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
import dev.chr0nzz.traefikmanager.ui.nav.AppLockGate
import dev.chr0nzz.traefikmanager.ui.nav.TmApp
import dev.chr0nzz.traefikmanager.ui.theme.TmTheme
import dev.chr0nzz.traefikmanager.widget.OpenAppAction

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val widgetTarget = mutableStateOf<Pair<String, String?>?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetTarget.value = readWidgetTarget(intent)
    }

    private fun readWidgetTarget(intent: android.content.Intent?): Pair<String, String?>? {
        val destination = intent?.getStringExtra(OpenAppAction.EXTRA_DESTINATION) ?: return null
        return destination to intent.getStringExtra(OpenAppAction.EXTRA_SERVER_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetTarget.value = readWidgetTarget(intent)
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
                AppLockGate(enabled = preferences.appLock) {
                    val target by widgetTarget
                    TmApp(
                        apiState = apiState,
                        migrationNotice = preferences.migrationNotice,
                        onNoticeShown = viewModel::onMigrationNoticeShown,
                        widgetDestination = target?.first,
                        widgetServerId = target?.second,
                        onWidgetTargetHandled = { widgetTarget.value = null },
                    )
                }
            }
        }
    }
}
