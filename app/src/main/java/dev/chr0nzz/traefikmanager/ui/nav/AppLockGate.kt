package dev.chr0nzz.traefikmanager.ui.nav

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@Composable
fun AppLockGate(enabled: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }

    val prompt: () -> Unit = remember(activity) {
        {
            if (activity == null) {
                unlocked = true
            } else {
                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        unlocked = true
                        lastError = null
                    }

                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        lastError = message.toString()
                    }
                }
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Traefik Manager")
                    .setSubtitle("Confirm it is you before your servers are shown")
                    .setAllowedAuthenticators(AppLock.AUTHENTICATORS)
                    .build()
                BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
            }
        }
    }

    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (enabled && event == Lifecycle.Event.ON_STOP) unlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(enabled, unlocked) {
        if (enabled && !unlocked) prompt()
    }

    if (!enabled || unlocked) {
        content()
        return
    }

    val palette = LocalTmPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(TmSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "Locked",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = TmSpacing.md),
        )
        Text(
            text = lastError ?: "Unlock to see your servers.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = TmSpacing.xs),
        )
        Button(onClick = prompt, modifier = Modifier.padding(top = TmSpacing.lg)) {
            Text("Unlock")
        }
    }
}
