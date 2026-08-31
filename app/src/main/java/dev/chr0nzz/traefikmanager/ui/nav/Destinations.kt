package dev.chr0nzz.traefikmanager.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector

enum class TmSection(val label: String) {
    Traffic("Traffic"),
    Observability("Observability"),
    Infrastructure("Infrastructure"),
    System("System"),
}

enum class TmDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val section: TmSection,
    val primary: Boolean = false,
    val serverTab: String? = null,
) {
    Home("home", "Dashboard", Icons.Outlined.Home, TmSection.Traffic, primary = true),
    Routes("routes", "Routes", Icons.Outlined.AltRoute, TmSection.Traffic, primary = true),
    Middlewares("middlewares", "Middleware", Icons.Outlined.Layers, TmSection.Traffic, primary = true),
    Services("services", "Services", Icons.Outlined.Dns, TmSection.Traffic),
    RouteMap(
        route = "routemap",
        label = "Route map",
        icon = Icons.Outlined.AccountTree,
        section = TmSection.Traffic,
        serverTab = "routemap",
    ),
    Logs(
        route = "logs",
        label = "Logs",
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        section = TmSection.Observability,
        primary = true,
        serverTab = "logs",
    ),
    CrowdSec(
        route = "crowdsec",
        label = "CrowdSec",
        icon = Icons.Outlined.Shield,
        section = TmSection.Observability,
        primary = true,
        serverTab = "crowdsec",
    ),
    Certificates(
        route = "certificates",
        label = "Certificates",
        icon = Icons.Outlined.VerifiedUser,
        section = TmSection.Infrastructure,
        serverTab = "certs",
    ),
    Plugins(
        route = "plugins",
        label = "Plugins",
        icon = Icons.Outlined.Extension,
        section = TmSection.Infrastructure,
        serverTab = "plugins",
    ),
    Backups(
        route = "backups",
        label = "Backups",
        icon = Icons.Outlined.Inventory2,
        section = TmSection.System,
    ),
    Settings("settings", "Settings", Icons.Outlined.Settings, TmSection.System),
    ;

    companion object {
        val primaryEntries: List<TmDestination> get() = entries.filter { it.primary }
    }
}
