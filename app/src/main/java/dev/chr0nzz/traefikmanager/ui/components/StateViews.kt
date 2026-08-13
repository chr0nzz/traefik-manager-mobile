package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.chr0nzz.traefikmanager.BuildConfig
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material3.TextButton
import android.widget.Toast
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "Loading") {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        ContainedLoadingIndicator()
    }
}

@Composable
fun MessageState(
    icon: ImageVector,
    headline: String,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val palette = LocalTmPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(TmSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = headline,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}

@Composable
fun EmptyState(
    headline: String,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) = MessageState(
    icon = Icons.Outlined.Inbox,
    headline = headline,
    body = body,
    actionLabel = actionLabel,
    onAction = onAction,
    modifier = modifier,
)

/**
 * A failure the user can report. The details go to the clipboard with the app and device stamped
 * on them, because a minified build's own message rarely says enough on its own.
 */
@Composable
fun ErrorState(
    headline: String,
    body: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    MessageState(
        icon = Icons.Outlined.CloudOff,
        headline = headline,
        body = body,
        actionLabel = if (onRetry != null) "Retry" else null,
        onAction = onRetry,
        modifier = modifier,
        secondaryLabel = "Copy details",
        onSecondary = {
            clipboard.setText(AnnotatedString(diagnosticsText(headline, body)))
            Toast.makeText(context, "Details copied", Toast.LENGTH_SHORT).show()
        },
    )
}

private fun diagnosticsText(headline: String, body: String?): String = buildString {
    appendLine("Traefik Manager ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine(headline)
    body?.let { appendLine(it) }
}.trim()
