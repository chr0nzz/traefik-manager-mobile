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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Troubleshoot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Menu
import dev.chr0nzz.traefikmanager.ui.components.DrawerButton
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

data class SettingsEntry(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

object SettingsRoutes {
    const val APPEARANCE = "settings/appearance"
    const val CONNECTION = "settings/connection"
    const val AGENTS = "settings/agents"
    const val AUTH = "settings/auth"
    const val NOTIFICATIONS = "settings/notifications"
    const val DIAGNOSTICS = "settings/diagnostics"
    const val ABOUT = "settings/about"
    const val NOTIFICATION_HISTORY = "notifications"
    const val AGENT_CONFIG = "settings/agents/{agentId}"

    fun agentConfig(agentId: String): String = "settings/agents/$agentId"
}

private val SERVER_ENTRIES = listOf(
    SettingsEntry(
        route = SettingsRoutes.CONNECTION,
        title = "Traefik connection",
        subtitle = "API URL, credentials and a connection test",
        icon = Icons.Outlined.Router,
    ),
    SettingsEntry(
        route = SettingsRoutes.AGENTS,
        title = "Servers",
        subtitle = "Add, edit and remove agents",
        icon = Icons.Outlined.Dns,
    ),
    SettingsEntry(
        route = SettingsRoutes.AUTH,
        title = "Authentication",
        subtitle = "Login status and API keys",
        icon = Icons.Outlined.Security,
    ),
    SettingsEntry(
        route = SettingsRoutes.NOTIFICATIONS,
        title = "Notifications",
        subtitle = "Webhook delivery and test",
        icon = Icons.Outlined.Notifications,
    ),
)

private val APP_ENTRIES = listOf(
    SettingsEntry(
        route = SettingsRoutes.APPEARANCE,
        title = "Appearance and security",
        subtitle = "Theme, dynamic colour and the app lock",
        icon = Icons.Outlined.Palette,
    ),
    SettingsEntry(
        route = SettingsRoutes.DIAGNOSTICS,
        title = "Diagnostics",
        subtitle = "How this device reaches the server",
        icon = Icons.Outlined.Troubleshoot,
    ),
    SettingsEntry(
        route = SettingsRoutes.ABOUT,
        title = "About",
        subtitle = "Versions and open source licences",
        icon = Icons.Outlined.Info,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenDrawer: () -> Unit,
    onOpen: (String) -> Unit,
    onDisconnect: () -> Unit,
    activeServerName: String,
    hostSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    // Authentication, connection and notifications configure the host itself, never an agent.
    val serverEntries = if (hostSelected) {
        SERVER_ENTRIES
    } else {
        SERVER_ENTRIES.filter { it.route == SettingsRoutes.AGENTS }
    }
    val palette = LocalTmPalette.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

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
                title = { Text("Settings") },
                navigationIcon = {
                    DrawerButton(onOpenDrawer)
                },
                scrollBehavior = scrollBehavior,
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
                SectionLabel("Server · $activeServerName")
            }
            if (!hostSelected) {
                item {
                    Text(
                        text = "Authentication, connection and notification settings belong to the host. " +
                            "Switch to the host to change them.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                }
            }
            item {
                TmCard {
                    serverEntries.forEachIndexed { index, entry ->
                        SettingsRow(entry = entry, onClick = { onOpen(entry.route) })
                        if (index < serverEntries.lastIndex) CardDivider()
                    }
                }
            }
            item { SectionLabel("This app", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    APP_ENTRIES.forEachIndexed { index, entry ->
                        SettingsRow(entry = entry, onClick = { onOpen(entry.route) })
                        if (index < APP_ENTRIES.lastIndex) CardDivider()
                    }
                }
            }
            item {
                TmCard(onClick = onDisconnect) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = TmSpacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Login,
                            contentDescription = null,
                            tint = palette.red,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Disconnect",
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.red,
                            )
                            Text(
                                text = "Forget this server and its API key on this device",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(entry: SettingsEntry, onClick: () -> Unit) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = TmSpacing.sm),
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            tint = palette.blue,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier.size(18.dp),
        )
    }
}
